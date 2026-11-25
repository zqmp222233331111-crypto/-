package com.example.data.device

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
            }
        }
        val cleanText = rawText
            .replace(ACTION_REGEX, "")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
        return cleanText to actions
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

    suspend fun executeActions(actions: List<PhoneAction>): PhoneExecutionSummary = withContext(Dispatchers.Main) {
        if (actions.isEmpty()) return@withContext PhoneExecutionSummary()

        var executed = 0
        var anyOk = false
        var needsAcc = false
        var lastDetail = ""
        var justOpenedApp = false

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
                delay(1350)
                justOpenedApp = false
            }

            val outcome: AccessibilityActionOutcome = when (action) {
                is PhoneAction.OpenApp -> {
                    justOpenedApp = true
                    openApp(action.appName)
                }
                is PhoneAction.OpenUrl -> {
                    justOpenedApp = true
                    openUrl(action.urlOrQuery)
                }
                is PhoneAction.TypeText -> {
                    val res = ShailaAccessibilityService.instance!!.typeText(
                        text = action.text,
                        append = action.append,
                        submitAfter = action.submitAfter
                    )
                    delay(300)
                    res
                }
                is PhoneAction.TapText -> {
                    val res = ShailaAccessibilityService.instance!!.tapByLabel(action.label)
                    delay(450)
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
                val res = withContext(Dispatchers.Main) { openApp(appName) }
                if (res.success) {
                    delay(1100)
                }
                resultObj.put("success", res.success)
                resultObj.put("message", res.detail)
            }
            "open_url" -> {
                val url = args.optString("url")
                val res = withContext(Dispatchers.Main) { openUrl(url) }
                if (res.success) {
                    delay(1100)
                }
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
                    val res = withContext(Dispatchers.Main) {
                        service.typeText(text = text, append = false, submitAfter = submit)
                    }
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
