package dev.sebastiano.holocard

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TiltTest {
    @Test
    fun pointerAtTheCentreLeavesTheCardFlat() {
        val tilt =
            tiltToward(dx = 0f, dy = 0f, halfWidth = 150f, halfHeight = 210f, maxDegrees = 16f)
        assertEquals(0f, tilt.x, EPSILON)
        assertEquals(0f, tilt.y, EPSILON)
    }

    @Test
    fun theSideUnderThePointerRecedes() {
        // Compose turns a positive rotationY so the right edge goes away, and a positive
        // rotationX so the top edge comes forward.
        val right =
            tiltToward(dx = 150f, dy = 0f, halfWidth = 150f, halfHeight = 210f, maxDegrees = 16f)
        assertEquals(16f, right.y, EPSILON)
        assertEquals(0f, right.x, EPSILON)

        val top =
            tiltToward(dx = 0f, dy = -210f, halfWidth = 150f, halfHeight = 210f, maxDegrees = 16f)
        assertEquals(-16f, top.x, EPSILON)
        assertEquals(0f, top.y, EPSILON)
    }

    @Test
    fun pointersBeyondTheCardDoNotTiltItFurther() {
        val far =
            tiltToward(dx = 900f, dy = 900f, halfWidth = 150f, halfHeight = 210f, maxDegrees = 16f)
        assertEquals(16f, far.y, EPSILON)
        assertEquals(16f, far.x, EPSILON)
    }

    @Test
    fun theSpringSettlesOnItsTarget() {
        val spring = TiltSpring()
        repeat(90) { spring.step(target = 20f, seconds = FRAME) }
        assertEquals(20f, spring.value, 0.05f)
        assertTrue(spring.isSettledAt(20f))
    }

    @Test
    fun theSpringOvershootsBeforeSettling() {
        val spring = TiltSpring()
        var peak = 0f
        repeat(90) {
            spring.step(target = 20f, seconds = FRAME)
            peak = maxOf(peak, spring.value)
        }
        assertTrue(peak > 20.5f, "a wobbly card should swing past its target, peaked at $peak")
        assertTrue(peak < 30f, "but not wildly, peaked at $peak")
    }

    @Test
    fun aMovingSpringIsNotSettled() {
        val spring = TiltSpring()
        assertFalse(spring.isSettledAt(20f))
        spring.step(target = 20f, seconds = FRAME)
        assertFalse(spring.isSettledAt(20f))
    }

    @Test
    fun aLongHitchDoesNotThrowTheCardAround() {
        val spring = TiltSpring()
        spring.step(target = 20f, seconds = 2f)
        assertTrue(abs(spring.value) <= 30f, "a 2 s frame left the tilt at ${spring.value}")
        repeat(90) { spring.step(target = 20f, seconds = FRAME) }
        assertEquals(20f, spring.value, 0.05f)
    }

    @Test
    fun theAutopilotStaysWithinReach() {
        var time = 0f
        var furthest = 0f
        while (time < 60f) {
            val tilt = autopilotTilt(time, maxDegrees = 20f)
            furthest = maxOf(furthest, hypot(tilt.x, tilt.y))
            time += FRAME
        }
        assertTrue(furthest <= 20f + EPSILON, "the autopilot reached $furthest°")
        assertTrue(furthest > 12f, "the autopilot should sweep wide, but reached $furthest°")
    }

    @Test
    fun theAutopilotMovesSmoothly() {
        var time = 0f
        while (time < 30f) {
            val now = autopilotTilt(time, maxDegrees = 20f)
            val next = autopilotTilt(time + FRAME, maxDegrees = 20f)
            assertTrue(hypot(next.x - now.x, next.y - now.y) < 1f, "jumped at $time s")
            time += FRAME
        }
    }

    private companion object {
        const val EPSILON = 1e-3f
        const val FRAME = 1f / 60f
    }
}
