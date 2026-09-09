package com.jarvis.assistant.chat

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/** Briefing intent matching (pure part — policy needs Android prefs). */
class BriefingTest {

    @Test
    fun `english morning intents match`() {
        assertTrue(Briefing.matchesIntent("good morning jarvis"))
        assertTrue(Briefing.matchesIntent("jarvis, brief me"))
        assertTrue(Briefing.matchesIntent("what's new today"))
        assertTrue(Briefing.matchesIntent("start my day"))
    }

    @Test
    fun `hinglish morning intents match`() {
        assertTrue(Briefing.matchesIntent("jarvis good morning"))
        assertTrue(Briefing.matchesIntent("suprabhat"))
    }

    @Test
    fun `ordinary questions do not trigger`() {
        assertFalse(Briefing.matchesIntent("what's the capital of France"))
        assertFalse(Briefing.matchesIntent("set a timer for 5 minutes"))
        assertFalse(Briefing.matchesIntent("tell me a joke"))
    }
}
