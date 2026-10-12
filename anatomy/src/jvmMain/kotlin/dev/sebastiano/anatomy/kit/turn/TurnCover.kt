// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.jsHypot
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * What shaders need from the runtime (`t3Cover`, `t3Anchor`, `t3PoseUniforms`, `t3FloorOf`): cover
 * masks of the parts painted over a target (each part's hull as at most ten edge lines), where a
 * rest point of a group is now, and a group's pose as uniforms for `toGroup` in SkSL.
 */

/** A point of a group now: in the world, on screen, and a direction turned with it. */
class TurnAnchor(val world: Point3, val screen: Flat, val direction: Point3?)

/** A group's world pose, `t3PoseOf`. */
internal fun poseOf(scene: TurnScene, cams: TurnCams?, name: String): GroupPose {
    val index = scene.groups.indexOfFirst { it.name == name }
    val cam = if (index >= 0 && cams != null) cams[index] else null
    return GroupPose(cam?.r?.copyOf() ?: EYE.copyOf(), cam?.t ?: Point3(0.0, 0.0, 0.0))
}

/** Where rest-world [point] of [name] (null for the world) is now, `t3Anchor`. */
fun turnAnchor(
    scene: TurnScene,
    cams: TurnCams,
    name: String?,
    point: Point3,
    direction: Point3?,
): TurnAnchor {
    val index = if (name == null) -1 else scene.groups.indexOfFirst { it.name == name }
    val cam = cams.of(index)
    val o = if (index >= 0) scene.groups[index].origin else Point3(0.0, 0.0, 0.0)
    val world = place(cam, Point3(point.x - o.x, point.y - o.y, point.z - o.z))
    val w = cams.world
    val screen =
        Flat(
            w.ox + w.m[0] * world.x + w.m[1] * world.y + w.m[2] * world.z,
            w.oy + w.m[3] * world.x + w.m[4] * world.y + w.m[5] * world.z,
        )
    return TurnAnchor(world, screen, direction?.let { t3Apply(cam.r, it) })
}

/** The lowest z of [name]'s parts under rotation [r] (relative to its origin), `TURN.floorOf`. */
fun turnFloorOf(scene: TurnScene, name: String, r: DoubleArray = EYE): Double {
    val index = scene.groups.indexOfFirst { it.name == name }
    var low = Double.POSITIVE_INFINITY
    for (run in scene.runs) {
        if (run.group != index) continue
        val c = run.cloud
        for (i in c.indices step 3) {
            val z = r[6] * c[i] + r[7] * c[i + 1] + r[8] * c[i + 2]
            if (z < low) low = z
        }
    }
    return low
}

/**
 * A group's pose as shader uniforms, `TURN.poseUniforms`: the columns `X`, `Y`, `Z` and the
 * translation `T` such that `toGroup(p)` gives the group's rest-world coordinates.
 */
class PoseUniforms(
    val x: List<Double>,
    val y: List<Double>,
    val z: List<Double>,
    val t: List<Double>,
)

fun TurnController.poseUniforms(group: String): PoseUniforms = poseUniformsOf(scene, cams, group)

/** [poseUniforms] for any scene and cameras. */
fun poseUniformsOf(scene: TurnScene, cams: TurnCams, group: String): PoseUniforms {
    val index = scene.groups.indexOfFirst { it.name == group }
    val cam = if (index >= 0) cams[index] else null
    val r = cam?.r ?: EYE
    val t = cam?.t ?: Point3(0.0, 0.0, 0.0)
    val o = if (index >= 0) scene.groups[index].origin else Point3(0.0, 0.0, 0.0)
    return PoseUniforms(
        listOf(r[0], r[3], r[6]),
        listOf(r[1], r[4], r[7]),
        listOf(r[2], r[5], r[8]),
        listOf(
            t.x - (r[0] * o.x + r[1] * o.y + r[2] * o.z),
            t.y - (r[3] * o.x + r[4] * o.y + r[5] * o.z),
            t.z - (r[6] * o.x + r[7] * o.y + r[8] * o.z),
        ),
    )
}

/** Cover masks for `coverOf`: per polygon up to `edges` lines `(nx, ny, c)`, and their counts. */
class Cover(val edge: FloatArray, val count: FloatArray, val dropped: Int, val parts: List<String>)

/** The hull of [points] as at most [edges] outward edge lines, cutting the cheapest corners. */
internal fun t3Enclose(points: List<Flat>, edges: Int): List<DoubleArray> {
    val hull = t3Hull(points)
    val n = hull.size
    if (n < 3) return emptyList()
    var area = 0.0
    for (i in 0 until n) area += hull[i].x * hull[(i + 1) % n].y - hull[(i + 1) % n].x * hull[i].y
    val sign = if (area >= 0) 1.0 else -1.0
    val lines = ArrayList<DoubleArray>()
    for (i in 0 until n) {
        val p = hull[i]
        val q = hull[(i + 1) % n]
        val dx = q.x - p.x
        val dy = q.y - p.y
        val l = jsHypot(dx, dy)
        if (l >= 1e-9) {
            val nx = (sign * dy) / l
            val ny = (-sign * dx) / l
            lines.add(doubleArrayOf(nx, ny, -(nx * p.x + ny * p.y)))
        }
    }
    while (lines.size > edges) {
        val best = cheapestCorner(lines, sign)
        if (best < 0) break
        lines.removeAt(best)
    }
    return lines
}

/** Where two lines `(nx, ny, c)` meet, or null when they are parallel. */
private fun meet(a: DoubleArray, b: DoubleArray): Flat? {
    val det = a[0] * b[1] - a[1] * b[0]
    if (abs(det) < 1e-12) return null
    return Flat((-a[2] * b[1] + b[2] * a[1]) / det, (-a[0] * b[2] + b[0] * a[2]) / det)
}

/** The line whose removal adds the least area to the hull, or -1. */
private fun cheapestCorner(lines: List<DoubleArray>, sign: Double): Int {
    var best = -1
    var cost = Double.POSITIVE_INFINITY
    val m = lines.size
    for (i in 0 until m) {
        val added =
            cornerCost(lines[(i - 1 + m) % m], lines[i], lines[(i + 1) % m], sign) ?: continue
        if (added < cost) {
            cost = added
            best = i
        }
    }
    return best
}

/** The area dropping [self] between [prev] and [next] adds, or null if it can't be dropped. */
private fun cornerCost(
    prev: DoubleArray,
    self: DoubleArray,
    next: DoubleArray,
    sign: Double,
): Double? {
    val turn = prev[0] * next[1] - prev[1] * next[0]
    if (sign * turn <= 1e-9) return null
    val apex = meet(prev, next) ?: return null
    val a = meet(prev, self) ?: return null
    val b = meet(self, next) ?: return null
    return abs((a.x - apex.x) * (b.y - apex.y) - (a.y - apex.y) * (b.x - apex.x)) / 2
}

private class CoverCandidate(val run: TurnRun, val f: TurnFrame, val share: Double)

/**
 * Cover masks for a shader drawn over [sets] of target parts (`TURN.cover`): every part painted
 * after a target whose footprint meets it, largest overlap first, as up to [polygons] hulls of up
 * to [edges] lines each, pushed out by [pad]. [layers] adds layers above the target's own.
 */
fun TurnController.cover(
    sets: List<List<String>>,
    polygons: Int = 6,
    edges: Int = 10,
    pad: Double = 0.3,
    layers: List<String>? = null,
): Cover = coverOf(scene, frames, { orderIndex(it) }, sets, polygons, edges, pad, layers)

/** [cover] for any scene, frames and orders. */
fun coverOf(
    scene: TurnScene,
    frames: List<TurnFrame>,
    orderIndex: (String) -> IntArray?,
    sets: List<List<String>>,
    polygons: Int = 6,
    edges: Int = 10,
    pad: Double = 0.3,
    layers: List<String>? = null,
): Cover {
    val candidates = ArrayList<CoverCandidate>()
    val taken = HashSet<Int>()
    for (list in sets) {
        val targets = scene.runs.filter { it.name in list }
        if (targets.isEmpty()) continue
        val search = CoverSearch(scene, frames, list, targets, pad, taken)
        val home = targets[0].layer
        scene.layers.forEachIndexed { index, layer ->
            val order = orderIndex(layer.name)
            val above = layers != null && layer.name in layers && index > home
            if (order != null && (index == home || above))
                search.scan(order, index == home, candidates)
        }
    }
    val sorted = candidates.sortedWith { a, b ->
        (b.share - a.share).let { if (it < 0) -1 else if (it > 0) 1 else 0 }
    }
    val edge = FloatArray(polygons * edges * 3)
    val count = FloatArray(polygons)
    val parts = ArrayList<String>()
    var slot = 0
    for (candidate in sorted) {
        if (slot >= polygons) break
        val lines = t3Enclose(outlineOf(candidate.f), edges)
        if (lines.isNotEmpty()) {
            lines.forEachIndexed { e, line ->
                val at = (slot * edges + e) * 3
                edge[at] = line[0].toFloat()
                edge[at + 1] = line[1].toFloat()
                edge[at + 2] = (line[2] - pad).toFloat()
            }
            count[slot] = lines.size.toFloat()
            parts.add(candidate.run.name)
            slot++
        }
    }
    return Cover(edge, count, max(0, candidates.size - slot), parts)
}

/** A frame's footprint: a tube's quads and cap rings, or a part's hull. */
private fun outlineOf(f: TurnFrame): List<Flat> {
    val points = ArrayList<Flat>()
    val quads = f.quadList
    if (f.quadN > 0 && quads != null) {
        for (q in quads) for (i in 0 until q.n) points.add(Flat(q.buf[2 * i], q.buf[2 * i + 1]))
    } else {
        for (i in 0 until f.hullN) points.add(Flat(f.hull[2 * i], f.hull[2 * i + 1]))
    }
    return points
}

/** The parts painted after one set of targets whose footprints meet theirs. */
private class CoverSearch(
    val scene: TurnScene,
    val frames: List<TurnFrame>,
    val names: List<String>,
    val targets: List<TurnRun>,
    val pad: Double,
    val taken: MutableSet<Int>,
) {
    private val box =
        doubleArrayOf(
            Double.POSITIVE_INFINITY,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
        )

    init {
        for (run in targets) {
            val b = frames[run.index].box
            box[0] = min(box[0], b[0])
            box[1] = min(box[1], b[1])
            box[2] = max(box[2], b[2])
            box[3] = max(box[3], b[3])
        }
    }

    /**
     * Adds the parts of a layer's [order] that cover the targets: after them in their [home] layer.
     */
    fun scan(order: IntArray, home: Boolean, out: MutableList<CoverCandidate>) {
        var from = 0
        if (home) for (at in order.indices) if (targets.any { it.index == order[at] }) from = at + 1
        for (at in from until order.size) {
            val run = scene.runs[order[at]]
            val f = frames[run.index]
            if (run.name !in names && run.index !in taken && meets(f)) {
                taken.add(run.index)
                val b = f.box
                val share =
                    (min(b[2], box[2]) - max(b[0], box[0])) *
                        (min(b[3], box[3]) - max(b[1], box[1]))
                out.add(CoverCandidate(run, f, share))
            }
        }
    }

    private fun meets(f: TurnFrame): Boolean {
        val b = f.box
        val near =
            b[0] < box[2] + pad && box[0] < b[2] + pad && b[1] < box[3] + pad && box[1] < b[3] + pad
        return near && targets.any { !turnDisjoint(frames[it.index], f, -pad) }
    }
}
