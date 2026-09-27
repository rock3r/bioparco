package dev.sebastiano.componentanatomy

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnatomyTimelineTest {
    @Test
    fun theLoopStartsAsAFlatCollapsedButtonAtRest() {
        val frame = AnatomyTimeline.at(0.5)
        assertEquals(AnatomyState.Normal, frame.state)
        assertEquals(0f, frame.tilt)
        assertEquals(0f, frame.explode)
        assertFalse(frame.outlined)
    }

    @Test
    fun eachStateGetsItsOwnBarWhileTheStackIsOpen() {
        val expected =
            listOf(
                AnatomyState.Hovered,
                AnatomyState.Pressed,
                AnatomyState.Focused,
                AnatomyState.Disabled,
                AnatomyState.Normal,
            )
        expected.forEachIndexed { index, state ->
            val frame = AnatomyTimeline.at(beatOfBar(bar = 2 + index) + 2.0)
            assertEquals(state, frame.state, "bar ${2 + index}")
            assertEquals(1f, frame.tilt, "bar ${2 + index} tilt")
            assertEquals(1f, frame.explode, "bar ${2 + index} explode")
        }
    }

    @Test
    fun theLastBarClosesTheStackBeforeTheLoopComesRound() {
        val frame = AnatomyTimeline.at(beatOfBar(bar = 8) - 0.05)
        assertEquals(0f, frame.tilt)
        assertEquals(0f, frame.explode)
    }

    @Test
    fun loopsAlternateBetweenTheDefaultAndTheOutlinedButton() {
        val loop = AnatomyTimeline.BEATS_PER_LOOP.toDouble()
        assertFalse(AnatomyTimeline.at(1.0).outlined)
        assertTrue(AnatomyTimeline.at(loop + 1.0).outlined)
        assertFalse(AnatomyTimeline.at(2 * loop + 1.0).outlined)
        assertTrue(AnatomyTimeline.at(-1.0).outlined, "the loop before the first one is outlined")
    }

    @Test
    fun thePulseKicksOnEachBeatAndFadesBeforeTheNext() {
        val onTheBeat = AnatomyTimeline.at(beatOfBar(bar = 3)).pulse
        val justBefore = AnatomyTimeline.at(beatOfBar(bar = 3) - 0.02).pulse
        assertEquals(1f, onTheBeat)
        assertTrue(justBefore < 0.05f, "pulse should have faded, was $justBefore")
    }

    @Test
    fun tiltAndExplodeNeverJump() {
        var previous = AnatomyTimeline.at(0.0)
        var beat = 0.0
        while (beat < 2.0 * AnatomyTimeline.BEATS_PER_LOOP) {
            beat += STEP
            val frame = AnatomyTimeline.at(beat)
            assertTrue(abs(frame.tilt - previous.tilt) < MAX_DELTA, "tilt jumps at beat $beat")
            assertTrue(abs(frame.explode - previous.explode) < MAX_DELTA, "explode jumps at $beat")
            previous = frame
        }
    }

    private fun beatOfBar(bar: Int) = (bar * AnatomyTimeline.BEATS_PER_BAR).toDouble()

    private companion object {
        const val STEP = 0.01
        const val MAX_DELTA = 0.05f
    }
}
