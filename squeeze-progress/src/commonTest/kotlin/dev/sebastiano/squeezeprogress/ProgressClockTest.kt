package dev.sebastiano.squeezeprogress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressClockTest {
    private fun clock() =
        ProgressClock(runMillis = 8_000L, holdMillis = 1_000L, rewindMillis = 500L)

    @Test
    fun `playing fills the bar at a steady rate`() {
        val clock = clock()
        clock.advance(2_000L)
        assertEquals(0.25f, clock.progress, 0.0001f)
        clock.advance(2_000L)
        assertEquals(0.5f, clock.progress, 0.0001f)
    }

    @Test
    fun `paused, time passes and the bar does not move`() {
        val clock = clock()
        clock.advance(1_000L)
        clock.playing = false
        clock.advance(5_000L)
        assertEquals(0.125f, clock.progress, 0.0001f)
        clock.playing = true
        clock.advance(1_000L)
        assertEquals(0.25f, clock.progress, 0.0001f)
    }

    @Test
    fun `a full bar holds, then rewinds to empty and runs again`() {
        val clock = clock()
        clock.advance(9_000L)
        assertEquals(1f, clock.progress, 0.0001f)
        clock.advance(999L)
        assertEquals(1f, clock.progress, 0.0001f)
        clock.advance(1L + 250L)
        assertEquals(0.5f, clock.progress, 0.0001f)
        clock.advance(250L)
        assertEquals(0f, clock.progress, 0.0001f)
        clock.advance(800L)
        assertEquals(0.1f, clock.progress, 0.0001f)
    }

    @Test
    fun `the hold and the rewind wait while paused`() {
        val clock = clock()
        clock.advance(8_000L)
        clock.advance(500L)
        clock.playing = false
        clock.advance(10_000L)
        assertEquals(1f, clock.progress, 0.0001f)
        clock.playing = true
        clock.advance(500L + 250L)
        assertEquals(0.5f, clock.progress, 0.0001f)
    }

    @Test
    fun `a long frame does not skip the hold`() {
        val clock = clock()
        clock.advance(7_900L)
        clock.advance(5_000L)
        assertEquals(1f, clock.progress, 0.0001f)
        assertTrue(clock.isFull)
    }

    @Test
    fun `percent rounds down, so 100 only shows on a full bar`() {
        val clock = clock()
        clock.advance(7_999L)
        assertEquals(99, clock.percent)
        assertFalse(clock.isFull)
        clock.advance(1L)
        assertEquals(100, clock.percent)
    }
}
