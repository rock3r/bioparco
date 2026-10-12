// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.jsHypot
import dev.sebastiano.anatomy.kit.jsRound
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * The small maths of `kit/turn.mjs`: the light bands, the smoothed staircase of flat faces, 3×3
 * rotations as row-major arrays, and the screen paths the runtime writes. Every expression keeps the
 * original's order of operations, so the frames match it to the digit.
 */

internal const val T3_TAU: Double = PI * 2
internal const val T3_RAD: Double = PI / 180
internal val T3_ROOT: Double = sqrt(1.6)

/** The light thresholds of curved surfaces and of faces on tilted axes (`LB`). */
internal val T3_LB = doubleArrayOf(0.0, 0.2, 0.42, 0.62)

/** The thresholds of upright prism sides (`EB`), the kit's `extrude` rule. */
internal val T3_EB = doubleArrayOf(0.25, 0.5, 0.75)

/** Half the width of a face's crossfade window, in light score. */
internal const val T3_SOFT = 0.035

/** Faded-line opacity steps. */
const val TURN_LEVELS: Int = 32

/** Crossfade steps per tone band of a flat face: `q / TURN_Q` is the tone. */
const val TURN_Q: Int = 64

/** The identity rotation. */
internal val EYE = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)

/** What every emit reads: the detail scale and whether coordinates are written exactly. */
internal object TurnState {
    var detail: Double = 1.0
    var exact: Boolean = false
}

/** `smoothstep(a, b, x)`. */
internal fun t3Step(a: Double, b: Double, x: Double): Double {
    val t = min(1.0, max(0.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)
}

/** A face's tone as a smoothed staircase: `TURN_Q` steps per band, crossfaded near each band. */
internal fun t3Level(score: Double, bands: DoubleArray): Int {
    var u = 0.0
    for (band in bands) u += t3Step(band - T3_SOFT, band + T3_SOFT, score)
    return jsRound(u * TURN_Q).toInt()
}

internal fun t3Unit(v: Point3): Point3 {
    val l = jsHypot(v.x, v.y, v.z).let { if (it == 0.0 || it.isNaN()) 1.0 else it }
    return Point3(v.x / l, v.y / l, v.z / l)
}

/** The rotation by [degrees] about [axis], row-major. */
internal fun t3Rot(axis: Point3, degrees: Double): DoubleArray {
    val (x, y, z) = t3Unit(axis)
    val c = jsCos(degrees * T3_RAD)
    val s = jsSin(degrees * T3_RAD)
    val k = 1 - c
    return doubleArrayOf(
        c + x * x * k,
        x * y * k - z * s,
        x * z * k + y * s,
        y * x * k + z * s,
        c + y * y * k,
        y * z * k - x * s,
        z * x * k - y * s,
        z * y * k + x * s,
        c + z * z * k,
    )
}

/** The rotation by [degrees] about [axis] as a row-major matrix: `TURN.rotation`. */
fun turnRotation(axis: Point3, degrees: Double): DoubleArray = t3Rot(axis, degrees)

/** `A·B` for row-major 3×3 matrices: `TURN.compose`. */
fun turnCompose(a: DoubleArray, b: DoubleArray): DoubleArray = t3Mul(a, b)

/** `A·B` for row-major 3×3 matrices. */
internal fun t3Mul(a: DoubleArray, b: DoubleArray): DoubleArray {
    val out = DoubleArray(9)
    for (r in 0 until 3) {
        for (c in 0 until 3) {
            out[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c]
        }
    }
    return out
}

internal fun t3Apply(r: DoubleArray, v: Point3): Point3 =
    Point3(
        r[0] * v.x + r[1] * v.y + r[2] * v.z,
        r[3] * v.x + r[4] * v.y + r[5] * v.z,
        r[6] * v.x + r[7] * v.y + r[8] * v.z,
    )

/** The rotation of a quaternion `(x, y, z, w)`, normalised first. */
internal fun t3Quat(q: DoubleArray): DoubleArray {
    val l = jsHypot4(q[0], q[1], q[2], q[3]).let { if (it == 0.0 || it.isNaN()) 1.0 else it }
    val x = q[0] / l
    val y = q[1] / l
    val z = q[2] / l
    val w = q[3] / l
    return doubleArrayOf(
        1 - 2 * (y * y + z * z),
        2 * (x * y - z * w),
        2 * (x * z + y * w),
        2 * (x * y + z * w),
        1 - 2 * (x * x + z * z),
        2 * (y * z - x * w),
        2 * (x * z - y * w),
        2 * (y * z + x * w),
        1 - 2 * (x * x + y * y),
    )
}

/** A unit vector's two perpendicular directions: the original's `t3Basis`. */
internal fun t3Basis(w: Point3): Pair<Point3, Point3> {
    val helper = if (kotlin.math.abs(w.z) > 0.9) Point3(1.0, 0.0, 0.0) else Point3(0.0, 0.0, 1.0)
    val u =
        t3Unit(
            Point3(
                helper.y * w.z - helper.z * w.y,
                helper.z * w.x - helper.x * w.z,
                helper.x * w.y - helper.y * w.x,
            )
        )
    return u to Point3(w.y * u.z - w.z * u.y, w.z * u.x - w.x * u.z, w.x * u.y - w.y * u.x)
}

/** The unit vectors `n` with `n·A = a` and `n·B = b`: none, or two. */
internal fun t3Meet(a3: Point3, a: Double, b3: Point3, b: Double): List<Point3> {
    val ab = a3.x * b3.x + a3.y * b3.y + a3.z * b3.z
    val det = 1 - ab * ab
    if (det < 1e-12) return emptyList()
    val alpha = (a - b * ab) / det
    val beta = (b - a * ab) / det
    val base =
        Point3(
            a3.x * alpha + b3.x * beta,
            a3.y * alpha + b3.y * beta,
            a3.z * alpha + b3.z * beta,
        )
    val rest = 1 - (base.x * base.x + base.y * base.y + base.z * base.z)
    if (rest < 0) return emptyList()
    val cr =
        Point3(
            a3.y * b3.z - a3.z * b3.y,
            a3.z * b3.x - a3.x * b3.z,
            a3.x * b3.y - a3.y * b3.x,
        )
    val k = sqrt(rest / det)
    return listOf(
        Point3(base.x + cr.x * k, base.y + cr.y * k, base.z + cr.z * k),
        Point3(base.x - cr.x * k, base.y - cr.y * k, base.z - cr.z * k),
    )
}

private val tables = HashMap<Int, DoubleArray>()

/** `cos` and `sin` of `count` steps round the circle, cached. */
internal fun t3Table(count: Int): DoubleArray =
    synchronized(tables) {
        tables.getOrPut(count) {
            DoubleArray(2 * count).also { table ->
                for (j in 0 until count) {
                    table[2 * j] = jsCos((j.toDouble() / count) * T3_TAU)
                    table[2 * j + 1] = jsSin((j.toDouble() / count) * T3_TAU)
                }
            }
        }
    }

/** The convex hull, counter-clockwise, sorted by x then y as the original sorts. */
internal fun t3Hull(points: List<Flat>): List<Flat> {
    val sorted = points.sortedWith { a, b ->
        val dx = a.x - b.x
        val d = if (dx != 0.0) dx else a.y - b.y
        if (d < 0) -1 else if (d > 0) 1 else 0
    }
    if (sorted.size < 3) return sorted
    fun cross(o: Flat, a: Flat, b: Flat) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    val lower = ArrayList<Flat>()
    for (p in sorted) {
        while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 1e-12) {
            lower.removeAt(lower.size - 1)
        }
        lower.add(p)
    }
    val upper = ArrayList<Flat>()
    for (i in sorted.indices.reversed()) {
        val p = sorted[i]
        while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 1e-12) {
            upper.removeAt(upper.size - 1)
        }
        upper.add(p)
    }
    return lower.subList(0, lower.size - 1) + upper.subList(0, upper.size - 1)
}

/** [subject] clipped to the convex polygon [clip]. */
internal fun t3ClipConvex(subject: List<Flat>, clip: List<Flat>): List<Flat> {
    if (subject.size < 3 || clip.size < 3) return emptyList()
    var area = 0.0
    for (i in clip.indices) {
        val a = clip[i]
        val b = clip[(i + 1) % clip.size]
        area += a.x * b.y - b.x * a.y
    }
    val sign = if (area >= 0) 1.0 else -1.0
    var out = subject
    var i = 0
    while (i < clip.size && out.isNotEmpty()) {
        val a = clip[i]
        val b = clip[(i + 1) % clip.size]
        val input = out
        val next = ArrayList<Flat>()
        for (j in input.indices) {
            val p = input[j]
            val q = input[(j + 1) % input.size]
            val sp = sign * ((b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x))
            val sq = sign * ((b.x - a.x) * (q.y - a.y) - (b.y - a.y) * (q.x - a.x))
            if (sp >= 0) next.add(p)
            if ((sp >= 0) != (sq >= 0)) {
                val t = sp / (sp - sq)
                next.add(Flat(p.x + (q.x - p.x) * t, p.y + (q.y - p.y) * t))
            }
        }
        out = next
        i++
    }
    return out
}

/** One polyline from a coordinate buffer; fewer than two points is no path. */
internal fun t3Path(buf: DoubleArray, count: Int, closed: Boolean): IsoPath {
    if (count < 2) return IsoPath.Empty
    val points = ArrayList<Flat>(count)
    for (i in 0 until count) points.add(Flat(buf[2 * i], buf[2 * i + 1]))
    return IsoPath(listOf(IsoPath.Run(points, closed)))
}

/** One polyline through [points]; fewer than two is no path. */
internal fun t3PathOf(points: List<Flat>, closed: Boolean): IsoPath =
    if (points.size < 2) IsoPath.Empty else IsoPath(listOf(IsoPath.Run(points.toList(), closed)))

/** The original's `t3Num`: to 0.01 (or 6 decimals with `TURN.exact`), as it writes paths. */
fun turnNumber(value: Double): String {
    if (TurnState.exact) return jsNumber(jsRound(value * 1e6) / 1e6)
    var t = jsRound(value * 100).toLong()
    if (t == 0L) return "0"
    var sign = ""
    if (t < 0) {
        sign = "-"
        t = -t
    }
    val whole = t / 100
    val part = t - whole * 100
    if (part == 0L) return "$sign$whole"
    if (part < 10) return "$sign$whole.0$part"
    return "$sign$whole." + if (part % 10 == 0L) "${part / 10}" else "$part"
}

/** The original's `t3Fix(v, scale)`: `String(Math.round(v * scale) / scale)`. */
fun turnFix(value: Double, scale: Double): String {
    val t = jsRound(value * scale) / scale
    return if (t == 0.0) "0" else jsNumber(t)
}

/** A path as the runtime writes its `d`. */
fun IsoPath.turnSvg(): String = buildString {
    for (run in runs) {
        run.points.forEachIndexed { at, point ->
            append(if (at == 0) 'M' else 'L')
            append(turnNumber(point.x))
            append(' ')
            append(turnNumber(point.y))
        }
        if (run.closed) append('Z')
    }
}

/** JavaScript's `String(value)` for a finite number. */
internal fun jsNumber(value: Double): String {
    if (value == 0.0) return "0"
    if (value == kotlin.math.floor(value) && kotlin.math.abs(value) < 1e21) {
        return java.math.BigDecimal(value).toPlainString()
    }
    val exact = java.math.BigDecimal(value.toString()).stripTrailingZeros()
    if (kotlin.math.abs(value) >= 1e-6) return exact.toPlainString()
    val digits = exact.unscaledValue().abs().toString()
    val exponent = digits.length - 1 - exact.scale()
    val mantissa = if (digits.length == 1) digits else "${digits[0]}.${digits.drop(1)}"
    return "${if (value < 0) "-" else ""}${mantissa}e$exponent"
}
