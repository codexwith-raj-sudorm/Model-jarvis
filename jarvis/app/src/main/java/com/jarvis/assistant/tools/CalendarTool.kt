package com.jarvis.assistant.tools

import android.content.Intent
import android.provider.CalendarContract
import com.jarvis.assistant.agent.Tool
import com.jarvis.assistant.agent.ToolContext
import java.util.Calendar

/**
 * Creates a calendar event via ACTION_INSERT — the calendar app opens
 * pre-filled, no permissions, the user confirms. (Reading the calendar
 * needs READ_CALENDAR at runtime; deferred.)
 */
class CalendarTool : Tool {

    override val name = "calendar"
    override val description =
        "Open the calendar app with a new event pre-filled — the user confirms."
    override val parameters =
        """{"title": "string, event title", "day": "string, 'today'|'tomorrow'|'YYYY-MM-DD'", "start": "string, optional '19:30' style time"}"""

    override suspend fun execute(args: Map<String, String>, context: ToolContext): String {
        val title = args["title"] ?: args["event"]?.takeIf { it.isNotBlank() }
            ?: return "[error] calendar needs a title, e.g. {\"title\": \"dentist\", \"day\": \"tomorrow\", \"start\": \"10:30\"}"

        val day = args["day"] ?: args["date"] ?: "today"
        val start = args["start"] ?: args["time"]

        val begin = resolveBegin(day, start, Calendar.getInstance())
            ?: return "[error] couldn't read the day/time \"$day ${start ?: ""}\" — try 'tomorrow' and '19:30' style"

        val end = begin + 60 * 60 * 1000L // default 1h
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title.take(120))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, start == null)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.appContext.startActivity(intent)
            "Calendar opened with \"$title\" pre-filled — the user confirms to save."
        } catch (e: Exception) {
            "[error] couldn't open the calendar app: ${e.message}"
        }
    }

    companion object {
        /**
         * Day + optional time → epoch millis. All-day events start at noon
         * (avoids midnight edge cases in some calendar apps). internal for
         * unit tests.
         */
        internal fun resolveBegin(day: String, start: String?, now: Calendar): Long? {
            val d = now.clone() as Calendar
            when (day.lowercase().trim()) {
                "today" -> {}
                "tomorrow" -> d.add(Calendar.DAY_OF_YEAR, 1)
                else -> {
                    val m = Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})").matchEntire(day.trim())
                        ?: return null
                    d.set(m.groupValues[1].toInt(), m.groupValues[2].toInt() - 1, m.groupValues[3].toInt(), 0, 0, 0)
                    d.set(Calendar.MILLISECOND, 0)
                }
            }

            if (start == null) {
                if (day.lowercase().trim() != "today") d.set(Calendar.HOUR_OF_DAY, 12)
                return d.timeInMillis
            }

            val t = Regex("(\\d{1,2})[:.]?(\\d{2})?\\s*(am|pm)?").matchEntire(start.trim())
                ?: return null
            var hour = t.groupValues[1].toIntOrNull() ?: return null
            val minute = t.groupValues[2].toIntOrNull() ?: 0
            when (t.groupValues[3].lowercase()) {
                "am" -> if (hour == 12) hour = 0
                "pm" -> if (hour != 12) hour += 12
            }
            if (hour > 23 || minute > 59) return null
            d.set(Calendar.HOUR_OF_DAY, hour)
            d.set(Calendar.MINUTE, minute)
            d.set(Calendar.SECOND, 0)
            return d.timeInMillis
        }
    }
}
