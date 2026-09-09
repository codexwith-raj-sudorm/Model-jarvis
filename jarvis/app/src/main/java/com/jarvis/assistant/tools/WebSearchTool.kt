package com.jarvis.assistant.tools

import com.jarvis.assistant.agent.Tool
import com.jarvis.assistant.agent.ToolContext
import com.jarvis.assistant.web.ParagraphRanker
import com.jarvis.assistant.web.Readability
import java.net.URLDecoder
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Mini-RAG web search:
 *   DuckDuckGo HTML (html.duckduckgo.com/html/) → top result URLs →
 *   fetch pages → Readability paragraphs → BM25 top-4 passages.
 *
 * Small models cannot read whole pages; they get the 4 passages most
 * relevant to the query, each capped at [PASSAGE_CHARS] so a normal result
 * lands *under* the orchestrator's 1500-char tool-result clamp (Session 12)
 * instead of being mid-sentence truncated.
 *
 * NOTE: html.duckduckgo.com is an unofficial endpoint — swap for a SearXNG
 * instance if it breaks (see README "Known limits").
 */
class WebSearchTool : Tool {

    override val name = "web_search"
    override val description =
        "Search the web (DuckDuckGo) and read the top pages for the most relevant passages. Use for anything fresh: latest news, scores, prices, 'today', 'right now'."
    override val parameters = """{"query": "string, what to search for"}"""

    override suspend fun execute(args: Map<String, String>, context: ToolContext): String {
        if (!context.webEnabled || context.web == null) {
            return "[offline] web search needs the internet — web is switched off"
        }
        val web = context.web
        val query = args["query"]?.takeIf { it.isNotBlank() }
            ?: args["q"]?.takeIf { it.isNotBlank() }
            ?: args["search"]?.takeIf { it.isNotBlank() }
            ?: return "[error] web_search needs a query"

        // 1) DuckDuckGo HTML endpoint — result links look like
        //    <a class="result__a" href="//duckduckgo.com/l/?uddg=<encoded>&amp;rut=…">
        val html = web.get("https://html.duckduckgo.com/html/?q=" + java.net.URLEncoder.encode(query, "UTF-8"))
        val doc = org.jsoup.Jsoup.parse(html)
        val results = doc.select("a.result__a").take(MAX_RESULT_PAGES).mapNotNull { a ->
            val href = a.attr("href")
            val title = a.text().trim()
            unwrapDdg(href)?.let { it to title }
        }
        if (results.isEmpty()) return "[error] no results from DuckDuckGo for \"$query\""

        // 2) fetch + extract paragraphs from each result page (parallel)
        val passages = coroutineScope {
            results.map { (url, title) ->
                async {
                    runCatching {
                        val page = web.get(url, maxBytes = 400_000)
                        val paras = Readability.paragraphs(page)
                        (title to paras)
                    }.getOrElse { title to emptyList() }
                }
            }.awaitAll()
        }

        // 3) rank globally across pages, keep top-k, cap each passage
        val allParagraphs = passages.flatMap { it.second }
        if (allParagraphs.isEmpty()) {
            // pages unreadable — at least hand back titles + URLs
            return results.joinToString("\n") { (url, title) -> "- $title — $url" } +
                "\n(pages had no extractable text) (source: duckduckgo.com)"
        }

        val top = ParagraphRanker.topK(query, allParagraphs, k = TOP_K)
        val sb = StringBuilder("Web results for \"$query\":\n")
        results.forEach { (url, title) -> sb.append("- ").append(title).append(" — ").append(url).append('\n') }
        sb.append("Most relevant passages:\n")
        top.forEachIndexed { i, p ->
            val cap = if (p.length > PASSAGE_CHARS) p.substring(0, PASSAGE_CHARS) + "…" else p
            sb.append(i + 1).append(") ").append(cap).append('\n')
        }
        sb.append("(source: duckduckgo.com)")
        return sb.toString()
    }

    /** Resolves DDG's redirect wrapper to the real target URL. */
    private fun unwrapDdg(href: String): String? {
        val h = when {
            href.startsWith("//") -> "https:$href"
            href.startsWith("http") -> href
            else -> return null
        }
        // …/l/?uddg=<urlencoded>&rut=…
        val marker = "uddg="
        val i = h.indexOf(marker)
        if (i < 0) return h
        val rest = h.substring(i + marker.length)
        val encoded = rest.substringBefore('&')
        return runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrNull()?.takeIf {
            it.startsWith("http")
        }
    }

    companion object {
        private const val MAX_RESULT_PAGES = 3
        private const val TOP_K = 4
        private const val PASSAGE_CHARS = 350
    }
}
