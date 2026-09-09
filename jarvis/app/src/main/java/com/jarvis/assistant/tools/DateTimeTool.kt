package com.jarvis.assistant.tools

import com.jarvis.assistant.agent.Tool
import com.jarvis.assistant.agent.ToolContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exact current date/time. The model's training data is months old — never
 * let it guess "today". Fully offline.
 */
class DateTimeTool : Tool {

    override val name = "datetime"
    override val description =
        "Get the exact current date and time on the phone (offline, always accurate)."
    override val parameters = """{"format": "string, optional: 'date' or 'time'"}"""

    override suspend fun execute(args: Map<String, String>, context: ToolContext): String {
        val now = Date()
        return when (args["format"]?.lowercase()) {
            "date" -> "Today is " + SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH).format(now) + "."
            "time" -> "It's " + SimpleDateFormat("h:mm a", Locale.ENGLISH).format(now) + "."
            else -> "It's " +
                SimpleDateFormat("EEEE, d MMMM yyyy, h:mm a", Locale.ENGLISH).format(now) +
                " (local time)."
        }
    }
}
