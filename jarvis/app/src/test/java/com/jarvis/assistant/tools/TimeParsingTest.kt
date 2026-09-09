package com.jarvis.assistant.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Alarm/timer natural-language time parsing — the flakiest logic in the app. */
class TimeParsingTest {

    // ---- AlarmTool.parseTimeOfDay ----

    @Test
    fun `clock times`() {
        assertEquals(7 to 30, AlarmTool().parseTimeOfDay("7:30"))
        assertEquals(19 to 45, AlarmTool().parseTimeOfDay("19:45"))
        assertEquals(9 to 15, AlarmTool().parseTimeOfDay("9.15"))
    }

    @Test
    fun `am pm handling`() {
        assertEquals(7 to 0, AlarmTool().parseTimeOfDay("7 am"))
        assertEquals(19 to 0, AlarmTool().parseTimeOfDay("7 pm"))
        assertEquals(0 to 0, AlarmTool().parseTimeOfDay("12 am"))
        assertEquals(12 to 0, AlarmTool().parseTimeOfDay("12 pm"))
    }

    @Test
    fun `military and bare numbers`() {
        assertEquals(7 to 30, AlarmTool().parseTimeOfDay("0730"))
        assertEquals(7 to 0, AlarmTool().parseTimeOfDay("at 7"))
    }

    @Test
    fun `garbage is rejected`() {
        assertNull(AlarmTool().parseTimeOfDay("whenever"))
        assertNull(AlarmTool().parseTimeOfDay(""))
    }

    // ---- TimerTool.parseDuration ----

    @Test
    fun `simple durations`() {
        assertEquals(600, TimerTool().parseDuration("10 minutes"))
        assertEquals(90, TimerTool().parseDuration("90 seconds"))
        assertEquals(3600, TimerTool().parseDuration("1 hour"))
    }

    @Test
    fun `compound durations`() {
        assertEquals(4800, TimerTool().parseDuration("1 hour 20 minutes"))
        assertEquals(330, TimerTool().parseDuration("5m 30s"))
        assertEquals(240, TimerTool().parseDuration("4 mins"))
    }

    @Test
    fun `bare number means minutes`() {
        assertEquals(300, TimerTool().parseDuration("5"))
    }

    @Test
    fun `nonsense is zero`() {
        assertEquals(0, TimerTool().parseDuration("a bit"))
    }
}
