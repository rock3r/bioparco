// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * The pieces `kit/tube.mjs` draws a chunk from: the route resampled once, a screen frame at every
 * point (a central difference along the whole route, so two chunks agree where they overlap), the
 * ribbon of edges and stripes, and the elliptical end caps the stripes run onto.
 */

/** The screen direction the tube stripes are lit from. */
private val TUBE_LIGHT: Flat = run {
    val length = jsHypot(-0.55, -0.835)
    Flat(-0.55 / length, -0.835 / length)
}

/** A centreline point on screen, its screen normal and the tube's half-width there. */
internal class TubeFrame(val p: Flat, val nx: Double, val ny: Double, val half: Double) {
    val toward: Double
        get() = nx * TUBE_LIGHT.x + ny * TUBE_LIGHT.y

    /** Where the shine band runs across the tube, as two offsets along the normal. */
    val shine: Pair<Double, Double>
        get() = (half * 0.44 * toward + half * 0.24) to (half * 0.44 * toward - half * 0.24)

    /** Where the shade band runs across the tube. */
    val shade: Pair<Double, Double>
        get() = (-half * 0.62 * toward + half * 0.2) to (-half * 0.62 * toward - half * 0.2)

    fun at(offset: Double): Flat = Flat(p.x + nx * offset, p.y + ny * offset)
}

/** A stretch of the route: its 3D points, on screen, its screen half-widths and frames. */
internal class TubePart(
    val points: List<Point3>,
    val flat: List<Flat>,
    val widths: List<Double>,
    val frames: List<TubeFrame>,
)

/** The original's `sideOf`: the two edges and the edges of both stripes, frame by frame. */
internal class Ribbon(frames: List<TubeFrame>) {
    val left = frames.map { it.at(it.half) }
    val right = frames.map { it.at(-it.half) }
    val shineA = frames.map { it.at(it.shine.first) }
    val shineB = frames.map { it.at(it.shine.second) }
    val shadeA = frames.map { it.at(it.shade.first) }
    val shadeB = frames.map { it.at(it.shade.second) }
}

/** The route of a tube, resampled once, with the original's frames and sub-paths. */
internal class TubeRoute(
    points3: List<Point3>,
    spacing: Double,
    val radiusAt: (Double) -> Double,
    val projection: Projection,
    val looped: Boolean,
) {
    val dense: List<Point3> = resample(points3, spacing)
    val total: Double = pathLength(dense)
    val k: Double = scaleOf(projection)
    private val step = max(0.05, min(spacing, total / 4) * 0.5)

    fun wrap(at: Double): Double =
        if (looped) ((at % total) + total) % total else max(0.0, min(total, at))

    private fun frameAt(at: Double, p: Flat, width: Double): TubeFrame {
        val a = iso(pointAlong(dense, wrap(at - step)), projection)
        val b = iso(pointAlong(dense, wrap(at + step)), projection)
        var tx = b.x - a.x
        var ty = b.y - a.y
        val length = jsHypot(tx, ty).orOne()
        tx /= length
        ty /= length
        return TubeFrame(p, -ty, tx, width)
    }

    /** The route from [start] to [end], which may run past either end of a looped route. */
    fun partOf(start: Double, end: Double): TubePart {
        var points = cut(dense, max(0.0, start), min(total, end))
        if (looped && start < 0) points = cut(dense, total + start, total) + points.drop(1)
        if (looped && end > total) points = points + cut(dense, 0.0, end - total).drop(1)
        var walked = if (looped) start else max(0.0, start)
        val along = points.mapIndexed { index, point ->
            if (index > 0) walked += len3(sub3(point, points[index - 1]))
            walked
        }
        val flat = points.map { iso(it, projection) }
        val widths = along.map { at ->
            radiusAt(if (looped) wrap(at) / total else min(1.0, max(0.0, at / total))) * k
        }
        val frames = flat.mapIndexed { index, p -> frameAt(along[index], p, widths[index]) }
        return TubePart(points, flat, widths, frames)
    }
}

/** The signed area of a polygon, twice over, summed as the original sums it. */
internal fun areaOf(points: List<Flat>): Double {
    var sum = 0.0
    for (index in points.indices) {
        val (x, y) = points[index]
        val (nx, ny) = points[(index + 1) % points.size]
        sum = sum + x * ny - nx * y
    }
    return sum
}

/** An end's projected cross-section, its outer half (`arc`) and its inner half. */
internal class TubeCap(val ring: List<Flat>, val arc: List<Flat>, val inner: List<Flat>)

private const val CAP_STEPS = 28

/** The original's `capOf`: the cross-section at [centre], halved by which way [inward] points. */
internal fun capOf(
    centre: Point3,
    tangent: Point3,
    radius: Double,
    inward: Flat,
    projection: Projection,
): TubeCap {
    val view = viewOf(projection)
    var n1 = cross3(tangent, view)
    if (len3(n1) < 1e-6) n1 = frameAlong(Point3(0.0, 0.0, 0.0), tangent).u
    n1 = unit3(n1)
    val n2 = unit3(cross3(tangent, n1))
    val ring =
        List(CAP_STEPS) { index ->
            val angle = (index.toDouble() / CAP_STEPS) * PI * 2
            val offset = add3(mul3(n1, radius * cos(angle)), mul3(n2, radius * sin(angle)))
            iso(add3(centre, offset), projection)
        }
    val c = iso(centre, projection)
    val probe = iso(add3(centre, n2), projection)
    val along = (probe.x - c.x) * inward.x + (probe.y - c.y) * inward.y
    val from = if (along > 0) CAP_STEPS / 2 else 0
    fun half(start: Int) = List(CAP_STEPS / 2 + 1) { ring[(start + it) % CAP_STEPS] }
    return TubeCap(ring, half(from), half((from + CAP_STEPS / 2) % CAP_STEPS))
}

/**
 * The original's `stripeEnd`: the points of an end's [half] ellipse between offsets [from] and [to]
 * across the tube, with the crossings of both levels, ordered from [from] to [to].
 */
internal fun stripeEnd(half: List<Flat>, frame: TubeFrame, from: Double, to: Double): List<Flat> {
    val centre = frame.p
    fun offset(p: Flat) = (p.x - centre.x) * frame.nx + (p.y - centre.y) * frame.ny
    val values = half.map(::offset)
    val lo = min(from, to)
    val hi = max(from, to)
    val points = ArrayList<Flat>()
    fun cross(k: Int, level: Double): Flat {
        val t =
            (level - values[k]) /
                (values[k + 1] - values[k]).let { if (it == 0.0 || it.isNaN()) 1e-9 else it }
        return Flat(
            half[k].x + (half[k + 1].x - half[k].x) * t,
            half[k].y + (half[k + 1].y - half[k].y) * t,
        )
    }
    for (k in half.indices) {
        if (values[k] >= lo && values[k] <= hi) points.add(half[k])
        if (k < half.size - 1) {
            for (level in listOf(lo, hi)) {
                if ((values[k] - level) * (values[k + 1] - level) < 0) points.add(cross(k, level))
            }
        }
    }
    val order = points.map(::offset)
    return points.indices
        .sortedWith { x, y -> jsOrder(if (from > to) order[y] - order[x] else order[x] - order[y]) }
        .map { points[it] }
}
