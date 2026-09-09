package com.jarvis.assistant.tools

import android.content.Intent
import android.net.Uri
import com.jarvis.assistant.agent.Tool
import com.jarvis.assistant.agent.ToolContext

/**
 * Opens the SMS app pre-filled with recipient and draft — ACTION_SENDTO,
 * no permission, the user taps send. Auto-sending (SmsManager) would need
 * the SEND_SMS runtime permission and fires messages without a confirm;
 * the one-tap hand-off is the right trade for now.
 */
class SmsTool : Tool {

    override val name = "send_sms"
    override val description =
        "Open the messaging app with a draft SMS ready — the user taps send."
    override val parameters =
        """{"number": "string, phone number with country code", "message": "string, the SMS text"}"""

    override suspend fun execute(args: Map<String, String>, context: ToolContext): String {
        val number = CallTool.normalize(args["number"] ?: args["to"])
            ?: return "[error] send_sms needs a phone number, e.g. {\"number\": \"+919812345678\", \"message\": \"...\"}"
        val message = args["message"] ?: args["text"]?.takeIf { it.isNotBlank() }
            ?: return "[error] send_sms needs a message body"

        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
            putExtra("sms_body", message.take(480)) // single-part SMS territory
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.appContext.startActivity(intent)
            "Messaging app opened to $number with the draft ready — the user taps send."
        } catch (e: Exception) {
            "[error] couldn't open the messaging app: ${e.message}"
        }
    }
}
