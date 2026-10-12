// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/* The spans of `kit/tube.mjs`: where a route is drawn, where its chunks end, where clamps cross it. */

/**
 * The drawn `from to to` spans of a route of length [total] once [gaps] (`from to to` arc-length
 * spans covered by a collar, clamp or fitting) are taken out. Record each run as its own tube.
 */
fun runsOf(
    total: Double,
    gaps: List<Pair<Double, Double>> = emptyList(),
): List<Pair<Double, Double>> {
    val runs = ArrayList<Pair<Double, Double>>()
    var at = 0.0
    for ((a, b) in cutsOf(total, gaps)) {
        if (a > at + 1e-6) runs.add(at to a)
        at = max(at, b)
    }
    if (total > at + 1e-6) runs.add(at to total)
    return runs
}

/**
 * Arc lengths where a route crosses the disc of a clamp or collar: the plane through [centre]
 * normal to [axis], within [radius] of the centre. Turn each into a gap of the clamp's thickness.
 */
fun crossingsOf(points: List<Point3>, centre: Point3, axis: Point3, radius: Double): List<Double> {
    val n = unit3(axis)
    val out = ArrayList<Double>()
    var walked = 0.0
    for (index in 1 until points.size) {
        val a = points[index - 1]
        val b = points[index]
        val length = len3(sub3(b, a))
        val da = dot3(sub3(a, centre), n)
        val db = dot3(sub3(b, centre), n)
        if ((da <= 0 && db > 0) || (da >= 0 && db < 0)) {
            val t = da / (da - db)
            val p = lerp3(a, b, t)
            if (len3(sub3(p, centre)) <= radius) out.add(walked + t * length)
        }
        walked += length
    }
    return out
}

/** One chunk of a route, by arc length, and whether each end is a joint or the edge of a gap. */
internal class TubeSpan(
    val from: Double,
    val to: Double,
    var open0: Boolean,
    var open1: Boolean,
    val gap0: Boolean,
    val gap1: Boolean,
)

/** The gaps clamped to the route and sorted, empty ones dropped. */
private fun cutsOf(total: Double, gaps: List<Pair<Double, Double>>): List<Pair<Double, Double>> =
    gaps
        .map { (a, b) -> max(0.0, min(a, b)) to min(total, max(a, b)) }
        .filter { (a, b) -> b - a > 1e-6 }
        .sortedWith { x, y -> jsOrder(x.first - y.first) }

/** A JavaScript sort comparator's number as a Kotlin comparison: zero (and NaN) keep the order. */
internal fun jsOrder(difference: Double): Int =
    when {
        difference > 0 -> 1
        difference < 0 -> -1
        else -> 0
    }

/** A drawn run, and whether a gap sits before and after it. */
private class TubeRun(val from: Double, val to: Double, val gapStart: Boolean, val gapEnd: Boolean)

/** The original's `spansOf`: the chunks of at most [maxLength] that a route is drawn in. */
internal fun spansOf(
    total: Double,
    gaps: List<Pair<Double, Double>>,
    breaks: List<Double>,
    maxLength: Double,
    closed: Boolean,
): List<TubeSpan> {
    val cuts = cutsOf(total, gaps)
    val runs = ArrayList<TubeRun>()
    var at = 0.0
    for ((a, b) in cuts) {
        if (a > at + 1e-6) runs.add(TubeRun(at, a, at > 0, true))
        at = max(at, b)
    }
    if (total > at + 1e-6) {
        runs.add(
            TubeRun(at, total, at > 0, cuts.isNotEmpty() && cuts.last().second >= total - 1e-6)
        )
    }
    val pieces = ArrayList<TubeSpan>()
    for (run in runs) {
        val inside = breaks.filter { it > run.from + 1e-6 && it < run.to - 1e-6 }
        val stops = listOf(run.from) + inside.sortedWith { x, y -> jsOrder(x - y) } + run.to
        for (index in 0 until stops.size - 1) {
            val a = stops[index]
            val b = stops[index + 1]
            val count = max(1, ceil((b - a) / maxLength - 1e-9).toInt())
            for (c in 0 until count) {
                val first = index == 0 && c == 0
                val last = index == stops.size - 2 && c == count - 1
                pieces.add(
                    TubeSpan(
                        from = a + ((b - a) * c) / count,
                        to = a + ((b - a) * (c + 1)) / count,
                        open0 = !first,
                        open1 = !last,
                        gap0 = first && run.gapStart,
                        gap1 = last && run.gapEnd,
                    )
                )
            }
        }
    }
    if (closed && cuts.isEmpty() && pieces.isNotEmpty()) {
        pieces.first().open0 = true
        pieces.last().open1 = true
    }
    return pieces
}
