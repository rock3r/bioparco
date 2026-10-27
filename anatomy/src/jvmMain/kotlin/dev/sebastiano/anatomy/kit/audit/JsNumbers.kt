// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.audit

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor

/* JavaScript's number printing, so the audit's labels and report read as the original's do. */

/** JavaScript's `value.toFixed(digits)`: the exact value, halves away from zero, `-0.00` kept. */
internal fun jsFixed(value: Double, digits: Int): String {
    if (value.isNaN()) return "NaN"
    if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"
    val sign = if (value < 0) "-" else ""
    return sign + BigDecimal(abs(value)).setScale(digits, RoundingMode.HALF_UP).toPlainString()
}

/** JavaScript's `String(value)` for the numbers the audit prints. */
internal fun jsString(value: Double): String {
    if (value.isNaN()) return "NaN"
    if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"
    if (value == 0.0) return "0"
    if (value == floor(value) && abs(value) < 1e21) return BigDecimal(value).toPlainString()
    val exact = BigDecimal(value.toString()).stripTrailingZeros()
    if (abs(value) >= 1e-6) return exact.toPlainString()
    val digits = exact.unscaledValue().abs().toString()
    val exponent = digits.length - 1 - exact.scale()
    val mantissa = if (digits.length == 1) digits else "${digits[0]}.${digits.drop(1)}"
    return "${if (value < 0) "-" else ""}${mantissa}e$exponent"
}
