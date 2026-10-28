package dev.sebastiano.routinetimeline

const val MINUTES_PER_DAY = 1440

/** The big readout: `9:52` and, set apart below it, `AM`. */
data class ClockText(val digits: String, val meridiem: String)

/**
 * The specimen keeps time as a running count of minutes that scrubbing can push past midnight or
 * below zero. This is where in the day that count falls.
 */
fun minuteOfDay(time: Double): Double = time.mod(MINUTES_PER_DAY.toDouble())

fun clockText(minute: Int): ClockText {
    val inDay = minute.mod(MINUTES_PER_DAY)
    val hour = inDay / 60
    val hour12 = if (hour % 12 == 0) 12 else hour % 12
    val minutes = (inDay % 60).toString().padStart(2, '0')
    return ClockText("$hour12:$minutes", if (hour < 12) "AM" else "PM")
}

/** `8:00 AM`. */
fun clockLabel(minute: Int): String = clockText(minute).let { "${it.digits} ${it.meridiem}" }

/** `8:00 AM–10:00 AM`. */
fun rangeLabel(start: Int, end: Int): String = "${clockLabel(start)}–${clockLabel(end)}"

/** The dial's marks: `12a`, `3a` … `9p`. */
fun hourMark(hour: Int): String {
    val inDay = hour.mod(24)
    val hour12 = if (inDay % 12 == 0) 12 else inDay % 12
    return "$hour12${if (inDay < 12) "a" else "p"}"
}
