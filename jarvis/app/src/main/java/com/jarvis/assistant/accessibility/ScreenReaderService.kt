package com.jarvis.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvis.assistant.agent.Orchestrator

/**
 * On-demand screen reading for "what's on my screen, JARVIS?" — the last
 * big context source a real assistant has.
 *
 * Privacy posture (local-first, deliberately narrow):
 *  - the service subscribes to almost nothing (window-state changes only,
 *    which are required for the framework to hand us window content) and
 *    ignores every event it receives;
 *  - text is only read at the moment a tool asks — [snapshot] walks the
 *    current window tree once and returns a digest; nothing is stored,
 *    logged, or forwarded anywhere;
 *  - the user enables the service manually in system Settings; until then
 *    the screen tool reports that it's off.
 */
class ScreenReaderService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // deliberately ignored — capture happens on demand, not on events
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /**
     * One walk of the active window's node tree → a flat text digest.
     * Bounds: [MAX_NODES] nodes and [MAX_CHARS] characters, whichever
     * hits first; duplicate lines collapsed. Returns "" when the window
     * is unavailable (lock screen, secure content, timing).
     */
    fun snapshot(): String {
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return ""
        val seen = HashSet<String>()
        val out = StringBuilder()
        var nodes = 0
        walk(root, seen, out) { nodes += it; nodes < MAX_NODES && out.length < MAX_CHARS }
        return out.toString().trim()
    }

    private fun walk(
        node: AccessibilityNodeInfo,
        seen: HashSet<String>,
        out: StringBuilder,
        budget: (Int) -> Boolean,
    ) {
        if (!budget(1)) return
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        for (line in listOfNotNull(text, desc)) {
            if (line.isEmpty() || line.length > 300 || line in seen) continue
            if (!budget(0)) return
            seen.add(line)
            out.append(line).append('\n')
        }
        for (i in 0 until node.childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            if (child != null) walk(child, seen, out, budget)
        }
    }

    companion object {
        @Volatile
        var instance: ScreenReaderService? = null
            private set // written only by the service lifecycle

        const val MAX_NODES = 320
        const val MAX_CHARS = Orchestrator.MAX_TOOL_RESULT_CHARS

        /** True when the user has enabled the service in system Settings. */
        fun isConnected(): Boolean = instance != null
    }
}
