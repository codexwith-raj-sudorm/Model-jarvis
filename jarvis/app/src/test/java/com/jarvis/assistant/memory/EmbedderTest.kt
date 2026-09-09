package com.jarvis.assistant.memory

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * HashEmbedder + cosine + BLOB round-trip — the semantic-memory primitives.
 * The numeric expectations mirror a pre-port simulation: related-paraphrase
 * pairs ≈0.3+, unrelated ≈0 (±0.03 collision noise at 512 dims).
 */
class EmbedderTest {

    private val e = HashEmbedder()

    @Test
    fun `deterministic across instances`() {
        val a = HashEmbedder().embed("my sister's birthday is March 3rd")
        val b = HashEmbedder().embed("my sister's birthday is March 3rd")
        assertTrue(a.contentEquals(b))
    }

    @Test
    fun `vectors are L2 normalized or zero`() {
        for (t in listOf("hello world", "Bhātpāra, West Bengal", "alarm clock")) {
            val v = e.embed(t)
            assertEquals(512, v.size)
            val sq = v.sumOf { (it * it).toDouble() }
            assertTrue(abs(sq - 1.0) < 1e-4, "norm of '$t' was $sq")
        }
        // pure-stopword text carries no content: zero vector, not a crash
        assertTrue(e.embed("a").all { it == 0f })
        assertTrue(e.embed("").all { it == 0f })
    }

    @Test
    fun `identical texts score near one`() {
        val v = e.embed("the butler serves tea at five")
        assertTrue(cosine(v, e.embed("the butler serves tea at five")) > 0.999f)
    }

    @Test
    fun `paraphrase query ranks the right memory first`() {
        // a real personal-memory case: different words, same fact
        val query = e.embed("when is my sister born")
        val right = e.embed("Raj's sister has her birthday on the 3rd of March")
        val wrong = e.embed("the capital of France is Paris")
        assertTrue(cosine(query, right) > cosine(query, wrong) + 0.05f)
    }

    @Test
    fun `diacritic folding makes transliterations match`() {
        val stored = e.embed("I live in Bhātpāra, West Bengal")
        val asked = e.embed("what is the weather like in bhatpara")
        val wrong = e.embed("the oven needs preheating before baking")
        assertTrue(cosine(asked, stored) > cosine(asked, wrong) + 0.05f)
    }

    @Test
    fun `same-script hindi query finds hindi memory`() {
        val stored = e.embed("सर, आपकी माँ का नाम सावित्री है")
        val asked = e.embed("माँ का नाम क्या है")
        val wrong = e.embed("cricket scores from last night")
        assertTrue(cosine(asked, stored) > cosine(asked, wrong))
    }

    @Test
    fun `plural stemming links inflected forms`() {
        val stored = e.embed("Raj lives in Bhātpāra")
        val asked = e.embed("where do I live")
        val wrong = e.embed("Raj's sister has her birthday on the 3rd of March")
        assertTrue(cosine(asked, stored) > cosine(asked, wrong))
    }

    @Test
    fun `unrelated short texts score near zero`() {
        val a = e.embed("alarm at seven")
        val b = e.embed("purplebanana")
        assertTrue(abs(cosine(a, b)) < 0.3f)
    }

    @Test
    fun `recall battery over a personal fact store`() {
        val facts = listOf(
            "User's name is Raj",
            "Raj lives in Bhātpāra, West Bengal",
            "Raj's sister has her birthday on the 3rd of March",
            "The capital of France is Paris",
            "Raj's favourite tea is Darjeeling first flush",
            "Raj works as a schoolteacher",
        )
        val probes = mapOf(
            "where do I live" to 1,
            "when is my sister born" to 2,
            "what is my favourite tea" to 4,
        )
        for ((query, want) in probes) {
            val scored = facts.mapIndexed { i, f -> cosine(e.embed(query), e.embed(f)) to i }
            val best = scored.maxBy { it.first }.second
            assertEquals(want, best, "query '$query' retrieved '${facts[best]}'")
        }
    }

    @Test
    fun `dims parameter is respected`() {
        assertEquals(64, HashEmbedder(dims = 64).embed("x").size)
        assertEquals(1024, HashEmbedder(dims = 1024).embed("x").size)
    }

    @Test
    fun `vecbytes round-trips exactly`() {
        val v = floatArrayOf(0.5f, -0.25f, 0.0f, 1e-20f, 12345.678f, Float.MIN_VALUE)
        val back = VecBytes.decode(VecBytes.encode(v))
        assertTrue(v.contentEquals(back!!))
        assertEquals(null, VecBytes.decode(byteArrayOf(1, 2, 3)))
    }
}
