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

    suspend fun typeTextSmart(
        text: String,
        append: Boolean = false,
        submitAfter: Boolean = false
    ): AccessibilityActionOutcome {
        val firstAttempt = withContext(Dispatchers.Main) {
            typeText(text = text, append = append, submitAfter = submitAfter)
        }
        if (firstAttempt.success) return firstAttempt

        // If no editable text box is currently open, try tapping a Search / Message entry point first
        val openedInput = withContext(Dispatchers.Main) {
            val triggerLabels = listOf(
                "search youtube",
                "search or type web address",
                "search",
                "সার্চ",
                "type a message",
                "message",
                "write a message"
            )
            triggerLabels.any { lbl -> tapByLabel(lbl).success }
        }
        if (!openedInput) {
            return firstAttempt
        }

        delay(550)
        return withContext(Dispatchers.Main) {
            typeText(text = text, append = append, submitAfter = submitAfter)
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

        val roots = activeRoots()
        if (roots.isEmpty()) {
            return AccessibilityActionOutcome(false, "সক্রিয় স্ক্রিন পড়া যাচ্ছে না।")
        }

        // 1. Prefer focused input node
        var targetNode: AccessibilityNodeInfo? = null
        for (root in roots) {
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null && focused.isVisibleToUser) {
                targetNode = focused
                break
            }
        }

        // 2. Otherwise locate any visible editable input field (EditText / search box)
        if (targetNode == null) {
            for (root in roots) {
                val all = mutableListOf<AccessibilityNodeInfo>()
                collectAllNodes(root, all)
                targetNode = all.firstOrNull { it.isEditable && it.isEnabled && it.isVisibleToUser }
                    ?: all.firstOrNull {
                        it.isVisibleToUser && it.className?.toString()?.contains("EditText", ignoreCase = true) == true
                    }
                if (targetNode != null) break
            }
        }

        if (targetNode == null) {
            return AccessibilityActionOutcome(
                false,
                "বর্তমান স্ক্রিনে কোনো লেখার বক্স (Text Box) খোলা নেই।"
            )
        }

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
        val roots = activeRoots()
        val focused = preferredInputNode ?: roots.firstNotNullOfOrNull {
            it.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        }

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

    fun tapByLabel(label: String): AccessibilityActionOutcome {
        val rawQuery = label.trim()
        if (rawQuery.isEmpty()) return AccessibilityActionOutcome(false, "ট্যাপ করার নাম দেওয়া হয়নি।")

        val searchTerms = expandSearchAliases(rawQuery)
        val roots = activeRoots()
        if (roots.isEmpty()) return AccessibilityActionOutcome(false, "সক্রিয় স্ক্রিন পাওয়া যায়নি।")

        val candidates = mutableListOf<AccessibilityNodeInfo>()
        for (root in roots) {
            collectAllNodes(root, candidates)
        }

        var bestNode: AccessibilityNodeInfo? = null
        var bestScore = -1
        for (node in candidates) {
            if (!node.isVisibleToUser) continue
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
                        t.startsWith(term) -> 80
                        t.contains(term) -> 60
                        else -> 0
                    }
                    val clickableBonus = if (node.isClickable) 15 else 0
                    if (score > 0 && score + clickableBonus > bestScore) {
                        bestScore = score + clickableBonus
                        bestNode = node
                    }
                }
            }
        }

        val match = bestNode ?: return AccessibilityActionOutcome(false, "\"$rawQuery\" স্ক্রিনে খুঁজে পাওয়া যায়নি।")

        // Walk up up to 5 ancestors to find clickable container
        var clickable: AccessibilityNodeInfo? = match
        var depth = 0
        while (clickable != null && !clickable.isClickable && depth < 5) {
            clickable = clickable.parent
            depth++
        }
        if (clickable != null && clickable.isClickable) {
            if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return AccessibilityActionOutcome(true, "\"$rawQuery\"-এ ট্যাপ করা হয়েছে।")
            }
        }

        // Fallback: dispatch tap gesture at node's center bounds
        val rect = Rect()
        match.getBoundsInScreen(rect)
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
        val forward = dir in listOf("down", "next", "forward", "নিচে", "নীচে", "নিচের দিকে")
        val roots = activeRoots()
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        roots.forEach { collectAllNodes(it, nodes) }

        val scrollable = nodes.firstOrNull { it.isScrollable && it.isVisibleToUser }
        if (scrollable != null) {
            val action = if (forward) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
            if (scrollable.performAction(action)) {
                return AccessibilityActionOutcome(true, if (forward) "নিচে স্ক্রল করা হয়েছে।" else "উপরে স্ক্রল করা হয়েছে।")
            }
        }

        // Gesture swipe fallback
        val dm = resources.displayMetrics
        val x = dm.widthPixels * 0.5f
        val startY = if (forward) dm.heightPixels * 0.72f else dm.heightPixels * 0.30f
        val endY = if (forward) dm.heightPixels * 0.28f else dm.heightPixels * 0.74f
        val swiped = dispatchSwipe(x, startY, x, endY, 320L)
        return if (swiped) {
            AccessibilityActionOutcome(true, if (forward) "নিচে স্ক্রল করা হয়েছে।" else "উপরে স্ক্রল করা হয়েছে।")
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
        val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
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
                out.addAll(listOf("search", "search youtube", "search or type web address", "সার্চ"))
            }
            q.contains("পাঠা") || q.contains("সেন্ড") || q.contains("send") -> {
                out.addAll(listOf("send", "পাঠান"))
            }
            q.contains("প্লে") || q.contains("play") || q.contains("চালা") -> {
                out.addAll(listOf("play", "প্লে"))
            }
            q.contains("মেসেজ") || q.contains("message") || q.contains("লিখ") -> {
                out.addAll(listOf("message", "type a message", "write a message", "chat"))
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
