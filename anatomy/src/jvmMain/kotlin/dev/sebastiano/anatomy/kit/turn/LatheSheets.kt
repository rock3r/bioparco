// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.IsoPath
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * `t3LatheSheets`: a lathe's light bands joined into quads between neighbouring levels. Arcs of one
 * band on two levels are linked where they overlap; one arc that splits into several (or several
 * that merge) is shared out by cutting it between them, and an arc with no partner closes to a
 * point. Each quad is wound like every other fill.
 */

private class Span(val from: Double, val to: Double)

private fun Arc.span() = Span(from, to)

private fun alignSpan(reference: Span, arc: Span): Span {
    var from = arc.from
    var to = arc.to
    val centre = (reference.from + reference.to) / 2
    while ((from + to) / 2 - centre > PI) {
        from -= T3_TAU
        to -= T3_TAU
    }
    while (centre - (from + to) / 2 > PI) {
        from += T3_TAU
        to += T3_TAU
    }
    return Span(from, to)
}

private fun overlap(x: Span, y: Span) = min(x.to, y.to) - max(x.from, y.from)

private fun whole(arc: Span) = arc.to - arc.from >= T3_TAU - 1e-6

/** Builds the quads of one band between two levels. */
private class SheetStrip(
    val geo: Geo,
    val step: Double,
    val a: LatheLevel,
    val b: LatheLevel,
    val add: (IsoPath) -> Unit,
) {
    fun arcPoints(l: LatheLevel, from: Double, to: Double): List<Flat> {
        val count = max(1, ceil((to - from) / step).toInt())
        return (0..count).map { latheAt(geo, l, from + ((to - from) * it) / count) }
    }

    fun oriented(points: MutableList<Flat>): List<Flat> {
        var area = 0.0
        for (i in points.indices) {
            val p = points[i]
            val q = points[(i + 1) % points.size]
            area += p.x * q.y - q.x * p.y
        }
        if (area < 0) points.reverse()
        return points
    }

    fun seat(arc: Span): Span {
        val gap = T3_TAU - (arc.to - arc.from)
        return Span(arc.from - gap / 2, arc.to + gap / 2)
    }

    fun quad(lo0: Span, hi0: Span) {
        var lo = lo0
        var hi = hi0
        if (whole(hi) && !whole(lo)) hi = seat(lo) else if (whole(lo) && !whole(hi)) lo = seat(hi)
        val points = ArrayList(arcPoints(a, lo.from, lo.to))
        points.addAll(arcPoints(b, hi.from, hi.to).reversed())
        add(t3PathOf(oriented(points), true))
    }

    fun share(span: Span, parts: List<Span>, flip: Boolean) {
        if (whole(span)) {
            val base = parts[0].from
            val sorted =
                parts
                    .map {
                        val shift = T3_TAU * floor((it.from - base) / T3_TAU)
                        Span(it.from - shift, it.to - shift)
                    }
                    .sortedWith { x, y -> compareDiff(x.from - y.from) }
            val n = sorted.size
            val cuts = sorted.mapIndexed { q, arc ->
                val next = if (q + 1 < n) sorted[q + 1].from else sorted[0].from + T3_TAU
                (arc.to + next) / 2
            }
            sorted.forEachIndexed { q, arc ->
                val piece = Span(if (q > 0) cuts[q - 1] else cuts[n - 1] - T3_TAU, cuts[q])
                if (flip) quad(arc, piece) else quad(piece, arc)
            }
            return
        }
        val sorted =
            parts.map { alignSpan(span, it) }.sortedWith { x, y -> compareDiff(x.from - y.from) }
        val cuts = arrayListOf(span.from)
        for (q in 1 until sorted.size) {
            cuts.add(min(span.to, max(span.from, (sorted[q - 1].to + sorted[q].from) / 2)))
        }
        cuts.add(span.to)
        sorted.forEachIndexed { q, arc ->
            val piece = Span(cuts[q], cuts[q + 1])
            if (flip) quad(arc, piece) else quad(piece, arc)
        }
    }

    fun run(tone: Int) {
        val below = a.groups[tone]?.map { it.span() } ?: emptyList()
        val above = b.groups[tone]?.map { it.span() } ?: emptyList()
        val links = below.map { ArrayList<Int>() }
        val back = above.map { ArrayList<Int>() }
        below.forEachIndexed { i, lo ->
            above.forEachIndexed { j, hi ->
                if (overlap(lo, alignSpan(lo, hi)) > -0.05) {
                    links[i].add(j)
                    back[j].add(i)
                }
            }
        }
        val doneLo = BooleanArray(below.size)
        val doneHi = BooleanArray(above.size)
        for (j in above.indices) {
            if (back[j].size < 2 || back[j].any { links[it].size != 1 }) continue
            share(above[j], back[j].map { below[it] }, false)
            doneHi[j] = true
            for (i in back[j]) doneLo[i] = true
        }
        for (i in below.indices) {
            if (doneLo[i] || links[i].size < 2 || links[i].any { back[it].size != 1 }) continue
            share(below[i], links[i].map { above[it] }, true)
            doneLo[i] = true
            for (j in links[i]) doneHi[j] = true
        }
        unpaired(below, above, links, doneLo, doneHi)
    }

    fun unpaired(
        below: List<Span>,
        above: List<Span>,
        links: List<List<Int>>,
        doneLo: BooleanArray,
        doneHi: BooleanArray,
    ) {
        for (i in below.indices) {
            if (doneLo[i]) continue
            val lo = below[i]
            var best = -1
            var score = Double.NEGATIVE_INFINITY
            for (j in links[i]) {
                if (doneHi[j]) continue
                val value = overlap(lo, alignSpan(lo, above[j]))
                if (value > score) {
                    score = value
                    best = j
                }
            }
            if (best >= 0) {
                doneHi[best] = true
                quad(lo, alignSpan(lo, above[best]))
            } else {
                val centre = (lo.from + lo.to) / 2
                quad(lo, Span(centre, centre))
            }
        }
        for (j in above.indices) {
            if (doneHi[j]) continue
            val centre = (above[j].from + above[j].to) / 2
            quad(Span(centre, centre), above[j])
        }
    }
}

private fun compareDiff(d: Double): Int = if (d < 0) -1 else if (d > 0) 1 else 0

/** Fills [buckets] (one per band) with the quads joining the bands of neighbouring [levels]. */
internal fun t3LatheSheets(
    levels: List<LatheLevel>,
    geo: Geo,
    step: Double,
    buckets: Array<IsoPath>,
) {
    fun centreOf(arc: Arc, reference: Double): Double {
        val mid = (arc.from + arc.to) / 2 - reference
        return ((mid % T3_TAU) + T3_TAU) % T3_TAU
    }
    val present = BooleanArray(5)
    for (l in levels) {
        val groups = arrayOfNulls<MutableList<Arc>>(5)
        for (arc in l.arcs) {
            (groups[arc.tone] ?: ArrayList<Arc>().also { groups[arc.tone] = it }).add(arc)
            present[arc.tone] = true
        }
        for (t in groups.indices) {
            val list = groups[t]
            if (list != null && list.size > 1) {
                groups[t] =
                    list
                        .sortedWith { x, y ->
                            compareDiff(centreOf(x, geo.light.phi) - centreOf(y, geo.light.phi))
                        }
                        .toMutableList()
            }
        }
        l.groups = groups
    }
    for (tone in 0 until 5) {
        if (!present[tone]) continue
        for (q in 1 until levels.size) {
            SheetStrip(geo, step, levels[q - 1], levels[q]) { buckets[tone] = buckets[tone] + it }
                .run(tone)
        }
    }
}
