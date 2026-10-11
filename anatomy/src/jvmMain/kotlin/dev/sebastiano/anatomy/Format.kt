package dev.sebastiano.anatomy

import kotlin.math.abs
import kotlin.math.floor

/** JavaScript's `toFixed(1)`, locale-free, for readouts: `14.0`, `-0.5`. */
fun oneDecimal(value: Double): String {
    val tenths = floor(abs(value) * 10 + 0.5).toLong()
    val sign = if (value < 0 && tenths != 0L) "-" else ""
    return "$sign${tenths / 10}.${tenths % 10}"
}
