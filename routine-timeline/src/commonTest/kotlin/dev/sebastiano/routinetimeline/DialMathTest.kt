package dev.sebastiano.routinetimeline

import kotlin.test.Test
import kotlin.test.assertEquals

class DialMathTest {
    @Test
    fun `half the dial is twelve hours, later times turn clockwise`() {
        val now = 9.0 * 60
        assertEquals(0.0, screenAngle(now, now))
        assertEquals(90.0, screenAngle(now + 6 * 60, now))
        assertEquals(-90.0, screenAngle(now - 6 * 60, now))
    }

    @Test
    fun `angles take the short way round the day`() {
        // 11 PM seen from 1 AM is two hours earlier, not twenty-two later.
        assertEquals(-30.0, screenAngle(23.0 * 60, 60.0))
        assertEquals(30.0, screenAngle(60.0, 23.0 * 60))
    }

    @Test
    fun `a turn is measured the short way across the back of the dial`() {
        assertEquals(20.0, turnDegrees(from = 170.0, to = -170.0))
        assertEquals(-20.0, turnDegrees(from = -170.0, to = 170.0))
        assertEquals(15.0, turnDegrees(from = 10.0, to = 25.0))
    }

    @Test
    fun `turning the dial clockwise brings earlier times under the marker`() {
        assertEquals(-60.0, minutesForTurn(15.0))
        assertEquals(120.0, minutesForTurn(-30.0))
    }

    @Test
    fun `now is the same time of day nearest to where the dial is`() {
        // Scrubbed two days and three hours ahead of 9 AM: back to 9 AM two days on.
        val nine = 9.0 * 60
        assertEquals(
            nine + 2 * MINUTES_PER_DAY,
            nearestEquivalent(nine, nine + 2 * MINUTES_PER_DAY + 180),
        )
        // Scrubbed back to 11 PM the day before: forward to 9 AM is ten hours, back is fourteen.
        assertEquals(nine, nearestEquivalent(nine, -60.0))
    }

    @Test
    fun `a step goes to the next quarter hour in its direction`() {
        assertEquals(600.0, quarterStep(592.0, 1))
        assertEquals(585.0, quarterStep(592.0, -1))
        // From a quarter hour the step moves a whole quarter.
        assertEquals(615.0, quarterStep(600.0, 1))
        assertEquals(585.0, quarterStep(600.0, -1))
        assertEquals(-15.0, quarterStep(0.0, -1))
    }

    @Test
    fun `an icon sits mid-capsule, kept inside the visible range`() {
        assertEquals(20.0, iconAngle(start = -10.0, sweep = 60.0, inset = 5.0, limit = 76.0))
        assertEquals(76.0, iconAngle(start = 50.0, sweep = 120.0, inset = 5.0, limit = 76.0))
        assertEquals(null, iconAngle(start = 100.0, sweep = 30.0, inset = 5.0, limit = 76.0))
    }

    @Test
    fun `a capsule that wraps past the back of the dial keeps its icon on the visible tail`() {
        // Sleep from 11 PM, seen at 11:30 AM: it starts at 172.5° and its tail shows at the top,
        // from -90° down to -67.5°.
        assertEquals(-76.0, iconAngle(start = 172.5, sweep = 120.0, inset = 5.0, limit = 76.0))
    }
}
