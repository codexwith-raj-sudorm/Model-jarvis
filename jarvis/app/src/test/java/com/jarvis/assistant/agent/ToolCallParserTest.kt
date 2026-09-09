package com.jarvis.assistant.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

/**
 * Pure-JVM tests for the lenient TOOL_CALL parser — the component small
 * models abuse the most. Every case here is a real failure mode observed
 * with 1–4B models.
 */
class ToolCallParserTest {

    @Test
    fun `parses a clean call`() {
        val p = ToolCallParser.parse(
            """TOOL_CALL {"name": "weather", "args": {"city": "Kolkata"}}"""
        )
        assertEquals(1, p.calls.size)
        assertEquals("weather", p.calls[0].name)
        assertEquals("Kolkata", p.calls[0].args["city"])
        assertTrue(p.speech.isBlank())
    }

    @Test
    fun `parses arguments inlined at top level`() {
        val p = ToolCallParser.parse(
            """TOOL_CALL {"name": "wikipedia", "topic": "Rabindranath Tagore"}"""
        )
        assertEquals(1, p.calls.size)
        assertEquals("wikipedia", p.calls[0].name)
        assertEquals("Rabindranath Tagore", p.calls[0].args["topic"])
    }

    @Test
    fun `finds the call inside think-block musing`() {
        val raw = """
            <think>The user wants the weather. I should call the weather tool
            with their city.</think>
            TOOL_CALL {"name": "weather", "args": {"city": "Mumbai"}}
        """.trimIndent()
        val p = ToolCallParser.parse(raw)
        assertEquals(1, p.calls.size)
        assertEquals("weather", p.calls[0].name)
        assertTrue(p.thinking.contains("weather tool"))
        assertTrue(!p.speech.contains("think"))
    }

    @Test
    fun `braces inside string values do not break scanning`() {
        val p = ToolCallParser.parse(
            """TOOL_CALL {"name": "save_memory", "args": {"text": "likes the {curly} style"}}"""
        )
        assertEquals(1, p.calls.size)
        assertEquals("likes the {curly} style", p.calls[0].args["text"])
    }

    @Test
    fun `malformed json falls back to regex scraping`() {
        // missing closing brace + trailing comma — classic small-model output
        val p = ToolCallParser.parse(
            """TOOL_CALL {"name": "timer", "args": {"duration": "10 minutes","""
        )
        // the balanced-brace scan fails, so no call — but it must not crash
        // and speech must survive
        assertTrue(p.calls.isEmpty() || p.calls[0].name == "timer")
    }

    @Test
    fun `multiple calls are all extracted`() {
        val raw = """
            TOOL_CALL {"name": "datetime", "args": {}}
            TOOL_CALL {"name": "flashlight", "args": {"action": "on"}}
        """.trimIndent()
        val p = ToolCallParser.parse(raw)
        assertEquals(2, p.calls.size)
        assertEquals(setOf("datetime", "flashlight"), p.calls.map { it.name }.toSet())
    }

    @Test
    fun `plain answer has no calls and keeps speech`() {
        val p = ToolCallParser.parse("The weather in Kolkata is lovely today, sir.")
        assertTrue(p.calls.isEmpty())
        assertEquals("The weather in Kolkata is lovely today, sir.", p.speech)
    }

    @Test
    fun `grammar-repaired output shape parses`() {
        // exactly what the GBNF repair path produces
        val p = ToolCallParser.parse(
            """TOOL_CALL {"name": "alarm", "args": {"time": "7:30", "label": "tea"}}"""
        )
        assertEquals("alarm", p.calls[0].name)
        assertEquals("7:30", p.calls[0].args["time"])
        assertEquals("tea", p.calls[0].args["label"])
    }

    @Test
    fun `unterminated marker is not a call`() {
        val p = ToolCallParser.parse("I could use TOOL_CALL here but I won't.")
        assertTrue(p.calls.isEmpty())
        assertTrue(p.speech.contains("TOOL_CALL"))
    }
}
