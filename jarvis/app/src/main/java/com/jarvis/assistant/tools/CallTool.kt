package com.jarvis.assistant.tools

import android.content.Intent
import android.net.Uri
import com.jarvis.assistant.agent.Tool
import com.jarvis.assistant.agent.ToolContext

/**
 * Opens the dialer pre-filled with a number — ACTION_DIAL, no permission,
 * the user's thumb makes the final call (pun intended). Contact-by-name
 * resolution deliberately out of scope until a contacts tool exists.
 */
class CallTool : Tool {

    override val name = "call"
    override val description =
        "Open the phone dialer with a number ready — the user confirms the call."
    override val parameters =
        """{"number": "string, phone number with country code, e.g. '+919812345678'"}"""

    override suspend fun execute(args: Map<String, String>, context: ToolContext): String {
        val number = normalize(args["number"] ?: args["to"] ?: args["who"])
            ?: return "[error] call needs a phone number, e.g. {\"number\": \"+919812345678\"}"

        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.appContext.startActivity(intent)
            "Dialer opened with $number — the user taps to confirm the call."
        } catch (e: Exception) {
            "[error] couldn't open the dialer: ${e.message}"
        }
    }

    companion object {
        /** Strips separators; null when no usable digits remain. internal for tests. */
        internal fun normalize(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val cleaned = raw.replace(Regex("[\\s()\\-.]"), "")
            if (!Regex("^\\+?\\d{6,15}$").matches(cleaned)) return null
            return cleaned
        }
    }
}
