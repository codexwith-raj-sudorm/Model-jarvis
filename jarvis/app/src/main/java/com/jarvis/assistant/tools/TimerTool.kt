package com.jarvis.assistant.tools

import android.content.Intent
import android.provider.AlarmClock
import com.jarvis.assistant.agent.Tool
import com.jarvis.assistant.agent.ToolContext

/**
 * Starts a countdown timer via the AlarmClock intent. Lenient duration
 * parsing: "10 minutes", "90 seconds", "1 hour 20 minutes", "5m 30s", "8 min".
 */
class TimerTool : Tool {

    override val name = "timer"
    override val description = "Start a countdown timer (e.g. for tea, laundry, a workout)."
    override val parameters = """{"duration": "string, e.g. '10 minutes', '90 seconds', '1 hour 20 minutes'", "label": "string, optional timer name"}"""

    override suspend fun execute(args: Map<String, String>, context: ToolContext): String {
        val dur = args["duration"] ?: args["for"] ?: args["length"]
            ?: return "[error] timer needs a duration, e.g. {\"duration\": \"10 minutes\"}"

        val seconds = parseDuration(dur)
        if (seconds <= 0) return "[error] couldn't read the duration \"$dur\""
        if (seconds > MAX_SECONDS) return "[error] timers above 24 hours aren't supported"

        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            args["label"]?.takeIf { it.isNotBlank() }?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.appContext.startActivity(intent)
            "Timer started: ${describe(seconds)}."
        } catch (e: android.content.ActivityNotFoundException) {
            "[error] no clock app accepted the timer request"
        }
    }

    /** Sums every "<n> <unit>" pair found in the string. */
    private fun parseDuration(s: String): Int {
        val t = s.lowercase().replace(" and ", " ")
        var total = 0
        for (m in Regex("(\\d+(?:\\.\\d+)?)\\s*(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)").findAll(t)) {
            val n = m.groupValues[1].toDoubleOrNull() ?: continue
            val mult = when (m.groupValues[2].first()) {
                'h' -> 3600
                'm' -> 60
                else -> 1
            }
            total += (n * mult).toInt()
        }
        // bare number ("timer 5") — assume minutes
        if (total == 0) {
            Regex("\\b(\\d+)\\b").find(t)?.let { total = (it.groupValues[1].toInt()) * 60 }
        }
        return total
    }

    private fun describe(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        val parts = ArrayList<String>()
        if (h > 0) parts.add("$h hour" + if (h > 1) "s" else "")
        if (m > 0) parts.add("$m minute" + if (m > 1) "s" else "")
        if (s > 0) parts.add("$s second" + if (s > 1) "s" else "")
        return parts.joinToString(" ")
    }

    companion object {
        private const val MAX_SECONDS = 24 * 3600
    }
}
