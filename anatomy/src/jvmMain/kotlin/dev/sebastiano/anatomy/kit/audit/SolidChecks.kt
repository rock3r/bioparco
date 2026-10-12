// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.audit

import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.curveRuns
import dev.sebastiano.anatomy.kit.jsRound
import dev.sebastiano.anatomy.kit.sub3
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/*
 * Solid against solid in an explode state, the slope of smoothed profiles, and saddle pads that
 * would fly off their host.
 */

private data class Cell(val x: Long, val y: Long, val z: Long)

private data class GridKey(val spacing: Double, val cell: Double)

/** Surface points on a grid of cells, per shape and setting, kept as long as the shape lives. */
private val grids: MutableMap<AuditShape, MutableMap<GridKey, Map<Cell, List<Point3>>>> =
    Collections.synchronizedMap(WeakHashMap())

private fun gridOf(shape: AuditShape, spacing: Double, cell: Double): Map<Cell, List<Point3>> {
    val samples = grids.getOrPut(shape) { HashMap() }
    return samples.getOrPut(GridKey(spacing, cell)) {
        val grid = LinkedHashMap<Cell, MutableList<Point3>>()
        for (p in surfaceOf(shape, spacing)) {
            val key = Cell(cellOf(p.x, cell), cellOf(p.y, cell), cellOf(p.z, cell))
            grid.getOrPut(key) { ArrayList() }.add(p)
        }
        grid
    }
}

private fun cellOf(v: Double, cell: Double): Long = floor(v / cell).toLong()

private fun inside(p: Point3, box: Bounds): Boolean =
    p.x >= box.min.x &&
        p.x <= box.max.x &&
        p.y >= box.min.y &&
        p.y <= box.max.y &&
        p.z >= box.min.z &&
        p.z <= box.max.z

private fun pointsIn(shape: AuditShape, box: Bounds, spacing: Double, cell: Double): List<Point3> {
    val grid = gridOf(shape, spacing, cell)
    val lo = point3 { floor(box.min[it] / cell) }
    val hi = point3 { floor(box.max[it] / cell) }
    val out = ArrayList<Point3>()
    if ((hi.x - lo.x + 1) * (hi.y - lo.y + 1) * (hi.z - lo.z + 1) > grid.size) {
        for (points in grid.values) points.filterTo(out) { inside(it, box) }
        return out
    }
    for (x in lo.x.toLong()..hi.x.toLong()) {
        for (y in lo.y.toLong()..hi.y.toLong()) {
            for (z in lo.z.toLong()..hi.z.toLong()) {
                grid[Cell(x, y, z)]?.filterTo(out) { inside(it, box) }
            }
        }
    }
    return out
}

/** [shape] as posed in [state], when it is a slide that stretches; null when it isn't one. */
private fun stretchedSlide(shape: AuditShape, state: ExplodeState?): BodyShape? {
    if (shape !is BodyShape || shape.slide == null || state == null || shape.move == null)
        return null
    val posed = moved(shape, offsetOf(shape, state)) as BodyShape
    return BodyShape(posed.frame, posed.poly, posed.meta, posed.slide).also {
        it.ride = shape.ride
        it.piece = shape.piece
    }
}

/**
 * Poses [solids] in [state] and, for every pair at a different offset (or a slide that stretched),
 * tests each one's surface points (every [spacing], gridded in [cell]s) against the other's
 * distance; anything more than [sunk] inside fails. A seated pad and its host are excused.
 */
fun solidClearances(
    solids: List<AuditShape>,
    state: ExplodeState? = null,
    sunk: Double = 0.5,
    spacing: Double = 0.6,
    cell: Double = 4.0,
): List<Clearance> {
    val stretched = HashSet<AuditShape>()
    val list =
        solids
            .filter { it !is TubeShape }
            .map { shape ->
                val next = stretchedSlide(shape, state) ?: return@map shape
                if (next.slide!!.to != (shape as BodyShape).slide!!.to) stretched.add(next)
                next
            }
    val offsets = list.map { offsetAt(it, state) }
    val label = state?.let { labelOf(it) } ?: ""
    val found = ArrayList<Clearance>()
    for (a in list.indices) {
        for (b in a + 1 until list.size) {
            val rel = sub3(offsets[a], offsets[b])
            val still = abs(rel.x) + abs(rel.y) + abs(rel.z) < 1e-6
            if (still && list[a] !in stretched && list[b] !in stretched) continue
            val hit =
                sunkBetween(list[a], list[b], rel, offsets[a], offsets[b], sunk, spacing, cell)
            if (hit != null) {
                found.add(
                    Clearance(
                        list[a].name,
                        list[b].name,
                        "through",
                        hit.first,
                        -sunk,
                        hit.second,
                        0.0,
                        0.0,
                        0.0,
                        label,
                        solid = true,
                    )
                )
            }
        }
    }
    return found.sortedBy { it.clearance }
}

private fun sunkBetween(
    a: AuditShape,
    b: AuditShape,
    rel: Point3,
    offsetA: Point3,
    offsetB: Point3,
    sunk: Double,
    spacing: Double,
    cell: Double,
): Pair<Double, Point3>? {
    val box =
        Bounds(
            point3 { max(a.bounds.min[it] + rel[it], b.bounds.min[it]) },
            point3 { min(a.bounds.max[it] + rel[it], b.bounds.max[it]) },
        )
    if ((0 until 3).any { box.min[it] > box.max[it] - sunk }) return null
    if (seatedOn(a, b) || seatedOn(b, a)) return null
    var worst = -sunk
    var at: Point3? = null
    val local = Bounds(sub3(box.min, rel), sub3(box.max, rel))
    for (p in pointsIn(a, local, spacing, cell)) {
        val d = sdf(b, add3(p, rel))
        if (d < worst) {
            worst = d
            at = add3(p, offsetA)
        }
    }
    for (p in pointsIn(b, box, spacing, cell)) {
        val d = sdf(a, sub3(p, rel))
        if (d < worst) {
            worst = d
            at = add3(p, offsetB)
        }
    }
    return at?.let { worst to it }
}

/** A smoothed profile whose slope turns at [at] on the run from [from] to [to]. */
class Wobble(val name: String?, val at: List<Double>, val from: Double, val to: Double)

/**
 * Samples `dr/ds` of every recorded `curve` at [samples] points per segment and lists the `s`
 * values where it turns between the run's ends.
 */
fun wobble(solids: List<AuditShape>, samples: Int = 20): List<Wobble> {
    val out = ArrayList<Wobble>()
    for (shape in solids) {
        val profile = shape.meta.curve ?: continue
        val segments = curveRuns(profile)
        var run = ArrayList<Int>()
        fun flush() {
            if (run.isNotEmpty()) {
                val turns = slopeTurns(profile, segments, run, samples)
                if (turns.isNotEmpty()) {
                    out.add(Wobble(shape.name, turns, profile[run[0]].s, profile[run.last() + 1].s))
                }
            }
            run = ArrayList()
        }
        segments.forEachIndexed { index, segment ->
            if (segment != null) run.add(index) else flush()
        }
        flush()
    }
    return out
}

private fun slopeTurns(
    profile: List<dev.sebastiano.anatomy.kit.Knot>,
    segments: List<dev.sebastiano.anatomy.kit.Curve?>,
    run: List<Int>,
    samples: Int,
): List<Double> {
    val s = ArrayList<Double>()
    val g = ArrayList<Double>()
    for (index in run) {
        val s0 = profile[index].s
        val s1 = profile[index + 1].s
        for (j in (if (s.isNotEmpty()) 1 else 0)..samples) {
            val at = s0 + ((s1 - s0) * j) / samples
            s.add(at)
            g.add(segments[index]!!.slope(at))
        }
    }
    val scale = max(1.0, g.maxOf { abs(it) }) * 1e-6
    val turns = ArrayList<Double>()
    var last = 0.0
    for (i in 1 until g.size) {
        val d = g[i] - g[i - 1]
        if (abs(d) <= scale) continue
        val sign = sign(d)
        if (last != 0.0 && sign != last) turns.add(jsRound(s[i - 1] * 100) / 100)
        last = sign
    }
    return turns
}

/** A seated shape cut to a host that moves with another assembly. */
class OffSeat(val name: String?, val host: String?, val piece: String?, val hostPiece: String?)

/** Every seated shape cut to a host that moves with another assembly. */
fun seats(solids: List<AuditShape>): List<OffSeat> {
    val out = ArrayList<OffSeat>()
    for (shape in solids) {
        if (!shape.meta.seated) continue
        for (host in shape.meta.cut) {
            val mine = shape.ride ?: shape.piece
            val theirs = host.ride ?: host.piece
            if (mine == theirs && sameOffset(shape.move ?: ZERO, host.move ?: ZERO)) continue
            out.add(OffSeat(shape.name, host.name, mine, theirs))
        }
    }
    return out
}
