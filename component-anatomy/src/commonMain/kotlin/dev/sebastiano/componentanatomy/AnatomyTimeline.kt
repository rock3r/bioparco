package dev.sebastiano.componentanatomy

import kotlin.math.exp
import kotlin.math.floor

/** The interaction states the tour walks through. */
enum class AnatomyState {
    Normal,
    Hovered,
    Pressed,
    Focused,
    Disabled,
}

/** What the stage shows at one moment: [tilt] and [explode] run 0..1, [pulse] is the beat. */
data class AnatomyFrame(
    val state: AnatomyState,
    val outlined: Boolean,
    val tilt: Float,
    val explode: Float,
    val pulse: Float,
)

/**
 * The choreography as a pure function of the beat clock. One loop is eight bars:
 *
 * | Bar | What happens                                |
 * |-----|---------------------------------------------|
 * | 0   | flat, collapsed, Normal                     |
 * | 1   | tilt, then explode                          |
 * | 2–6 | Hovered, Pressed, Focused, Disabled, Normal |
 * | 7   | collapse, then flatten                      |
 *
 * Odd loops show the outlined button, even loops the default one.
 */
object AnatomyTimeline {
    const val BEATS_PER_BAR = 4
    const val BARS_PER_LOOP = 8
    const val BEATS_PER_LOOP = BEATS_PER_BAR * BARS_PER_LOOP

    private const val TILT_IN = 4.0
    private const val EXPLODE_IN = 5.0
    private const val OPENING_BEATS = 2.0
    private const val COLLAPSE = 28.0
    private const val COLLAPSE_BEATS = 1.5
    private const val TILT_OUT = 29.0
    private const val PULSE_DECAY = 5.0

    private val tour =
        mapOf(
            2 to AnatomyState.Hovered,
            3 to AnatomyState.Pressed,
            4 to AnatomyState.Focused,
            5 to AnatomyState.Disabled,
        )

    fun at(beats: Double): AnatomyFrame {
        val loop = floor(beats / BEATS_PER_LOOP)
        val inLoop = beats - loop * BEATS_PER_LOOP
        val bar = (inLoop / BEATS_PER_BAR).toInt()
        val tilt =
            easeInOutCubic(progress(inLoop, TILT_IN, OPENING_BEATS)) -
                easeInOutCubic(progress(inLoop, TILT_OUT, OPENING_BEATS))
        val explode =
            easeOutBack(progress(inLoop, EXPLODE_IN, OPENING_BEATS)) -
                easeInOutCubic(progress(inLoop, COLLAPSE, COLLAPSE_BEATS))
        return AnatomyFrame(
            state = tour[bar] ?: AnatomyState.Normal,
            outlined = loop.toLong().mod(2L) == 1L,
            tilt = tilt.toFloat(),
            explode = explode.toFloat(),
            pulse = exp(-PULSE_DECAY * (beats - floor(beats))).toFloat(),
        )
    }

    private fun progress(beat: Double, start: Double, length: Double) =
        ((beat - start) / length).coerceIn(0.0, 1.0)

    private fun easeInOutCubic(t: Double) =
        if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).let { it * it * it } / 2

    /** Overshoots a little past 1 before settling, so the layers spring apart. */
    private fun easeOutBack(t: Double): Double {
        // The polynomial is 0 at t = 0 only up to rounding. A collapsed stack must be exactly 0.
        if (t <= 0.0) return 0.0
        val c1 = 1.70158
        val c3 = c1 + 1
        val u = t - 1
        return 1 + c3 * u * u * u + c1 * u * u
    }
}
