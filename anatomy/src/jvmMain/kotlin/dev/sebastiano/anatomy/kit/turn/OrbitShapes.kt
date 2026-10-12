// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.audit.AuditShape
import dev.sebastiano.anatomy.kit.audit.BallFlat
import dev.sebastiano.anatomy.kit.audit.BallShape
import dev.sebastiano.anatomy.kit.audit.BodyShape
import dev.sebastiano.anatomy.kit.audit.BoxShape
import dev.sebastiano.anatomy.kit.audit.PrismShape
import dev.sebastiano.anatomy.kit.audit.TubeShape
import dev.sebastiano.anatomy.kit.jsHypot
import dev.sebastiano.anatomy.kit.jsRound
import kotlin.math.max

/*
 * The orbit audit's poses (`turn-audit.mjs`'s `place`, `unplace` and `posed`): a group's pose moves
 * a recorded shape from its rest-world place to where the group has taken it, so the kit's
 * distance functions can measure live shapes in any state.
 */

/** A group's pose for shapes: rotation, translation, and the rest point it turns about. */
class ShapePose(val r: DoubleArray, val t: Point3, val origin: Point3)

internal val IDENTITY = ShapePose(EYE, Point3(0.0, 0.0, 0.0), Point3(0.0, 0.0, 0.0))

/** Rest-world [p] of a group posed by [pose]. */
internal fun place(pose: ShapePose, p: Point3): Point3 {
    val q = t3Apply(pose.r, Point3(p.x - pose.origin.x, p.y - pose.origin.y, p.z - pose.origin.z))
    return Point3(q.x + pose.t.x, q.y + pose.t.y, q.z + pose.t.z)
}

/** The rest-world point a group posed by [pose] has taken to world [p]. */
internal fun unplace(pose: ShapePose, p: Point3): Point3 {
    val r = pose.r
    val v = Point3(p.x - pose.t.x, p.y - pose.t.y, p.z - pose.t.z)
    return Point3(
        r[0] * v.x + r[3] * v.y + r[6] * v.z + pose.origin.x,
        r[1] * v.x + r[4] * v.y + r[7] * v.z + pose.origin.y,
        r[2] * v.x + r[5] * v.y + r[8] * v.z + pose.origin.z,
    )
}

/** The pose of [group] in [cams], or the identity for none. */
internal fun poseFor(cams: TurnCams, group: TurnGroup?): ShapePose =
    if (group == null) IDENTITY else ShapePose(cams[group.index].r, cams[group.index].t, group.origin)

/** [shape] moved from rest by [pose]: the original's `posed`. */
fun posed(shape: AuditShape, pose: ShapePose?): AuditShape {
    if (pose == null) return shape
    val meta = if (shape.meta.cut.isEmpty()) shape.meta else shape.meta.copy(cut = shape.meta.cut.map { posed(it, pose) })
    val r = pose.r
    fun frame(f: Frame) = Frame(place(pose, f.o), t3Apply(r, f.a), t3Apply(r, f.u), t3Apply(r, f.v))
    val out =
        when (shape) {
            is BodyShape -> BodyShape(frame(shape.frame), shape.poly, meta, shape.slide)
            is PrismShape -> PrismShape(frame(shape.frame), shape.poly, shape.s0, shape.s1, meta)
            is BallShape -> BallShape(place(pose, shape.o), shape.r, meta, shape.flats.map { BallFlat(t3Apply(r, it.n), it.d) })
            is BoxShape -> BoxShape(place(pose, shape.o), shape.axes.map { t3Apply(r, it) }, shape.half, meta)
            is TubeShape ->
                TubeShape(
                    shape.points.map { place(pose, it) },
                    shape.radii,
                    meta,
                    shape.round,
                    shape.tips?.map { tip -> tip?.let { place(pose, it) } },
                    shape.tipAxes?.map { t3Apply(r, it) },
                    shape.tipRims,
                    shape.free,
                    shape.offset,
                    shape.total,
                )
        }
    out.move = shape.move
    out.ride = shape.ride
    out.piece = shape.piece
    return out
}

/** Points on [shape]'s surface about [spacing] apart, for clearances: `surfacePoints`. */
internal fun surfacePoints(shape: AuditShape, spacing: Double = 0.6): List<Point3> {
    val r = (shape as? BallShape)?.r ?: 3.0
    return sampleShape(shape, max(16, jsRound(TAU / max(0.05, spacing / max(1.0, r))).toInt()))
}

/** A bounding sphere of [points]. */
internal class Bubble(val c: Point3, val r: Double)

internal fun bubbleOf(points: List<Point3>): Bubble {
    val c = DoubleArray(3)
    for (p in points) {
        c[0] += p.x / max(1, points.size)
        c[1] += p.y / max(1, points.size)
        c[2] += p.z / max(1, points.size)
    }
    var r = 0.0
    for (p in points) r = max(r, jsHypot(p.x - c[0], p.y - c[1], p.z - c[2]))
    return Bubble(Point3(c[0], c[1], c[2]), r)
}

