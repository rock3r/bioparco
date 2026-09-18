package dev.sebastiano.grabbystepper

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GrabPhysicsTest {
    private val rest = RestLayout(width = 200f, height = 64f, thumbDiameter = 56f, inset = 4f)

    @Test
    fun stretchGrowsRightWithPositiveOffset() {
        val stretched = stretchTrack(rest, offsetX = 40f, offsetY = 0f, follow = 1f)
        assertEquals(rest.restLeft, stretched.left)
        assertEquals(rest.restRight + 40f, stretched.right)
        assertEquals(rest.restTop, stretched.top)
        assertEquals(rest.restBottom, stretched.bottom)
    }

    @Test
    fun stretchGrowsLeftWithNegativeOffset() {
        val stretched = stretchTrack(rest, offsetX = -24f, offsetY = 0f, follow = 1f)
        assertEquals(rest.restLeft - 24f, stretched.left)
        assertEquals(rest.restRight, stretched.right)
    }

    @Test
    fun verticalFollowReachesTowardThumbThenLetsGo() {
        val coupled = stretchTrack(rest, offsetX = 0f, offsetY = 30f, follow = 1f)
        assertTrue(coupled.bottom > rest.restBottom)

        val detached = stretchTrack(rest, offsetX = 0f, offsetY = 30f, follow = 0f)
        assertEquals(rest.restBottom, detached.bottom)
        assertEquals(1f, trackFollow(distance = 10f, detachStart = 40f, detachEnd = 80f))
        assertEquals(0f, trackFollow(distance = 80f, detachStart = 40f, detachEnd = 80f))
        assertTrue(trackFollow(distance = 60f, detachStart = 40f, detachEnd = 80f) in 0.4f..0.6f)
    }

    @Test
    fun rubberbandPassesLimitWithLessThanLinearExtra() {
        val inside = rubberband(20f, 40f)
        assertEquals(20f, inside)
        val outside = rubberband(80f, 40f)
        assertTrue(outside > 40f)
        assertTrue(outside < 80f)
    }

    @Test
    fun stepsAndResetThreshold() {
        assertEquals(3, stepsFromDisplacement(36f, 12f))
        assertEquals(-2, stepsFromDisplacement(-25f, 12f))
        assertEquals(0, stepsFromDisplacement(11f, 12f))
        assertTrue(shouldResetOnRelease(80f, 70f))
        assertFalse(shouldResetOnRelease(69f, 70f))
        assertEquals(0, applyStep(0, -1))
        assertEquals(4, applyStep(3, 1))
    }

    @Test
    fun holdTickGetsFasterAsOffsetGrows() {
        assertEquals(Long.MAX_VALUE, tickIntervalMs(0.05f))
        val slow = tickIntervalMs(0.3f)
        val fast = tickIntervalMs(1f)
        assertTrue(slow > fast)
        assertTrue(fast >= HOLD_TICK_FAST_MS.toLong() - 1)
        assertTrue(abs(tickIntervalMs(-1f) - fast) <= 1L)
    }
}
