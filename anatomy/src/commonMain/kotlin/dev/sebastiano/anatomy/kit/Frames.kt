// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Frames, the camera's view and light, tones, and the hull and run helpers of `kit/lathe.mjs`. A
 * frame turns any axis into a place to build round parts: a point on the part is `(s, r, angle)`,
 * `s` along the axis from the origin, `r` from the axis, and `angle` from `u` toward `v`.
 */

private const val LIGHT_UP = 1.2

/** The tone thresholds: a light score below the first is tone 0, above the last tone 4 (top). */
internal val BANDS = doubleArrayOf(0.0, 0.2, 0.42, 0.62)

/** An origin [o], the unit axis [a], and two unit radial directions [u] and [v]. */
data class Frame(val o: Point3, val a: Point3, val u: Point3, val v: Point3)

/** The unit direction to the viewer: `dot3(p, viewOf(P))` equals `depthOf(p, P)`. */
fun viewOf(projection: Projection): Point3 {
    val c = projection.camera
    return Point3(c.cosA * c.cosE, c.sinA * c.cosE, c.sinE)
}

/** The kit's light, from the back left and above. */
fun lightOf(projection: Projection): Point3 {
    val c = projection.camera
    return unit3(Point3(-c.sinA, c.cosA, LIGHT_UP))
}

/** Screen units per world unit. */
fun scaleOf(projection: Projection): Double = projection.camera.k

/** The tone bucket of a light score: 0 (darkest) to 4 (the top tone). */
fun bandOf(score: Double): Int {
    var tone = 0
    while (tone < BANDS.size && score >= BANDS[tone]) tone++
    return tone
}

/** The tone bucket of a surface facing [normal]. */
fun toneOf(normal: Point3, projection: Projection): Int =
    bandOf(dot3(unit3(normal), lightOf(projection)))

/** A frame on axis [a] through [o], with `u` and `v` chosen for you. */
fun frameAlong(o: Point3, a: Point3): Frame {
    val axis = unit3(a)
    val helper = if (abs(axis.z) > 0.9) Point3(1.0, 0.0, 0.0) else Point3(0.0, 0.0, 1.0)
    val u = unit3(cross3(helper, axis))
    val v = cross3(axis, u)
    return Frame(o, axis, u, v)
}

/** A frame with explicit radial directions, so angles mean something. */
fun frameOf(o: Point3, a: Point3, u: Point3, v: Point3): Frame = Frame(o, a, u, v)

/** The world point at [s] along the axis, [r] from it, at [angle] from `u` toward `v`. */
fun pointOf(frame: Frame, s: Double, r: Double, angle: Double): Point3 {
    val c = cos(angle)
    val n = sin(angle)
    val f = frame
    return Point3(
        f.o.x + f.a.x * s + r * (c * f.u.x + n * f.v.x),
        f.o.y + f.a.y * s + r * (c * f.u.y + n * f.v.y),
        f.o.z + f.a.z * s + r * (c * f.u.z + n * f.v.z),
    )
}

/** The outward radial direction at [angle]. */
fun radialOf(frame: Frame, angle: Double): Point3 {
    val c = cos(angle)
    val n = sin(angle)
    val f = frame
    return Point3(c * f.u.x + n * f.v.x, c * f.u.y + n * f.v.y, c * f.u.z + n * f.v.z)
}

/** The direction of increasing angle at [angle]. */
fun tangentOf(frame: Frame, angle: Double): Point3 {
    val c = cos(angle)
    val n = sin(angle)
    val f = frame
    return Point3(-n * f.u.x + c * f.v.x, -n * f.u.y + c * f.v.y, -n * f.u.z + c * f.v.z)
}

/** The angle whose radial direction faces [direction]. */
fun angleToward(frame: Frame, direction: Point3): Double =
    atan2(dot3(direction, frame.v), dot3(direction, frame.u))

/**
 * A facet count. With [scale] (screen units per world unit, [scaleOf]) it keeps every facet about 3
 * px long on screen; without it it grows with the radius.
 */
fun stepsFor(radius: Double, least: Int = 16, most: Int = 112, scale: Double = 0.0): Int {
    val wanted =
        if (scale != 0.0 && !scale.isNaN()) ceil(TAU * radius * scale / 3)
        else jsRound(radius * 1.3 / 4) * 4
    return max(least.toDouble(), min(most.toDouble(), wanted)).toInt()
}

/** How much a surface at [angle], with profile slope [slope], faces the viewer. */
fun facingOf(frame: Frame, angle: Double, projection: Projection, slope: Double = 0.0): Double {
    val radial = radialOf(frame, angle)
    val a = frame.a
    val normal =
        unit3(Point3(radial.x - slope * a.x, radial.y - slope * a.y, radial.z - slope * a.z))
    return dot3(normal, viewOf(projection))
}

/** The convex hull of screen points, as the original computes it. */
fun hullOf(points: List<Flat>): List<Flat> = hullTagged(points) { it }

/** The runs of consecutive true flags, as vertex indices, wrapping around. */
fun runsOf(flags: List<Boolean>): List<List<Int>> = runsOf(flags, true)

/** The convex hull of tagged points, keeping each point's tag. */
internal fun <T> hullTagged(points: List<T>, at: (T) -> Flat): List<T> {
    val sorted = points.sortedWith { p, q ->
        val a = at(p)
        val b = at(q)
        val dx = a.x - b.x
        val order = if (dx != 0.0 && !dx.isNaN()) dx else a.y - b.y
        when {
            order > 0 -> 1
            order < 0 -> -1
            else -> 0
        }
    }
    if (sorted.size < 3) return sorted
    fun cross(o: T, a: T, b: T): Double {
        val po = at(o)
        val pa = at(a)
        val pb = at(b)
        return (pa.x - po.x) * (pb.y - po.y) - (pa.y - po.y) * (pb.x - po.x)
    }
    val lower = ArrayList<T>()
    for (point in sorted) {
        while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), point) <= 1e-9) {
            lower.removeAt(lower.size - 1)
        }
        lower.add(point)
    }
    val upper = ArrayList<T>()
    for (index in sorted.indices.reversed()) {
        val point = sorted[index]
        while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), point) <= 1e-9) {
            upper.removeAt(upper.size - 1)
        }
        upper.add(point)
    }
    return lower.dropLast(1) + upper.dropLast(1)
}

/** A run of facets that share a [key], as vertex indices. */
internal class KeyedRun(val key: Int, val run: List<Int>)

/** The runs of consecutive equal keys around a ring, skipping null (hidden) ones. */
internal fun groupsOf(keys: List<Int?>): List<KeyedRun> {
    val count = keys.size
    val out = ArrayList<KeyedRun>()
    val start = keys.indices.indexOfFirst { keys[it] != keys[(it + count - 1) % count] }
    if (start < 0) {
        val key = keys[0]
        if (key != null) out.add(KeyedRun(key, List(count + 1) { it % count }))
        return out
    }
    var key: Int? = null
    var run: MutableList<Int>? = null
    for (step in 0 until count) {
        val index = (start + step) % count
        if (run != null && key == keys[index]) {
            run.add((index + 1) % count)
            continue
        }
        if (run != null && key != null) out.add(KeyedRun(key, run))
        key = keys[index]
        run = mutableListOf(index, (index + 1) % count)
    }
    if (run != null && key != null) out.add(KeyedRun(key, run))
    return out
}
