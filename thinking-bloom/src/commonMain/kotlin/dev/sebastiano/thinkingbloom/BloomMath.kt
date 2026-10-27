package dev.sebastiano.thinkingbloom

import kotlin.math.ceil
import kotlin.random.Random

/**
 * One pass of the bloom. A band sweeps the label from left to right. Where it arrives, specks of
 * colour appear on the black glyphs; they open into flowers that hide the ink, then close back to
 * specks and leave the plain glyphs behind. Positions along the label are in label widths, 0 at the
 * left edge and 1 at the right. A point's phase says how far the band has passed it: below 0 the
 * band has not reached it, 0 to 1 it is inside the band, above 1 the band has gone.
 */
internal object BloomMath {
    /** One sweep plus a short rest on the plain label, as in the reference. */
    const val LOOP_SECONDS = 3.0f
    const val SWEEP_SECONDS = 2.85f

    /** How wide the band is, in label widths. */
    const val BAND = 1.1f

    /**
     * Room before and after the label, in phases, so blossoms that lag the front start and end
     * clean.
     */
    const val EDGE = 0.06f

    /** Where the band's leading edge is at [seconds], in label widths. */
    fun front(seconds: Float): Float {
        val inLoop = seconds.mod(LOOP_SECONDS)
        val t = (inLoop / SWEEP_SECONDS).coerceIn(0f, 1f)
        // Mostly steady, easing a little into and out of the sweep.
        val eased = t + (smoothstep(t) - t) * EASE
        val start = -EDGE * BAND
        val end = 1f + BAND * (1f + EDGE)
        return start + (end - start) * eased
    }

    /** How far the band has passed the point at [x], given its leading edge at [front]. */
    fun phase(x: Float, front: Float): Float = (front - x) / BAND

    /** A blossom's size at [phase], as a fraction of its full size. */
    fun bloom(phase: Float): Float =
        when {
            phase <= 0f || phase >= 1f -> 0f
            phase < ARRIVED -> SPECK * smoothstep(phase / ARRIVED)
            phase < OPENED -> SPECK + (1f - SPECK) * backOut((phase - ARRIVED) / (OPENED - ARRIVED))
            phase < CLOSING -> 1f
            phase < CLOSED -> 1f - (1f - SPECK) * smoothstep((phase - CLOSING) / (CLOSED - CLOSING))
            else -> SPECK * (1f - smoothstep((phase - CLOSED) / (1f - CLOSED)))
        }

    /** How much of the plain glyph's ink shows at [phase]: none under full blossoms. */
    fun ink(phase: Float): Float =
        when {
            phase <= INK_FADING -> 1f
            phase < INK_GONE -> 1f - smoothstep((phase - INK_FADING) / (INK_GONE - INK_FADING))
            phase <= INK_BACK -> 0f
            phase < INK_FULL -> smoothstep((phase - INK_BACK) / (INK_FULL - INK_BACK))
            else -> 1f
        }

    private fun smoothstep(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    /** Overshoots a little past 1 before settling, so a blossom pops open. */
    private fun backOut(t: Float): Float {
        val x = t.coerceIn(0f, 1f) - 1f
        return 1f + (OVERSHOOT + 1f) * x * x * x + OVERSHOOT * x * x
    }

    private const val EASE = 0.15f
    private const val SPECK = 0.22f
    private const val ARRIVED = 0.12f
    private const val OPENED = 0.28f
    private const val CLOSING = 0.6f
    private const val CLOSED = 0.8f
    private const val OVERSHOOT = 1.4f
    private const val INK_FADING = 0.08f
    private const val INK_GONE = 0.2f
    private const val INK_BACK = 0.64f
    private const val INK_FULL = 0.86f
}

/** A point in a shape, in its pixels. */
internal data class Spot(val x: Float, val y: Float)

/**
 * Jittered-grid points over a [width] by [height] area, at most one per [spacing]-sized cell,
 * keeping those [inside] the shape. Even coverage without the clumps of plain random points. No
 * spacing, as from a zero font size, scatters nothing.
 */
internal fun scatter(
    width: Int,
    height: Int,
    spacing: Float,
    random: Random,
    inside: (x: Float, y: Float) -> Boolean,
): List<Spot> {
    if (spacing <= 0f) return emptyList()
    val columns = ceil(width / spacing).toInt()
    val rows = ceil(height / spacing).toInt()
    val spots = ArrayList<Spot>()
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            val x = (column + random.nextFloat()) * spacing
            val y = (row + random.nextFloat()) * spacing
            if (x < width && y < height && inside(x, y)) spots += Spot(x, y)
        }
    }
    return spots
}
