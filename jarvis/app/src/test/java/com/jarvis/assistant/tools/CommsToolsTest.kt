package com.jarvis.assistant.tools

import java.util.Calendar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Comms tools' pure logic: number normalization + calendar date math. */
class CommsToolsTest {

    // ---- CallTool.normalize ----

    @Test
    fun `phone numbers survive formatting noise`() {
        assertEquals("+919812345678", CallTool.normalize("+91 98123 45678"))
        assertEquals("+919812345678", CallTool.normalize("+91-98123-45678"))
        assertEquals("0123456789", CallTool.normalize("(012) 345-6789"))
    }

    @Test
    fun `garbage numbers are rejected`() {
        assertNull(CallTool.normalize("Priyanka"))
        assertNull(CallTool.normalize("12345"))        // too short
        assertNull(CallTool.normalize("call her now"))
        assertNull(CallTool.normalize(null))
        assertNull(CallTool.normalize("  "))
    }

    // ---- CalendarTool.resolveBegin ----

    private fun noon(day: Int, month: Int, year: Int): Calendar =
        Calendar.getInstance().apply {
            set(year, month - 1, day, 15, 42, 0)
            set(Calendar.MILLISECOND, 0)
        }

    @Test
    fun `today keeps the current clock time`() {
        val now = noon(10, 9, 2026)
        val begin = CalendarTool.resolveBegin("today", null, now.clone() as Calendar)
        assertEquals(now.timeInMillis, begin)
    }

    @Test
    fun `timed events land on the minute`() {
        val now = noon(10, 9, 2026)
        val begin = CalendarTool.resolveBegin("tomorrow", "19:30", now.clone() as Calendar)
        val cal = Calendar.getInstance().apply { timeInMillis = begin!! }
        assertEquals(11, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(19, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
    }

    @Test
    fun `explicit dates and am pm work`() {
        val now = noon(10, 9, 2026)
        val begin = CalendarTool.resolveBegin("2026-12-25", "9:15 pm", now.clone() as Calendar)
        val cal = Calendar.getInstance().apply { timeInMillis = begin!! }
        assertEquals(25, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(11, cal.get(Calendar.MONTH)) // December
        assertEquals(21, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(15, cal.get(Calendar.MINUTE))
    }

    @Test
    fun `noon mass all day and future events`() {
        val now = noon(10, 9, 2026)
        val begin = CalendarTool.resolveBegin("tomorrow", null, now.clone() as Calendar)!!
        val cal = Calendar.getInstance().apply { timeInMillis = begin }
        assertEquals(12, cal.get(Calendar.HOUR_OF_DAY))
        assertTrue(begin > now.timeInMillis)
    }

    @Test
    fun `bad input is rejected without crashing`() {
        val now = noon(10, 9, 2026)
        assertNull(CalendarTool.resolveBegin("next week", "10:00", now))
        assertNull(CalendarTool.resolveBegin("2026-09-10", "whenever", now))
        assertNull(CalendarTool.resolveBegin("2026-9-10", "25:99", now))
    }
}
