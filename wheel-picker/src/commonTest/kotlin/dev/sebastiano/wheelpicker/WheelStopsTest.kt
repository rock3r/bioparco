package dev.sebastiano.wheelpicker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WheelStopsTest {
    @Test
    fun readoutSpellsHoursAndMinutes() {
        assertEquals("∞", formatReadout(null))
        assertEquals("5m", formatReadout(5))
        assertEquals("45m", formatReadout(45))
        assertEquals("1h", formatReadout(60))
        assertEquals("1h 15m", formatReadout(75))
        assertEquals("2h 30m", formatReadout(150))
        assertEquals("24h", formatReadout(24 * 60))
    }

    @Test
    fun theRulerStartsWithNoLimitAndEndsAtADay() {
        val stops = WheelStops.all
        assertNull(stops.first().minutes)
        assertTrue(stops.first().isMajor)
        assertEquals(24 * 60, stops.last().minutes)
        assertEquals(stops.lastIndex, WheelStops.lastIndex)
    }

    @Test
    fun stopsOnlyGrow() {
        val minutes = WheelStops.all.mapNotNull { it.minutes }
        assertEquals(WheelStops.all.size - 1, minutes.size, "only the first stop has no limit")
        assertEquals(minutes.sorted().distinct(), minutes)
    }

    @Test
    fun majorStopsAreTheLabelledHours() {
        val majors = WheelStops.all.filter { it.isMajor }.map { formatReadout(it.minutes) }
        assertEquals(listOf("∞", "1h", "2h", "4h", "8h", "12h", "24h"), majors)
    }

    @Test
    fun slotsPadTheReadoutToAFixedCount() {
        assertEquals(listOf('1', 'h', ' ', '5', 'm', NO_GLYPH, NO_GLYPH), readoutSlots("1h 5m", 7))
        assertEquals(listOf('∞', NO_GLYPH, NO_GLYPH), readoutSlots("∞", 3))
        assertEquals(listOf('1', 'h'), readoutSlots("1h 15m", 2), "extra characters are cut")
    }

    @Test
    fun everyReadoutFitsTheSlots() {
        for (stop in WheelStops.all) {
            assertTrue(formatReadout(stop.minutes).length <= READOUT_SLOTS)
        }
    }
}
