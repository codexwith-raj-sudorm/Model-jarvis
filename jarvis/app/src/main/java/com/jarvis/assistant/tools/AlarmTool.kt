package com.jarvis.assistant.tools

import android.content.Intent
import android.provider.AlarmClock
import com.jarvis.assistant.agent.Tool
import com.jarvis.assistant.agent.ToolContext

/**
 * Sets a system alarm via the public AlarmClock intent (normal permission,
 * granted at install — see the manifest). Time parsing is deliberately
 * lenient: "7:30", "0730", "7 am", "9:15 pm" all work.
 */
class AlarmTool : Tool {

    override val name = "alarm"
    override val description = "Set an alarm on the phone for a specific time of day."
    override val parameters = """{"time": "string, e.g. '7:30', '7 am', '19:45'", "label": "string, optional alarm label"}"""

    override suspend fun execute(args: Map<String, String>, context: ToolContext): String {
        val timeArg = args["time"] ?: args["at"] ?: args["when"]
            ?: return "[error] alarm needs a time, e.g. {\"time\": \"7:30\"}"

        val parsed = parseTimeOfDay(timeArg)
            ?: return "[error] couldn't read the time \"$timeArg\" — try '7:30 am' style"

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, parsed.first)
            putExtra(AlarmClock.EXTRA_MINUTES, parsed.second)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            args["label"]?.takeIf { it.isNotBlank() }?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.appContext.startActivity(intent)
            val amPm = if (parsed.first < 12) "am" else "pm"
            val h12 = when {
                parsed.first == 0 -> 12
                parsed.first > 12 -> parsed.first - 12
                else -> parsed.first
            }
            "Alarm set for $h12:%02d $amPm.".format(parsed.second)
        } catch (e: android.content.ActivityNotFoundException) {
            "[error] no clock app accepted the alarm request"
        }
    }

    /** Parses "7:30", "0730", "7 am", "9:15 pm", "19" → (hour, minute). */
    private fun parseTimeOfDay(s: String): Pair<Int, Int>? {
        val t = s.trim().lowercase()

        Regex("(\\d{1,2})[:.](\\d{2})\\s*(am|pm)?").find(t)?.let { m ->
            var h = m.groupValues[1].toIntOrNull() ?: return@let
            val min = m.groupValues[2].toIntOrNull() ?: return@let
            val ampm = m.groupValues[3]
            h = applyAmPm(h, ampm) ?: return@let
            if (h in 0..23 && min in 0..59) return h to min
        }

        Regex("(\\d{1,2})\\s*(am|pm)").find(t)?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: return@let
            val adj = applyAmPm(h, m.groupValues[2]) ?: return@let
            if (adj in 0..23) return adj to 0
        }

        Regex("\\b(\\d{4})\\b").find(t)?.let { m ->
            val v = m.groupValues[1].toIntOrNull() ?: return@let
            val h = v / 100
            val min = v % 100
            if (h in 0..23 && min in 0..59) return h to min
        }

        Regex("\\b(\\d{1,2})\\b").find(t)?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: return@let
            if (h in 0..23) return h to 0
        }
        return null
    }

    private fun applyAmPm(hour: Int, ampm: String): Int? = when {
        ampm == "am" -> if (hour == 12) 0 else hour
        ampm == "pm" -> if (hour == 12) 12 else hour + 12
        else -> hour
    }
}
