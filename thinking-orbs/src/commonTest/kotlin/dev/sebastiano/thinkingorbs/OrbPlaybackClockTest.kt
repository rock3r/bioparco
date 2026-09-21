package dev.sebastiano.thinkingorbs

import kotlin.test.Test
import kotlin.test.assertEquals

class OrbPlaybackClockTest {
    @Test
    fun firstFrameKeepsTheInitialAnimationTime() {
        val clock = OrbPlaybackClock(initialSeconds = 0.6)

        clock.onFrame(nanos(1.0))

        assertEquals(0.6, clock.seconds, ABSOLUTE_TOLERANCE)
    }

    @Test
    fun laterFramesAccumulateElapsedDeltas() {
        val clock = OrbPlaybackClock(initialSeconds = 0.6)

        clock.onFrame(nanos(1.0))
        clock.onFrame(nanos(1.25))
        clock.onFrame(nanos(1.5))

        assertEquals(1.1, clock.seconds, ABSOLUTE_TOLERANCE)
    }

    @Test
    fun resumeAfterPauseDoesNotJumpByThePausedInterval() {
        val clock = OrbPlaybackClock(initialSeconds = 0.6)
        clock.onFrame(nanos(1.0))
        clock.onFrame(nanos(1.2))
        assertEquals(0.8, clock.seconds, ABSOLUTE_TOLERANCE)

        clock.pause()
        clock.onFrame(nanos(5.0))

        assertEquals(0.8, clock.seconds, ABSOLUTE_TOLERANCE)

        clock.onFrame(nanos(5.1))

        assertEquals(0.9, clock.seconds, ABSOLUTE_TOLERANCE)
    }

    private fun nanos(seconds: Double): Long = (seconds * NANOS_PER_SECOND).toLong()

    private companion object {
        const val ABSOLUTE_TOLERANCE = 1e-9
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
