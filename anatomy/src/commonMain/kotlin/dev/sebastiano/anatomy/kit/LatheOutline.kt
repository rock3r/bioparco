// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/*
 * The outline of a turned part: the hull of its rings, or, for a waisted body, its true silhouette
 * (where the surface turns away from the viewer) joined by the visible end arcs.
 */

/** A lathe's [fill] and [outline], without the shading. */
class LatheOutline(val fill: IsoPath, val outline: IsoPath)

/** Only the fill and outline of [lathe], for a body whose outline you draw separately. */
fun outlineOf(
    meridian: List<Knot>,
    frame: Frame,
    projection: Projection,
    steps: Int = stepsFor(meridian.maxOf { it.r }, 32, 160, scaleOf(projection)),
    fill: LatheFill = LatheFill.Auto,
    hole: Knot? = null,
    rims: Rims = Rims.All,
    smooth: Smooth? = Smooth.auto(meridian),
    waist: Double = 0.5,
): LatheOutline =
    latheOutline(meridian, frame, projection, steps, fill, hole, rims, smooth, waist, null)

/** [outlineOf], reusing the facets [all] of a lathe already built for `LatheFill.Facets`. */
internal fun latheOutline(
    meridian: List<Knot>,
    frame: Frame,
    projection: Projection,
    steps: Int,
    fill: LatheFill,
    hole: Knot?,
    rims: Rims,
    smooth: Smooth?,
    waist: Double,
    all: List<IsoPath>?,
): LatheOutline {
    val shape = shapeOf(meridian, projection, smooth)
    val side = if (fill == LatheFill.Auto || fill == LatheFill.Surface) sideOf(shape) else null
    val surface =
        if (side != null && hole == null) surfaceOf(side, frame, projection, rims, smooth?.slope)
        else null
    if (surface != null && (fill == LatheFill.Surface || surface.gap > waist)) {
        return LatheOutline(surface.fill, surface.outline)
    }
    if (fill != LatheFill.Facets) return hullOutline(shape, frame, projection, steps, hole, rims)
    val facets =
        if (all != null) IsoPath.join(all)
        else {
            val paths =
                lathe(
                    meridian,
                    frame,
                    projection,
                    steps,
                    LatheFill.Auto,
                    hole,
                    0.0,
                    rims,
                    smooth,
                    waist,
                )
            IsoPath.join(paths.shades) + paths.top
        }
    return LatheOutline(facets, IsoPath.Empty)
}

private class Tagged(val p: Flat, val ring: Int)

private class Edge(val a: Flat, val b: Flat, val keep: Boolean)

/** The hull of the rings of [shape], dropping the end rings' edges that [rims] doesn't keep. */
private fun hullOutline(
    shape: List<Knot>,
    frame: Frame,
    projection: Projection,
    steps: Int,
    hole: Knot?,
    rims: Rims,
): LatheOutline {
    val most = shape.maxOf { it.r }
    val around = min(320, max(steps, ceil(TAU * most * scaleOf(projection) / 1.5).toInt()))
    fun circle(s: Double, r: Double) =
        List(around) { j -> iso(pointOf(frame, s, r, j.toDouble() / around * TAU), projection) }
    val tagged = shape.flatMapIndexed { index, (s, r) -> circle(s, r).map { Tagged(it, index) } }
    val hull = hullTagged(tagged) { it.p }
    val holePath = if (hole != null) pathOf(circle(hole.s, hole.r), true) else IsoPath.Empty
    val fill = pathOf(hull.map { it.p }, true) + holePath
    if (rims == Rims.All) return LatheOutline(fill, fill)
    val keepFirst = rims == Rims.First || rims == Rims.Ends
    val keepLast = rims == Rims.Last || rims == Rims.Ends
    val lastRing = shape.size - 1
    val edges = hull.mapIndexed { index, a ->
        val b = hull[(index + 1) % hull.size]
        val keep =
            a.ring != b.ring ||
                (a.ring != 0 && a.ring != lastRing) ||
                (keepFirst && a.ring == 0) ||
                (keepLast && a.ring == lastRing)
        Edge(a.p, b.p, keep)
    }
    return LatheOutline(fill, IsoPath.join(chains(edges).map { pathOf(it) }) + holePath)
}

/** The kept edges of a closed polygon as polylines, joined across the wrap. */
private fun chains(edges: List<Edge>): List<List<Flat>> {
    val out = ArrayList<List<Flat>>()
    var current: MutableList<Flat>? = null
    for (edge in edges) {
        if (!edge.keep) {
            current?.let { out.add(it) }
            current = null
            continue
        }
        if (current == null) current = mutableListOf(edge.a)
        current.add(edge.b)
    }
    current?.let { out.add(it) }
    if (out.size > 1 && edges[0].keep && edges.last().keep) {
        val last = out.removeAt(out.size - 1)
        out[0] = last + out[0].drop(1)
    }
    return out
}

private class Surface(val fill: IsoPath, val outline: IsoPath, val gap: Double)

/** A waisted body's fill and outline: both silhouettes joined by the visible end arcs. */
private fun surfaceOf(
    side: List<Knot>,
    frame: Frame,
    projection: Projection,
    rims: Rims,
    slope: ((Double) -> Double)?,
    samples: Int = 96,
): Surface? {
    val view = viewOf(projection)
    val aV = dot3(frame.a, view)
    val facing = atan2(dot3(frame.v, view), dot3(frame.u, view))
    val (left, right) = silhouettePoints(side, frame, projection, samples, slope)
    if (left.size <= samples || right.size <= samples) return null
    fun angleOf(point: Point3): Double {
        val foot = add3(frame.o, mul3(frame.a, dot3(sub3(point, frame.o), frame.a)))
        return angleToward(frame, sub3(point, foot))
    }
    fun arc(s: Double, r: Double, from: Double, to: Double, through: Double): List<Flat> {
        var sweep = wrapAngle(to - from)
        if (wrapAngle(through - from) > sweep) sweep -= TAU
        val count =
            maxOf(
                4.0,
                ceil(abs(sweep) / (TAU / 96)),
                ceil(abs(sweep) * r * scaleOf(projection) / 1.5),
            )
        val n = count.toInt()
        return List(n + 1) { index ->
            iso(pointOf(frame, s, r, from + sweep * index / count), projection)
        }
    }
    val first = side[0]
    val last = side[side.size - 1]
    val bottom = if (-aV > 0) facing + PI else facing
    val top = if (aV > 0) facing + PI else facing
    val leftFlat = left.map { iso(it, projection) }
    val rightFlat = right.map { iso(it, projection) }
    val topArc = arc(last.s, last.r, angleOf(left.last()), angleOf(right.last()), top)
    val bottomArc = arc(first.s, first.r, angleOf(right[0]), angleOf(left[0]), bottom)
    val keepFirst = rims == Rims.All || rims == Rims.First || rims == Rims.Ends
    val keepLast = rims == Rims.All || rims == Rims.Last || rims == Rims.Ends
    val fill = pathOf(leftFlat + topArc + rightFlat.reversed() + bottomArc, true)
    val outline =
        pathOf(leftFlat) +
            pathOf(rightFlat) +
            (if (keepLast) pathOf(topArc) else IsoPath.Empty) +
            (if (keepFirst) pathOf(bottomArc) else IsoPath.Empty)
    val hull =
        hullOf(
            arc(first.s, first.r, 0.0, TAU - 1e-6, PI) +
                arc(last.s, last.r, 0.0, TAU - 1e-6, PI) +
                leftFlat +
                rightFlat
        )
    return Surface(fill, outline, gapOf(leftFlat + rightFlat, hull))
}

/** How far the farthest of [points] lies inside [hull]. */
private fun gapOf(points: List<Flat>, hull: List<Flat>): Double {
    var gap = 0.0
    for (p in points) {
        var best = Double.POSITIVE_INFINITY
        for (index in hull.indices) {
            val a = hull[index]
            val b = hull[(index + 1) % hull.size]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val t =
                max(
                    0.0,
                    min(1.0, ((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy).orOne()),
                )
            best = min(best, jsHypot(p.x - a.x - dx * t, p.y - a.y - dy * t))
        }
        gap = max(gap, best)
    }
    return gap
}

/**
 * The two side edges of the surface of revolution through [profile] (linearly interpolated, or
 * along [radius]), as 3D points: where the surface turns away from the viewer. Pass the analytic
 * [slope] so the edge doesn't kink at every profile point.
 */
fun silhouettePoints(
    profile: List<Knot>,
    frame: Frame,
    projection: Projection,
    samples: Int = 72,
    slope: ((Double) -> Double)? = null,
    radius: ((Double) -> Double)? = null,
): List<List<Point3>> {
    val view = viewOf(projection)
    val aV = dot3(frame.a, view)
    val uV = dot3(frame.u, view)
    val vV = dot3(frame.v, view)
    val facing = atan2(vV, uV)
    val flat = jsHypot(uV, vV).let { if (it == 0.0 || it.isNaN()) 1e-9 else it }
    val s0 = profile[0].s
    val s1 = profile[profile.size - 1].s
    val radiusAt =
        radius
            ?: { s: Double ->
                var index = 1
                while (index < profile.size - 1 && profile[index].s < s) index++
                val (sa, ra) = profile[index - 1]
                val (sb, rb) = profile[index]
                ra + (rb - ra) * (s - sa) / (sb - sa).orOne()
            }
    fun side(sign: Int): List<Point3> {
        val points = ArrayList<Point3>()
        for (step in 0..samples) {
            val s = s0 + (s1 - s0) * step / samples
            val h = ((s1 - s0) / samples / 2).let { if (it == 0.0 || it.isNaN()) 0.01 else it }
            val hi = min(s1, s + h)
            val lo = max(s0, s - h)
            val g = slope?.invoke(s) ?: ((radiusAt(hi) - radiusAt(lo)) / (hi - lo).orOne())
            val c = g * aV / flat
            if (abs(c) > 1) continue
            points.add(pointOf(frame, s, radiusAt(s), facing + sign * acos(c)))
        }
        return points
    }
    return listOf(side(1), side(-1))
}

/** The two side edges of [silhouettePoints] as one path of two polylines. */
fun silhouetteOf(
    profile: List<Knot>,
    frame: Frame,
    projection: Projection,
    samples: Int = 72,
    slope: ((Double) -> Double)? = null,
    radius: ((Double) -> Double)? = null,
): IsoPath =
    IsoPath.join(
        silhouettePoints(profile, frame, projection, samples, slope, radius).map { points ->
            pathOf(points.map { iso(it, projection) })
        }
    )
