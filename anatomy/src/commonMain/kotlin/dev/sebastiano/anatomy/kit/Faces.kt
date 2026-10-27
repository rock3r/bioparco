// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/* Lines on faces and the parts library: grooves, rings, ticks, knurls, seams and coils. */

/** A polyline on a horizontal face at height [z]. */
fun onTop(
    points: List<Floor>,
    z: Double,
    projection: Projection,
    closed: Boolean = false,
): IsoPath = pathOf(points.map { iso(Point3(it.x, it.y, z), projection) }, closed)

/** A rounded-rectangle outline on a face, for grooves and inlays. */
fun planOutline(plan: Plan, z: Double, projection: Projection, steps: Int = 8): IsoPath =
    onTop(ringOf(plan, steps), z, projection, closed = true)

/** One segment on a horizontal face. */
fun lineOnTop(from: Floor, to: Floor, z: Double, projection: Projection): IsoPath =
    onTop(listOf(from, to), z, projection)

/** Any 3D segment. */
fun segment(from: Point3, to: Point3, projection: Projection): IsoPath =
    pathOf(listOf(iso(from, projection), iso(to, projection)))

/** A vertical segment at [at], from [from] up to [to]. */
fun rise(at: Floor, from: Double, to: Double, projection: Projection): IsoPath =
    segment(Point3(at.x, at.y, from), Point3(at.x, at.y, to), projection)

/** A circle on a face, for screw rings, bezels and seats. */
fun ring(
    cx: Double,
    cy: Double,
    radius: Double,
    z: Double,
    projection: Projection,
    steps: Int = 40,
): IsoPath = onTop(circleRing(cx, cy, radius, steps), z, projection, closed = true)

/** Projects a list of points, for screw and rivet dots. */
fun pointsAt(points: List<Point3>, projection: Projection): List<Flat> = points.map {
    iso(it, projection)
}

/** Projected perforations: [columns] by [rows] dots, [gap] apart, on a face at [z]. */
fun dotGrid(
    x: Double,
    y: Double,
    columns: Int,
    rows: Int,
    gap: Double,
    z: Double,
    projection: Projection,
): List<Flat> {
    val dots = ArrayList<Flat>()
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            dots.add(iso(Point3(x + column * gap, y + row * gap, z), projection))
        }
    }
    return dots
}

/** Vertical lines on the visible half of a cylinder. */
fun knurl(
    cx: Double,
    cy: Double,
    radius: Double,
    z0: Double,
    z1: Double,
    count: Int,
    projection: Projection,
    inset: Double = 0.4,
): IsoPath {
    val view = towardViewer(projection)
    val lines = ArrayList<IsoPath>()
    for (index in 0 until count) {
        val angle = index.toDouble() / count * PI * 2
        val nx = cos(angle)
        val ny = sin(angle)
        if (nx * view.x + ny * view.y < 0.15) continue
        lines.add(
            segment(
                Point3(cx + radius * nx, cy + radius * ny, z0 + inset),
                Point3(cx + radius * nx, cy + radius * ny, z1 - inset),
                projection,
            )
        )
    }
    return IsoPath.join(lines)
}

/** The visible front half of a horizontal circle on a cylinder side: seams and bands. */
fun sideArc(
    cx: Double,
    cy: Double,
    radius: Double,
    z: Double,
    projection: Projection,
    steps: Int = 24,
): IsoPath {
    val view = towardViewer(projection)
    val facing = atan2(view.y, view.x)
    val points =
        (0..steps).map { step ->
            val angle = facing - PI / 2 + step.toDouble() / steps * PI
            iso(Point3(cx + radius * cos(angle), cy + radius * sin(angle), z), projection)
        }
    return pathOf(points)
}

/** [sideSeam] for any floor outline: the ring you gave [extrude]. */
fun ringSeam(ring: List<Floor>, z: Double, projection: Projection): IsoPath {
    val count = ring.size
    val counter = floorArea(ring) > 0
    val view = towardViewer(projection)
    val facing = ring.mapIndexed { index, a ->
        val b = ring[(index + 1) % count]
        val dx = b.x - a.x
        val dy = b.y - a.y
        val nx = if (counter) dy else -dy
        val ny = if (counter) -dx else dx
        nx * view.x + ny * view.y > 1e-9
    }
    return IsoPath.join(
        runsOf(facing, true).map { run ->
            pathOf(run.map { iso(Point3(ring[it].x, ring[it].y, z), projection) })
        }
    )
}

/**
 * The visible near half of a rounded plan at height [z], wrapping its corners and ending exactly on
 * the slab's vertical silhouette edges: a weld bead, a band, a lid seam or a trim line on a box.
 * Pass the slab's own [steps] and draw it as the slab's `inner`.
 */
fun sideSeam(plan: Plan, z: Double, projection: Projection, steps: Int = 8): IsoPath =
    ringSeam(ringOf(plan, steps), z, projection)

/** Which vertical face a [sideRing] or [sideMatrix] lies on. */
enum class Axis {
    X,
    Y,
}

/** A circle on a vertical face: bores, rod ends, lenses. [axis] is the face's normal. */
fun sideRing(
    center: Point3,
    radius: Double,
    axis: Axis,
    projection: Projection,
    steps: Int = 20,
): IsoPath {
    val (px, py, pz) = center
    val points =
        List(steps) { step ->
            val angle = step.toDouble() / steps * PI * 2
            val u = radius * cos(angle)
            val v = radius * sin(angle)
            iso(
                if (axis == Axis.X) Point3(px, py + u, pz + v) else Point3(px + u, py, pz + v),
                projection,
            )
        }
    return pathOf(points, closed = true)
}

/** A coil spring split into the halves behind and in front of what it wraps. */
class Coil(val back: IsoPath, val front: IsoPath)

/**
 * A coil spring along [axis] from [from] to [to], around the line through [center] (its two other
 * coordinates). Paint [Coil.back], then the rod, then [Coil.front].
 */
fun coil(
    from: Double,
    to: Double,
    center: Floor,
    radius: Double,
    turns: Double,
    axis: Axis,
    projection: Projection,
    perTurn: Int = 16,
): Coil {
    val count = max(1, jsRound(turns * perTurn).toInt())
    val back = ArrayList<IsoPath>()
    val front = ArrayList<IsoPath>()
    var run = ArrayList<Flat>()
    var near: Boolean? = null
    for (index in 0..count) {
        val share = index.toDouble() / count
        val angle = share * turns * PI * 2
        val u = radius * cos(angle)
        val v = radius * sin(angle)
        val along = from + (to - from) * share
        val point =
            if (axis == Axis.X) Point3(along, center.x + u, center.y + v)
            else Point3(center.x + u, along, center.y + v)
        val normal =
            if (axis == Axis.X) Point3(0.0, cos(angle), sin(angle))
            else Point3(cos(angle), 0.0, sin(angle))
        val facing = depthOf(normal, projection) > 0
        val flat = iso(point, projection)
        if (near == null) near = facing
        if (facing != near) {
            run.add(flat)
            (if (near) front else back).add(pathOf(run))
            run = arrayListOf(flat)
            near = facing
            continue
        }
        run.add(flat)
    }
    if (run.size > 1) (if (near == true) front else back).add(pathOf(run))
    return Coil(IsoPath.join(back), IsoPath.join(front))
}

/** Ruler or dial ticks, minor and major, to draw `lo` and `mid`. */
class Ticks(val minor: IsoPath, val major: IsoPath)

/**
 * Ruler ticks on a top face from [from] to [to] every [step], a major one every [every]th. Each
 * grows from the line [edge] in +y (or +x when [axis] is [Axis.Y]); [lengths] are minor and major.
 */
fun topTicks(
    from: Double,
    to: Double,
    step: Double,
    every: Int,
    edge: Double,
    z: Double,
    lengths: Pair<Double, Double>,
    projection: Projection,
    axis: Axis = Axis.X,
): Ticks =
    ticks(from, to, step, every, lengths) { at, length ->
        val a = if (axis == Axis.X) Floor(at, edge) else Floor(edge, at)
        val b = if (axis == Axis.X) Floor(at, edge + length) else Floor(edge + length, at)
        lineOnTop(a, b, z, projection)
    }

/** Ticks hanging down a vertical face at [face] from its [top] edge. */
fun sideTicks(
    from: Double,
    to: Double,
    step: Double,
    every: Int,
    face: Double,
    top: Double,
    lengths: Pair<Double, Double>,
    projection: Projection,
    axis: Axis = Axis.X,
): Ticks =
    ticks(from, to, step, every, lengths) { at, length ->
        val a = if (axis == Axis.X) Point3(at, face, top) else Point3(face, at, top)
        val b =
            if (axis == Axis.X) Point3(at, face, top - length) else Point3(face, at, top - length)
        segment(a, b, projection)
    }

private inline fun ticks(
    from: Double,
    to: Double,
    step: Double,
    every: Int,
    lengths: Pair<Double, Double>,
    tick: (at: Double, length: Double) -> IsoPath,
): Ticks {
    val minor = ArrayList<IsoPath>()
    val major = ArrayList<IsoPath>()
    val first = ceil(from / step) * step
    var at = first
    var index = jsRound(first / step).toInt()
    while (at <= to + 1e-9) {
        val big = index % every == 0
        (if (big) major else minor).add(tick(at, if (big) lengths.second else lengths.first))
        at += step
        index++
    }
    return Ticks(IsoPath.join(minor), IsoPath.join(major))
}

/**
 * Dial and turntable ticks: [count] around a circle of [radius] at height [z], a major one every
 * [every]th. Angle 0 points to −y and grows clockwise seen from above; [span] limits the arc.
 */
fun radialTicks(
    cx: Double,
    cy: Double,
    radius: Double,
    count: Int,
    every: Int,
    z: Double,
    lengths: Pair<Double, Double>,
    projection: Projection,
    from: Double = 0.0,
    span: Double = PI * 2,
): Ticks {
    val minor = ArrayList<IsoPath>()
    val major = ArrayList<IsoPath>()
    val closed = abs(span - PI * 2) < 1e-9
    val steps = if (closed) count else count + 1
    for (index in 0 until steps) {
        val angle = from + index.toDouble() / count * span
        val big = index % every == 0
        val inner = radius - if (big) lengths.second else lengths.first
        val sx = sin(angle)
        val sy = -cos(angle)
        (if (big) major else minor).add(
            lineOnTop(
                Floor(cx + sx * inner, cy + sy * inner),
                Floor(cx + sx * radius, cy + sy * radius),
                z,
                projection,
            )
        )
    }
    return Ticks(IsoPath.join(minor), IsoPath.join(major))
}
