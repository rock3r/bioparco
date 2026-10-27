package dev.sebastiano.squeezeprogress

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cosh
import kotlin.math.sin
import kotlin.math.tanh

/**
 * The kink a paused bar crumples into, at one moment. The line is described by `s`, the distance
 * along it before crumpling; the kink deforms the stretch `centre ± halfWidth` of it.
 *
 * Across that stretch the line makes one and a half waves: it rises, dips a little deeper, then
 * rises a little and lies flat again. [height] is the tallest excursion in px (negative mirrors it,
 * which the release uses for its ripple).
 *
 * [squeeze] pulls the waves together sideways and squares them off. At 0 they are plain sine waves.
 * At 1 they are the squiggle in the video: upright strokes joined by round caps, like a softened
 * square wave. Past 1 the strokes lean back and the line crosses itself, which is where a springy
 * pause overshoots to. Squeezing takes up length, so the line left of the kink slides right to make
 * room; the tip, right of it, never moves.
 */
data class Crumple(val centre: Float, val halfWidth: Float, val height: Float, val squeeze: Float)

/** Where the point `s` along the line ends up across. */
fun crumpleX(s: Float, crumple: Crumple): Float {
    val u = (s - crumple.centre) / crumple.halfWidth
    val taken = crumple.squeeze * crumple.halfWidth
    return when {
        u <= -1f -> s + taken * TakenUp.total
        u >= 1f -> s
        else -> s + taken * (TakenUp.total - TakenUp.upTo(u))
    }
}

/** Where the point `s` along the line ends up vertically, relative to the line. Screen y: down. */
fun crumpleY(s: Float, crumple: Crumple): Float {
    val u = (s - crumple.centre) / crumple.halfWidth
    if (u <= -1f || u >= 1f) return 0f
    val squareness = SQUARENESS * crumple.squeeze.coerceIn(0f, 1f)
    val wave = squared(phase(u), squareness)
    return (-crumple.height * lobeGain(u) * edge(u) * wave).toFloat()
}

/** The phase across the stretch: three half waves, 0 at the left edge, 3π at the right. */
private fun phase(u: Float): Double = WAVES * PI * (u + 1.0)

/**
 * A sine squared off towards a square wave as [k] grows: `tanh(k sin φ) / tanh k`. It still peaks
 * at exactly 1, and at `k` near 0 it is the sine itself.
 */
private fun squared(phi: Double, k: Double): Double =
    if (k < SINE_LIMIT) sin(phi) else tanh(k * sin(phi)) / tanh(k)

/**
 * How tall each lobe is: 0.95, 1 and 0.45 of the tallest, as measured off the video. The gain only
 * changes around the points where the line crosses its rest height, where the wave is zero, so the
 * changes never show as bumps.
 */
private fun lobeGain(u: Float): Double =
    FIRST_LOBE +
        (MIDDLE_LOBE - FIRST_LOBE) * smoothstep((u + THIRD + GAIN_STEP) / (2 * GAIN_STEP)) +
        (LAST_LOBE - MIDDLE_LOBE) * smoothstep((u - THIRD + GAIN_STEP) / (2 * GAIN_STEP))

/** 1 across the stretch, easing to 0 over its last few percent each end: a small round corner. */
private fun edge(u: Float): Double = smoothstep((1.0 - abs(u)) / EDGE)

private fun smoothstep(t: Double): Double {
    val c = t.coerceIn(0.0, 1.0)
    return c * c * (3 - 2 * c)
}

/**
 * How hard the squeeze pulls at `u`: fully wherever the line is steep, not at all around the lobe
 * tops, so `x' = 1 − squeeze × pull` stands the strokes upright at a squeeze of 1 and leaves the
 * caps round. `sech²(k sin φ)` is the slope profile of the squared-off wave, so the pull follows
 * exactly where it climbs. It is exactly 1 where the line crosses its rest height.
 */
private fun pull(u: Float): Double {
    val c = cosh(SQUARENESS * sin(phase(u)))
    return edge(u) / (c * c)
}

/**
 * How much length the squeeze has taken up left of `u`, per unit of squeeze and half width: the
 * integral of [pull] from -1. It has no closed form, so it is tabulated once.
 */
private object TakenUp {
    private const val STEPS = 1_024
    private val table = FloatArray(STEPS + 1)

    init {
        var sum = 0.0
        var previous = pull(-1f)
        for (i in 1..STEPS) {
            val next = pull(-1f + 2f * i / STEPS)
            sum += (previous + next) / 2 * (2.0 / STEPS)
            table[i] = sum.toFloat()
            previous = next
        }
    }

    val total: Float = table[STEPS]

    fun upTo(u: Float): Float {
        val position = (u + 1f) / 2f * STEPS
        val index = position.toInt().coerceIn(0, STEPS - 1)
        val fraction = position - index
        return table[index] + (table[index + 1] - table[index]) * fraction
    }
}

/** One and a half waves across the stretch. */
private const val WAVES = 1.5

/** How square the fully squeezed wave is: 2.5 gives the video's upright strokes and round caps. */
private const val SQUARENESS = 2.5

/**
 * Below this the squaring is too slight to tell from a sine, and `tanh k` too small to divide by.
 */
private const val SINE_LIMIT = 1e-3

private const val FIRST_LOBE = 0.95
private const val MIDDLE_LOBE = 1.0
private const val LAST_LOBE = 0.45

/** Where the lobes meet, in `u`. */
private const val THIRD = 1.0 / 3.0

/** Half the width, in `u`, over which one lobe's gain hands over to the next. */
private const val GAIN_STEP = 0.1

/** The share of the half width, each end, over which the line eases out of the flat. */
private const val EDGE = 0.12
