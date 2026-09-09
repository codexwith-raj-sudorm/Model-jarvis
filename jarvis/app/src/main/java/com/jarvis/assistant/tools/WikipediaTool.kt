package com.jarvis.assistant.tools

import com.jarvis.assistant.agent.Tool
import com.jarvis.assistant.agent.ToolContext
import java.net.URLEncoder

/**
 * Wikipedia lookup via the REST search + page-summary endpoints
 * (free, CC BY-SA — the answer says so, per the license notes).
 */
class WikipediaTool : Tool {

    override val name = "wikipedia"
    override val description =
        "Look up a topic on Wikipedia and return its summary (good for facts, people, places, history)."
    override val parameters = """{"topic": "string, the thing to look up"}"""

    override suspend fun execute(args: Map<String, String>, context: ToolContext): String {
        if (!context.webEnabled || context.web == null) {
            return "[offline] wikipedia needs the internet — web is switched off"
        }
        val web = context.web
        val topic = args["topic"]?.takeIf { it.isNotBlank() }
            ?: args["query"]?.takeIf { it.isNotBlank() }
            ?: args["q"]?.takeIf { it.isNotBlank() }
            ?: return "[error] wikipedia needs a topic to look up"

        // 1) search for the best-matching article title
        val search = web.getJson(
            "https://en.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=1&srsearch=" +
                URLEncoder.encode(topic, "UTF-8")
        )
        val title = search.optJSONObject("query")?.optJSONArray("search")?.optJSONObject(0)
            ?.optString("title")
            ?: return "[error] no Wikipedia article found for \"$topic\""

        // 2) fetch the summary (follows redirects)
        val summary = web.getJson(
            "https://en.wikipedia.org/api/rest_v1/page/summary/" +
                URLEncoder.encode(title.replace(' ', '_'), "UTF-8")
        )
        val extract = summary.optString("extract", "")
        if (extract.isBlank()) return "[error] Wikipedia has no summary for \"$title\""

        val clamped = if (extract.length > MAX_EXTRACT) extract.substring(0, MAX_EXTRACT) + "…" else extract
        return "$clamped\n(source: Wikipedia — en.wikipedia.org/wiki/${title.replace(' ', '_')}, CC BY-SA)"
    }

    companion object {
        private const val MAX_EXTRACT = 1200
    }
}
