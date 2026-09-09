package com.jarvis.assistant.agent

import com.jarvis.assistant.agent.WebIntents.Need
import kotlin.test.Test
import kotlin.test.assertEquals

/** Fresh-data router — English + Hindi/Hinglish cues. */
class WebIntentsTest {

    @Test
    fun `english weather cues`() {
        assertEquals(Need.WEATHER, WebIntents.classify("what's the weather in Kolkata"))
        assertEquals(Need.WEATHER, WebIntents.classify("will it rain today"))
        assertEquals(Need.WEATHER, WebIntents.classify("how hot is it outside"))
    }

    @Test
    fun `hinglish weather and news cues`() {
        assertEquals(Need.WEATHER, WebIntents.classify("aaj mausam kaisa hai"))
        assertEquals(Need.NEWS, WebIntents.classify("koi khabar sunao"))
    }

    @Test
    fun `news vs scores vs latest`() {
        assertEquals(Need.NEWS, WebIntents.classify("show me the headlines"))
        assertEquals(Need.SCORES, WebIntents.classify("what's the match score"))
        assertEquals(Need.LATEST, WebIntents.classify("bitcoin price of right now"))
    }

    @Test
    fun `offline question needs no web`() {
        assertEquals(Need.NONE, WebIntents.classify("set an alarm for 7 am"))
        assertEquals(Need.NONE, WebIntents.classify("who wrote Gitanjali"))
    }

    @Test
    fun `suggested tools map correctly`() {
        assertEquals("weather", WebIntents.suggestedTool(Need.WEATHER))
        assertEquals("news", WebIntents.suggestedTool(Need.NEWS))
        assertEquals("web_search", WebIntents.suggestedTool(Need.LATEST))
        assertEquals(null, WebIntents.suggestedTool(Need.NONE))
    }
}
