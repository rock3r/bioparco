// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/*
 * Profiles: the half-section a round part is turned from, and the one curve the kit draws through
 * its smooth knots (a monotone cubic Hermite, Fritsch–Carlson slopes), so that normals, tone sheets,
 * silhouette and hull all describe the same shape.
 */

/**
 * One point of a meridian or profile: [s] along the axis, [r] from it. A [smooth] knot is a ring
 * with no crease, inside a curve: the original's `[s, r, 1]`.
 */
data class Knot(val s: Double, val r: Double, val smooth: Boolean = false)

/**
 * Smooth shading, the original's `smooth` option. `Smooth()` is `smooth: true`; [slope] is an
 * analytic `dr/ds` for the normals and [radius] an analytic `r(s)` for the surface between the
 * knots. [rows] is the starting sampling along each segment, refined until every tone boundary is
 * within [error] px of a chord, down to [depth] levels or [fineness] px on screen. [steps]
 * overrides the facet count the tone arcs are sampled from.
 */
class Smooth(
    val rows: Int = 6,
    val slope: ((Double) -> Double)? = null,
    val radius: ((Double) -> Double)? = null,
    val steps: Int? = null,
    val error: Double = 0.35,
    val depth: Int = 8,
    val fineness: Double = 0.1,
) {
    companion object {
        /** The original's `smooth: "auto"`: on when any knot of [meridian] is smooth. */
        fun auto(meridian: List<Knot>): Smooth? = if (softOf(meridian)) Smooth() else null
    }
}

/** True when any knot is marked smooth. */
fun softOf(meridian: List<Knot>): Boolean = meridian.any { it.smooth }

/** One span of a profile's curve: its radius and slope at any `s` inside it. */
interface Curve {
    fun radius(s: Double): Double

    fun slope(s: Double): Double
}

/** A cubic Hermite span from `(s0, r0)` to `(s0 + h, r1)` with end slopes [d0] and [d1]. */
private class Hermite(
    val s0: Double,
    val r0: Double,
    val r1: Double,
    val h: Double,
    val d0: Double,
    val d1: Double,
) : Curve {
    override fun radius(s: Double): Double {
        val t = (s - s0) / h
        val t2 = t * t
        val t3 = cube(t)
        return (2 * t3 - 3 * t2 + 1) * r0 +
            (t3 - 2 * t2 + t) * h * d0 +
            (-2 * t3 + 3 * t2) * r1 +
            (t3 - t2) * h * d1
    }

    override fun slope(s: Double): Double {
        val t = (s - s0) / h
        val t2 = t * t
        return ((6 * t2 - 6 * t) * r0 + (6 * t - 6 * t2) * r1) / h +
            (3 * t2 - 4 * t + 1) * d0 +
            (3 * t2 - 2 * t) * d1
    }
}

/** An analytic curve, the same on every span. */
private class Analytic(val r: (Double) -> Double, val dr: ((Double) -> Double)?) : Curve {
    override fun radius(s: Double): Double = r(s)

    override fun slope(s: Double): Double = dr?.invoke(s) ?: Double.NaN
}

/** Fritsch–Carlson slopes for a run whose `s` strictly changes: zero where the profile turns. */
private fun hermiteSlopes(run: List<Knot>): DoubleArray {
    val n = run.size - 1
    val h = DoubleArray(n) { run[it + 1].s - run[it].s }
    val delta = DoubleArray(n) { (run[it + 1].r - run[it].r) / h[it] }
    val d = DoubleArray(n + 1)
    fun end(h0: Double, h1: Double, d0: Double, d1: Double): Double {
        var value = ((2 * h0 + h1) * d0 - h0 * d1) / (h0 + h1)
        if (sign(value) != sign(d0)) value = 0.0
        else if (sign(d0) != sign(d1) && abs(value) > abs(3 * d0)) value = 3 * d0
        return value
    }
    if (n == 1) {
        d[0] = delta[0]
        d[1] = delta[0]
    } else {
        d[0] = end(h[0], h[1], delta[0], delta[1])
        d[n] = end(h[n - 1], h[n - 2], delta[n - 1], delta[n - 2])
    }
    for (k in 1 until n) {
        if (delta[k - 1] * delta[k] <= 0) continue
        val w1 = 2 * h[k] + h[k - 1]
        val w2 = h[k] + 2 * h[k - 1]
        d[k] = (w1 + w2) / (w1 / delta[k - 1] + w2 / delta[k])
    }
    return d
}

/**
 * The curve through each run of smooth knots of [meridian], one entry per segment: null where the
 * segment is straight (outside a smooth run, or where `s` doesn't change).
 */
fun curveRuns(meridian: List<Knot>): List<Curve?> {
    val m = meridian.size
    val segments = arrayOfNulls<Curve>(max(0, m - 1))
    var start = 0
    while (start < m - 1) {
        var end = start + 1
        while (
            end < m - 1 &&
                meridian[end].smooth &&
                abs(meridian[end + 1].s - meridian[end].s) > 1e-9 &&
                abs(meridian[end].s - meridian[end - 1].s) > 1e-9
        ) end++
        val run = meridian.subList(start, end + 1)
        val n = run.size - 1
        if (n >= 2 && (1..n).all { abs(run[it].s - run[it - 1].s) > 1e-9 }) {
            val d = hermiteSlopes(run)
            for (j in 0 until n) {
                val (s0, r0) = run[j]
                val (s1, r1) = run[j + 1]
                segments[start + j] = Hermite(s0, r0, r1, s1 - s0, d[j], d[j + 1])
            }
        }
        start = end
    }
    return segments.toList()
}

/**
 * [meridian] with knots added on each curved segment until it is within [error] px of its chords.
 */
internal fun curved(
    meridian: List<Knot>,
    projection: Projection,
    segments: List<Curve?>,
    error: Double = 0.2,
    depth: Int = 6,
): List<Knot> {
    val k = scaleOf(projection)
    val out = arrayListOf(meridian[0])
    fun split(curve: Curve, a: Double, b: Double, level: Int) {
        val mid = (a + b) / 2
        val chord = (curve.radius(a) + curve.radius(b)) / 2
        if (level < depth && abs(curve.radius(mid) - chord) * k > error) {
            split(curve, a, mid, level + 1)
            out.add(Knot(mid, curve.radius(mid), true))
            split(curve, mid, b, level + 1)
        }
    }
    for (i in 0 until meridian.size - 1) {
        val curve = segments[i]
        if (curve != null) split(curve, meridian[i].s, meridian[i + 1].s, 0)
        out.add(meridian[i + 1])
    }
    return out
}

/** The shape the outline and hull follow: the meridian, refined along its curve if smooth. */
internal fun shapeOf(meridian: List<Knot>, projection: Projection, smooth: Smooth?): List<Knot> {
    val radius = smooth?.radius
    if (radius != null) {
        val analytic = Analytic(radius, smooth.slope)
        val segments =
            (0 until meridian.size - 1).map { index ->
                if (abs(meridian[index + 1].s - meridian[index].s) > 1e-9) analytic else null
            }
        return curved(meridian, projection, segments)
    }
    val curves = if (smooth != null && smooth.slope == null) curveRuns(meridian) else null
    return if (curves != null && curves.any { it != null }) curved(meridian, projection, curves)
    else meridian
}

/** The side of a meridian without its caps, if `s` rises along all of it. */
internal fun sideOf(meridian: List<Knot>): List<Knot>? {
    val m = meridian.size
    val first = if (meridian[0].r <= 1e-9) 1 else 0
    val last = if (meridian[m - 1].r <= 1e-9) m - 2 else m - 1
    if (last + 1 <= first) return null
    val side = meridian.subList(first, last + 1)
    for (index in 1 until side.size) if (side[index].s - side[index - 1].s <= 1e-9) return null
    return if (side.size >= 3) side else null
}

/** The curve `smooth` draws through a profile whose `s` rises: [radius] and [slope] at any `s`. */
class ProfileCurve(
    val s0: Double,
    val s1: Double,
    val radius: (Double) -> Double,
    val slope: (Double) -> Double,
    val analytic: Boolean,
)

/**
 * The curve through [profile]: the monotone Hermite through each run of smooth knots, straight
 * elsewhere, clamped to the profile's ends. An analytic [slope] or [radius] replaces it. Throws if
 * `s` doesn't rise: a step in a profile is a crease and belongs to a separate solid.
 */
fun profileCurve(
    profile: List<Knot>,
    slope: ((Double) -> Double)? = null,
    radius: ((Double) -> Double)? = null,
): ProfileCurve {
    for (index in 1 until profile.size) {
        require(profile[index].s - profile[index - 1].s > 1e-9) {
            "profileCurve: s must rise along the profile " +
                "(point $index at s = ${jsNumber(profile[index].s)})"
        }
    }
    val segments = curveRuns(profile)
    val s0 = profile[0].s
    val s1 = profile[profile.size - 1].s
    fun find(s: Double): Int {
        var lo = 0
        var hi = profile.size - 2
        while (lo < hi) {
            val mid = (lo + hi + 1) shr 1
            if (profile[mid].s <= s) lo = mid else hi = mid - 1
        }
        return lo
    }
    fun at(s: Double, curve: (Curve, Double) -> Double, line: (Knot, Knot, Double) -> Double) =
        max(s0, min(s1, s)).let { clamped ->
            val index = find(clamped)
            val segment = segments[index]
            if (segment != null) curve(segment, clamped)
            else line(profile[index], profile[index + 1], clamped)
        }
    val radiusAt =
        radius
            ?: { s: Double ->
                at(s, { c, x -> c.radius(x) }) { a, b, x ->
                    a.r + (b.r - a.r) * (x - a.s) / (b.s - a.s)
                }
            }
    val slopeAt =
        slope
            ?: { s: Double ->
                at(s, { c, x -> c.slope(x) }) { a, b, _ -> (b.r - a.r) / (b.s - a.s) }
            }
    return ProfileCurve(s0, s1, radiusAt, slopeAt, slope != null && radius != null)
}

/** A number the way JavaScript writes it in a template: `5`, not `5.0`. */
private fun jsNumber(value: Double): String =
    if (value % 1.0 == 0.0 && abs(value) < 1e15) value.toLong().toString() else value.toString()
