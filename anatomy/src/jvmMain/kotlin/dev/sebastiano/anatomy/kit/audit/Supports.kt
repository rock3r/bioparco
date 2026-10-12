// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.audit

import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.cross3
import dev.sebastiano.anatomy.kit.jsHypot
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.unit3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Support: a contact graph of solids and rigid tubes from points sampled on every surface, so a
 * part that touches nothing leading to the stand, or one sunk into its neighbour, is found.
 */

/** Points on [shape]'s surface about [spacing] apart, outside its cuts. */
internal fun surfaceOf(shape: AuditShape, spacing: Double = 0.6): List<Point3> {
    val points =
        when (shape) {
            is BodyShape -> bodySurface(shape, spacing)
            is BoxShape -> boxSurface(shape, spacing)
            is PrismShape -> prismSurface(shape, spacing)
            is BallShape -> ballSurface(shape, spacing)
            is TubeShape ->
                samplesOf(shape, spacing).flatMap { (p, r) ->
                    List(8) { k -> add3(p, mul3(Point3(cos(k * PI / 4), sin(k * PI / 4), 0.0), r)) }
                }
        }
    return if (shape.meta.cut.isNotEmpty()) points.filter { cutDistance(shape, it) >= -1e-3 }
    else points
}

private operator fun TubeSample.component1(): Point3 = p

private operator fun TubeSample.component2(): Double = r

private fun bodySurface(shape: BodyShape, spacing: Double): List<Point3> {
    val points = ArrayList<Point3>()
    val f = shape.frame
    val poly = shape.poly
    for (i in 0 until poly.size - 1) {
        val (s0, r0) = poly[i]
        val (s1, r1) = poly[i + 1]
        val steps = max(1.0, ceil(jsHypot(s1 - s0, r1 - r0) / spacing)).toInt()
        for (j in 0..steps) {
            val s = s0 + ((s1 - s0) * j) / steps
            val r = r0 + ((r1 - r0) * j) / steps
            val around = max(1.0, min(160.0, ceil((2 * PI * r) / spacing))).toInt()
            for (k in 0 until around) {
                val t = (k.toDouble() / around) * 2 * PI
                points.add(
                    add3(
                        add3(f.o, mul3(f.a, s)),
                        add3(mul3(f.u, r * cos(t)), mul3(f.v, r * sin(t))),
                    )
                )
            }
        }
    }
    return points
}

private fun boxSurface(shape: BoxShape, spacing: Double): List<Point3> {
    val points = ArrayList<Point3>()
    val half = shape.half
    for (axis in 0 until 3) {
        for (sign in intArrayOf(-1, 1)) {
            val o1 = (axis + 1) % 3
            val o2 = (axis + 2) % 3
            val n1 = max(1.0, ceil((2 * half[o1]) / spacing)).toInt()
            val n2 = max(1.0, ceil((2 * half[o2]) / spacing)).toInt()
            for (i in 0..n1) {
                for (j in 0..n2) {
                    val c = DoubleArray(3)
                    c[axis] = sign * half[axis]
                    c[o1] = -half[o1] + (2 * half[o1] * i) / n1
                    c[o2] = -half[o2] + (2 * half[o2] * j) / n2
                    val local =
                        add3(
                            add3(mul3(shape.axes[0], c[0]), mul3(shape.axes[1], c[1])),
                            mul3(shape.axes[2], c[2]),
                        )
                    points.add(add3(shape.o, local))
                }
            }
        }
    }
    return points
}

private fun prismSurface(shape: PrismShape, spacing: Double): List<Point3> {
    val points = ArrayList<Point3>()
    val f = shape.frame
    val poly = shape.poly
    fun at(s: Double, x: Double, y: Double) =
        add3(add3(f.o, mul3(f.a, s)), add3(mul3(f.u, x), mul3(f.v, y)))
    val rows = max(1.0, ceil((shape.s1 - shape.s0) / spacing)).toInt()
    var j = poly.size - 1
    for (i in poly.indices) {
        val (x0, y0) = poly[j]
        val (x1, y1) = poly[i]
        val steps = max(1.0, ceil(jsHypot(x1 - x0, y1 - y0) / spacing)).toInt()
        for (k in 0 until steps) {
            for (row in 0..rows) {
                points.add(
                    at(
                        shape.s0 + ((shape.s1 - shape.s0) * row) / rows,
                        x0 + ((x1 - x0) * k) / steps,
                        y0 + ((y1 - y0) * k) / steps,
                    )
                )
            }
        }
        j = i
    }
    val maxX = poly.maxOf { it.x }
    val maxY = poly.maxOf { it.y }
    var x = poly.minOf { it.x }
    while (x <= maxX) {
        var y = poly.minOf { it.y }
        while (y <= maxY) {
            if (polygonDistance(poly, x, y) < 0) {
                points.add(at(shape.s0, x, y))
                points.add(at(shape.s1, x, y))
            }
            y += spacing
        }
        x += spacing
    }
    return points
}

private fun ballSurface(shape: BallShape, spacing: Double): List<Point3> {
    val count =
        max(24.0, min(600.0, ceil((4 * PI * shape.r * shape.r) / (spacing * spacing)))).toInt()
    val sphere =
        List(count) { i ->
            val z = 1 - (2 * (i + 0.5)) / count
            val ring = sqrt(1 - z * z)
            val t = i * 2.399963
            add3(shape.o, mul3(Point3(ring * cos(t), ring * sin(t), z), shape.r))
        }
    if (shape.flats.isEmpty()) return sphere
    fun keeps(p: Point3, slack: Double) =
        shape.flats.all { (n, cut) ->
            n.x * (p.x - shape.o.x) + n.y * (p.y - shape.o.y) + n.z * (p.z - shape.o.z) <=
                cut + slack
        }
    val points = ArrayList(sphere.filter { keeps(it, 1e-9) })
    for ((n, cut) in shape.flats) {
        val helper = if (abs(n.z) > 0.9) Point3(1.0, 0.0, 0.0) else Point3(0.0, 0.0, 1.0)
        val u = unit3(cross3(helper, n))
        val v = cross3(n, u)
        val rho = sqrt(max(0.0, shape.r * shape.r - cut * cut))
        val centre = add3(shape.o, mul3(n, cut))
        var x = -rho
        while (x <= rho) {
            var y = -rho
            while (y <= rho) {
                val p = add3(centre, add3(mul3(u, x), mul3(v, y)))
                if (x * x + y * y <= rho * rho && keeps(p, 1e-6)) points.add(p)
                y += spacing
            }
            x += spacing
        }
    }
    return points
}

/** True when [shape] is seated on [host] by design: [host] is named in its cut. */
internal fun seatedOn(shape: AuditShape, host: AuditShape): Boolean =
    shape.meta.seated &&
        shape.meta.cut.any { it === host || (it.name != null && it.name == host.name) }

/** The default ground: a shape marked `ground`, or named `stand.…`. */
fun grounded(shape: AuditShape): Boolean =
    shape.meta.ground || (shape.name ?: "").startsWith("stand.")

/** Two solids sunk [depth] into each other. */
class SunkPair(val a: String?, val b: String?, val depth: Double)

/** Solids and rigid tubes that touch nothing leading to the ground, and pairs sunk too deep. */
class Support(val loose: List<String?>, val hanging: List<String?>, val sunk: List<SunkPair>)

/**
 * Builds a contact graph of [solids] and rigid [tubes] (within [touch]) and lists every solid not
 * connected to the [ground], every rigid tube that isn't, and every pair of solids sunk more than
 * [sunk] into each other unless they share an owner or one is seated on the other.
 */
fun supports(
    solids: List<AuditShape>,
    tubes: List<TubeShape>,
    ground: (AuditShape) -> Boolean = ::grounded,
    touch: Double = 0.15,
    sunk: Double = 0.5,
): Support {
    val nodes = solids + tubes.filter { !it.meta.limp }
    val surfaces = nodes.map { surfaceOf(it) }
    val links = List(nodes.size) { ArrayList<Int>() }
    val deep = ArrayList<SunkPair>()
    for (a in nodes.indices) {
        for (b in a + 1 until nodes.size) {
            if (!boundsMeet(nodes[a].bounds, nodes[b].bounds, touch + 0.05)) continue
            val gap = nearestTo(nodes[a], surfaces[b], nearestTo(nodes[b], surfaces[a], BIG))
            if (gap <= touch) {
                links[a].add(b)
                links[b].add(a)
            }
            if (sunkPair(nodes[a], nodes[b], gap, sunk))
                deep.add(SunkPair(nodes[a].name, nodes[b].name, -gap))
        }
    }
    val held = BooleanArray(nodes.size)
    val queue = ArrayList<Int>()
    nodes.forEachIndexed { index, shape ->
        if (ground(shape)) {
            held[index] = true
            queue.add(index)
        }
    }
    while (queue.isNotEmpty()) {
        val index = queue.removeAt(queue.size - 1)
        for (next in links[index]) {
            if (!held[next]) {
                held[next] = true
                queue.add(next)
            }
        }
    }
    val loose = nodes.indices.filter { !held[it] && nodes[it] !is TubeShape }.map { nodes[it].name }
    val hanging =
        nodes.indices.filter { !held[it] && nodes[it] is TubeShape }.map { nodes[it].name }
    return Support(loose, hanging, deep.sortedByDescending { it.depth })
}

/** The least of [start] and [shape]'s distance at each of [points]. */
private fun nearestTo(shape: AuditShape, points: List<Point3>, start: Double): Double {
    var gap = start
    for (p in points) gap = min(gap, sdf(shape, p))
    return gap
}

private fun sunkPair(a: AuditShape, b: AuditShape, gap: Double, sunk: Double): Boolean {
    val same =
        (a.owner != null && (a.owner == b.owner || a.owner == b.name)) ||
            (b.owner != null && b.owner == a.name)
    val solidPair = a !is TubeShape && b !is TubeShape
    return solidPair && !same && gap < -sunk && !seatedOn(a, b) && !seatedOn(b, a)
}
