package dev.sebastiano.routinetimeline

import kotlin.test.Test
import kotlin.test.assertEquals

class DayClockTest {
    @Test
    fun `the readout is twelve-hour digits and a separate meridiem`() {
        assertEquals(ClockText("9:52", "AM"), clockText(9 * 60 + 52))
        assertEquals(ClockText("4:05", "PM"), clockText(16 * 60 + 5))
    }

    @Test
    fun `midnight and noon read twelve`() {
        assertEquals(ClockText("12:00", "AM"), clockText(0))
        assertEquals(ClockText("12:00", "PM"), clockText(12 * 60))
        assertEquals(ClockText("12:59", "PM"), clockText(12 * 60 + 59))
    }

    @Test
    fun `a minute past the end of the day wraps round`() {
        assertEquals(ClockText("12:01", "AM"), clockText(MINUTES_PER_DAY + 1))
        assertEquals(ClockText("11:59", "PM"), clockText(-1))
    }

    @Test
    fun `a range joins two times with an en dash`() {
        assertEquals("8:00 AM–10:00 AM", rangeLabel(8 * 60, 10 * 60))
        assertEquals("11:00 PM–7:00 AM", rangeLabel(23 * 60, 7 * 60))
    }

    @Test
    fun `hour marks are the short a and p forms`() {
        assertEquals(
            listOf("12a", "3a", "6a", "9a", "12p", "3p", "6p", "9p"),
            (0 until 24 step 3).map(::hourMark),
        )
    }

    @Test
    fun `the time of day comes from any running minute, before zero too`() {
        assertEquals(60.5, minuteOfDay(MINUTES_PER_DAY * 3 + 60.5))
        assertEquals(MINUTES_PER_DAY - 30.0, minuteOfDay(-30.0))
    }
}
