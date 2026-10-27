package dev.sebastiano.wheelpicker

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WheelMathTest {
    private val radius = 300f

    @Test
    fun theCentreIsUntouched() {
        val point = lens(0f, radius)
        assertEquals(0f, point.x)
        assertEquals(1f, point.visibility)
        assertEquals(0f, point.edge)
    }

    @Test
    fun theLensIsSymmetric() {
        for (distance in listOf(10f, 120f, 280f, 460f)) {
            val right = lens(distance, radius)
            val left = lens(-distance, radius)
            assertEquals(-right.x, left.x, 1e-4f)
            assertEquals(right.visibility, left.visibility, 1e-6f)
            assertEquals(right.edge, left.edge, 1e-6f)
        }
    }

    @Test
    fun ticksSqueezeTowardTheEdges() {
        var previous = lens(0f, radius)
        var previousGap = Float.MAX_VALUE
        for (step in 1..40) {
            val point = lens(step * 10f, radius)
            val gap = point.x - previous.x
            assertTrue(gap > 0f, "x keeps growing at ${step * 10}")
            assertTrue(gap <= previousGap + 1e-4f, "spacing never widens at ${step * 10}")
            assertTrue(point.x <= step * 10f, "the lens never pushes a tick outward")
            assertTrue(point.visibility <= previous.visibility, "ticks only fade outward")
            assertTrue(point.edge >= previous.edge, "the edge effect only grows outward")
            previous = point
            previousGap = gap
        }
    }

    @Test
    fun pastTheRimNothingShows() {
        val rim = radius * (PI / 2).toFloat()
        val beyond = lens(rim + 1f, radius)
        assertEquals(0f, beyond.visibility)
        assertEquals(radius, beyond.x)
        assertEquals(1f, beyond.edge)
        assertEquals(-radius, lens(-rim - 50f, radius).x)
    }

    @Test
    fun unlensUndoesTheLens() {
        for (distance in listOf(-420f, -200f, -3f, 0f, 3f, 90f, 300f, 450f)) {
            assertEquals(distance, unlens(lens(distance, radius).x, radius), 0.05f)
        }
    }

    @Test
    fun unlensClampsOutsideTheRuler() {
        val rim = radius * (PI / 2).toFloat()
        assertEquals(rim, unlens(radius * 3f, radius), 1e-3f)
        assertEquals(-rim, unlens(-radius * 3f, radius), 1e-3f)
    }

    @Test
    fun aStillReleaseSnapsToTheNearestStop() {
        assertEquals(3, snapIndex(position = 3.4f, velocity = 0f, lastIndex = 10))
        assertEquals(4, snapIndex(position = 3.6f, velocity = 0f, lastIndex = 10))
    }

    @Test
    fun aFlickCoastsInItsDirection() {
        val forward = snapIndex(position = 3f, velocity = 20f, lastIndex = 40)
        val backward = snapIndex(position = 30f, velocity = -20f, lastIndex = 40)
        assertTrue(forward > 3, "forward flick lands past the start, got $forward")
        assertTrue(backward < 30, "backward flick lands before the start, got $backward")
        assertEquals(3 + (30 - backward), forward, "coasting is symmetric")
    }

    @Test
    fun snappingStaysOnTheRuler() {
        assertEquals(0, snapIndex(position = -2f, velocity = -50f, lastIndex = 10))
        assertEquals(10, snapIndex(position = 9f, velocity = 500f, lastIndex = 10))
    }

    @Test
    fun aReleaseRightAfterMovingKeepsItsVelocity() {
        assertEquals(24f, releaseVelocity(24f, lastMoveMillis = 1_000L, releaseMillis = 1_008L))
        assertEquals(-24f, releaseVelocity(-24f, lastMoveMillis = 1_000L, releaseMillis = 1_000L))
    }

    @Test
    fun aReleaseAfterHoldingStillDoesNotFlick() {
        assertEquals(0f, releaseVelocity(24f, lastMoveMillis = 1_000L, releaseMillis = 1_300L))
        assertEquals(
            0f,
            releaseVelocity(
                -24f,
                lastMoveMillis = 1_000L,
                releaseMillis = 1_000L + STILL_MILLIS + 1,
            ),
        )
    }

    @Test
    fun rubberBandLeavesTheRulerAlone() {
        assertEquals(0f, rubberBand(0f, 10))
        assertEquals(4.25f, rubberBand(4.25f, 10))
        assertEquals(10f, rubberBand(10f, 10))
    }

    @Test
    fun rubberBandResistsPastTheEnds() {
        val pastEnd = rubberBand(12f, 10) - 10f
        val pastStart = -rubberBand(-2f, 10)
        assertTrue(pastEnd > 0f && pastEnd < 2f, "overscroll shrinks, got $pastEnd")
        assertEquals(pastEnd, pastStart, 1e-5f)
        assertTrue(rubberBand(1_000f, 10) - 10f < RUBBER_BAND_LIMIT + 1e-4f, "and is bounded")
        assertTrue(rubberBand(13f, 10) > rubberBand(12f, 10), "but still follows the pointer")
    }

    @Test
    fun theCentreBumpPeaksUnderTheIndicator() {
        assertEquals(1f, centreBump(0f, 40f))
        assertTrue(centreBump(40f, 40f) < 0.5f)
        assertTrue(abs(centreBump(400f, 40f)) < 1e-3f)
        assertEquals(centreBump(25f, 40f), centreBump(-25f, 40f))
    }
}
