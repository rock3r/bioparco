// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.SolidPaths
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.dot3
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.pathOf
import dev.sebastiano.anatomy.kit.toneOf
import dev.sebastiano.anatomy.kit.unit3

/** Unlike the kit's convex prism, the cabinet wall follows its concave outline. */
internal fun arcadeSigned(poly: List<Floor>) =
    poly.indices.sumOf { i ->
        val a = poly[i]
        val b = poly[(i + 1) % poly.size]
        a.x * b.y - b.x * a.y
    }

internal fun arcadeRuns(flags: List<Boolean>, want: Boolean): List<List<Int>> {
    val n = flags.size
    val start =
        flags.indices.firstOrNull { flags[it] != flags[(it + n - 1) % n] }
            ?: return if (flags[0] == want) listOf((0..n).map { it % n }) else emptyList()
    val out = ArrayList<List<Int>>()
    var run: MutableList<Int>? = null
    repeat(n) { step ->
        val i = (start + step) % n
        if (flags[i] == want) {
            if (run == null) run = arrayListOf(i)
            run!!.add((i + 1) % n)
        } else if (run != null) {
            out.add(run!!.toList())
            run = null
        }
    }
    run?.let { out.add(it) }
    return out
}

internal fun arcadePrism(f: Frame, poly: List<Floor>, s0: Double, s1: Double): SolidPaths {
    val ring = if (arcadeSigned(poly) > 0) poly else poly.reversed()
    val n = ring.size
    fun world(s: Double, p: Floor): Point3 =
        Point3(
            f.o.x + f.a.x * s + f.u.x * p.x + f.v.x * p.y,
            f.o.y + f.a.y * s + f.u.y * p.x + f.v.y * p.y,
            f.o.z + f.a.z * s + f.u.z * p.x + f.v.z * p.y,
        )
    val capNear = dot3(f.a, Arcade.V) > 0
    val near = ring.map { Arcade.at(world(if (capNear) s1 else s0, it)) }
    val far = ring.map { Arcade.at(world(if (capNear) s0 else s1, it)) }
    val normals =
        ring.indices.map { i ->
            val a = ring[i]
            val b = ring[(i + 1) % n]
            unit3(add3(mul3(f.u, b.y - a.y), mul3(f.v, -(b.x - a.x))))
        }
    val facing = normals.map { dot3(it, Arcade.V) > 1e-9 }
    val outline = ArrayList<IsoPath>()
    val crease = ArrayList<IsoPath>()
    for (run in arcadeRuns(facing, false)) outline.add(pathOf(run.map { near[it] }))
    for (run in arcadeRuns(facing, true)) {
        crease.add(pathOf(run.map { near[it] }))
        outline.add(pathOf(run.map { far[it] }))
    }
    for (i in 0 until n) if (facing[(i + n - 1) % n] != facing[i])
        outline.add(pathOf(listOf(near[i], far[i])))
    val parts = arrayListOf(orientedPath(near), orientedPath(far))
    val buckets = List(5) { ArrayList<IsoPath>() }
    for (i in 0 until n) if (facing[i]) {
        val quad = orientedPath(listOf(near[i], near[(i + 1) % n], far[(i + 1) % n], far[i]))
        parts.add(quad)
        buckets[toneOf(normals[i], Arcade.P)].add(quad)
    }
    buckets[toneOf(if (capNear) f.a else mul3(f.a, -1.0), Arcade.P)].add(orientedPath(near))
    return SolidPaths(
        IsoPath.join(parts),
        IsoPath.join(outline),
        IsoPath.join(crease),
        IsoPath.join(buckets[4]),
        buckets.take(4).map { IsoPath.join(it) },
    )
}

private fun orientedPath(points: List<Flat>): IsoPath {
    val signed =
        points.indices.sumOf { i ->
            val a = points[i]
            val b = points[(i + 1) % points.size]
            a.x * b.y - b.x * a.y
        }
    return pathOf(if (signed < 0) points.reversed() else points, true)
}
