// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.audit

import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.cross3
import dev.sebastiano.anatomy.kit.len3
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.pathLength
import dev.sebastiano.anatomy.kit.resample
import dev.sebastiano.anatomy.kit.sub3
import dev.sebastiano.anatomy.kit.unit3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Where tube ends land: the nearest anchored shape to each end's rim, whether the rim sits on it,
 * and whether, apart, an end still touches something that moves with it.
 */

/** A point along a tube: where, which way, how far along, how far from the nearer end, how wide. */
internal class TubeSample(
    val p: Point3,
    val t: Point3,
    val along: Double,
    val edge: Double,
    val r: Double,
)

internal fun samplesOf(shape: TubeShape, spacing: Double): List<TubeSample> {
    val dense = resample(shape.points, spacing)
    val total = pathLength(shape.points).let { if (it == 0.0) 1.0 else it }
    val lengths = DoubleArray(shape.points.size)
    for (index in 1 until shape.points.size) {
        lengths[index] =
            lengths[index - 1] + len3(sub3(shape.points[index], shape.points[index - 1]))
    }
    fun radiusAt(at: Double): Double {
        var index = 1
        while (index < lengths.size - 1 && lengths[index] < at) index++
        val span = (lengths[index] - lengths[index - 1]).let { if (it == 0.0) 1.0 else it }
        val t = max(0.0, min(1.0, (at - lengths[index - 1]) / span))
        return shape.radii[index - 1] + (shape.radii[index] - shape.radii[index - 1]) * t
    }
    val step = total / max(1, dense.size - 1)
    return dense.mapIndexed { index, p ->
        val t = unit3(sub3(dense[min(dense.size - 1, index + 1)], dense[max(0, index - 1)]))
        TubeSample(
            p,
            t,
            index * step,
            min(index * step, total - index * step),
            radiusAt(index * step),
        )
    }
}

internal class Centreline(val d: Double, val r: Double)

/** The nearest point of [shape]'s centreline to [p], and the radius there. */
internal fun centreline(shape: TubeShape, p: Point3): Centreline {
    var best = Centreline(BIG, 0.0)
    for (index in 0 until shape.points.size - 1) {
        val hit = segmentPoint(p, shape.points[index], shape.points[index + 1])
        if (hit.d < best.d) {
            best =
                Centreline(
                    hit.d,
                    shape.radii[index] + (shape.radii[index + 1] - shape.radii[index]) * hit.t,
                )
        }
    }
    return best
}

internal fun sameOffset(a: Point3, b: Point3): Boolean =
    abs(a.x - b.x) + abs(a.y - b.y) + abs(a.z - b.z) < 1e-6

internal fun placed(shape: AuditShape, state: ExplodeState?): AuditShape =
    if (state == null || shape.move == null) shape else moved(shape, offsetOf(shape, state))

internal fun offsetAt(mover: Mover, state: ExplodeState?): Point3 =
    if (state == null || mover.move == null) ZERO else offsetOf(mover, state)

private fun tipDistance(other: AuditShape, p: Point3): Double =
    if (other is TubeShape) centreline(other, p).let { it.d - it.r } else sdf(other, p)

/** Twelve points round a circle of radius [r] about [centre], square to [axis]. */
internal fun circleAround(centre: Point3, axis: Point3, r: Double): List<Point3> {
    val helper = if (abs(axis.z) > 0.9) Point3(1.0, 0.0, 0.0) else Point3(0.0, 0.0, 1.0)
    val u = unit3(cross3(helper, axis))
    val v = cross3(axis, u)
    return List(12) { k ->
        add3(centre, add3(mul3(u, r * cos(k * PI / 6)), mul3(v, r * sin(k * PI / 6))))
    }
}

private fun rimOf(tube: TubeShape, index: Int): List<Point3> {
    val tip = tube.tips!![index]!!
    val points = tube.points
    val axis =
        tube.tipAxes?.get(index)
            ?: unit3(
                if (index != 0) sub3(points[points.size - 1], points[points.size - 2])
                else sub3(points[1], points[0])
            )
    val radius = tube.tipRims?.get(index) ?: tube.radii[if (index != 0) tube.radii.size - 1 else 0]
    return circleAround(tip, axis, radius)
}

/** The shape a tube end sits on: how far its tip is, and the rim's highest and lowest gap. */
class Mount(
    val shape: AuditShape,
    val score: Double,
    val d: Double,
    val high: Double,
    val low: Double,
    val tip: Point3,
    val landed: Boolean,
    /** The tube whose own fitting [shape] is, when it is one. */
    val host: TubeShape? = null,
)

/**
 * For every tube end, the nearest anchored shape to its end rim (12 points at the flange or
 * connector radius); the end has `landed` when every rim point is within [tolerance] of it.
 */
fun mountsOf(
    tubes: List<TubeShape>,
    solids: List<AuditShape>,
    tolerance: Double = 0.15,
): Map<TubeShape, List<Mount?>> {
    val byName = HashMap<String?, TubeShape>()
    for (tube in tubes) byName[tube.name] = tube
    val out = LinkedHashMap<TubeShape, List<Mount?>>()
    for (tube in tubes) {
        val found = arrayOfNulls<Mount>(2)
        tube.tips?.forEachIndexed { index, tip ->
            if (tip == null) return@forEachIndexed
            val best = nearestMount(tube, index, tip, solids + tubes) ?: return@forEachIndexed
            val host = byName[best.shape.owner]?.takeIf { best.shape !is TubeShape }
            found[index] =
                Mount(
                    best.shape,
                    best.score,
                    best.d,
                    best.high,
                    best.low,
                    tip,
                    best.high <= tolerance && best.low >= -tolerance,
                    host,
                )
        }
        out[tube] = found.toList()
    }
    return out
}

private fun nearestMount(
    tube: TubeShape,
    index: Int,
    tip: Point3,
    others: List<AuditShape>,
): Mount? {
    val rim = rimOf(tube, index)
    val pad = 3 + (tube.tipRims?.get(index) ?: 0.0)
    var best: Mount? = null
    for (other in others) {
        val own =
            other === tube ||
                (other.owner != null && other.owner == tube.owner) ||
                other.name == tube.owner ||
                !other.meta.anchor
        if (own || !pointNear(tip, other.bounds, pad)) continue
        val ds = rim.map { tipDistance(other, it) }
        val d = tipDistance(other, tip)
        val score = (abs(d) + ds.sumOf { abs(it) } / ds.size) / 2
        if (best == null || score < best.score) {
            best = Mount(other, score, d, ds.max(), ds.min(), tip, false)
        }
    }
    return best
}

/** A tube end whose rim isn't on its mount. */
class Terminal(
    val tube: String?,
    val end: String,
    val at: Point3,
    val d: Double,
    val high: Double,
    val low: Double,
    val nearest: String?,
)

/** Every tube end whose rim isn't within [tolerance] of the nearest anchored shape. */
fun terminals(
    tubes: List<TubeShape>,
    solids: List<AuditShape>,
    tolerance: Double = 0.15,
    mounts: Map<TubeShape, List<Mount?>> = mountsOf(tubes, solids, tolerance),
): List<Terminal> {
    val out = ArrayList<Terminal>()
    for (tube in tubes) {
        mounts[tube].orEmpty().forEachIndexed { index, mount ->
            val tip = tube.tips?.get(index)
            if (tip == null || (mount != null && mount.landed)) return@forEachIndexed
            out.add(
                Terminal(
                    tube.name,
                    if (index != 0) "end" else "start",
                    tip,
                    mount?.d ?: Double.POSITIVE_INFINITY,
                    mount?.high ?: Double.POSITIVE_INFINITY,
                    mount?.low ?: Double.NEGATIVE_INFINITY,
                    mount?.shape?.name ?: "nothing",
                )
            )
        }
    }
    return out
}

/** A tube end that touches nothing that moves with it. */
class LooseEnd(
    val tube: String?,
    val end: String,
    val at: Point3,
    val state: String,
    val free: Boolean,
)

/**
 * Every tube end that, in [state], touches no solid of another owner that moves with it. Ends
 * marked free are listed too, as `free`.
 */
fun looseEnds(
    tubes: List<TubeShape>,
    solids: List<AuditShape>,
    state: ExplodeState = Uniform(1.0),
    tolerance: Double = 0.15,
): List<LooseEnd> {
    val out = ArrayList<LooseEnd>()
    for (tube in tubes) {
        val offset = offsetAt(tube, state)
        tube.tips?.forEachIndexed { index, tip ->
            if (tip == null) return@forEachIndexed
            val hit = solids.any { shape ->
                val own =
                    (shape.owner != null && shape.owner == tube.owner) || shape.name == tube.owner
                !own &&
                    sameOffset(offsetAt(shape, state), offset) &&
                    pointNear(tip, shape.bounds, 1.0) &&
                    sdf(shape, tip) <= tolerance
            }
            if (!hit) {
                out.add(
                    LooseEnd(
                        tube.name,
                        if (index != 0) "end" else "start",
                        add3(tip, offset),
                        labelOf(state),
                        tube.free?.getOrNull(index) ?: false,
                    )
                )
            }
        }
    }
    return out
}
