// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/*
 * The vector helpers of `kit/lathe.mjs`, on `Point3`, plus the few JavaScript number semantics the
 * round parts rely on, so they reproduce the original's paths to the digit.
 */

internal const val TAU: Double = PI * 2

fun dot3(a: Point3, b: Point3): Double = a.x * b.x + a.y * b.y + a.z * b.z

fun add3(a: Point3, b: Point3): Point3 = Point3(a.x + b.x, a.y + b.y, a.z + b.z)

fun sub3(a: Point3, b: Point3): Point3 = Point3(a.x - b.x, a.y - b.y, a.z - b.z)

fun mul3(a: Point3, k: Double): Point3 = Point3(a.x * k, a.y * k, a.z * k)

fun len3(a: Point3): Double = jsHypot(a.x, a.y, a.z)

/** [a] scaled to length 1; a zero vector stays zero. */
fun unit3(a: Point3): Point3 {
    val length = len3(a).orOne()
    return Point3(a.x / length, a.y / length, a.z / length)
}

fun cross3(a: Point3, b: Point3): Point3 =
    Point3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)

fun lerp3(a: Point3, b: Point3, t: Double): Point3 =
    Point3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t)

/** JavaScript's `x || 1` on a number: zero and NaN become 1. */
internal fun Double.orOne(): Double = if (this == 0.0 || isNaN()) 1.0 else this

/** JavaScript's `Math.sign(x) || 1`. */
internal fun signOrOne(value: Double): Double =
    when {
        value > 0 -> 1.0
        value < 0 -> -1.0
        else -> 1.0
    }

/**
 * V8's `Math.hypot(a, b)`: normalised by the largest value and summed with Kahan compensation. It
 * differs from the JVM's `hypot` in the last bit, which can tip a rounding in the original's paths.
 */
internal fun jsHypot(a: Double, b: Double): Double {
    val x = abs(a)
    val y = abs(b)
    val max = largest(x, y, 0.0)
    if (max == Double.POSITIVE_INFINITY) return max
    if (a.isNaN() || b.isNaN()) return Double.NaN
    if (max == 0.0) return 0.0
    val sum = Kahan()
    sum.add(x / max)
    sum.add(y / max)
    return sqrt(sum.sum) * max
}

/** V8's `Math.hypot(a, b, c)`. See the two-argument one. */
internal fun jsHypot(a: Double, b: Double, c: Double): Double {
    val x = abs(a)
    val y = abs(b)
    val z = abs(c)
    val max = largest(x, y, z)
    if (max == Double.POSITIVE_INFINITY) return max
    if (a.isNaN() || b.isNaN() || c.isNaN()) return Double.NaN
    if (max == 0.0) return 0.0
    val sum = Kahan()
    sum.add(x / max)
    sum.add(y / max)
    sum.add(z / max)
    return sqrt(sum.sum) * max
}

/** The largest of three magnitudes, skipping NaN as V8 does. */
private fun largest(x: Double, y: Double, z: Double): Double {
    var max = 0.0
    if (x > max) max = x
    if (y > max) max = y
    if (z > max) max = z
    return max
}

/** A sum of squares with Kahan compensation, in V8's order. */
private class Kahan {
    var sum = 0.0
    private var compensation = 0.0

    fun add(n: Double) {
        val summand = n * n - compensation
        val preliminary = sum + summand
        compensation = (preliminary - sum) - summand
        sum = preliminary
    }
}

/**
 * `t ** 3`, correctly rounded. V8's `Math.pow` is within one ulp of this; `t * t * t` rounds twice
 * and is off far more often.
 */
internal fun cube(t: Double): Double {
    val square = t * t
    val product = square * t
    return product + (productError(square, t, product) + productError(t, t, square) * t)
}

/** The exact rounding error of `a * b = product`, by Dekker's split. */
private fun productError(a: Double, b: Double, product: Double): Double {
    val aHigh = splitHigh(a)
    val aLow = a - aHigh
    val bHigh = splitHigh(b)
    val bLow = b - bHigh
    return ((aHigh * bHigh - product) + aHigh * bLow + aLow * bHigh) + aLow * bLow
}

private fun splitHigh(value: Double): Double {
    val c = 134217729.0 * value
    return c - (c - value)
}

/** `Math.ceil(Math.log2(value))` for a normal value of at least 1, exact at powers of two. */
internal fun ceilLog2(value: Double): Int {
    val bits = value.toRawBits()
    val exponent = ((bits shr 52) and 0x7ff).toInt() - 1023
    val power = bits and 0xfffffffffffffL == 0L
    return if (power) exponent else exponent + 1
}

/** The original's `oriented`: [points] wound the same way as every other fill. */
internal fun windOf(points: List<Flat>): List<Flat> {
    var sum = 0.0
    for (index in points.indices) {
        val (x, y) = points[index]
        val (nx, ny) = points[(index + 1) % points.size]
        sum = sum + x * ny - nx * y
    }
    return if (sum < 0) points.reversed() else points
}
