package com.jarvis.assistant.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** SpeechFormatter — cleans model output for spoken delivery. */
class SpeechFormatterTest {

    @Test
    fun `markdown is flattened`() {
        val out = SpeechFormatter.forSpeech(
            "**Important**: use `adb install` — see [docs](https://example.com/x)"
        )
        assertFalse(out.contains("**"))
        assertFalse(out.contains("`"))
        assertFalse(out.contains("https://"))
        assertTrue(out.contains("Important"))
        assertTrue(out.contains("docs"))
    }

    @Test
    fun `citation markers are dropped`() {
        val out = SpeechFormatter.forSpeech("Bhātpāra is a town[1] in West Bengal[2].")
        assertFalse(out.contains("[1]"))
        assertFalse(out.contains("[2]"))
        assertTrue(out.contains("Bhātpāra"))
    }

    @Test
    fun `bare urls are removed`() {
        val out = SpeechFormatter.forSpeech("Read more at https://example.com/page today.")
        assertFalse(out.contains("example.com"))
        assertTrue(out.contains("Read more"))
    }

    @Test
    fun `list bullets become plain text`() {
        val out = SpeechFormatter.forSpeech("- first item\n- second item")
        assertEquals("first item second item", out)
    }

    @Test
    fun `capForSpeech cuts on a word boundary`() {
        val text = "word ".repeat(200).trim()
        val capped = SpeechFormatter.capForSpeech(text, maxChars = 100)
        assertTrue(capped.length <= 101) // 100 + ellipsis
        assertFalse(capped.endsWith(" w"))
    }

    @Test
    fun `short text passes through untouched`() {
        assertEquals("Good morning, sir.", SpeechFormatter.capForSpeech("Good morning, sir."))
    }
}
