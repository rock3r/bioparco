// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/* Paths in 3D, for pipes and cables: fillets, even spacing, lengths, cuts, spirals and rings. */

/**
 * Rounds every corner of a polyline with a bend of [radius], clamped to 0.48 of the shorter leg.
 * With [projection] and [tube] (the pipe radius) each bend grows to `margin · tube / (0.7 · f²)`,
 * `f` being how squarely the bend's plane faces the viewer, so its outline never folds back.
 */
fun fillet(
    points: List<Point3>,
    radius: Double,
    perArc: Int = 7,
    projection: Projection? = null,
    tube: Double = 0.0,
    margin: Double = 1.3,
): List<Point3> {
    if (points.size < 3) return points.toList()
    val view = if (projection != null && tube != 0.0 && !tube.isNaN()) viewOf(projection) else null
    val out = arrayListOf(points[0])
    for (index in 1 until points.size - 1) {
        val prev = points[index - 1]
        val here = points[index]
        val next = points[index + 1]
        val into = unit3(sub3(here, prev))
        val outOf = unit3(sub3(next, here))
        val turn = acos(max(-1.0, min(1.0, dot3(into, outOf))))
        if (turn < 1e-3) {
            out.add(here)
            continue
        }
        val room = min(len3(sub3(here, prev)), len3(sub3(next, here))) * 0.48
        val bend =
            if (view == null) radius
            else {
                val facing = max(0.05, abs(dot3(unit3(cross3(into, outOf)), view)))
                max(radius, margin * tube / (0.7 * (facing * facing)))
            }
        val cut = min(bend * tan(turn / 2), room)
        val a = sub3(here, mul3(into, cut))
        val b = add3(here, mul3(outOf, cut))
        val count = max(2, ceil(perArc * turn / (PI / 2)).toInt())
        for (step in 0..count) {
            val t = step.toDouble() / count
            out.add(lerp3(lerp3(a, here, t), lerp3(here, b, t), t))
        }
    }
    out.add(points[points.size - 1])
    return out
}

/** [points] resampled about [spacing] apart along their length, keeping both ends. */
fun resample(points: List<Point3>, spacing: Double): List<Point3> {
    val lengths = DoubleArray(points.size)
    for (index in 1 until points.size) {
        lengths[index] = lengths[index - 1] + len3(sub3(points[index], points[index - 1]))
    }
    val total = lengths[lengths.size - 1]
    val count = max(1, jsRound(total / spacing).toInt())
    val out = ArrayList<Point3>()
    var segment = 1
    for (step in 0..count) {
        val at = total * step / count
        while (segment < points.size - 1 && lengths[segment] < at) segment++
        val span = (lengths[segment] - lengths[segment - 1]).orOne()
        val t = min(1.0, max(0.0, (at - lengths[segment - 1]) / span))
        out.add(lerp3(points[segment - 1], points[segment], t))
    }
    return out
}

/** The total length of a polyline. */
fun pathLength(points: List<Point3>): Double {
    var total = 0.0
    for (index in 1 until points.size) total += len3(sub3(points[index], points[index - 1]))
    return total
}

/** The part of a polyline between arc lengths [from] and [to]. */
fun cut(points: List<Point3>, from: Double, to: Double): List<Point3> {
    val out = ArrayList<Point3>()
    var walked = 0.0
    for (index in 1 until points.size) {
        val a = points[index - 1]
        val b = points[index]
        val length = len3(sub3(b, a))
        val start = walked
        val end = walked + length
        if (end >= from && start <= to) {
            val t0 = max(0.0, (from - start) / length.orOne())
            val t1 = min(1.0, (to - start) / length.orOne())
            if (out.isEmpty()) out.add(lerp3(a, b, t0))
            out.add(lerp3(a, b, t1))
        }
        walked = end
    }
    return out
}

/** The point at arc length [at]. */
fun pointAlong(points: List<Point3>, at: Double): Point3 =
    cut(points, at, at + 0.01).firstOrNull() ?: points[points.size - 1]

/** The unit direction at arc length [at], over about 1.2 units of the path. */
fun tangentAlong(points: List<Point3>, at: Double): Point3 {
    val total = pathLength(points)
    val a = pointAlong(points, max(0.0, at - 0.6))
    val b = pointAlong(points, min(total, at + 0.6))
    return unit3(sub3(b, a))
}

/** 3D points of a spiral from radius [r0] at [a0] to [r1] at [a1]: a pump volute. */
fun spiral(
    frame: Frame,
    s: Double,
    r0: Double,
    r1: Double,
    a0: Double,
    a1: Double,
    steps: Int = 28,
): List<Point3> =
    List(steps + 1) { index ->
        val t = index.toDouble() / steps
        pointOf(frame, s, r0 + (r1 - r0) * t, a0 + (a1 - a0) * t)
    }

/** 3D points around a ring from [a0] to [a1]: a manifold, a deluge ring, a harness lane. */
fun arcPoints(
    frame: Frame,
    s: Double,
    r: Double,
    a0: Double,
    a1: Double,
    steps: Int = 24,
): List<Point3> = List(steps + 1) { index -> pointOf(frame, s, r, a0 + (a1 - a0) * index / steps) }
