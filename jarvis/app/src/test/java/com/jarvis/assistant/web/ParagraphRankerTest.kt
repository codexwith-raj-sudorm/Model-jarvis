package com.jarvis.assistant.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** BM25-lite passage ranking — the "rank" half of mini-RAG. */
class ParagraphRankerTest {

    @Test
    fun `relevant paragraph ranks first`() {
        val paras = listOf(
            "The chef spent years perfecting a delicate balance of spices in every dish.",
            "The 2023 cricket world cup final was decided on the last ball of the match.",
            "Cricket batting averages are computed over a player's career innings.",
        )
        val top = ParagraphRanker.topK("cricket world cup final", paras, k = 2)
        assertEquals(2, top.size)
        assertEquals(paras[1], top[0])
    }

    @Test
    fun `unicode queries tokenize`() {
        val paras = listOf(
            "मौसम आज बहुत अच्छा है और बारिश नहीं होगी।",
            "Completely unrelated English text about gardening tools.",
        )
        val top = ParagraphRanker.topK("आज मौसम", paras, k = 1)
        assertTrue(top.isNotEmpty())
        assertEquals(paras[0], top[0])
    }

    @Test
    fun `empty inputs are safe`() {
        assertTrue(ParagraphRanker.topK("query", emptyList()).isEmpty())
        assertTrue(ParagraphRanker.topK("", listOf("a".repeat(80)), k = 2).isNotEmpty())
    }

    @Test
    fun `no term overlap returns longest first`() {
        val paras = listOf("short one", "a much longer paragraph that has no query terms at all here")
        val top = ParagraphRanker.topK("zzz qqq", paras, k = 1)
        assertEquals(paras[1], top[0])
    }

    @Test
    fun `tokenize splits on punctuation and keeps alphanumerics`() {
        assertEquals(listOf("hello", "world", "2", "times"), ParagraphRanker.tokenize("Hello, world! 2 times."))
    }
}
