// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.audit

import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Knot
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.dot3
import dev.sebastiano.anatomy.kit.jsHypot
import dev.sebastiano.anatomy.kit.len3
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.sub3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

/* Signed distances (negative inside) and world bounds of the recorded shapes. */

internal const val BIG = 1e9

/** The `i`th coordinate, as the original indexes its `[x, y, z]` arrays. */
internal operator fun Point3.get(i: Int): Double =
    when (i) {
        0 -> x
        1 -> y
        else -> z
    }

internal fun point3(f: (Int) -> Double): Point3 = Point3(f(0), f(1), f(2))

/** The signed distance from a turned `(s, r)` polygon; edges on the axis are not surface. */
internal fun polyDistance(poly: List<Knot>, s: Double, r: Double): Double {
    var best = BIG
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val (s0, r0) = poly[j]
        val (s1, r1) = poly[i]
        val ds = s1 - s0
        val dr = r1 - r0
        val span = ds * ds + dr * dr
        val t = if (span > 0) max(0.0, min(1.0, ((s - s0) * ds + (r - r0) * dr) / span)) else 0.0
        val es = s - s0 - ds * t
        val er = r - r0 - dr * t
        val d = es * es + er * er
        if (d < best && (r0 > 1e-9 || r1 > 1e-9)) best = d
        if ((r0 > r) != (r1 > r) && s < s0 + ((r - r0) * ds) / (if (dr == 0.0) 1e-12 else dr)) {
            inside = !inside
        }
        j = i
    }
    val d = sqrt(best)
    return if (inside) -d else d
}

/** The signed distance from a flat polygon. */
internal fun polygonDistance(poly: List<Floor>, x: Double, y: Double): Double {
    var best = BIG
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val (x0, y0) = poly[j]
        val (x1, y1) = poly[i]
        val dx = x1 - x0
        val dy = y1 - y0
        val span = dx * dx + dy * dy
        val t = if (span > 0) max(0.0, min(1.0, ((x - x0) * dx + (y - y0) * dy) / span)) else 0.0
        val ex = x - x0 - dx * t
        val ey = y - y0 - dy * t
        val d = ex * ex + ey * ey
        if (d < best) best = d
        if ((y0 > y) != (y1 > y) && x < x0 + ((y - y0) * dx) / (if (dy == 0.0) 1e-12 else dy)) {
            inside = !inside
        }
        j = i
    }
    val d = sqrt(best)
    return if (inside) -d else d
}

/** The distance from a capped cylinder from [a] to [b], tapering from [r] to [endRadius]. */
internal fun cappedSegment(
    p: Point3,
    a: Point3,
    b: Point3,
    r: Double,
    endRadius: Double = r,
): Double {
    val bx = b.x - a.x
    val by = b.y - a.y
    val bz = b.z - a.z
    val px = p.x - a.x
    val py = p.y - a.y
    val pz = p.z - a.z
    val baba = (bx * bx + by * by + bz * bz).let { if (it == 0.0 || it.isNaN()) 1e-12 else it }
    val paba = px * bx + py * by + pz * bz
    if (endRadius != r) {
        val length = sqrt(baba)
        val along = paba / length
        val radial = jsHypot(px - bx * paba / baba, py - by * paba / baba, pz - bz * paba / baba)
        val poly = listOf(Knot(0.0, 0.0), Knot(0.0, r), Knot(length, endRadius), Knot(length, 0.0))
        return polyDistance(poly, along, radial)
    }
    val x = jsHypot(px * baba - bx * paba, py * baba - by * paba, pz * baba - bz * paba) - r * baba
    val y = abs(paba - baba * 0.5) - baba * 0.5
    val x2 = x * x
    val y2 = y * y * baba
    val d = if (max(x, y) < 0) -min(x2, y2) else (if (x > 0) x2 else 0.0) + (if (y > 0) y2 else 0.0)
    return (sign(d) * sqrt(abs(d))) / baba
}

/** The distance from [p] to the segment [a]–[b], and where along it the nearest point is. */
internal class SegmentPoint(val d: Double, val t: Double)

internal fun segmentPoint(p: Point3, a: Point3, b: Point3): SegmentPoint {
    val ba = sub3(b, a)
    val pa = sub3(p, a)
    val t = max(0.0, min(1.0, dot3(pa, ba) / dot3(ba, ba).let { if (it == 0.0) 1e-12 else it }))
    return SegmentPoint(len3(sub3(pa, mul3(ba, t))), t)
}

/** The signed distance from [shape] at [p]: negative inside. */
fun sdf(shape: AuditShape, p: Point3): Double {
    val d = baseSdf(shape, p)
    return if (shape.meta.cut.isNotEmpty()) max(d, -cutDistance(shape, p)) else d
}

internal fun cutDistance(shape: AuditShape, p: Point3): Double {
    var best = BIG
    for (cut in shape.meta.cut) best = min(best, sdf(cut, p))
    return best
}

private fun baseSdf(shape: AuditShape, p: Point3): Double =
    when (shape) {
        is BodyShape -> {
            val f = shape.frame
            val q = sub3(p, f.o)
            polyDistance(shape.poly, dot3(q, f.a), jsHypot(dot3(q, f.u), dot3(q, f.v)))
        }
        is BallShape -> {
            var d = len3(sub3(p, shape.o)) - shape.r
            for ((n, cut) in shape.flats) {
                d =
                    max(
                        d,
                        n.x * (p.x - shape.o.x) +
                            n.y * (p.y - shape.o.y) +
                            n.z * (p.z - shape.o.z) - cut,
                    )
            }
            d
        }
        is PrismShape -> {
            val f = shape.frame
            val q = sub3(p, f.o)
            val s = dot3(q, f.a)
            val flat = polygonDistance(shape.poly, dot3(q, f.u), dot3(q, f.v))
            val along = max(shape.s0 - s, s - shape.s1)
            jsHypot(max(flat, 0.0), max(along, 0.0)) + min(max(flat, along), 0.0)
        }
        is BoxShape -> {
            val q = sub3(p, shape.o)
            val d = DoubleArray(3) { abs(dot3(q, shape.axes[it])) - shape.half[it] }
            val outside = jsHypot(max(d[0], 0.0), max(d[1], 0.0), max(d[2], 0.0))
            outside + min(max(max(d[0], d[1]), d[2]), 0.0)
        }
        is TubeShape -> tubeSdf(shape, p)
    }

private fun tubeSdf(shape: TubeShape, p: Point3): Double {
    val points = shape.points
    val radii = shape.radii
    var best = BIG
    val last = points.size - 1
    for (index in 0 until last) {
        val d = cappedSegment(p, points[index], points[index + 1], radii[index], radii[index + 1])
        if (d < best) best = d
    }
    if (shape.round.first) best = min(best, len3(sub3(p, points[0])) - radii[0])
    if (shape.round.second) best = min(best, len3(sub3(p, points[last])) - radii[last])
    return best
}

/** The world bounds of [shape]. */
fun boundsOf(shape: AuditShape): Bounds = shape.bounds

internal fun boundsFor(shape: AuditShape): Bounds =
    when (shape) {
        is BodyShape -> {
            val f = shape.frame
            val s0 = shape.poly.minOf { it.s }
            val s1 = shape.poly.maxOf { it.s }
            val rm = shape.poly.maxOf { it.r }
            val a = add3(f.o, mul3(f.a, s0))
            val b = add3(f.o, mul3(f.a, s1))
            val spread = point3 { rm * sqrt(max(0.0, 1 - f.a[it] * f.a[it])) }
            Bounds(
                point3 { min(a[it], b[it]) - spread[it] },
                point3 { max(a[it], b[it]) + spread[it] },
            )
        }
        is BallShape -> Bounds(point3 { shape.o[it] - shape.r }, point3 { shape.o[it] + shape.r })
        is PrismShape -> prismBounds(shape)
        is BoxShape -> {
            val extent = point3 { i ->
                var sum = 0.0
                shape.axes.forEachIndexed { j, axis -> sum += abs(axis[i]) * shape.half[j] }
                sum
            }
            Bounds(point3 { shape.o[it] - extent[it] }, point3 { shape.o[it] + extent[it] })
        }
        is TubeShape -> {
            val min = doubleArrayOf(BIG, BIG, BIG)
            val max = doubleArrayOf(-BIG, -BIG, -BIG)
            shape.points.forEachIndexed { index, p ->
                for (i in 0 until 3) {
                    min[i] = min(min[i], p[i] - shape.radii[index])
                    max[i] = max(max[i], p[i] + shape.radii[index])
                }
            }
            Bounds(point3 { min[it] }, point3 { max[it] })
        }
    }

private fun prismBounds(shape: PrismShape): Bounds {
    val f = shape.frame
    val min = doubleArrayOf(BIG, BIG, BIG)
    val max = doubleArrayOf(-BIG, -BIG, -BIG)
    for (s in doubleArrayOf(shape.s0, shape.s1)) {
        for ((x, y) in shape.poly) {
            val p = add3(add3(f.o, mul3(f.a, s)), add3(mul3(f.u, x), mul3(f.v, y)))
            for (i in 0 until 3) {
                min[i] = min(min[i], p[i])
                max[i] = max(max[i], p[i])
            }
        }
    }
    return Bounds(point3 { min[it] }, point3 { max[it] })
}

/** True when [a] and [b] overlap with [pad] to spare. */
internal fun boundsMeet(a: Bounds, b: Bounds, pad: Double): Boolean =
    (0 until 3).all { a.min[it] - pad <= b.max[it] && b.min[it] - pad <= a.max[it] }

/** True when the point [p] is within [pad] of [b]. */
internal fun pointNear(p: Point3, b: Bounds, pad: Double): Boolean =
    boundsMeet(Bounds(p, p), b, pad)
