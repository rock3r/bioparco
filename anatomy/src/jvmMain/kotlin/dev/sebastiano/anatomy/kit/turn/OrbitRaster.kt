// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.jsHypot
import dev.sebastiano.anatomy.kit.jsRound
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * The orbit audit's screen geometry (`turn-audit.mjs`): a part's footprint and the overlap of two,
 * a near-Hausdorff distance between outlines, and the tone raster that measures how much of a part
 * changes owner between two frames. The original reads these from the runtime's path strings, so
 * every coordinate is rounded here exactly as those strings round it.
 */

/** A coordinate as the runtime's path string writes it, read back as a number. */
private fun written(v: Double): Double = if (TurnState.exact) jsRound(v * 1e6) / 1e6 else jsRound(v * 100) / 100

/** The polygons of a path as the original parses its string (`pathPolys`). */
internal fun polysOf(path: IsoPath): List<List<Flat>> =
    path.runs.map { run -> run.points.map { Flat(written(it.x), written(it.y)) } }

internal fun polygonArea(poly: List<Flat>): Double {
    var s = 0.0
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[(i + 1) % poly.size]
        s += a.x * b.y - b.x * a.y
    }
    return s / 2
}

/** The area of [subject] clipped to the convex [clip]. */
internal fun clipArea(subject: List<Flat>, clip: List<Flat>): Double {
    val out = t3ClipConvex(subject, clip)
    return if (out.size >= 3) abs(polygonArea(out)) else 0.0
}

/** The audit's own hull: collinear points dropped (`<= 0`). */
internal fun auditHull(points: List<Flat>): List<Flat> {
    val sorted =
        points.sortedWith { a, b ->
            val d = if (a.x - b.x != 0.0) a.x - b.x else a.y - b.y
            if (d < 0) -1 else if (d > 0) 1 else 0
        }
    if (sorted.size < 3) return sorted
    fun cross(o: Flat, a: Flat, b: Flat) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    val lower = ArrayList<Flat>()
    for (p in sorted) {
        while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0) lower.removeAt(lower.size - 1)
        lower.add(p)
    }
    val upper = ArrayList<Flat>()
    for (i in sorted.indices.reversed()) {
        val p = sorted[i]
        while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0) upper.removeAt(upper.size - 1)
        upper.add(p)
    }
    return lower.subList(0, lower.size - 1) + upper.subList(0, upper.size - 1)
}

/** A part's footprint: each tube quad's hull, a fixed picture's hull, else its fill's hull. */
internal fun footprints(run: TurnRun, f: OrbitFrame): List<List<Flat>> {
    if (run is TubeRun) return f.quads.map { q -> auditHull(List(q.size / 2) { Flat(q[2 * it], q[2 * it + 1]) }) }
    if (run is FixedRun) return listOf(List(f.hullN) { Flat(f.hull[2 * it], f.hull[2 * it + 1]) })
    val polys = polysOf(f.d[run.role.getValue("fill")])
    return if (polys.isNotEmpty()) listOf(auditHull(polys.flatten())) else emptyList()
}

/** How much two parts' footprints overlap on screen, in px². */
internal fun overlapArea(runA: TurnRun, fa: OrbitFrame, runB: TurnRun, fb: OrbitFrame): Double {
    if (fa.box[2] < fb.box[0] || fb.box[2] < fa.box[0] || fa.box[3] < fb.box[1] || fb.box[3] < fa.box[1]) return 0.0
    var best = 0.0
    for (a in footprints(runA, fa)) for (b in footprints(runB, fb)) best = max(best, clipArea(a, b))
    return best
}

/** A frame as the audit keeps it: paths, box, hull and tube quads. */
internal class OrbitFrame(val d: Array<IsoPath>, val box: DoubleArray, val hull: DoubleArray, val hullN: Int, val quads: List<DoubleArray>) {
    companion object {
        /** A snapshot of [f]: its paths, box and hull copied, its tube quads as the original shares them. */
        fun of(f: TurnFrame): OrbitFrame =
            OrbitFrame(f.d.copyOf(), f.box.copyOf(), f.hull.copyOf(), f.hullN, f.quadList?.map { it.buf.copyOfRange(0, 2 * it.n) } ?: emptyList())
    }
}

private fun segDistance(p: Flat, a: Flat, b: Flat): Double {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val l = dx * dx + dy * dy
    val t = if (l != 0.0) max(0.0, min(1.0, ((p.x - a.x) * dx + (p.y - a.y) * dy) / l)) else 0.0
    return jsHypot(p.x - a.x - dx * t, p.y - a.y - dy * t)
}

/** A near-Hausdorff distance between two closed outlines, searched near matching indices first. */
internal fun hausdorffish(a: List<Flat>, b: List<Flat>): Double {
    if (a.isEmpty() || b.isEmpty()) return 0.0
    fun one(p: List<Flat>, q: List<Flat>): Double {
        var worst = 0.0
        val m = q.size
        val n = p.size
        for (i in 0 until n) {
            val centre = jsRound((i.toDouble() / n) * m).toInt()
            var best = Double.POSITIVE_INFINITY
            for (w in -10..10) {
                val j = (((centre + w) % m) + m) % m
                best = min(best, segDistance(p[i], q[j], q[(j + 1) % m]))
            }
            if (best > 0.02) for (j in 0 until m) best = min(best, segDistance(p[i], q[j], q[(j + 1) % m]))
            worst = max(worst, best)
        }
        return worst
    }
    return max(one(a, b), one(b, a))
}

/** The total area of a path's polygons. */
internal fun areaOfPath(path: IsoPath): Double = polysOf(path).sumOf { abs(polygonArea(it)) }

private fun jitter(a: Double, b: Double): Double {
    val t = jsSin(a * 12.9898 + b * 78.233) * 43758.5453
    return t - floor(t)
}

/** A polygon set and the tone it paints. */
internal class ToneLayer(val polys: List<DoubleArray>, val v: Double, val id: Int)

/** The tone each role of a frame paints, for the raster (`toneLayers`). */
internal fun toneLayers(run: TurnRun, f: TurnFrame): List<ToneLayer> {
    val out = ArrayList<ToneLayer>()
    run.roles.forEachIndexed { r, role ->
        val d = f.d[r]
        if (d.isEmpty) return@forEachIndexed
        val polys = polysOf(d).filter { it.size >= 3 }.map { poly -> DoubleArray(2 * poly.size) { if (it % 2 == 0) poly[it / 2].x else poly[it / 2].y } }
        val v =
            when {
                role.kind == RoleKind.Face -> f.q[r].toDouble() / TURN_Q
                role.name == "fill" && role.isPath -> 2.0
                (role.kind == RoleKind.Shade || role.kind == RoleKind.Top) && role.name.length == 2 -> role.name.drop(1).toDouble()
                role.name == "body" -> 2.0
                role.name == "shine" -> 4.0
                role.name == "shade" -> 0.0
                else -> return@forEachIndexed
            }
        out.add(ToneLayer(polys, v, r))
    }
    return out
}

/** The tones and owners of [layers] rasterised over [box] at [scale] samples per unit. */
private fun toneRaster(layers: List<ToneLayer>, box: DoubleArray, scale: Int): Pair<FloatArray, IntArray> {
    val x0 = box[0]
    val y0 = box[1]
    val w = max(1, ceil((box[2] - x0) * scale).toInt())
    val h = max(1, ceil((box[3] - y0) * scale).toInt())
    val out = FloatArray(w * h) { -1f }
    val owner = IntArray(w * h) { -1 }
    for (layer in layers) {
        if (layer.polys.isEmpty()) continue
        var ymin = Double.POSITIVE_INFINITY
        var ymax = Double.NEGATIVE_INFINITY
        for (p in layer.polys) {
            for (i in 1 until p.size step 2) {
                if (p[i] < ymin) ymin = p[i]
                if (p[i] > ymax) ymax = p[i]
            }
        }
        val r0 = max(0, floor((ymin - y0) * scale).toInt())
        val r1 = min(h - 1, ceil((ymax - y0) * scale).toInt())
        for (row in r0..r1) for (sub in 0 until 8) scanRow(layer, row, sub, x0, y0, w, scale, out, owner)
    }
    return out to owner
}

private fun scanRow(layer: ToneLayer, row: Int, sub: Int, x0: Double, y0: Double, w: Int, scale: Int, out: FloatArray, owner: IntArray) {
    val y = y0 + (row + (sub + 0.5) / 8) / scale
    val hits = ArrayList<Pair<Double, Int>>()
    for (p in layer.polys) {
        val n = p.size
        for (i in 0 until n step 2) {
            val ax = p[i]
            val ay = p[i + 1]
            val bx = p[(i + 2) % n]
            val by = p[(i + 3) % n]
            if ((ay <= y) == (by <= y)) continue
            hits.add((ax + ((y - ay) * (bx - ax)) / (by - ay)) to if (by > ay) 1 else -1)
        }
    }
    if (hits.size < 2) return
    hits.sortWith { a, b -> (a.first - b.first).let { if (it < 0) -1 else if (it > 0) 1 else 0 } }
    var winding = 0
    for (k in 0 until hits.size - 1) {
        winding += hits[k].second
        if (winding == 0) continue
        val xa = hits[k].first
        val xb = hits[k + 1].first
        val c0 = max(0, floor((xa - x0) * scale).toInt())
        val c1 = min(w - 1, floor((xb - x0) * scale).toInt())
        for (c in c0..c1) {
            if (floor(jitter(c.toDouble(), row.toDouble()) * 8).toInt() != sub) continue
            if (c == c0 || c == c1) {
                val x = x0 + (c + jitter(row.toDouble(), c.toDouble())) / scale
                if (x < xa || x >= xb) continue
            }
            out[row * w + c] = layer.v.toFloat()
            owner[row * w + c] = layer.id
        }
    }
}

/** How much of [run]'s picture changes owner from each frame to the next, in tone·px². */
internal fun visibleChange(run: TurnRun, frames: List<TurnFrame>, scale: Int = 4): List<Double> {
    val box = doubleArrayOf(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY)
    for (f in frames) {
        box[0] = min(box[0], f.box[0] - 1)
        box[1] = min(box[1], f.box[1] - 1)
        box[2] = max(box[2], f.box[2] + 1)
        box[3] = max(box[3], f.box[3] + 1)
    }
    val rasters = frames.map { toneRaster(toneLayers(run, it), box, scale) }
    val steps = ArrayList<Double>()
    for (i in 1 until rasters.size) {
        val (av, ao) = rasters[i - 1]
        val (bv, bo) = rasters[i]
        var sum = 0.0
        for (j in av.indices) {
            if (ao[j] == bo[j]) continue
            val va = av[j].toDouble()
            val vb = bv[j].toDouble()
            if (va < 0 || vb < 0) sum += 2 else {
                val d = abs(va - vb)
                if (d > 1.0 / 16 + 1e-6) sum += d
            }
        }
        steps.add(sum / (scale * scale))
    }
    return steps
}
