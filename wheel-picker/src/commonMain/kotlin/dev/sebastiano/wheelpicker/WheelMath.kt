package dev.sebastiano.wheelpicker

import kotlin.math.exp
import kotlin.math.roundToInt

/** How far past either end a drag can pull the ruler, in stops. */
const val RUBBER_BAND_LIMIT = 0.6f

/** How long a flick coasts before it snaps, in seconds of its release velocity. */
const val COAST_SECONDS = 0.18f

/**
 * The stop a release lands on: where [velocity] (stops a second) would carry [position] in
 * [COAST_SECONDS], rounded, and kept on the ruler.
 */
fun snapIndex(position: Float, velocity: Float, lastIndex: Int): Int =
    (position + velocity * COAST_SECONDS).roundToInt().coerceIn(0, lastIndex)

/**
 * A drag [position] with the ends made elastic: inside the ruler it is unchanged, past either end
 * it follows the pointer less and less, never further than [RUBBER_BAND_LIMIT].
 */
fun rubberBand(position: Float, lastIndex: Int): Float =
    when {
        position < 0f -> -band(-position)
        position > lastIndex -> lastIndex + band(position - lastIndex)
        else -> position
    }

private fun band(overscroll: Float): Float =
    RUBBER_BAND_LIMIT * (1f - exp(-overscroll / RUBBER_BAND_LIMIT))

/**
 * A soft bump, 1 under the indicator and falling off over [width], that lifts the nearest ticks.
 */
fun centreBump(distance: Float, width: Float): Float {
    val t = distance / width
    return exp(-t * t)
}
