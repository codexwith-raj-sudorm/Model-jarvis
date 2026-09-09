package com.jarvis.assistant.tools

import android.util.Xml
import com.jarvis.assistant.agent.Tool
import com.jarvis.assistant.agent.ToolContext
import org.xmlpull.v1.XmlPullParser

/**
 * News headlines from Google News RSS (free, no key), India/English edition.
 * With a topic, searches that topic; without, returns top headlines.
 */
class NewsTool : Tool {

    override val name = "news"
    override val description =
        "Current news headlines (Google News, India edition). Optionally filter by topic."
    override val parameters = """{"topic": "string, optional — e.g. 'cricket', 'technology', 'india'}"""

    override suspend fun execute(args: Map<String, String>, context: ToolContext): String {
        if (!context.webEnabled || context.web == null) {
            return "[offline] news needs the internet — web is switched off"
        }
        val topic = args["topic"]?.takeIf { it.isNotBlank() }
            ?: args["query"]?.takeIf { it.isNotBlank() }
            ?: args["q"]?.takeIf { it.isNotBlank() }

        val url = if (topic.isNullOrBlank()) {
            HEADLINES_URL
        } else {
            "https://news.google.com/rss/search?q=" +
                java.net.URLEncoder.encode(topic, "UTF-8") + "&hl=en-IN&gl=IN&ceid=IN:en"
        }

        val xml = context.web.get(url)
        val items = parseRss(xml)
        if (items.isEmpty()) return "[error] no news items came back"

        val sb = StringBuilder("News headlines")
        if (!topic.isNullOrBlank()) sb.append(" about \"$topic\"")
        sb.append(":\n")
        items.forEachIndexed { i, (title, source, age) ->
            sb.append(i + 1).append(") ").append(title)
            if (source.isNotBlank()) sb.append(" (").append(source).append(')')
            if (age.isNotBlank()) sb.append(" — ").append(age)
            sb.append('\n')
        }
        sb.append("(source: news.google.com, India edition)")
        return sb.toString()
    }

    private data class Item(val title: String, val source: String, val age: String)

    /** Minimal pull-parser for <item><title><pubDate><source>. */
    private fun parseRss(xml: String): List<Item> {
        val items = ArrayList<Item>()
        return try {
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(xml.reader())
            }
            var title = ""
            var pubDate = ""
            var source = ""
            var inItem = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "item" -> { inItem = true; title = ""; pubDate = ""; source = "" }
                        "title" -> if (inItem) title = parser.nextText().trim()
                        "pubDate" -> if (inItem) pubDate = parser.nextText().trim()
                        "source" -> if (inItem) source = parser.nextText().trim()
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "item") {
                        inItem = false
                        if (title.isNotBlank()) items.add(Item(title, source, relativeAge(pubDate)))
                    }
                }
                event = parser.next()
            }
            items.take(MAX_ITEMS)
        } catch (_: Exception) {
            items.take(MAX_ITEMS)
        }
    }

    /** "2 hours ago"-style age from an RSS pubDate, best effort. */
    private fun relativeAge(pubDate: String): String {
        if (pubDate.isBlank()) return ""
        return try {
            val format = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.ENGLISH)
            val then = format.parse(pubDate)?.time ?: return ""
            val mins = (System.currentTimeMillis() - then) / 60000
            when {
                mins < 1 -> "just now"
                mins < 60 -> "$mins min ago"
                mins < 60 * 24 -> "${mins / 60} h ago"
                else -> "${mins / (60 * 24)} d ago"
            }
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        private const val HEADLINES_URL = "https://news.google.com/rss?hl=en-IN&gl=IN&ceid=IN:en"
        private const val MAX_ITEMS = 8
    }
}
