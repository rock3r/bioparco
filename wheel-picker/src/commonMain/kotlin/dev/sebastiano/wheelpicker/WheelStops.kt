package dev.sebastiano.wheelpicker

/** One snap point on the ruler. [minutes] is `null` for the first stop, which means no limit. */
data class WheelStop(val minutes: Int?, val isMajor: Boolean)

/**
 * The ruler's stops. They get coarser as durations grow, as in the original: five minutes apart up
 * to an hour, then quarter hours, half hours, whole hours, and two hours at the end of the day. The
 * labelled majors double from one hour: 1h, 2h, 4h, 8h, then 12h and 24h.
 */
object WheelStops {
    private val majors = setOf(null, 60, 120, 240, 480, 720, 1440)

    val all: List<WheelStop> =
        (listOf<Int?>(null) +
                (5..55 step 5) +
                listOf(60, 75, 90, 105, 120, 150, 180, 210, 240) +
                (300..720 step 60) +
                (840..1440 step 120))
            .map { WheelStop(it, it in majors) }

    val lastIndex: Int = all.lastIndex
}

/** Enough slots for the longest readout, "1h 45m". */
const val READOUT_SLOTS = 7

/** An empty readout slot, which takes no width. */
const val NO_GLYPH = '\u0000'

/** "∞" for no limit, then "45m", "1h" or "1h 15m". */
fun formatReadout(minutes: Int?): String {
    if (minutes == null) return "∞"
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> "${rest}m"
        rest == 0 -> "${hours}h"
        else -> "${hours}h ${rest}m"
    }
}

/**
 * [text] spread over [count] slots, one character each, padded with [NO_GLYPH]. Each slot rolls on
 * its own when its character changes.
 */
fun readoutSlots(text: String, count: Int): List<Char> =
    List(count) { text.getOrElse(it) { NO_GLYPH } }
