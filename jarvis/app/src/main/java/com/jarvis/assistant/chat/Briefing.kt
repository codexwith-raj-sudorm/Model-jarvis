package com.jarvis.assistant.chat

import android.content.Context
import com.jarvis.assistant.agent.ToolContext
import com.jarvis.assistant.tools.DateTimeTool
import com.jarvis.assistant.tools.NewsTool
import com.jarvis.assistant.tools.WeatherTool
import java.util.Calendar

/**
 * The morning briefing: "Good morning, sir…" — date, home-city weather and
 * the top headlines, delivered by voice on the first summon of the day.
 *
 * Deliberately app-driven (no LLM round): it must work instantly, even when
 * the model isn't loaded yet. Each section degrades gracefully — offline,
 * the briefing still wishes you a good morning and tells you the date.
 */
object Briefing {

    const val KEY_LAST_DAY = "last_briefing_day"

    private val NEWS_LINE = Regex("^\\d+\\)\\s*")
    private val SOURCE_TAIL = Regex("\\s*\\((source|Source): [^)]*\\)\\s*")

    /** First summon of the day between 05:00 and 12:00 gets the briefing. */
    fun shouldDeliver(context: Context): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour < 5 || hour >= 12) return false
        val prefs = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_DAY, null) != dayStamp()
    }

    /** Marks today as briefed — called when delivery STARTS, so it never double-fires. */
    fun markDelivered(context: Context) {
        context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_DAY, dayStamp()).apply()
    }

    private fun dayStamp(): String {
        val c = Calendar.getInstance()
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH) + 1}-${c.get(Calendar.DAY_OF_MONTH)}"
    }

    /** "good morning jarvis" / "brief me" — an explicit ask, any time of day. */
    fun matchesIntent(text: String): Boolean {
        val t = text.lowercase().trim()
        return listOf(
            "good morning", "good morng", "subah", "suprabhat", "briefing",
            "brief me", "what's new", "whats new", "start my day", "morning update",
        ).any { t.contains(it) }
    }

    /**
     * Builds the spoken briefing. Weather/news sections drop out silently
     * when offline or on error — a partial briefing beats a failed one.
     */
    suspend fun build(toolContext: ToolContext): String {
        val parts = ArrayList<String>()

        val date = DateTimeTool().execute(mapOf("format" to "date"), toolContext)
            .removePrefix("Today is ")
        parts += "Good morning, sir. It is ${date.removeSuffix(".")}."

        runCatching {
            val weather = WeatherTool().execute(emptyMap(), toolContext)
            if (!weather.startsWith("[")) {
                parts += weather.replace(SOURCE_TAIL, "") + "."
            }
        }

        runCatching {
            val news = NewsTool().execute(emptyMap(), toolContext)
            if (!news.startsWith("[")) {
                val headlines = ArrayList<String>()
                for (raw in news.lineSequence()) {
                    if (!NEWS_LINE.containsMatchIn(raw)) continue
                    val headline = raw
                        .replace(NEWS_LINE, "")
                        .substringBefore(" (")
                        .substringBefore(" —")
                        .trim()
                    if (headline.isNotEmpty()) headlines.add(headline)
                    if (headlines.size >= 3) break
                }
                if (headlines.isNotEmpty()) {
                    parts += "In the news: " + headlines.joinToString(". ") + "."
                }
            }
        }

        parts += "Anything else, sir?"
        return parts.joinToString(" ")
    }
}
