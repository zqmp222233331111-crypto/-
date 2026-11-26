package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class AccessibilityActionOutcome(
    val success: Boolean,
    val detail: String
)

class ShailaAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Window state is queried on-demand via rootInActiveWindow / windows
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
            _isConnected.value = false
        }
    }

    private fun activeRoots(): List<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { list.add(it) }
        runCatching {
            windows?.forEach { win ->
                win.root?.let { root ->
                    if (list.none { it == root }) list.add(root)
                }
            }
        }
        return list
    }

    private fun collectAllNodes(root: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        out.add(root)
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            collectAllNodes(child, out)
        }
    }

    fun getScreenContextSummary(): String {
        val root = rootInActiveWindow ?: return "No active screen window detected."
        val pkg = root.packageName?.toString().orEmpty()
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(root, nodes)

        val editables = nodes.filter { it.isEditable && it.isVisibleToUser }
            .mapNotNull {
                val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) it.hintText?.toString() else null
                val desc = it.contentDescription?.toString()
                val text = it.text?.toString()
                (hint ?: desc ?: text ?: "input box").trim().takeIf { s -> s.isNotEmpty() }
            }
            .distinct()
            .take(5)

        val clickables = nodes.filter { (it.isClickable || it.isCheckable) && it.isVisibleToUser }
            .mapNotNull {
                val t = it.text?.toString()?.trim()
                val d = it.contentDescription?.toString()?.trim()
                t?.takeIf { s -> s.isNotEmpty() } ?: d?.takeIf { s -> s.isNotEmpty() }
            }
            .distinct()
            .take(15)

        return buildString {
            append("Current app package: $pkg. ")
            if (editables.isNotEmpty()) append("Text inputs: ${editables.joinToString(", ")}. ")
            if (clickables.isNotEmpty()) append("Visible buttons/items: ${clickables.joinToString(", ")}.")
        }
    }

    fun currentForegroundPackage(): String? = rootInActiveWindow?.packageName?.toString()

    private fun externalOrAllRoots(): List<AccessibilityNodeInfo> {
        val all = activeRoots()
        val ownPkg = applicationContext.packageName
        val external = all.filter {
            val pkg = it.packageName?.toString().orEmpty()
            pkg.isNotEmpty() && pkg != ownPkg && pkg != "com.android.systemui"
        }
        return external.ifEmpty { all }
    }

    private fun isNodeVisibleOnScreen(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser) return false
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty || rect.width() < 4 || rect.height() < 4) return false
        val dm = resources.displayMetrics
        return rect.right > 0 && rect.bottom > 0 && rect.left < dm.widthPixels && rect.top < dm.heightPixels
    }

    private fun findEditableNode(roots: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        for (root in roots) {
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null && isNodeVisibleOnScreen(focused) &&
                (focused.isEditable || focused.className?.toString()?.contains("EditText", ignoreCase = true) == true)
            ) {
                return focused
            }
        }
        for (root in roots) {
            val all = mutableListOf<AccessibilityNodeInfo>()
            collectAllNodes(root, all)
            val found = all.firstOrNull { it.isEditable && it.isEnabled && isNodeVisibleOnScreen(it) }
                ?: all.firstOrNull {
                    isNodeVisibleOnScreen(it) && it.className?.toString()?.contains("EditText", ignoreCase = true) == true
                }
            if (found != null) return found
        }
        return null
    }

    suspend fun typeTextSmart(
        text: String,
        append: Boolean = false,
        submitAfter: Boolean = false
    ): AccessibilityActionOutcome {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) {
            return AccessibilityActionOutcome(false, "টাইপ করার জন্য কোনো লেখা পাওয়া যায়নি।")
        }

        var roots = externalOrAllRoots()
        if (roots.isEmpty()) {
            return AccessibilityActionOutcome(false, "সক্রিয় স্ক্রিন পড়া যাচ্ছে না।")
        }

        var targetNode = withContext(Dispatchers.Main) { findEditableNode(roots) }

        // If no editable text box is currently open, try tapping a Search / Message / Comment box first
        if (targetNode == null) {
            val openedInput = withContext(Dispatchers.Main) {
                val triggerLabels = listOf(
                    "search youtube",
                    "search or type web address",
                    "search",
                    "সার্চ",
                    "type a message",
                    "message",
                    "write a message",
                    "add a comment",
                    "write a comment"
                )
                triggerLabels.any { lbl -> tapByLabel(lbl).success }
            }
            if (openedInput) {
                delay(600)
                roots = externalOrAllRoots()
                targetNode = withContext(Dispatchers.Main) { findEditableNode(roots) }
            }
        }

        if (targetNode == null) {
            return AccessibilityActionOutcome(
                false,
                "বর্তমান স্ক্রিনে কোনো লেখার বক্স (Text Box) খোলা নেই।"
            )
        }

        // Click & focus first to open the keyboard / attach input connection cleanly
        withContext(Dispatchers.Main) {
            runCatching {
                targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val rect = Rect()
                targetNode.getBoundsInScreen(rect)
                if (!rect.isEmpty) {
                    dispatchTap(rect.exactCenterX(), rect.exactCenterY())
                }
            }
        }
        delay(220)

        val setOk = withContext(Dispatchers.Main) {
            val freshNode = findEditableNode(externalOrAllRoots()) ?: targetNode
            val finalValue = if (append) {
                val existing = freshNode.text?.toString().orEmpty()
                val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    freshNode.hintText?.toString().orEmpty()
                } else {
                    ""
                }
                if (existing.isNotBlank() && existing != hint) "$existing $cleanText" else cleanText
            } else {
                cleanText
            }

            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, finalValue)
            }
            var ok = freshNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (!ok) {
                ok = runCatching {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Shaila", finalValue))
                    freshNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                }.getOrDefault(false)
            }
            ok
        }

        if (setOk && submitAfter) {
            delay(180)
            withContext(Dispatchers.Main) {
                pressEnterOrSubmit(findEditableNode(externalOrAllRoots()) ?: targetNode)
            }
        }

        return if (setOk) {
            AccessibilityActionOutcome(true, "লেখা টাইপ করা হয়েছে: $cleanText")
        } else {
            AccessibilityActionOutcome(false, "এই ফিল্ডে সরাসরি টাইপ করা যায়নি।")
        }
    }

    fun typeText(
        text: String,
        append: Boolean = false,
        submitAfter: Boolean = false
    ): AccessibilityActionOutcome {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) {
            return AccessibilityActionOutcome(false, "টাইপ করার জন্য কোনো লেখা পাওয়া যায়নি।")
        }
        val roots = externalOrAllRoots()
        if (roots.isEmpty()) {
            return AccessibilityActionOutcome(false, "সক্রিয় স্ক্রিন পড়া যাচ্ছে না।")
        }
        val targetNode = findEditableNode(roots)
            ?: return AccessibilityActionOutcome(false, "বর্তমান স্ক্রিনে কোনো লেখার বক্স (Text Box) খোলা নেই।")

        runCatching {
            targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        }

        val finalValue = if (append) {
            val existing = targetNode.text?.toString().orEmpty()
            val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                targetNode.hintText?.toString().orEmpty()
            } else {
                ""
            }
            if (existing.isNotBlank() && existing != hint) "$existing $cleanText" else cleanText
        } else {
            cleanText
        }

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, finalValue)
        }
        var setOk = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

        if (!setOk) {
            setOk = runCatching {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Shaila", finalValue))
                targetNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            }.getOrDefault(false)
        }

        if (setOk && submitAfter) {
            pressEnterOrSubmit(targetNode)
        }

        return if (setOk) {
            AccessibilityActionOutcome(true, "লেখা টাইপ করা হয়েছে: $cleanText")
        } else {
            AccessibilityActionOutcome(false, "এই ফিল্ডে সরাসরি টাইপ করা যায়নি।")
        }
    }

    fun pressEnterOrSubmit(preferredInputNode: AccessibilityNodeInfo? = null): AccessibilityActionOutcome {
        val roots = externalOrAllRoots()
        val focused = preferredInputNode ?: findEditableNode(roots)

        if (focused != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val ok = focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
            if (ok) return AccessibilityActionOutcome(true, "সাবমিট/এন্টার করা হয়েছে।")
        }

        val submitLabels = listOf("send", "search", "go", "done", "submit", "পাঠান", "সার্চ", "খুঁজুন")
        for (lbl in submitLabels) {
            val tapRes = tapByLabel(lbl)
            if (tapRes.success) return tapRes
        }
        return AccessibilityActionOutcome(false, "সাবমিট/সার্চ বাটন পাওয়া যায়নি।")
    }

    private fun extractOrdinalIndex(query: String): Int? {
        val q = query.lowercase()
        return when {
            q.contains("first") || q.contains("1st") || q.contains("প্রথম") || q.contains("১ নম্বর") || q.contains("১ম") -> 0
            q.contains("second") || q.contains("2nd") || q.contains("দ্বিতীয়") || q.contains("দ্বিতীয়") || q.contains("২ নম্বর") || q.contains("২য়") -> 1
            q.contains("third") || q.contains("3rd") || q.contains("তৃতীয়") || q.contains("তৃতীয়") || q.contains("৩ নম্বর") || q.contains("৩য়") -> 2
            q.contains("fourth") || q.contains("4th") || q.contains("চতুর্থ") || q.contains("৪ নম্বর") -> 3
            else -> null
        }
    }

    fun tapByLabel(label: String): AccessibilityActionOutcome {
        val rawQuery = label.trim()
        if (rawQuery.isEmpty()) return AccessibilityActionOutcome(false, "ট্যাপ করার নাম দেওয়া হয়নি।")

        val roots = externalOrAllRoots()
        if (roots.isEmpty()) return AccessibilityActionOutcome(false, "সক্রিয় স্ক্রিন পাওয়া যায়নি।")

        val candidates = mutableListOf<AccessibilityNodeInfo>()
        for (root in roots) {
            collectAllNodes(root, candidates)
        }

        val dm = resources.displayMetrics

        // Positional / ordinal item tapping (e.g., "first video", "প্রথম ভিডিও", "প্রথমটা")
        val ordinalIndex = extractOrdinalIndex(rawQuery)
        if (ordinalIndex != null) {
            val contentItems = candidates.filter { node ->
                if (!node.isClickable || !isNodeVisibleOnScreen(node)) return@filter false
                val r = Rect()
                node.getBoundsInScreen(r)
                val minTop = (dm.heightPixels * 0.14f).toInt()
                val maxBottom = (dm.heightPixels * 0.90f).toInt()
                r.top >= minTop && r.bottom <= maxBottom && r.width() >= dm.widthPixels * 0.25f && r.height() >= 72
            }.sortedBy { node ->
                val r = Rect()
                node.getBoundsInScreen(r)
                r.top * 10000 + r.left
            }
            val chosen = contentItems.getOrNull(ordinalIndex)
            if (chosen != null) {
                val r = Rect()
                chosen.getBoundsInScreen(r)
                val ok = chosen.performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
                    dispatchTap(r.exactCenterX(), r.exactCenterY())
                if (ok) {
                    return AccessibilityActionOutcome(true, "\"$rawQuery\"-এ ট্যাপ করা হয়েছে।")
                }
            }
        }

        val searchTerms = expandSearchAliases(rawQuery)
        var bestNode: AccessibilityNodeInfo? = null
        var bestScore = -1
        for (node in candidates) {
            if (!isNodeVisibleOnScreen(node)) continue
            val texts = buildList {
                node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it.lowercase()) }
                node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it.lowercase()) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    node.hintText?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it.lowercase()) }
                }
            }
            for (t in texts) {
                for (term in searchTerms) {
                    val score = when {
                        t == term -> 100
                        t.startsWith(term) -> 82
                        t.contains(term) -> 62
                        else -> 0
                    }
                    val clickableBonus = if (node.isClickable) 16 else 0
                    if (score > 0 && score + clickableBonus > bestScore) {
                        bestScore = score + clickableBonus
                        bestNode = node
                    }
                }
            }
        }

        val match = bestNode ?: return AccessibilityActionOutcome(false, "\"$rawQuery\" স্ক্রিনে খুঁজে পাওয়া যায়নি।")

        // Walk up ancestors to find clickable container
        var clickable: AccessibilityNodeInfo? = match
        var depth = 0
        while (clickable != null && !clickable.isClickable && depth < 6) {
            clickable = clickable.parent
            depth++
        }
        if (clickable != null && clickable.isClickable) {
            if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return AccessibilityActionOutcome(true, "\"$rawQuery\"-এ ট্যাপ করা হয়েছে।")
            }
        }

        // Physical touch gesture fallback at node bounds center
        val rect = Rect()
        (clickable ?: match).getBoundsInScreen(rect)
        if (!rect.isEmpty) {
            val tapped = dispatchTap(rect.exactCenterX(), rect.exactCenterY())
            if (tapped) {
                return AccessibilityActionOutcome(true, "\"$rawQuery\"-এ ট্যাপ করা হয়েছে।")
            }
        }
        return AccessibilityActionOutcome(false, "\"$rawQuery\"-এ ট্যাপ করা যায়নি।")
    }

    fun scroll(direction: String): AccessibilityActionOutcome {
        val dir = direction.lowercase().trim()
        val isHorizontalLeft = dir in listOf("left", "বামে", "বাম")
        val isHorizontalRight = dir in listOf("right", "ডানে", "ডান")
        val isUp = dir in listOf("up", "previous", "backward", "উপরে", "উপরের দিকে")
        val forward = !isUp && !isHorizontalLeft

        val roots = externalOrAllRoots()
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        roots.forEach { collectAllNodes(it, nodes) }

        val dm = resources.displayMetrics
        val wantHorizontal = isHorizontalLeft || isHorizontalRight

        // Pick the LARGEST matching-orientation scrollable container (ignores small horizontal chip bars when scrolling feed down/up)
        val bestScrollable = nodes
            .filter { it.isScrollable && isNodeVisibleOnScreen(it) }
            .mapNotNull { node ->
                val r = Rect()
                node.getBoundsInScreen(r)
                val area = r.width().toLong() * r.height().toLong()
                val matchesOrientation = if (wantHorizontal) {
                    r.width() >= r.height()
                } else {
                    r.height() >= (dm.heightPixels * 0.30f)
                }
                if (matchesOrientation) node to area else null
            }
            .maxByOrNull { it.second }
            ?.first

        if (bestScrollable != null) {
            val action = if (forward) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
            if (bestScrollable.performAction(action)) {
                return AccessibilityActionOutcome(
                    true,
                    when {
                        isHorizontalLeft -> "বামে স্ক্রল করা হয়েছে।"
                        isHorizontalRight -> "ডানে স্ক্রল করা হয়েছে।"
                        isUp -> "উপরে স্ক্রল করা হয়েছে।"
                        else -> "নিচে স্ক্রল করা হয়েছে।"
                    }
                )
            }
        }

        // Reliable physical gesture swipe fallback (works on YouTube Shorts, Reels, WebViews, Compose feeds)
        val swiped = if (wantHorizontal) {
            val y = dm.heightPixels * 0.50f
            val startX = if (isHorizontalRight) dm.widthPixels * 0.80f else dm.widthPixels * 0.20f
            val endX = if (isHorizontalRight) dm.widthPixels * 0.20f else dm.widthPixels * 0.80f
            dispatchSwipe(startX, y, endX, y, 360L)
        } else {
            val x = dm.widthPixels * 0.50f
            val startY = if (isUp) dm.heightPixels * 0.28f else dm.heightPixels * 0.74f
            val endY = if (isUp) dm.heightPixels * 0.74f else dm.heightPixels * 0.26f
            dispatchSwipe(x, startY, x, endY, 400L)
        }

        return if (swiped) {
            AccessibilityActionOutcome(
                true,
                when {
                    isHorizontalLeft -> "বামে স্ক্রল করা হয়েছে।"
                    isHorizontalRight -> "ডানে স্ক্রল করা হয়েছে।"
                    isUp -> "উপরে স্ক্রল করা হয়েছে।"
                    else -> "নিচে স্ক্রল করা হয়েছে।"
                }
            )
        } else {
            AccessibilityActionOutcome(false, "স্ক্রল করা যায়নি।")
        }
    }

    fun performSystemNav(action: String): AccessibilityActionOutcome {
        val globalAction = when (action.lowercase().trim()) {
            "back", "ব্যাক", "পিছনে" -> GLOBAL_ACTION_BACK
            "home", "হোম" -> GLOBAL_ACTION_HOME
            "recents", "recent", "রিসেন্ট" -> GLOBAL_ACTION_RECENTS
            "notifications", "notification", "নোটিফিকেশন" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings", "কুইক সেটিংস" -> GLOBAL_ACTION_QUICK_SETTINGS
            else -> return AccessibilityActionOutcome(false, "অজানা নেভিগেশন কমান্ড: $action")
        }
        val ok = performGlobalAction(globalAction)
        return AccessibilityActionOutcome(ok, if (ok) "সম্পন্ন হয়েছে।" else "নেভিগেশন সম্পন্ন করা যায়নি।")
    }

    private fun dispatchTap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 65L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    private fun dispatchSwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    private fun expandSearchAliases(query: String): List<String> {
        val q = query.lowercase().trim()
        val out = mutableListOf(q)
        when {
            q.contains("সার্চ") || q.contains("search") || q.contains("খুঁজ") -> {
                out.addAll(listOf("search", "search youtube", "search or type web address", "সার্চ", "অনুসন্ধান"))
            }
            q.contains("পাঠা") || q.contains("সেন্ড") || q.contains("send") -> {
                out.addAll(listOf("send", "পাঠান"))
            }
            q.contains("প্লে") || q.contains("play") || q.contains("চালা") -> {
                out.addAll(listOf("play", "প্লে", "চালান"))
            }
            q.contains("লাইক") || q.contains("like") -> {
                out.addAll(listOf("like", "like this video", "লাইক"))
            }
            q.contains("সাবস্ক্রাইব") || q.contains("subscribe") -> {
                out.addAll(listOf("subscribe", "সাবস্ক্রাইব"))
            }
            q.contains("কমেন্ট") || q.contains("comment") -> {
                out.addAll(listOf("comments", "add a comment", "comment", "মন্তব্য"))
            }
            q.contains("মেসেজ") || q.contains("message") || q.contains("লিখ") -> {
                out.addAll(listOf("message", "type a message", "write a message", "chat"))
            }
            q.contains("পোস্ট") || q.contains("post") -> {
                out.addAll(listOf("post", "পোস্ট"))
            }
            q.contains("সেভ") || q.contains("save") -> {
                out.addAll(listOf("save", "সংরক্ষণ"))
            }
        }
        return out.distinct()
    }

    companion object {
        @Volatile
        var instance: ShailaAccessibilityService? = null
            private set

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    }
}
