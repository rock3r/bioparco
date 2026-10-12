// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.audit

import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.Knot
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.curveRuns
import dev.sebastiano.anatomy.kit.dot3
import dev.sebastiano.anatomy.kit.len3
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.pathLength
import dev.sebastiano.anatomy.kit.roundedPlan
import dev.sebastiano.anatomy.kit.sub3
import dev.sebastiano.anatomy.kit.unit3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * The recorded shapes of `kit/audit.mjs`: the true 3D shape of everything a figure draws, in world
 * units, so the audit can measure depth, clearance, contact and support from the real geometry. The
 * constructors take the original's arguments in the original's order; its `meta` object is
 * [ShapeMeta].
 */

internal val ZERO = Point3(0.0, 0.0, 0.0)

private val UP = Point3(0.0, 0.0, 1.0)
private val EAST = Point3(1.0, 0.0, 0.0)
private val NORTH = Point3(0.0, 1.0, 0.0)
private val FLOOR = Frame(ZERO, UP, EAST, NORTH)

/**
 * What the audit knows about a shape besides its geometry, the original's `meta`. [name] is used in
 * reports and matched by prefix (`"nozzle.bell*"`); [owner] groups a pipe with its own flanges,
 * bands and valve; [touch] names what a tube may touch on purpose; [bundle] lets cables of one
 * harness lie side by side; [joined] marks the runs of one gapped pipe; [limp] a cable, which holds
 * nothing up; `anchor = false` a clamp, which an end may not count as its mount; [fitting] a clamp
 * or collar whose centre `crowding` checks; [seated] a pad sunk into its host by design; [cut] the
 * shapes subtracted from this one; [curve] the smoothed profile of a turned part, for `wobble`;
 * [ground] a shape that stands on the ground, as a name starting `stand.` does.
 */
data class ShapeMeta(
    val name: String? = null,
    val owner: String? = null,
    val touch: List<String> = emptyList(),
    val bundle: String? = null,
    val joined: Boolean = false,
    val limp: Boolean = false,
    val anchor: Boolean = true,
    val fitting: Boolean = false,
    val seated: Boolean = false,
    val cut: List<AuditShape> = emptyList(),
    val curve: List<Knot>? = null,
    val ground: Boolean = false,
)

/** Something that moves in an explode: an item, a shape, a route. */
interface Mover {
    /** The world move at share 1, or null for none. */
    val move: Point3?
    /** The assembly whose spring moves it. */
    val ride: String?
    /** The assembly it highlights with. */
    val piece: String?
}

/** World bounds. */
data class Bounds(val min: Point3, val max: Point3)

/**
 * A recorded shape. [move], [ride] and [piece] are stamped by the recorder's `put` with those of
 * the item that draws it, so the shape can be posed in any explode state.
 */
sealed class AuditShape(val meta: ShapeMeta) : Mover {
    override var move: Point3? = null
    override var ride: String? = null
    override var piece: String? = null

    val name: String?
        get() = meta.name

    val owner: String?
        get() = meta.owner

    /** World bounds, computed once. */
    val bounds: Bounds by lazy { boundsFor(this) }

    internal fun stampedFrom(other: AuditShape): AuditShape = apply {
        move = other.move
        ride = other.ride
        piece = other.piece
    }
}

/** A closed `(s, r)` polygon turned round [frame]. A [slide] stretches instead of moving. */
class BodyShape(
    val frame: Frame,
    val poly: List<Knot>,
    meta: ShapeMeta,
    val slide: Slide? = null,
) : AuditShape(meta) {
    internal var depthCache: BodyView? = null
}

/** A rod sliding out of a gland at [from], reaching [to]. */
data class Slide(val from: Double, val to: Double, val r: Double)

/** A polygon in the frame's `u`, `v` plane extruded along its axis from [s0] to [s1]. */
class PrismShape(
    val frame: Frame,
    val poly: List<Floor>,
    val s0: Double,
    val s1: Double,
    meta: ShapeMeta,
) : AuditShape(meta)

/** A box round [o] with unit [axes] and [half] extents along them. */
class BoxShape(val o: Point3, val axes: List<Point3>, val half: List<Double>, meta: ShapeMeta) :
    AuditShape(meta)

/** A flat cut off a ball: the half-space `n·(p − c) ≤ d` is kept. */
data class BallFlat(val n: Point3, val d: Double)

/** A sphere, with optional [flats]. */
class BallShape(
    val o: Point3,
    val r: Double,
    meta: ShapeMeta,
    val flats: List<BallFlat> = emptyList(),
) : AuditShape(meta)

/**
 * A pipe or cable centreline with a radius per point. The recorder's runs also carry [tips] (the
 * route's true end points, null for an end that merges into a body), [tipAxes], [tipRims] (the
 * flange or connector radius at each end), [free] ends, and where the run sits on its route
 * ([offset], [total]).
 */
class TubeShape(
    val points: List<Point3>,
    val radii: List<Double>,
    meta: ShapeMeta,
    val round: Pair<Boolean, Boolean> = false to false,
    val tips: List<Point3?>? = null,
    val tipAxes: List<Point3>? = null,
    val tipRims: List<Double>? = null,
    val free: List<Boolean>? = null,
    val offset: Double? = null,
    val total: Double? = null,
) : AuditShape(meta)

/** Any closed `(s, r)` polygon turned round [frame], taken as it is. */
fun body(frame: Frame, poly: List<Knot>, meta: ShapeMeta = ShapeMeta()): BodyShape =
    BodyShape(frame, poly.map { Knot(it.s, max(0.0, it.r)) }, meta)

/**
 * The polygon `solid` and `lathe` record: [meridian] with runs of smooth knots traced along the
 * same Hermite curve the drawing uses (or along [radius]) until within [error] of a chord.
 */
fun traced(
    meridian: List<Knot>,
    radius: ((Double) -> Double)? = null,
    error: Double = 0.02,
    depth: Int = 6,
): List<Knot> {
    val segments = if (radius != null) null else curveRuns(meridian)
    val out = arrayListOf(Knot(meridian[0].s, meridian[0].r))
    for (index in 0 until meridian.size - 1) {
        val s0 = meridian[index].s
        val (s1, r1) = meridian[index + 1]
        val along = abs(s1 - s0) > 1e-9
        val at: ((Double) -> Double)? =
            when {
                !along -> null
                radius != null -> radius
                else -> segments?.get(index)?.let { curve -> { s: Double -> curve.radius(s) } }
            }
        if (at != null) traceSplit(at, s0, s1, 0, depth, error, out)
        out.add(Knot(s1, r1))
    }
    return out
}

private fun traceSplit(
    at: (Double) -> Double,
    a: Double,
    b: Double,
    level: Int,
    depth: Int,
    error: Double,
    out: MutableList<Knot>,
) {
    val mid = (a + b) / 2
    if (level >= depth) return
    val ra = at(a)
    val rb = at(b)
    var deviation = Double.NEGATIVE_INFINITY
    for (t in doubleArrayOf(0.25, 0.5, 0.75)) {
        deviation = max(deviation, abs(at(a + (b - a) * t) - (ra + (rb - ra) * t)))
    }
    if (deviation <= error) return
    traceSplit(at, a, mid, level + 1, depth, error, out)
    out.add(Knot(mid, at(mid)))
    traceSplit(at, mid, b, level + 1, depth, error, out)
}

/** The shape of the kit's `lathe(meridian, frame, P)`: any closed meridian, traced. */
fun lathe(
    frame: Frame,
    meridian: List<Knot>,
    radius: ((Double) -> Double)? = null,
    meta: ShapeMeta = ShapeMeta(),
): BodyShape = body(frame, traced(meridian, radius), meta)

/** A turned part: [profile] closed to the axis at both ends, the list you give `solidOf`. */
fun solid(
    frame: Frame,
    profile: List<Knot>,
    radius: ((Double) -> Double)? = null,
    meta: ShapeMeta = ShapeMeta(),
): BodyShape {
    val first = profile[0]
    val last = profile[profile.size - 1]
    val clamped = radius?.let { r -> { s: Double -> r(max(first.s, min(last.s, s))) } }
    val closed = listOf(Knot(first.s, 0.0)) + profile + Knot(last.s, 0.0)
    return body(frame, traced(closed, clamped), meta)
}

/** The shape of the kit's `prismOf`: [polygon] in the frame's `u`, `v` plane, along its axis. */
fun prism(
    frame: Frame,
    polygon: List<Floor>,
    s0: Double,
    s1: Double,
    meta: ShapeMeta = ShapeMeta(),
): PrismShape = PrismShape(frame, polygon.toList(), min(s0, s1), max(s0, s1), meta)

/** The shape of `extrude`: any floor ring stood from [z] up by [height]. */
fun extruded(
    ring: List<Floor>,
    z: Double,
    height: Double,
    meta: ShapeMeta = ShapeMeta(),
): PrismShape = prism(FLOOR, ring, z, z + height, meta)

/** The shape of `slabOf`: the same rounded plan, stood on the floor. */
fun slab(
    plan: Plan,
    z: Double,
    height: Double,
    meta: ShapeMeta = ShapeMeta(),
    steps: Int = 8,
): PrismShape = extruded(roundedPlan(plan, steps), z, height, meta)

/** The shape of the kit's `cylinder`. */
fun cylinder(
    cx: Double,
    cy: Double,
    r: Double,
    z: Double,
    height: Double,
    meta: ShapeMeta = ShapeMeta(),
): BodyShape = disc(Frame(Point3(cx, cy, 0.0), UP, EAST, NORTH), z, z + height, r, meta)

/** A flange, boss, pin or collar: radius [r] from [s0] to [s1] along [frame]. */
fun disc(
    frame: Frame,
    s0: Double,
    s1: Double,
    r: Double,
    meta: ShapeMeta = ShapeMeta(),
): BodyShape = body(frame, listOf(Knot(s0, 0.0), Knot(s0, r), Knot(s1, r), Knot(s1, 0.0)), meta)

/** A ring from [rIn] to [rOut]. */
fun ring(
    frame: Frame,
    s0: Double,
    s1: Double,
    rIn: Double,
    rOut: Double,
    meta: ShapeMeta = ShapeMeta(),
): BodyShape =
    body(frame, listOf(Knot(s0, rIn), Knot(s0, rOut), Knot(s1, rOut), Knot(s1, rIn)), meta)

/** A square block from [min] to [max]. */
fun box(min: Point3, max: Point3, meta: ShapeMeta = ShapeMeta()): BoxShape {
    val centre = mul3(add3(min, max), 0.5)
    val half = mul3(sub3(max, min), 0.5)
    return BoxShape(centre, listOf(EAST, NORTH, UP), listOf(half.x, half.y, half.z), meta)
}

/** A block round [centre] along any [axes]: a beam at 45°. */
fun orientedBox(
    centre: Point3,
    axes: List<Point3>,
    half: List<Double>,
    meta: ShapeMeta = ShapeMeta(),
): BoxShape = BoxShape(centre, axes.map { unit3(it) }, half, meta)

/** A sphere or a terminal dot, with optional [flats]. */
fun ball(
    at: Point3,
    r: Double,
    meta: ShapeMeta = ShapeMeta(),
    flats: List<BallFlat> = emptyList(),
): BallShape = BallShape(at, r, meta, flats)

/** A pipe or cable of one radius. */
fun tube(
    points: List<Point3>,
    radius: Double,
    meta: ShapeMeta = ShapeMeta(),
    round: Pair<Boolean, Boolean> = false to false,
): TubeShape = TubeShape(points, List(points.size) { radius }, meta, round)

/** A pipe or cable with a radius per point. */
fun tube(
    points: List<Point3>,
    radii: List<Double>,
    meta: ShapeMeta = ShapeMeta(),
    round: Pair<Boolean, Boolean> = false to false,
): TubeShape = TubeShape(points, radii, meta, round)

/** A pipe or cable whose radius is a function of the share 0..1 of its length. */
fun tube(
    points: List<Point3>,
    radius: (Double) -> Double,
    meta: ShapeMeta = ShapeMeta(),
    round: Pair<Boolean, Boolean> = false to false,
): TubeShape {
    val total = pathLength(points).let { if (it == 0.0) 1.0 else it }
    var walked = 0.0
    val radii = points.mapIndexed { index, point ->
        if (index > 0) walked += len3(sub3(point, points[index - 1]))
        radius(walked / total)
    }
    return TubeShape(points, radii, meta, round)
}

/**
 * A rod sliding out of a gland at [from]: a disc from [from] to [to] that, posed, stretches from
 * the gland face to `to + offset · a` instead of translating. Put it on the moving item.
 */
fun slide(
    frame: Frame,
    from: Double,
    to: Double,
    r: Double,
    meta: ShapeMeta = ShapeMeta(),
): BodyShape {
    val drawn = disc(frame, from, to, r, meta)
    return BodyShape(drawn.frame, drawn.poly, meta, Slide(from, to, r))
}

/** [shape] translated by [offset]; a slide stretches instead. */
fun moved(shape: AuditShape, offset: Point3?): AuditShape {
    if (offset == null || (offset.x == 0.0 && offset.y == 0.0 && offset.z == 0.0)) return shape
    if (shape is BodyShape && shape.slide != null) {
        val (from, to, r) = shape.slide
        val reach = max(from + 1e-3, to + dot3(offset, shape.frame.a))
        val poly = listOf(Knot(from, 0.0), Knot(from, r), Knot(reach, r), Knot(reach, 0.0))
        return BodyShape(shape.frame, poly, shape.meta, Slide(from, reach, r)).stampedFrom(shape)
    }
    val meta =
        if (shape.meta.cut.isEmpty()) shape.meta
        else shape.meta.copy(cut = shape.meta.cut.map { moved(it, offset) })
    val out =
        when (shape) {
            is BodyShape ->
                BodyShape(shape.frame.copy(o = add3(shape.frame.o, offset)), shape.poly, meta)
            is PrismShape ->
                PrismShape(
                    shape.frame.copy(o = add3(shape.frame.o, offset)),
                    shape.poly,
                    shape.s0,
                    shape.s1,
                    meta,
                )
            is TubeShape -> movedTube(shape, offset, meta)
            is BoxShape -> BoxShape(add3(shape.o, offset), shape.axes, shape.half, meta)
            is BallShape -> BallShape(add3(shape.o, offset), shape.r, meta, shape.flats)
        }
    return out.stampedFrom(shape)
}

private fun movedTube(shape: TubeShape, offset: Point3, meta: ShapeMeta): TubeShape =
    TubeShape(
        shape.points.map { add3(it, offset) },
        shape.radii,
        meta,
        shape.round,
        shape.tips?.map { tip -> tip?.let { add3(it, offset) } },
        shape.tipAxes,
        shape.tipRims,
        shape.free,
        shape.offset,
        shape.total,
    )
