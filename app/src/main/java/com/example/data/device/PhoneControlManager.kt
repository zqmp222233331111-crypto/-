package com.example.data.device

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import com.example.service.AccessibilityActionOutcome
import com.example.service.ShailaAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed class PhoneAction {
    data class OpenApp(val appName: String) : PhoneAction()
    data class TypeText(val text: String, val append: Boolean = false, val submitAfter: Boolean = false) : PhoneAction()
    data class TapText(val label: String) : PhoneAction()
    data object Submit : PhoneAction()
    data class Scroll(val direction: String) : PhoneAction()
    data class SystemNav(val action: String) : PhoneAction()
    data class OpenUrl(val urlOrQuery: String) : PhoneAction()
    data class Dial(val phoneNumber: String) : PhoneAction()
    data class Flashlight(val turnOn: Boolean) : PhoneAction()
    data class SetAlarm(val hour: Int, val minute: Int, val label: String = "Shaila Alarm") : PhoneAction()
}

data class PhoneExecutionSummary(
    val executedCount: Int = 0,
    val anySucceeded: Boolean = false,
    val needsAccessibilitySetup: Boolean = false,
    val feedbackMessageBn: String = "",
    val feedbackMessageEn: String = ""
)

object PhoneActionParser {

    private val ACTION_REGEX = Regex("""\[\[ACTION:([A-Z_]+)(?:\|([^\]]*))?\]\]""")

    fun parseActionTags(rawText: String): Pair<String, List<PhoneAction>> {
        val actions = mutableListOf<PhoneAction>()
        ACTION_REGEX.findAll(rawText).forEach { match ->
            val type = match.groupValues.getOrNull(1)?.trim().orEmpty()
            val arg = match.groupValues.getOrNull(2)?.trim().orEmpty()
            when (type) {
                "OPEN_APP" -> if (arg.isNotEmpty()) actions.add(PhoneAction.OpenApp(arg))
                "TYPE_TEXT" -> if (arg.isNotEmpty()) actions.add(PhoneAction.TypeText(arg))
                "APPEND_TEXT" -> if (arg.isNotEmpty()) actions.add(PhoneAction.TypeText(arg, append = true))
                "TAP_TEXT", "CLICK", "TAP" -> if (arg.isNotEmpty()) actions.add(PhoneAction.TapText(arg))
                "SUBMIT", "ENTER", "SEND" -> actions.add(PhoneAction.Submit)
                "SCROLL" -> actions.add(PhoneAction.Scroll(arg.ifBlank { "down" }))
                "GLOBAL", "NAV" -> if (arg.isNotEmpty()) actions.add(PhoneAction.SystemNav(arg))
                "OPEN_URL", "URL" -> if (arg.isNotEmpty()) actions.add(PhoneAction.OpenUrl(arg))
                "DIAL", "CALL" -> if (arg.isNotEmpty()) actions.add(PhoneAction.Dial(arg))
                "FLASHLIGHT", "TORCH" -> {
                    val on = arg.lowercase() in listOf("on", "1", "true", "চালু", "অন")
                    actions.add(PhoneAction.Flashlight(on))
                }
                "ALARM" -> {
                    parseTimePair(arg)?.let { (h, m) ->
                        actions.add(PhoneAction.SetAlarm(h, m))
                    }
                }
            }
        }
        val cleanText = rawText
            .replace(ACTION_REGEX, "")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
        return cleanText to actions
    }

    /**
     * Safety net when an LLM omits [[ACTION:...]] tags on explicit phone-control requests.
     */
    fun inferFallbackActionsFromUserPrompt(userPrompt: String): List<PhoneAction> {
        val text = userPrompt.trim()
        if (text.isEmpty()) return emptyList()
        val lower = text.lowercase()
        val actions = mutableListOf<PhoneAction>()

        // 1. Flashlight
        if (lower.contains("টর্চ") || lower.contains("ফ্ল্যাশ") || lower.contains("flashlight") || lower.contains("torch")) {
            val turnOff = lower.contains("বন্ধ") || lower.contains("অফ") || lower.contains("off")
            actions.add(PhoneAction.Flashlight(!turnOff))
            return actions
        }

        // 2. Dial phone number
        if (lower.contains("কল") || lower.contains("call") || lower.contains("dial")) {
            val digits = Regex("""\+?[0-9][0-9\- ]{4,15}[0-9]""").find(text)?.value?.replace(" ", "")
            if (!digits.isNullOrBlank()) {
                actions.add(PhoneAction.Dial(digits))
                return actions
            }
        }

        // 3. System navigation
        if (lower == "ব্যাক" || lower.contains("ব্যাক করো") || lower.contains("পিছনে যাও") || lower == "go back") {
            return listOf(PhoneAction.SystemNav("back"))
        }
        if (lower.contains("হোমে যাও") || lower.contains("হোম স্ক্রিন") || lower == "go home") {
            return listOf(PhoneAction.SystemNav("home"))
        }
        if (lower.contains("নোটিফিকেশন দেখাও") || lower.contains("notifications")) {
            return listOf(PhoneAction.SystemNav("notifications"))
        }

        // 4. Scroll
        if (lower.contains("নিচে স্ক্রল") || lower.contains("নিচের দিকে স্ক্রল") || lower.contains("scroll down")) {
            return listOf(PhoneAction.Scroll("down"))
        }
        if (lower.contains("উপরে স্ক্রল") || lower.contains("উপরের দিকে স্ক্রল") || lower.contains("scroll up")) {
            return listOf(PhoneAction.Scroll("up"))
        }

        // 5. Known app opening (+ optional search query inside app)
        val knownAppKeywords = listOf(
            "youtube" to listOf("youtube", "ইউটিউব", "ইউ টিউব"),
            "whatsapp" to listOf("whatsapp", "হোয়াটসঅ্যাপ", "ওয়াটসঅ্যাপ", "হোয়াটস আপ"),
            "facebook" to listOf("facebook", "ফেসবুক"),
            "messenger" to listOf("messenger", "মেসেঞ্জার"),
            "chrome" to listOf("chrome", "ক্রোম", "গুগল ক্রোম", "ব্রাউজার", "browser"),
            "instagram" to listOf("instagram", "ইনস্টাগ্রাম", "ইন্সটাগ্রাম"),
            "tiktok" to listOf("tiktok", "টিকটক"),
            "telegram" to listOf("telegram", "টেলিগ্রাম"),
            "imo" to listOf("imo", "ইমো"),
            "gmail" to listOf("gmail", "জিমেইল"),
            "maps" to listOf("google maps", "maps", "ম্যাপস", "ম্যাপ"),
            "play store" to listOf("play store", "প্লে স্টোর", "প্লেস্টোর"),
            "settings" to listOf("settings", "সেটিংস", "সেটিং"),
            "camera" to listOf("camera", "ক্যামেরা"),
            "gallery" to listOf("gallery", "গ্যালারি"),
            "calculator" to listOf("calculator", "ক্যালকুলেটর"),
            "clock" to listOf("clock", "অ্যালার্ম", "ঘড়ি"),
            "messages" to listOf("messages", "এসএমএস", "মেসেজ অ্যাপ"),
            "bkash" to listOf("bkash", "বিকাশ"),
            "nagad" to listOf("nagad", "নগদ"),
            "spotify" to listOf("spotify", "স্পটিফাই"),
            "capcut" to listOf("capcut", "ক্যাপকাট"),
            "google" to listOf("গুগল", "google")
        )

        val hasOpenVerb = listOf("খোল", "খুল", "ওপেন", "চালু", "open", "launch", "start", "চালাও", "খুলে")
            .any { lower.contains(it) }

        if (hasOpenVerb) {
            var matchedApp: String? = null
            var matchedAlias: String? = null
            for ((canonical, aliases) in knownAppKeywords) {
                val hit = aliases.firstOrNull { lower.contains(it) }
                if (hit != null) {
                    matchedApp = canonical
                    matchedAlias = hit
                    break
                }
            }

            if (matchedApp == null) {
                val enOpen = Regex("""open\s+([a-zA-Z0-9 ._-]{2,24})""", RegexOption.IGNORE_CASE).find(text)
                val bnOpen = Regex("""([^\s,।?!]{2,24})\s+(?:অ্যাপ\s+)?(?:খোলো|খোল|খুলুন|খুলে\s+দাও|ওপেন\s+করো|চালু\s+করো)""").find(text)
                matchedApp = (enOpen?.groupValues?.getOrNull(1) ?: bnOpen?.groupValues?.getOrNull(1))?.trim()
            }

            if (!matchedApp.isNullOrBlank()) {
                actions.add(PhoneAction.OpenApp(matchedApp))
                // Also detect chained search/type query after opening the app
                val afterApp = if (matchedAlias != null) {
                    val idx = lower.indexOf(matchedAlias)
                    text.substring((idx + matchedAlias.length).coerceAtMost(text.length)).trim()
                } else {
                    ""
                }
                val searchQuery = extractTypedOrSearchQuery(afterApp)
                if (!searchQuery.isNullOrBlank()) {
                    if (matchedApp == "youtube" || matchedApp == "chrome" || matchedApp == "google") {
                        actions.add(PhoneAction.TapText("Search"))
                    }
                    actions.add(PhoneAction.TypeText(searchQuery))
                    if (lower.contains("সার্চ") || lower.contains("search") || lower.contains("পাঠা") || lower.contains("send")) {
                        actions.add(PhoneAction.Submit)
                    }
                }
                return actions
            }
        }

        // 6. Direct typing instruction without opening a new app
        val hasTypeVerb = listOf("টাইপ কর", "লিখে দাও", "লেখো", "লিখো", "type ", "write ")
            .any { lower.contains(it) }
        if (hasTypeVerb && !lower.contains("কবিতা") && !lower.contains("গল্প") && !lower.contains("রচনা") && !lower.contains("ক্যাপশন")) {
            val typed = extractDirectTypeText(text)
            if (!typed.isNullOrBlank()) {
                actions.add(PhoneAction.TypeText(typed))
                if (lower.contains("পাঠিয়ে দাও") || lower.contains("পাঠাও") || lower.contains("সেন্ড") || lower.contains("সার্চ")) {
                    actions.add(PhoneAction.Submit)
                }
                return actions
            }
        }

        return emptyList()
    }

    private fun extractTypedOrSearchQuery(tail: String): String? {
        if (tail.isBlank()) return null
        val patterns = listOf(
            Regex("""(?:খুলে|ওপেন করে|চালু করে)\s+(.+?)\s+(?:লিখে\s+সার্চ|সার্চ\s+কর|লিখে\s+দাও|টাইপ\s+কর|লেখো|লিখো|search|type)""", RegexOption.IGNORE_CASE),
            Regex("""(?:এ|তে|য়ে)\s+(.+?)\s+(?:সার্চ\s+কর|খুঁজো|লিখে\s+দাও|লেখো|search)""", RegexOption.IGNORE_CASE),
            Regex("""(?:type|search)\s+(.+)""", RegexOption.IGNORE_CASE)
        )
        for (p in patterns) {
            val found = p.find(tail)?.groupValues?.getOrNull(1)?.trim()
            if (!found.isNullOrBlank()) return found
        }
        return null
    }

    private fun extractDirectTypeText(text: String): String? {
        val patterns = listOf(
            Regex("""(?:টাইপ করো|টাইপ কর|লিখে দাও|লেখো|লিখো)\s*[:\-–]?\s*["']?(.+?)["']?$"""),
            Regex("""(?:type|write)\s+["']?(.+?)["']?$""", RegexOption.IGNORE_CASE)
        )
        for (p in patterns) {
            val found = p.find(text)?.groupValues?.getOrNull(1)?.trim()
            if (!found.isNullOrBlank()) {
                return found.removeSuffix("পাঠাও").removeSuffix("সেন্ড করো").removeSuffix("সার্চ করো").trim()
            }
        }
        return null
    }

    private fun parseTimePair(raw: String): Pair<Int, Int>? {
        val m = Regex("""(\d{1,2})[:.](\d{2})""").find(raw) ?: return null
        val h = m.groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: return null
        val min = m.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: return null
        return h to min
    }
}

class PhoneControlManager(context: Context) {

    private val appContext = context.applicationContext

    fun isAccessibilityEnabled(): Boolean {
        if (ShailaAccessibilityService.instance != null) return true
        return runCatching {
            val expected = ComponentName(appContext, ShailaAccessibilityService::class.java)
            val enabledServices = Settings.Secure.getString(
                appContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            enabledServices.split(':')
                .mapNotNull { ComponentName.unflattenFromString(it) }
                .any { it == expected }
        }.getOrDefault(false)
    }

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { appContext.startActivity(intent) }
    }

    private val appAliases: Map<String, List<String>> = mapOf(
        "youtube" to listOf("youtube", "ইউটিউব", "ইউ টিউব", "yt"),
        "whatsapp" to listOf("whatsapp", "হোয়াটসঅ্যাপ", "হোয়াটস আপ", "ওয়াটসঅ্যাপ"),
        "facebook" to listOf("facebook", "ফেসবুক", "fb"),
        "messenger" to listOf("messenger", "মেসেঞ্জার"),
        "chrome" to listOf("chrome", "ক্রোম", "গুগল ক্রোম", "browser", "ব্রাউজার"),
        "instagram" to listOf("instagram", "ইনস্টাগ্রাম", "ইন্সটাগ্রাম", "insta"),
        "tiktok" to listOf("tiktok", "টিকটক"),
        "telegram" to listOf("telegram", "টেলিগ্রাম"),
        "imo" to listOf("imo", "ইমো"),
        "gmail" to listOf("gmail", "জিমেইল", "email", "ইমেইল"),
        "maps" to listOf("maps", "google maps", "ম্যাপ", "ম্যাপস"),
        "play store" to listOf("play store", "google play", "প্লে স্টোর", "প্লেস্টোর"),
        "settings" to listOf("settings", "সেটিংস", "সেটিং"),
        "camera" to listOf("camera", "ক্যামেরা"),
        "gallery" to listOf("gallery", "photos", "গ্যালারি", "ফটো", "ছবি"),
        "phone" to listOf("phone", "dialer", "ফোন", "কল", "ডায়ালার"),
        "messages" to listOf("messages", "sms", "মেসেজ", "এসএমএস"),
        "calculator" to listOf("calculator", "ক্যালকুলেটর"),
        "clock" to listOf("clock", "alarm", "ঘড়ি", "অ্যালার্ম"),
        "calendar" to listOf("calendar", "ক্যালেন্ডার"),
        "files" to listOf("files", "my files", "ফাইল", "ফাইল ম্যানেজার"),
        "bkash" to listOf("bkash", "বিকাশ"),
        "nagad" to listOf("nagad", "নগদ"),
        "rocket" to listOf("rocket", "রকেট"),
        "google" to listOf("google", "গুগল"),
        "spotify" to listOf("spotify", "স্পটিফাই"),
        "capcut" to listOf("capcut", "ক্যাপকাট"),
        "snapchat" to listOf("snapchat", "স্ন্যাপচ্যাট")
    )

    private fun resolveCanonicalAppTerms(rawQuery: String): List<String> {
        val q = rawQuery.lowercase().trim()
        val terms = mutableListOf(q)
        for ((canonical, aliases) in appAliases) {
            if (aliases.any { q == it || q.contains(it) || it.contains(q) }) {
                terms.add(canonical)
                terms.addAll(aliases)
            }
        }
        return terms.distinct()
    }

    fun openApp(rawAppName: String): AccessibilityActionOutcome {
        val query = rawAppName.trim()
        if (query.isEmpty()) {
            return AccessibilityActionOutcome(false, "অ্যাপের নাম বলা হয়নি।")
        }

        val pm = appContext.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val activities = runCatching {
            pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        }.getOrElse {
            pm.queryIntentActivities(launcherIntent, 0)
        }

        val searchTerms = resolveCanonicalAppTerms(query)
        var bestPackage: String? = null
        var bestLabel: String? = null
        var bestScore = 0

        for (resolveInfo in activities) {
            val pkg = resolveInfo.activityInfo?.packageName ?: continue
            if (pkg == appContext.packageName) continue
            val label = resolveInfo.loadLabel(pm)?.toString().orEmpty().trim()
            val lowerLabel = label.lowercase()
            val lowerPkg = pkg.lowercase()

            for (term in searchTerms) {
                val score = when {
                    lowerLabel == term -> 100
                    lowerLabel.startsWith(term) -> 85
                    lowerLabel.contains(term) -> 70
                    lowerPkg.endsWith(".$term") -> 65
                    lowerPkg.contains(term) -> 50
                    else -> 0
                }
                if (score > bestScore) {
                    bestScore = score
                    bestPackage = pkg
                    bestLabel = label.ifBlank { query }
                }
            }
        }

        if (bestPackage != null) {
            val launchIntent = pm.getLaunchIntentForPackage(bestPackage)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            if (launchIntent != null) {
                val started = runCatching { appContext.startActivity(launchIntent) }.isSuccess
                if (started) {
                    return AccessibilityActionOutcome(true, "${bestLabel ?: query} খোলা হয়েছে।")
                }
            }
        }

        // System-action fallbacks if OEM hides launcher package
        val lower = query.lowercase()
        val systemFallback: Intent? = when {
            searchTerms.contains("settings") -> Intent(Settings.ACTION_SETTINGS)
            searchTerms.contains("camera") -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            searchTerms.contains("phone") -> Intent(Intent.ACTION_DIAL)
            lower.contains("wifi") || lower.contains("ওয়াইফাই") -> Intent(Settings.ACTION_WIFI_SETTINGS)
            lower.contains("bluetooth") || lower.contains("ব্লুটুথ") -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            else -> null
        }
        if (systemFallback != null) {
            systemFallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { appContext.startActivity(systemFallback) }.isSuccess) {
                return AccessibilityActionOutcome(true, "$query খোলা হয়েছে।")
            }
        }

        return AccessibilityActionOutcome(false, "\"$query\" অ্যাপটি ফোনে খুঁজে পাওয়া যায়নি।")
    }

    fun openUrl(urlOrQuery: String): AccessibilityActionOutcome {
        val raw = urlOrQuery.trim()
        if (raw.isEmpty()) return AccessibilityActionOutcome(false, "লিংক বা সার্চ টেক্সট দেওয়া হয়নি।")
        val targetUri = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) {
            Uri.parse(raw)
        } else if (raw.contains('.') && !raw.contains(' ')) {
            Uri.parse("https://$raw")
        } else {
            Uri.parse("https://www.google.com/search?q=${Uri.encode(raw)}")
        }
        val intent = Intent(Intent.ACTION_VIEW, targetUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val ok = runCatching { appContext.startActivity(intent) }.isSuccess
        return AccessibilityActionOutcome(ok, if (ok) "ব্রাউজার খোলা হয়েছে।" else "লিংক খোলা যায়নি।")
    }

    fun openYouTubeSearchDirect(query: String): AccessibilityActionOutcome {
        val encoded = Uri.encode(query.trim())
        val ytUri = Uri.parse("https://www.youtube.com/results?search_query=$encoded")
        val intent = Intent(Intent.ACTION_VIEW, ytUri).apply {
            setPackage("com.google.android.youtube")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val ok = runCatching { appContext.startActivity(intent) }.isSuccess ||
            runCatching {
                appContext.startActivity(
                    Intent(Intent.ACTION_VIEW, ytUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.isSuccess
        return AccessibilityActionOutcome(ok, if (ok) "ইউটিউবে \"$query\" সার্চ করা হয়েছে।" else "ইউটিউব সার্চ খোলা যায়নি।")
    }

    fun dialNumber(phoneNumber: String): AccessibilityActionOutcome {
        val clean = phoneNumber.trim()
        if (clean.isEmpty()) return AccessibilityActionOutcome(false, "ফোন নম্বর দেওয়া হয়নি।")
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val ok = runCatching { appContext.startActivity(intent) }.isSuccess
        return AccessibilityActionOutcome(ok, if (ok) "$clean নম্বরে ডায়ালার খোলা হয়েছে।" else "ডায়ালার খোলা যায়নি।")
    }

    fun setFlashlight(turnOn: Boolean): AccessibilityActionOutcome {
        return runCatching {
            val camMgr = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = camMgr.cameraIdList.firstOrNull { id ->
                camMgr.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return AccessibilityActionOutcome(false, "এই ডিভাইসে ফ্ল্যাশলাইট পাওয়া যায়নি।")
            camMgr.setTorchMode(cameraId, turnOn)
            AccessibilityActionOutcome(true, if (turnOn) "ফ্ল্যাশলাইট চালু করা হয়েছে।" else "ফ্ল্যাশলাইট বন্ধ করা হয়েছে।")
        }.getOrElse {
            AccessibilityActionOutcome(false, "ফ্ল্যাশলাইট নিয়ন্ত্রণ করা যায়নি।")
        }
    }

    fun setAlarm(hour: Int, minute: Int, label: String): AccessibilityActionOutcome {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
            putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val ok = runCatching { appContext.startActivity(intent) }.isSuccess
        return AccessibilityActionOutcome(ok, if (ok) "অ্যালার্ম সেট করা হয়েছে।" else "অ্যালার্ম সেট করা যায়নি।")
    }

    private suspend fun waitForForegroundChange(previousPkg: String?, maxWaitMs: Long = 2200L) {
        val steps = (maxWaitMs / 220L).toInt().coerceAtLeast(1)
        repeat(steps) {
            delay(220L)
            val current = ShailaAccessibilityService.instance?.currentForegroundPackage()
            if (current != null && current != appContext.packageName && current != previousPkg) {
                delay(320L) // allow UI nodes inside newly opened app to settle
                return
            }
        }
    }

    suspend fun executeActions(actions: List<PhoneAction>): PhoneExecutionSummary = withContext(Dispatchers.Main) {
        if (actions.isEmpty()) return@withContext PhoneExecutionSummary()

        // Smart shortcut: if user asked to Open YouTube + Type/Search, and Accessibility is OFF,
        // launch YouTube search intent directly so it works immediately even before Accessibility is enabled.
        val firstOpen = actions.firstOrNull() as? PhoneAction.OpenApp
        val firstType = actions.firstOrNull { it is PhoneAction.TypeText } as? PhoneAction.TypeText
        if (ShailaAccessibilityService.instance == null &&
            firstOpen != null &&
            resolveCanonicalAppTerms(firstOpen.appName).contains("youtube") &&
            firstType != null
        ) {
            val ytRes = openYouTubeSearchDirect(firstType.text)
            return@withContext PhoneExecutionSummary(
                executedCount = 1,
                anySucceeded = ytRes.success,
                needsAccessibilitySetup = false,
                feedbackMessageBn = ytRes.detail,
                feedbackMessageEn = ytRes.detail
            )
        }

        var executed = 0
        var anyOk = false
        var needsAcc = false
        var lastDetail = ""
        var justOpenedApp = false
        var prevPkg: String? = null

        for (action in actions) {
            val requiresAccessibility = action is PhoneAction.TypeText ||
                action is PhoneAction.TapText ||
                action is PhoneAction.Submit ||
                action is PhoneAction.Scroll ||
                action is PhoneAction.SystemNav

            if (requiresAccessibility && ShailaAccessibilityService.instance == null) {
                needsAcc = true
                continue
            }

            if (justOpenedApp && requiresAccessibility) {
                waitForForegroundChange(prevPkg)
                justOpenedApp = false
            }

            val outcome: AccessibilityActionOutcome = when (action) {
                is PhoneAction.OpenApp -> {
                    prevPkg = ShailaAccessibilityService.instance?.currentForegroundPackage()
                    justOpenedApp = true
                    openApp(action.appName)
                }
                is PhoneAction.OpenUrl -> {
                    prevPkg = ShailaAccessibilityService.instance?.currentForegroundPackage()
                    justOpenedApp = true
                    openUrl(action.urlOrQuery)
                }
                is PhoneAction.Dial -> dialNumber(action.phoneNumber)
                is PhoneAction.Flashlight -> setFlashlight(action.turnOn)
                is PhoneAction.SetAlarm -> setAlarm(action.hour, action.minute, action.label)
                is PhoneAction.TypeText -> {
                    val res = ShailaAccessibilityService.instance!!.typeTextSmart(
                        text = action.text,
                        append = action.append,
                        submitAfter = action.submitAfter
                    )
                    delay(280)
                    res
                }
                is PhoneAction.TapText -> {
                    val res = ShailaAccessibilityService.instance!!.tapByLabel(action.label)
                    delay(420)
                    res
                }
                PhoneAction.Submit -> {
                    ShailaAccessibilityService.instance!!.pressEnterOrSubmit()
                }
                is PhoneAction.Scroll -> {
                    ShailaAccessibilityService.instance!!.scroll(action.direction)
                }
                is PhoneAction.SystemNav -> {
                    ShailaAccessibilityService.instance!!.performSystemNav(action.action)
                }
            }

            executed++
            if (outcome.success) anyOk = true
            lastDetail = outcome.detail
        }

        val msgBn = when {
            needsAcc && !anyOk ->
                "অ্যাপের ভিতরে টাইপ ও বাটন ট্যাপ করতে Settings থেকে \"Shaila (শাইলা) ফোন কন্ট্রোল\" Accessibility চালু করুন।"
            needsAcc && anyOk ->
                "$lastDetail তবে টাইপ/ট্যাপ করতে Accessibility চালু করুন।"
            else -> lastDetail
        }
        val msgEn = when {
            needsAcc ->
                "Enable \"Shaila Phone Control\" in Android Accessibility Settings so Shaila can type and tap inside apps."
            else -> lastDetail
        }

        PhoneExecutionSummary(
            executedCount = executed,
            anySucceeded = anyOk,
            needsAccessibilitySetup = needsAcc,
            feedbackMessageBn = msgBn,
            feedbackMessageEn = msgEn
        )
    }

    suspend fun executeLiveToolCall(name: String, args: JSONObject): JSONObject {
        val resultObj = JSONObject()
        when (name) {
            "open_app" -> {
                val appName = args.optString("app_name")
                val prev = ShailaAccessibilityService.instance?.currentForegroundPackage()
                val res = withContext(Dispatchers.Main) { openApp(appName) }
                if (res.success) {
                    waitForForegroundChange(prev, maxWaitMs = 1800L)
                }
                resultObj.put("success", res.success)
                resultObj.put("message", res.detail)
            }
            "open_url" -> {
                val url = args.optString("url")
                val res = withContext(Dispatchers.Main) { openUrl(url) }
                if (res.success) {
                    delay(900)
                }
                resultObj.put("success", res.success)
                resultObj.put("message", res.detail)
            }
            "dial_number" -> {
                val phone = args.optString("phone_number")
                val res = withContext(Dispatchers.Main) { dialNumber(phone) }
                resultObj.put("success", res.success)
                resultObj.put("message", res.detail)
            }
            "flashlight" -> {
                val on = args.optBoolean("turn_on", true)
                val res = withContext(Dispatchers.Main) { setFlashlight(on) }
                resultObj.put("success", res.success)
                resultObj.put("message", res.detail)
            }
            "set_alarm" -> {
                val hour = args.optInt("hour", 7)
                val minute = args.optInt("minute", 0)
                val label = args.optString("label", "Shaila Alarm")
                val res = withContext(Dispatchers.Main) { setAlarm(hour, minute, label) }
                resultObj.put("success", res.success)
                resultObj.put("message", res.detail)
            }
            "type_text" -> {
                val text = args.optString("text")
                val submit = args.optBoolean("submit", false)
                val service = ShailaAccessibilityService.instance
                if (service == null) {
                    resultObj.put("success", false)
                    resultObj.put("needs_accessibility", true)
                    resultObj.put("message", "Accessibility service is not enabled yet. Ask the user to enable Shaila Phone Control in Accessibility Settings.")
                } else {
                    val res = service.typeTextSmart(text = text, append = false, submitAfter = submit)
                    resultObj.put("success", res.success)
                    resultObj.put("message", res.detail)
                }
            }
            "tap_button" -> {
                val label = args.optString("label")
                val service = ShailaAccessibilityService.instance
                if (service == null) {
                    resultObj.put("success", false)
                    resultObj.put("needs_accessibility", true)
                    resultObj.put("message", "Accessibility service is not enabled yet.")
                } else {
                    val res = withContext(Dispatchers.Main) { service.tapByLabel(label) }
                    resultObj.put("success", res.success)
                    resultObj.put("message", res.detail)
                }
            }
            "scroll_screen" -> {
                val dir = args.optString("direction", "down")
                val service = ShailaAccessibilityService.instance
                if (service == null) {
                    resultObj.put("success", false)
                    resultObj.put("needs_accessibility", true)
                    resultObj.put("message", "Accessibility service is not enabled yet.")
                } else {
                    val res = withContext(Dispatchers.Main) { service.scroll(dir) }
                    resultObj.put("success", res.success)
                    resultObj.put("message", res.detail)
                }
            }
            "system_navigation" -> {
                val action = args.optString("action")
                val service = ShailaAccessibilityService.instance
                if (service == null) {
                    resultObj.put("success", false)
                    resultObj.put("needs_accessibility", true)
                    resultObj.put("message", "Accessibility service is not enabled yet.")
                } else {
                    val res = withContext(Dispatchers.Main) { service.performSystemNav(action) }
                    resultObj.put("success", res.success)
                    resultObj.put("message", res.detail)
                }
            }
            "get_screen_info" -> {
                val service = ShailaAccessibilityService.instance
                if (service == null) {
                    resultObj.put("success", false)
                    resultObj.put("needs_accessibility", true)
                    resultObj.put("message", "Accessibility service is not enabled yet.")
                } else {
                    val summary = withContext(Dispatchers.Main) { service.getScreenContextSummary() }
                    resultObj.put("success", true)
                    resultObj.put("screen_summary", summary)
                }
            }
            else -> {
                resultObj.put("success", false)
                resultObj.put("message", "Unknown tool: $name")
            }
        }
        return resultObj
    }
}
