package dev.sebastiano.componentanatomy

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GrooveSynthTest {
    private val groove = GrooveSynth.render()

    @Test
    fun theGrooveIsExactlyOneChoreographyLoopLong() {
        val seconds = AnatomyTimeline.BEATS_PER_LOOP / BEATS_PER_SECOND
        assertEquals(
            (seconds * GrooveSynth.SAMPLE_RATE).toInt() * GrooveSynth.CHANNELS,
            groove.size,
        )
    }

    @Test
    fun itIsAudibleButLeavesHeadroom() {
        val peak = groove.maxOf { abs(it.toInt()) }.toFloat() / Short.MAX_VALUE
        val rms =
            sqrt(
                groove.sumOf { (it.toDouble() / Short.MAX_VALUE).let { s -> s * s } } / groove.size
            )
        assertTrue(peak < 0.9f, "peak $peak should stay under the soft clipper's ceiling")
        assertTrue(rms > 0.05, "rms $rms is too quiet")
    }

    @Test
    fun theKickLandsOnTheBeat() {
        // The loudest moment near each downbeat should be right after it, not before.
        val left =
            ShortArray(groove.size / GrooveSynth.CHANNELS) { groove[it * GrooveSynth.CHANNELS] }
        val beat = (GrooveSynth.SAMPLE_RATE / BEATS_PER_SECOND).toInt()
        val window = beat / 8
        val after = (0 until window).maxOf { abs(left[it].toInt()) }
        val before = (left.size - window until left.size).maxOf { abs(left[it].toInt()) }
        assertTrue(
            after > before,
            "downbeat $after should be louder than the end of the loop $before",
        )
    }
}
