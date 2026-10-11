// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/* The round solids that are not turned profiles: saddle pads, prisms on a frame, balls and rods. */

/**
 * A saddle pad: a cylinder of radius [a] along the frame's axis from a curved host surface to a
 * flat face at [s1]. [start] gives where each generator leaves the host, by angle.
 */
fun saddleOf(
    frame: Frame,
    a: Double,
    start: (Double) -> Double,
    s1: Double,
    projection: Projection,
    steps: Int = stepsFor(a, 24),
): SolidPaths {
    val view = viewOf(projection)
    val light = lightOf(projection)
    val n = steps
    fun angle(j: Double) = j / n * TAU
    val low =
        List(n) { j ->
            iso(pointOf(frame, start(angle(j.toDouble())), a, angle(j.toDouble())), projection)
        }
    val high = List(n) { j -> iso(pointOf(frame, s1, a, angle(j.toDouble())), projection) }
    val normals = List(n) { j -> radialOf(frame, angle(j + 0.5)) }
    val seen = normals.map { dot3(it, view) > 1e-6 }
    val keys = normals.mapIndexed { j, normal ->
        if (seen[j]) bandOf(dot3(normal, light)) else null
    }
    val buckets = List(SHADES + 1) { ArrayList<IsoPath>() }
    val fills = ArrayList<IsoPath>()
    for (group in groupsOf(keys)) {
        val d =
            pathOf(windOf(group.run.map { low[it] } + group.run.map { high[it] }.reversed()), true)
        buckets[group.key].add(d)
        fills.add(d)
    }
    val capSeen = dot3(frame.a, view) > 1e-6
    if (capSeen) {
        val d = pathOf(windOf(high), true)
        buckets[bandOf(dot3(frame.a, light))].add(d)
        fills.add(d)
    }
    val front = runsOf(seen)
    val back = runsOf(seen.map { !it })
    val edges = ArrayList<IsoPath>()
    for (run in front) {
        edges.add(pathOf(run.map { low[it] }))
        edges.add(pathOf(listOf(low[run.first()], high[run.first()])))
        edges.add(pathOf(listOf(low[run.last()], high[run.last()])))
    }
    for (run in if (capSeen) back else front) edges.add(pathOf(run.map { high[it] }))
    return SolidPaths(
        fill = IsoPath.join(fills),
        outline = IsoPath.join(edges),
        crease =
            if (capSeen) IsoPath.join(front.map { run -> pathOf(run.map { high[it] }) })
            else IsoPath.Empty,
        top = IsoPath.join(buckets[SHADES]),
        shades = (0 until SHADES).map { IsoPath.join(buckets[it]) },
        seamless = true,
    )
}

/** A saddle pad on a host cylinder of radius [host] perpendicular to the frame's axis. */
fun saddleOf(
    frame: Frame,
    a: Double,
    host: Double,
    s1: Double,
    projection: Projection,
    steps: Int = stepsFor(a, 24),
): SolidPaths = saddleOf(frame, a, { saddleStart(host, a, it) }, s1, projection, steps)

/**
 * A prism of any convex [polygon] in the frame's `u`, `v` plane (`Floor(x, y)` is `x·u + y·v`),
 * extruded along the axis from [s0] to [s1], with facet tones and creases only at sharp corners.
 */
fun prismOf(
    frame: Frame,
    polygon: List<Floor>,
    s0: Double,
    s1: Double,
    projection: Projection,
): SolidPaths {
    val view = viewOf(projection)
    val light = lightOf(projection)
    val ring = windOf(polygon.map { Flat(it.x, it.y) })
    val n = ring.size
    val f = frame
    fun at(s: Double, p: Flat) =
        Point3(
            f.o.x + f.a.x * s + f.u.x * p.x + f.v.x * p.y,
            f.o.y + f.a.y * s + f.u.y * p.x + f.v.y * p.y,
            f.o.z + f.a.z * s + f.u.z * p.x + f.v.z * p.y,
        )
    val low = ring.map { iso(at(s0, it), projection) }
    val high = ring.map { iso(at(s1, it), projection) }
    val buckets = List(SHADES + 1) { ArrayList<IsoPath>() }
    val seen = BooleanArray(n)
    for (j in 0 until n) {
        val p0 = ring[j]
        val p1 = ring[(j + 1) % n]
        val normal = unit3(add3(mul3(f.u, p1.y - p0.y), mul3(f.v, -(p1.x - p0.x))))
        seen[j] = dot3(normal, view) > 1e-6
        if (seen[j]) {
            val quad = listOf(low[j], low[(j + 1) % n], high[(j + 1) % n], high[j])
            buckets[bandOf(dot3(normal, light))].add(pathOf(quad, true))
        }
    }
    val ends = listOf(low to mul3(f.a, -1.0), high to f.a)
    val capSeen = ends.map { (_, normal) -> dot3(normal, view) > 1e-6 }
    ends.forEachIndexed { index, (points, normal) ->
        if (capSeen[index]) buckets[bandOf(dot3(normal, light))].add(pathOf(points, true))
    }
    val hull = hullOf(low + high)
    val creases = ArrayList<IsoPath>()
    for (j in 0 until n) {
        val prev = (j - 1 + n) % n
        val next = ring[(j + 1) % n]
        val ax = ring[j].x - ring[prev].x
        val ay = ring[j].y - ring[prev].y
        val bx = next.x - ring[j].x
        val by = next.y - ring[j].y
        val turn = (ax * bx + ay * by) / (jsHypot(ax, ay) * jsHypot(bx, by)).orOne()
        if (turn < cos(0.35) && seen[j] && seen[prev]) creases.add(pathOf(listOf(low[j], high[j])))
        ends.forEachIndexed { index, (points, _) ->
            if (capSeen[index] && seen[j])
                creases.add(pathOf(listOf(points[j], points[(j + 1) % n])))
        }
    }
    return SolidPaths(
        fill = pathOf(hull, true),
        outline = pathOf(hull, true),
        crease = IsoPath.join(creases),
        top = IsoPath.join(buckets[SHADES]),
        shades = (0 until SHADES).map { IsoPath.join(buckets[it]) },
    )
}

/** The hull of two circles of radius [r0] at 0 and [r1] at [offset]: a plate joining two parts. */
fun stadium(r0: Double, r1: Double, offset: Double, steps: Int = 32): List<Floor> {
    fun circle(cx: Double, r: Double) =
        List(steps) { index ->
            val angle = index.toDouble() / steps * TAU
            Flat(cx + r * cos(angle), r * sin(angle))
        }
    return hullOf(circle(0.0, r0) + circle(offset, r1)).map { Floor(it.x, it.y) }
}

/**
 * A ball with exact smooth tones: each tone region is the projected hull of a spherical cap. Use it
 * for rod-end bearings, tee fittings and knobs.
 */
fun sphereOf(centre: Point3, r: Double, projection: Projection, steps: Int = 48): SolidPaths {
    val view = viewOf(projection)
    val light = lightOf(projection)
    val frame = frameAlong(centre, view)
    val rim = List(steps) { index -> pointOf(frame, 0.0, r, index.toDouble() / steps * TAU) }
    val outline = pathOf(rim.map { iso(it, projection) }, true)
    fun capOf(threshold: Double): IsoPath {
        val points =
            rim.filter { dot3(sub3(it, centre), light) / r >= threshold }
                .mapTo(ArrayList()) { iso(it, projection) }
        val c = dot3(light, view)
        val capFrame = frameAlong(add3(centre, mul3(light, r * threshold)), light)
        val radius = r * sqrt(max(0.0, 1 - threshold * threshold))
        for (index in 0 until steps) {
            val p = pointOf(capFrame, 0.0, radius, index.toDouble() / steps * TAU)
            if (dot3(sub3(p, centre), view) >= -1e-9) points.add(iso(p, projection))
        }
        if (c > threshold) points.add(iso(add3(centre, mul3(view, r)), projection))
        return if (points.size > 2) pathOf(hullOf(points), true) else IsoPath.Empty
    }
    val shades = listOf(outline, capOf(BANDS[1]), capOf(BANDS[2]), capOf(BANDS[3]))
    return SolidPaths(
        fill = outline,
        outline = outline,
        crease = IsoPath.Empty,
        top = IsoPath.Empty,
        shades = shades,
        seamless = true,
    )
}

/**
 * A rod that slides out of a gland: [paths] is one rod from `from − stroke` to [to], [clip] a path
 * covering it from the gland face to [reach]. Draw [paths] in a group clipped to [clip], held still
 * while the group moves (`ArtBuilder.group(part, clip = rod.clip)`).
 */
class Rod(
    val paths: SolidPaths,
    val clip: IsoPath,
    val from: Double,
    val to: Double,
    val stroke: Double,
    val reach: Double,
)

/**
 * A rod that slides out of a gland at `s = from` (the frame's axis points out of it), its tip at
 * [to] at rest, travelling up to [stroke] further out, or the same rod retracting into it.
 */
fun rodOut(
    frame: Frame,
    from: Double,
    to: Double,
    r: Double,
    projection: Projection,
    stroke: Double = 0.0,
    steps: Int = stepsFor(r, 32, 160, scaleOf(projection)),
    fill: LatheFill = LatheFill.Auto,
    hole: Knot? = null,
    bevel: Double = 0.0,
    rims: Rims = Rims.All,
    smooth: Smooth? = null,
    waist: Double = 0.5,
): Rod {
    val pad = 0.35 / scaleOf(projection)
    fun ring(s: Double) =
        List(48) { index ->
            iso(pointOf(frame, s, r + pad, index.toDouble() / 48 * TAU), projection)
        }
    return Rod(
        paths =
            disc(
                from - stroke,
                to,
                r,
                frame,
                projection,
                steps,
                fill,
                hole,
                bevel,
                rims,
                smooth,
                waist,
            ),
        clip = pathOf(hullOf(ring(from) + ring(to + stroke)), true),
        from = from,
        to = to,
        stroke = stroke,
        reach = to + stroke,
    )
}
