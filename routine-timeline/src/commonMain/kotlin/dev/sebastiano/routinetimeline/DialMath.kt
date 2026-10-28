package dev.sebastiano.routinetimeline

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Half the dial is twelve hours, so the whole dial is exactly one day: a quarter of a degree a
 * minute. Now sits at 0° (three o'clock) and later times turn clockwise, which is down the screen.
 */
const val DEGREES_PER_MINUTE = 0.25

private const val QUARTER_HOUR = 15.0

/** Steps closer than this to a quarter hour count as on it. */
private const val ON_QUARTER = 1e-6

/** Where [minute] sits on screen when the dial shows [now], in (-180°, 180°]. */
fun screenAngle(minute: Double, now: Double): Double {
    var offset = (minute - now).mod(MINUTES_PER_DAY.toDouble())
    if (offset > MINUTES_PER_DAY / 2) offset -= MINUTES_PER_DAY
    return offset * DEGREES_PER_MINUTE
}

/** How far a pointer turned round the dial between two angles, taken the short way. */
fun turnDegrees(from: Double, to: Double): Double {
    var delta = (to - from).mod(360.0)
    if (delta > 180.0) delta -= 360.0
    return delta
}

/**
 * Turning the dial clockwise carries the earlier times above the marker down onto it, so the time
 * goes back.
 */
fun minutesForTurn(degrees: Double): Double = -degrees / DEGREES_PER_MINUTE

/** The running minute showing [minuteOfDay] that is closest to [near]. */
fun nearestEquivalent(minuteOfDay: Double, near: Double): Double {
    val days = ((near - minuteOfDay) / MINUTES_PER_DAY).roundToLong()
    return minuteOfDay + days * MINUTES_PER_DAY
}

/** The next quarter hour after [time] in [direction] (1 later, -1 earlier). */
fun quarterStep(time: Double, direction: Int): Double =
    if (direction > 0) {
        (floor((time + ON_QUARTER) / QUARTER_HOUR) + 1) * QUARTER_HOUR
    } else {
        (ceil((time - ON_QUARTER) / QUARTER_HOUR) - 1) * QUARTER_HOUR
    }

/**
 * Where a capsule's icon goes: its middle, kept [inset] inside its ends and within ±[limit]°, or
 * null if no part of it is in range. A capsule whose start has wrapped to the back of the dial
 * draws its tail at the top, so it is also tried a whole turn earlier.
 */
fun iconAngle(start: Double, sweep: Double, inset: Double, limit: Double): Double? =
    placeIcon(start, sweep, inset, limit) ?: placeIcon(start - 360.0, sweep, inset, limit)

private fun placeIcon(start: Double, sweep: Double, inset: Double, limit: Double): Double? {
    val low = maxOf(start + inset, -limit)
    val high = minOf(start + sweep - inset, limit)
    if (low > high) return null
    return (start + sweep / 2).coerceIn(low, high)
}
