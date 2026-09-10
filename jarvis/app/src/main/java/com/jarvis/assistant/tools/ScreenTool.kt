package com.jarvis.assistant.tools

import com.jarvis.assistant.accessibility.ScreenReaderService
import com.jarvis.assistant.agent.Tool
import com.jarvis.assistant.agent.ToolContext

/**
 * "What's on my screen?" — reads the current window via the on-demand
 * accessibility service. Nothing is captured or kept unless the user asks
 * through this tool; if the service is off, we say so plainly.
 */
class ScreenTool : Tool {

    override val name = "screen"
    override val description =
        "Read the text currently visible on the user's screen (needs the accessibility service enabled)."
    override val parameters = """{}"""

    override suspend fun execute(args: Map<String, String>, context: ToolContext): String {
        val service = ScreenReaderService.instance
            ?: return "[error] screen context is off — the user must enable JARVIS under " +
                "Settings → Accessibility for screen reading. Answer without it."
        val snap = service.snapshot()
        if (snap.isBlank()) {
            return "[error] couldn't read the screen right now (lock screen or secure window)."
        }
        return snap
    }
}
