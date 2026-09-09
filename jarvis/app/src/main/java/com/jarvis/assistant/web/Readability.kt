package com.jarvis.assistant.web

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Readability-style article extraction: turn a messy web page into clean
 * paragraphs. Small models can't read whole pages — this plus ParagraphRanker
 * is the "mini-RAG" front half (extract → rank → top-k passages).
 *
 * Heuristic, in order:
 *  1. nuke non-content elements (scripts, navs, footers, forms, …);
 *  2. score container elements by the amount of <p> text they hold;
 *  3. keep <p>/<li> blocks with ≥ [MIN_PARAGRAPH_CHARS] chars from the
 *     best-scoring container (fall back to the whole body);
 *  4. drop boilerplate lines ("cookie", "subscribe", "share", …).
 */
object Readability {

    private val DROP_TAGS = setOf(
        "script", "style", "noscript", "nav", "header", "footer", "aside",
        "form", "button", "iframe", "svg", "canvas", "figure", "img", "video",
        "audio", "template", "select", "label",
    )

    private val BOILERPLATE = listOf(
        "cookie", "subscribe", "sign in", "sign up", "log in", "share",
        "advertisement", "newsletter", "accept", "privacy policy",
        "terms of", "all rights reserved", "click here", "read more",
    )

    const val MIN_PARAGRAPH_CHARS = 60

    fun extract(html: String): String = paragraphs(html).joinToString("\n\n")

    /** Cleaned paragraphs in reading order — feed these to ParagraphRanker. */
    fun paragraphs(html: String): List<String> {
        val doc: Document = try {
            Jsoup.parse(html)
        } catch (_: Exception) {
            return emptyList()
        }

        doc.select(DROP_TAGS.joinToString(",")).remove()
        doc.select("[aria-hidden=true]").remove()

        // score containers by accumulated <p> text length
        var best: Element? = null
        var bestScore = 0
        for (el in doc.select("article, main, section, div")) {
            val score = el.select("p").sumOf { it.text().length }
            if (score > bestScore) {
                bestScore = score
                best = el
            }
        }
        val scope: Element = if (bestScore >= 400) best!! else doc.body() ?: return emptyList()

        val out = ArrayList<String>()
        for (p in scope.select("p, li")) {
            var text = p.text().trim()
            if (text.contains("|")) {
                // table-of-contents style "Home | News | Sports" nav remnants
                val parts = text.split("|")
                if (parts.size > 3) continue
                text = parts.joinToString(" ")
            }
            if (text.length < MIN_PARAGRAPH_CHARS) continue
            val lower = text.lowercase()
            if (BOILERPLATE.any { lower.contains(it) && text.length < 160 }) continue
            out.add(text)
        }
        return out
    }
}
