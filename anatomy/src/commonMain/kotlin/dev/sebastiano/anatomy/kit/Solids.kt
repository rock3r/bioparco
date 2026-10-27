// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/* Solids: floor outlines raised into shaded prisms, and the plans they are drawn from. */

private fun signedArea(points: List<Flat>): Double {
    var area = 0.0
    for (index in points.indices) {
        val a = points[index]
        val b = points[(index + 1) % points.size]
        area += a.x * b.y - b.x * a.y
    }
    return area / 2
}

internal fun floorArea(points: List<Floor>): Double = signedArea(points.map { Flat(it.x, it.y) })

private fun oriented(points: List<Flat>): List<Flat> =
    if (signedArea(points) < 0) points.reversed() else points

private fun cross(o: Flat, a: Flat, b: Flat): Double =
    (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

internal fun convexHull(points: List<Flat>): List<Flat> {
    val sorted = points.sortedWith(compareBy<Flat> { it.x }.thenBy { it.y })
    if (sorted.size < 3) return sorted
    val lower = ArrayList<Flat>()
    for (point in sorted) {
        while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], point) <= 0) {
            lower.removeAt(lower.size - 1)
        }
        lower.add(point)
    }
    val upper = ArrayList<Flat>()
    for (index in sorted.indices.reversed()) {
        val point = sorted[index]
        while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], point) <= 0) {
            upper.removeAt(upper.size - 1)
        }
        upper.add(point)
    }
    return lower.dropLast(1) + upper.dropLast(1)
}

/** The runs of consecutive edges whose flag is [want], as vertex indices, wrapping around. */
internal fun runsOf(flags: List<Boolean>, want: Boolean): List<List<Int>> {
    val count = flags.size
    val start = flags.indices.indexOfFirst { flags[it] != flags[(it + count - 1) % count] }
    if (start < 0) {
        return if (flags[0] == want) listOf(List(count + 1) { it % count }) else emptyList()
    }
    val runs = ArrayList<List<Int>>()
    var run: MutableList<Int>? = null
    for (step in 0 until count) {
        val index = (start + step) % count
        if (flags[index] == want) {
            if (run == null) run = mutableListOf(index)
            run.add((index + 1) % count)
        } else if (run != null) {
            runs.add(run)
            run = null
        }
    }
    if (run != null) runs.add(run)
    return runs
}

/**
 * A solid's paths, painted in this order: [fill] in paper colour so it hides what is behind,
 * [shades] (the visible sides, darkest first, bucketed by how they face the light), the lighter
 * [top], the [crease] where top meets the visible sides, the optional [bevel], then the [outline].
 * [seamless] marks smooth shading, whose abutting tone sheets are stroked in their own colour.
 */
class SolidPaths(
    val fill: IsoPath,
    val outline: IsoPath,
    val crease: IsoPath,
    val top: IsoPath,
    val shades: List<IsoPath>,
    val bevel: IsoPath? = null,
    val seamless: Boolean = false,
)

/**
 * Any floor outline, raised from [z] by [height]. The sides are shaded in four tones by how they
 * face the light; pass `convex = false` for a concave outline, whose fill is then its faces rather
 * than a hull. [bevel] (0.3–1.6) adds a highlight inset on the top along the visible edges.
 */
fun extrude(
    ring: List<Floor>,
    z: Double,
    height: Double,
    projection: Projection,
    convex: Boolean = true,
    bevel: Double = 0.0,
): SolidPaths {
    val normals = outwardNormals(ring)
    val view = towardViewer(projection)
    val facing = normals.map { it.x * view.x + it.y * view.y > 1e-9 }
    val top = ring.map { iso(Point3(it.x, it.y, z + height), projection) }
    val bottom = ring.map { iso(Point3(it.x, it.y, z), projection) }
    return SolidPaths(
        fill =
            if (convex) pathOf(convexHull(top + bottom), closed = true)
            else facesOf(top, bottom, facing),
        outline = silhouetteOf(top, bottom, facing, height),
        crease = IsoPath.join(runsOf(facing, true).map { run -> pathOf(run.map { top[it] }) }),
        top = pathOf(oriented(top), closed = true),
        shades = shadesOf(normals, facing, top, bottom, height, projection),
        bevel =
            if (bevel > 0) bevelOf(ring, normals, facing, top, z + height, bevel, projection)
            else null,
    )
}

/** Each edge's outward unit normal on the floor. */
private fun outwardNormals(ring: List<Floor>): List<Floor> {
    val counter = floorArea(ring) > 0
    return ring.mapIndexed { index, a ->
        val b = ring[(index + 1) % ring.size]
        val dx = b.x - a.x
        val dy = b.y - a.y
        val length = hypot(dx, dy).takeIf { it != 0.0 } ?: 1.0
        if (counter) Floor(dy / length, -dx / length) else Floor(-dy / length, dx / length)
    }
}

/** The side quad of edge [index], wound the same way as every other fill. */
private fun sideOf(top: List<Flat>, bottom: List<Flat>, index: Int): IsoPath {
    val next = (index + 1) % top.size
    return pathOf(oriented(listOf(top[index], top[next], bottom[next], bottom[index])), true)
}

/** A concave solid's fill: its top, its bottom and every visible side. */
private fun facesOf(top: List<Flat>, bottom: List<Flat>, facing: List<Boolean>): IsoPath =
    IsoPath.join(
        listOf(pathOf(oriented(top), true), pathOf(oriented(bottom), true)) +
            top.indices.filter { facing[it] }.map { sideOf(top, bottom, it) }
    )

/** The far top edges, the near bottom edges, and the verticals where the sides turn away. */
private fun silhouetteOf(
    top: List<Flat>,
    bottom: List<Flat>,
    facing: List<Boolean>,
    height: Double,
): IsoPath {
    val count = top.size
    val outline = ArrayList<IsoPath>()
    for (run in runsOf(facing, false)) outline.add(pathOf(run.map { top[it] }))
    if (height > 0) {
        for (run in runsOf(facing, true)) outline.add(pathOf(run.map { bottom[it] }))
        for (index in 0 until count) {
            if (facing[(index + count - 1) % count] != facing[index]) {
                outline.add(pathOf(listOf(top[index], bottom[index])))
            }
        }
    }
    return IsoPath.join(outline)
}

/** The visible sides, bucketed into [SHADES] tones by how they face the light. */
private fun shadesOf(
    normals: List<Floor>,
    facing: List<Boolean>,
    top: List<Flat>,
    bottom: List<Flat>,
    height: Double,
    projection: Projection,
): List<IsoPath> {
    val buckets = List(SHADES) { ArrayList<IsoPath>() }
    if (height > 0) {
        val c = projection.camera
        val lx = -c.sinA
        val ly = c.cosA
        for (index in top.indices) {
            if (!facing[index]) continue
            val n = normals[index]
            val lit = (n.x * lx + n.y * ly + 1) / 2
            val shade = min(SHADES - 1, max(0, floor(lit * SHADES).toInt()))
            buckets[shade].add(sideOf(top, bottom, index))
        }
    }
    return buckets.map { IsoPath.join(it) }
}

/** A highlight inset by [by] along the visible top edges, running into the silhouette corners. */
private fun bevelOf(
    ring: List<Floor>,
    normals: List<Floor>,
    facing: List<Boolean>,
    top: List<Flat>,
    z: Double,
    by: Double,
    projection: Projection,
): IsoPath {
    val count = ring.size
    val insetTop = ring.mapIndexed { index, p ->
        val a = normals[(index + count - 1) % count]
        val b = normals[index]
        val mx = a.x + b.x
        val my = a.y + b.y
        val length = hypot(mx, my).takeIf { it != 0.0 } ?: 1.0
        iso(Point3(p.x - mx / length * by, p.y - my / length * by, z), projection)
    }
    return IsoPath.join(
        runsOf(facing, true).map { run ->
            pathOf(
                listOf(top[run.first()]) +
                    run.subList(1, run.size - 1).map { insetTop[it] } +
                    top[run.last()]
            )
        }
    )
}

/**
 * A rounded box. [steps] is the points per corner: 2–4 for small parts, 8–10 for large rounded
 * ones. [bevel] (0.3–1.6) adds a highlight line on the top along the visible edges.
 */
fun slabOf(
    plan: Plan,
    z: Double,
    height: Double,
    projection: Projection,
    steps: Int = 8,
    bevel: Double = 0.0,
): SolidPaths = extrude(ringOf(plan, steps), z, height, projection, convex = true, bevel = bevel)

/** A round post, puck, foot, knob or pad: 16–20 steps for small ones, 56–72 for big discs. */
fun cylinder(
    cx: Double,
    cy: Double,
    radius: Double,
    z: Double,
    height: Double,
    projection: Projection,
    steps: Int = 32,
    bevel: Double = 0.0,
): SolidPaths =
    extrude(circleRing(cx, cy, radius, steps), z, height, projection, convex = true, bevel = bevel)

/** A fill path for a soft shadow under a plan. Draw it with `halo`. */
fun haloOf(plan: Plan, z: Double, height: Double, projection: Projection): IsoPath =
    slabOf(plan, z, height, projection).fill
