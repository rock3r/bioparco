// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/*
 * Smooth shading, the original's `smoothRows`. Each meridian segment is sampled in rows, refined
 * until every tone boundary is within `error` px of a chord and every change of band count happens
 * in a sliver; then each tone is emitted as one polygon (a sheet) for as long as its number of
 * regions holds, and bridged by quads (a strip) only where its own count changes.
 */

internal class SmoothRows(
    private val meridian: List<Knot>,
    private val frame: Frame,
    private val projection: Projection,
    private val smooth: Smooth,
    steps: Int,
) {
    private val view = Seen.of(frame, viewOf(projection))
    private val light = Seen.of(frame, lightOf(projection))
    private val m = meridian.size
    private val faces: List<Flat> =
        (0 until m - 1).map { i ->
            val ds = meridian[i + 1].s - meridian[i].s
            val dr = meridian[i + 1].r - meridian[i].r
            val length = jsHypot(ds, dr).orOne()
            Flat(ds / length, -dr / length)
        }
    private val splines = if (smooth.slope != null) null else curveRuns(meridian)
    private val buckets = List(SHADES + 1) { ArrayList<IsoPath>() }
    private val fine = TAU / max(24.0, min(192.0, (smooth.steps ?: steps) * 1.5))
    private val scale = scaleOf(projection)
    private var run = ArrayList<Level>()

    /** The tone sheets, by tone: 0–3 are the shades, 4 the top tone. */
    fun build(): List<List<IsoPath>> {
        for (i in 0 until m - 1) {
            val ds = meridian[i + 1].s - meridian[i].s
            if (abs(ds) < 1e-9) {
                flush()
                continue
            }
            val count = max(1, smooth.rows)
            val reach = jsHypot(ds, meridian[i + 1].r - meridian[i].r) * scale / count
            val limit = max(1, min(smooth.depth, ceilLog2(max(2.0, reach / smooth.fineness))))
            val coarse = (0..count).map { levelOf(i, it.toDouble() / count) }
            run.add(coarse[0])
            for (index in 1..count) refine(i, coarse[index - 1], coarse[index], 0, limit)
        }
        flush()
        return buckets
    }

    private fun refine(i: Int, a: Level, b: Level, depth: Int, limit: Int) {
        val mid = levelOf(i, (a.u + b.u) / 2)
        val split =
            depth < limit &&
                (!a.same(b) || !a.same(mid) || (depth < 6 && strayOf(i, a, mid, b) > smooth.error))
        if (!split) {
            run.add(b)
            return
        }
        refine(i, a, mid, depth + 1, limit)
        refine(i, mid, b, depth + 1, limit)
    }

    private fun vertexNormal(knot: Int, own: Flat): Flat {
        if (knot <= 0 || knot >= m - 1 || !meridian[knot].smooth) return own
        val a = faces[knot - 1]
        val b = faces[knot]
        val x = a.x + b.x
        val y = a.y + b.y
        val length = jsHypot(x, y).orOne()
        return Flat(x / length, y / length)
    }

    private fun normalAt(i: Int, u: Double): Flat {
        val s0 = meridian[i].s
        val s1 = meridian[i + 1].s
        val s = s0 + (s1 - s0) * u
        val sign = signOrOne(s1 - s0)
        val g = smooth.slope?.invoke(s) ?: splines?.get(i)?.slope(s)
        if (g != null) {
            val length = jsHypot(1.0, g)
            return Flat(sign / length, -sign * g / length)
        }
        val a = vertexNormal(i, faces[i])
        val b = vertexNormal(i + 1, faces[i])
        val x = a.x + (b.x - a.x) * u
        val y = a.y + (b.y - a.y) * u
        val length = jsHypot(x, y).orOne()
        return Flat(x / length, y / length)
    }

    private fun pointAt(i: Int, u: Double, angle: Double): Flat {
        val (s0, r0) = meridian[i]
        val (s1, r1) = meridian[i + 1]
        val s = s0 + (s1 - s0) * u
        val radius = smooth.radius
        val spline = splines?.get(i)
        val r =
            when {
                radius != null -> radius(s)
                spline != null -> spline.radius(s)
                else -> r0 + (r1 - r0) * u
            }
        return iso(pointOf(frame, s, r, angle), projection)
    }

    private fun arcPoints(i: Int, u: Double, from: Double, to: Double): List<Flat> {
        val count = max(1, ceil((to - from) / fine).toInt())
        return List(count + 1) { index -> pointAt(i, u, from + (to - from) * index / count) }
    }

    private fun levelOf(i: Int, u: Double): Level {
        val normal = normalAt(i, u)
        val groups = LinkedHashMap<Int, MutableList<ToneArc>>()
        for (arc in labelledArcs(normal.x, normal.y, view, light, faces[i].x, faces[i].y)) {
            groups.getOrPut(arc.tone) { ArrayList() }.add(arc)
        }
        for (list in groups.values) list.sortBy { centreOf(it, light.phi) }
        return Level(i, u, groups)
    }

    private fun centreOf(arc: ToneArc, reference: Double): Double =
        wrapAngle((arc.from + arc.to) / 2 - reference)

    private fun strayOf(i: Int, a: Level, mid: Level, b: Level): Double {
        var worst = 0.0
        for ((tone, list) in mid.groups) {
            list.forEachIndexed { index, arc ->
                val lo = align(arc, a.groups.getValue(tone)[index])
                val hi = align(arc, b.groups.getValue(tone)[index])
                for (from in listOf(true, false)) {
                    val p = pointAt(i, mid.u, if (from) arc.from else arc.to)
                    val p0 = pointAt(i, a.u, if (from) lo.from else lo.to)
                    val p1 = pointAt(i, b.u, if (from) hi.from else hi.to)
                    worst = max(worst, jsHypot(p.x - (p0.x + p1.x) / 2, p.y - (p0.y + p1.y) / 2))
                }
            }
        }
        return worst
    }

    /** One polygon per region of [tone] across [levels], which all have the same count of it. */
    private fun sheet(tone: Int, levels: List<Level>) {
        levels[0].groups.getValue(tone).forEachIndexed { index, arc ->
            val chain = arrayListOf(arc)
            for (k in 1 until levels.size) {
                chain.add(align(chain[k - 1], levels[k].groups.getValue(tone)[index]))
            }
            val last = levels.size - 1
            val points = ArrayList(arcPoints(levels[0].i, levels[0].u, chain[0].from, chain[0].to))
            for (k in 1 until last) points.add(pointAt(levels[k].i, levels[k].u, chain[k].to))
            val end = levels[last]
            points.addAll(arcPoints(end.i, end.u, chain[last].from, chain[last].to).reversed())
            for (k in last - 1 downTo 1) {
                points.add(pointAt(levels[k].i, levels[k].u, chain[k].from))
            }
            buckets[tone].add(pathOf(windOf(points), true))
        }
    }

    /** Quads that bridge [tone] from level [a] to level [b] where its count changes. */
    private fun strip(tone: Int, a: Level, b: Level) {
        val below = a.groups[tone] ?: emptyList()
        val above = b.groups[tone] ?: emptyList()
        val used = BooleanArray(above.size)
        fun quad(lo: ToneArc, hi: ToneArc) {
            val points =
                arcPoints(a.i, a.u, lo.from, lo.to) + arcPoints(b.i, b.u, hi.from, hi.to).reversed()
            buckets[tone].add(pathOf(windOf(points), true))
        }
        for (arc in below) {
            var best = -1
            var score = Double.NEGATIVE_INFINITY
            above.forEachIndexed { index, entry ->
                if (used[index]) return@forEachIndexed
                val aligned = align(arc, entry)
                val value = min(arc.to, aligned.to) - max(arc.from, aligned.from)
                if (value > score) {
                    score = value
                    best = index
                }
            }
            if (best >= 0 && score > -0.05) {
                used[best] = true
                quad(arc, align(arc, above[best]))
            } else {
                val centre = (arc.from + arc.to) / 2
                quad(arc, ToneArc(tone, centre, centre))
            }
        }
        above.forEachIndexed { index, entry ->
            if (used[index]) return@forEachIndexed
            val centre = (entry.from + entry.to) / 2
            quad(ToneArc(tone, centre, centre), entry)
        }
    }

    private fun flush() {
        val tones = run.flatMap { it.groups.keys }.distinct().sorted()
        for (tone in tones) {
            var start = 0
            for (at in 1..run.size) {
                if (at < run.size && run[at - 1].count(tone) == run[at].count(tone)) continue
                if (at - 1 > start && run[start].count(tone) != 0) {
                    sheet(tone, run.subList(start, at))
                }
                if (at < run.size) strip(tone, run[at - 1], run[at])
                start = at
            }
        }
        run = ArrayList()
    }
}
