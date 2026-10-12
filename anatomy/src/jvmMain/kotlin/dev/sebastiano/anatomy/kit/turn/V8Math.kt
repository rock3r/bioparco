// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed, and from
// V8's src/base/ieee754.cc (fdlibm, Copyright (C) 1993 by Sun Microsystems, Inc.: "Permission to
// use, copy, modify, and distribute this software is freely granted, provided that this notice is
// preserved."). See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import kotlin.math.abs
import kotlin.math.sqrt

/*
 * `Math.sin`, `Math.cos`, `Math.acos` and `Math.atan2` exactly as the original's runtime computes
 * them in Node (V8 13.6 on arm64): V8's fdlibm, compiled by clang with floating-point contraction,
 * so every `a·b + c` in one expression is a fused multiply-add. The JVM's `Math` and `StrictMath`
 * differ from it in the last bit for about one argument in a hundred, and the runtime turns some of
 * those bits into whole decisions (a ring's sample count at an exact `ceil`), so the 3D mode uses
 * these to match the original's frames to the digit. Checked against Node on 200,000 arguments.
 */

private fun fma(a: Double, b: Double, c: Double): Double = Math.fma(a, b, c)

private fun high(x: Double): Int = (x.toRawBits() ushr 32).toInt()

private val ATAN_HI =
    doubleArrayOf(
        4.63647609000806093515e-01,
        7.85398163397448278999e-01,
        9.82793723247329054082e-01,
        1.57079632679489655800e+00,
    )
private val ATAN_LO =
    doubleArrayOf(
        2.26987774529616870924e-17,
        3.06161699786838301793e-17,
        1.39033110312309984516e-17,
        6.12323399573676603587e-17,
    )
private val AT =
    doubleArrayOf(
        3.33333333333329318027e-01,
        -1.99999999998764832476e-01,
        1.42857142725034663711e-01,
        -1.11111104054623557880e-01,
        9.09088713343650656196e-02,
        -7.69187620504482999495e-02,
        6.66107313738753120669e-02,
        -5.83357013379057348645e-02,
        4.97687799461593236017e-02,
        -3.65315727442169155270e-02,
        1.62858201153657823623e-02,
    )
private const val PI = 3.1415926535897931160E+00
private const val PI_LO = 1.2246467991473531772E-16
private const val PI_O_2 = 1.5707963267948965580E+00

/** V8's `Math.atan`. */
internal fun jsAtan(x0: Double): Double {
    var x = x0
    val hx = high(x)
    val ix = hx and 0x7fffffff
    if (ix >= 0x44100000) {
        if (x.isNaN()) return x + x
        return if (hx > 0) ATAN_HI[3] + ATAN_LO[3] else -ATAN_HI[3] - ATAN_LO[3]
    }
    val id: Int
    if (ix < 0x3FDC0000) {
        if (ix < 0x3E400000) return x
        id = -1
    } else {
        x = abs(x)
        if (ix < 0x3FF30000) {
            if (ix < 0x3FE60000) {
                id = 0
                x = fma(2.0, x, -1.0) / (2.0 + x)
            } else {
                id = 1
                x = (x - 1.0) / (x + 1.0)
            }
        } else if (ix < 0x40038000) {
            id = 2
            x = (x - 1.5) / fma(1.5, x, 1.0)
        } else {
            id = 3
            x = -1.0 / x
        }
    }
    val z = x * x
    val w = z * z
    val s1 = z * fma(w, fma(w, fma(w, fma(w, fma(w, AT[10], AT[8]), AT[6]), AT[4]), AT[2]), AT[0])
    val s2 = w * fma(w, fma(w, fma(w, fma(w, AT[9], AT[7]), AT[5]), AT[3]), AT[1])
    if (id < 0) return fma(-x, s1 + s2, x)
    val r = ATAN_HI[id] - (fma(x, s1 + s2, -ATAN_LO[id]) - x)
    return if (hx < 0) -r else r
}

/** V8's `Math.atan2`. */
internal fun jsAtan2(y: Double, x: Double): Double {
    if (x.isNaN() || y.isNaN()) return x + y
    if (x == 1.0) return jsAtan(y)
    if (x.isInfinite() || y.isInfinite()) return StrictMath.atan2(y, x)
    val hx = high(x)
    val hy = high(y)
    val m = ((hy ushr 31) and 1) or ((hx ushr 30) and 2)
    if (y == 0.0) return atan2OnAxis(m, y)
    if (x == 0.0) return if (hy < 0) -PI_O_2 else PI_O_2
    return atan2Quadrant(y, x, hx, hy, m)
}

/** `atan2(±0, x)`: ±0 for x > 0 and ±π for x < 0, by the sign bits in [m]. */
private fun atan2OnAxis(m: Int, y: Double) =
    when (m) {
        0,
        1 -> y
        2 -> PI
        else -> -PI
    }

/** `atan2` off the axes, with the high words [hx], [hy] and the sign bits [m0]. */
private fun atan2Quadrant(y: Double, x: Double, hx: Int, hy: Int, m0: Int): Double {
    var m = m0
    val k = ((hy and 0x7fffffff) - (hx and 0x7fffffff)) shr 20
    val z: Double
    if (k > 60) {
        z = fma(0.5, PI_LO, PI_O_2)
        m = m and 1
    } else if (hx < 0 && k < -60) {
        z = 0.0
    } else {
        z = jsAtan(abs(y / x))
    }
    return when (m) {
        0 -> z
        1 -> -z
        2 -> PI - (z - PI_LO)
        else -> (z - PI_LO) - PI
    }
}

private const val PIO2_HI = 1.57079632679489655800e+00
private const val PIO2_LO = 6.12323399573676603587e-17
private val PS =
    doubleArrayOf(
        1.66666666666666657415e-01,
        -3.25565818622400915405e-01,
        2.01212532134862925881e-01,
        -4.00555345006794114027e-02,
        7.91534994289814532176e-04,
        3.47933107596021167570e-05,
    )
private val QS =
    doubleArrayOf(
        0.0,
        -2.40339491173441421878e+00,
        2.02094576023350569471e+00,
        -6.88283971605453293030e-01,
        7.70381505559019352791e-02,
    )

private fun acosP(z: Double) =
    z * fma(z, fma(z, fma(z, fma(z, fma(z, PS[5], PS[4]), PS[3]), PS[2]), PS[1]), PS[0])

private fun acosQ(z: Double) = fma(z, fma(z, fma(z, fma(z, QS[4], QS[3]), QS[2]), QS[1]), 1.0)

/** V8's `Math.acos`. */
internal fun jsAcos(x: Double): Double {
    val hx = high(x)
    val ix = hx and 0x7fffffff
    if (ix >= 0x3FF00000) {
        if (x == 1.0) return 0.0
        if (x == -1.0) return PI + 2.0 * PIO2_LO
        return Double.NaN
    }
    if (ix < 0x3FE00000) {
        if (ix <= 0x3C600000) return PIO2_HI + PIO2_LO
        val z = x * x
        val r = acosP(z) / acosQ(z)
        return PIO2_HI - (x - fma(-x, r, PIO2_LO))
    }
    if (hx < 0) {
        val z = (1.0 + x) * 0.5
        val s = sqrt(z)
        val r = acosP(z) / acosQ(z)
        val w = fma(r, s, -PIO2_LO)
        return fma(-2.0, s + w, PI)
    }
    val z = (1.0 - x) * 0.5
    val s = sqrt(z)
    val df = Double.fromBits(s.toRawBits() and -0x100000000L)
    val c = fma(-df, df, z) / (s + df)
    val r = acosP(z) / acosQ(z)
    val w = fma(r, s, c)
    return 2.0 * (df + w)
}

private val SS =
    doubleArrayOf(
        -1.66666666666666324348e-01,
        8.33333333332248946124e-03,
        -1.98412698298579493134e-04,
        2.75573137070700676789e-06,
        -2.50507602534068634195e-08,
        1.58969099521155010221e-10,
    )
private val CS =
    doubleArrayOf(
        4.16666666666666019037e-02,
        -1.38888888888741095749e-03,
        2.48015872894767294178e-05,
        -2.75573143513906633035e-07,
        2.08757232129817482790e-09,
        -1.13596475577881948265e-11,
    )

private fun kernelSin(x: Double, y: Double, iy: Int): Double {
    val ix = high(x) and 0x7fffffff
    if (ix < 0x3E400000 && x.toInt() == 0) return x
    val z = x * x
    val v = z * x
    val r = fma(z, fma(z, fma(z, fma(z, SS[5], SS[4]), SS[3]), SS[2]), SS[1])
    if (iy == 0) return fma(v, fma(z, r, SS[0]), x)
    return x - fma(-v, SS[0], fma(z, fma(0.5, y, -(v * r)), -y))
}

private fun kernelCos(x: Double, y: Double): Double {
    val ix = high(x) and 0x7fffffff
    if (ix < 0x3E400000 && x.toInt() == 0) return 1.0
    val z = x * x
    val r = z * fma(z, fma(z, fma(z, fma(z, fma(z, CS[5], CS[4]), CS[3]), CS[2]), CS[1]), CS[0])
    val zr = fma(z, r, -(x * y))
    if (ix < 0x3FD33333) return 1.0 - fma(0.5, z, -zr)
    val qx = if (ix > 0x3FE90000) 0.28125 else Double.fromBits((ix - 0x00200000).toLong() shl 32)
    val iz = fma(0.5, z, -qx)
    val a = 1.0 - qx
    return a - (iz - zr)
}

private val NPIO2_HW =
    intArrayOf(
        0x3FF921FB,
        0x400921FB,
        0x4012D97C,
        0x401921FB,
        0x401F6A7A,
        0x4022D97C,
        0x4025FDBB,
        0x402921FB,
        0x402C463A,
        0x402F6A7A,
        0x4031475C,
        0x4032D97C,
        0x40346B9C,
        0x4035FDBB,
        0x40378FDB,
        0x403921FB,
        0x403AB41B,
        0x403C463A,
        0x403DD85A,
        0x403F6A7A,
        0x40407E4C,
        0x4041475C,
        0x4042106C,
        0x4042D97C,
        0x4043A28C,
        0x40446B9C,
        0x404534AC,
        0x4045FDBB,
        0x4046C6CB,
        0x40478FDB,
        0x404858EB,
        0x404921FB,
    )
private const val INV_PIO2 = 6.36619772367581382433e-01
private const val PIO2_1 = 1.57079632673412561417e+00
private const val PIO2_1T = 6.07710050650619224932e-11
private const val PIO2_2 = 6.07710050630396597660e-11
private const val PIO2_2T = 2.02226624879595063154e-21
private const val PIO2_3 = 2.02226624871116645580e-21
private const val PIO2_3T = 8.47842766036889956997e-32

/** `__ieee754_rem_pio2` for `|x| ≤ 2^19·π/2`: `x = n·π/2 + y[0] + y[1]`; returns `n`. */
private fun remPio2(x: Double, y: DoubleArray): Int {
    val hx = high(x)
    val ix = hx and 0x7fffffff
    if (ix < 0x4002D97C) {
        val sign = if (hx > 0) 1.0 else -1.0
        var z = x - sign * PIO2_1
        if (ix != 0x3FF921FB) {
            y[0] = z - sign * PIO2_1T
            y[1] = (z - y[0]) - sign * PIO2_1T
        } else {
            z -= sign * PIO2_2
            y[0] = z - sign * PIO2_2T
            y[1] = (z - y[0]) - sign * PIO2_2T
        }
        return if (hx > 0) 1 else -1
    }
    var t = abs(x)
    val n = fma(t, INV_PIO2, 0.5).toInt()
    val fn = n.toDouble()
    var r = fma(-fn, PIO2_1, t)
    var w = fn * PIO2_1T
    if (n < 32 && ix != NPIO2_HW[n - 1]) {
        y[0] = r - w
    } else {
        val j = ix shr 20
        y[0] = r - w
        var i = j - ((high(y[0]) shr 20) and 0x7ff)
        if (i > 16) {
            t = r
            w = fn * PIO2_2
            r = t - w
            w = fma(fn, PIO2_2T, -((t - r) - w))
            y[0] = r - w
            i = j - ((high(y[0]) shr 20) and 0x7ff)
            if (i > 49) {
                t = r
                w = fn * PIO2_3
                r = t - w
                w = fma(fn, PIO2_3T, -((t - r) - w))
                y[0] = r - w
            }
        }
    }
    y[1] = (r - y[0]) - w
    if (hx < 0) {
        y[0] = -y[0]
        y[1] = -y[1]
        return -n
    }
    return n
}

/** V8's `Math.sin` (StrictMath beyond `2^19·π/2`, where nothing here goes). */
internal fun jsSin(x: Double): Double {
    val ix = high(x) and 0x7fffffff
    if (ix <= 0x3FE921FB) return kernelSin(x, 0.0, 0)
    if (ix > 0x413921FB) return StrictMath.sin(x)
    val y = DoubleArray(2)
    return when (remPio2(x, y) and 3) {
        0 -> kernelSin(y[0], y[1], 1)
        1 -> kernelCos(y[0], y[1])
        2 -> -kernelSin(y[0], y[1], 1)
        else -> -kernelCos(y[0], y[1])
    }
}

/** V8's `Math.cos`. */
internal fun jsCos(x: Double): Double {
    val ix = high(x) and 0x7fffffff
    if (ix <= 0x3FE921FB) return kernelCos(x, 0.0)
    if (ix > 0x413921FB) return StrictMath.cos(x)
    val y = DoubleArray(2)
    return when (remPio2(x, y) and 3) {
        0 -> kernelCos(y[0], y[1])
        1 -> -kernelSin(y[0], y[1], 1)
        2 -> -kernelCos(y[0], y[1])
        else -> kernelSin(y[0], y[1], 1)
    }
}

/** V8's four-argument `Math.hypot`: normalised by the largest and summed with compensation. */
internal fun jsHypot4(a: Double, b: Double, c: Double, d: Double): Double {
    val values =
        doubleArrayOf(
            kotlin.math.abs(a),
            kotlin.math.abs(b),
            kotlin.math.abs(c),
            kotlin.math.abs(d),
        )
    var most = 0.0
    for (v in values) {
        if (v == Double.POSITIVE_INFINITY) return v
        if (v > most) most = v
    }
    if (values.any { it.isNaN() }) return Double.NaN
    if (most == 0.0) return 0.0
    var sum = 0.0
    var compensation = 0.0
    for (v in values) {
        val n = v / most
        val summand = n * n - compensation
        val preliminary = sum + summand
        compensation = (preliminary - sum) - summand
        sum = preliminary
    }
    return sqrt(sum) * most
}
