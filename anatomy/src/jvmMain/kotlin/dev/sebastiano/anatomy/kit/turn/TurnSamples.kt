// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.audit.AuditShape
import dev.sebastiano.anatomy.kit.audit.BallShape
import dev.sebastiano.anatomy.kit.audit.BodyShape
import dev.sebastiano.anatomy.kit.audit.BoxShape
import dev.sebastiano.anatomy.kit.audit.PrismShape
import dev.sebastiano.anatomy.kit.audit.TubeShape
import dev.sebastiano.anatomy.kit.cross3
import dev.sebastiano.anatomy.kit.dot3
import dev.sebastiano.anatomy.kit.frameAlong
import dev.sebastiano.anatomy.kit.jsHypot
import dev.sebastiano.anatomy.kit.jsRound
import dev.sebastiano.anatomy.kit.len3
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.pointOf
import dev.sebastiano.anatomy.kit.sub3
import dev.sebastiano.anatomy.kit.unit3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * The build's geometry helpers from `kit/turn-build.mjs`: sample clouds of recorded shapes
 * (`sampleShape`), separating planes between clouds (`marginOf`, `closest`, `quickSeparate`,
 * `separate`), and polygon helpers (`roundedPolygon`, `signedArea`, `convexOrThrow`).
 */

internal const val TAU = PI * 2

/** A plane `n·p = d` between two clouds and its margin (negative: they interpenetrate). */
class Plane(val n: Point3, val d: Double, val margin: Double)

internal fun signedArea(points: List<Floor>): Double {
    var s = 0.0
    for (i in points.indices) {
        val a = points[i]
        val b = points[(i + 1) % points.size]
        s += a.x * b.y - b.x * a.y
    }
    return s / 2
}

internal fun convexOrThrow(points: List<Floor>, name: String) {
    val n = points.size
    var sign = 0.0
    for (i in 0 until n) {
        val a = points[i]
        val b = points[(i + 1) % n]
        val c = points[(i + 2) % n]
        val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        if (abs(cross) < 1e-9) continue
        if (sign == 0.0) sign = kotlin.math.sign(cross)
        else if (kotlin.math.sign(cross) != sign) {
            error("turning: part $name is not convex; split it into convex pieces")
        }
    }
}

/** [polygon], counter-clockwise, with its corners rounded by [radius] in [steps] steps. */
fun roundedPolygon(polygon: List<Floor>, radius: Double = 0.0, steps: Int = 4): List<Floor> {
    val poly = if (signedArea(polygon) < 0) polygon.reversed() else polygon.toList()
    if (!(radius > 0)) return poly
    val n = poly.size
    val normals =
        poly.indices.map { i ->
            val a = poly[i]
            val b = poly[(i + 1) % n]
            val l = jsHypot(b.x - a.x, b.y - a.y).let { if (it == 0.0) 1.0 else it }
            Floor((b.y - a.y) / l, -(b.x - a.x) / l)
        }
    val out = ArrayList<Floor>()
    for (i in 0 until n) {
        val na = normals[(i - 1 + n) % n]
        val nb = normals[i]
        val denom = 1 + na.x * nb.x + na.y * nb.y
        val c =
            Floor(
                poly[i].x - (radius * (na.x + nb.x)) / denom,
                poly[i].y - (radius * (na.y + nb.y)) / denom,
            )
        val a0 = jsAtan2(na.y, na.x)
        var a1 = jsAtan2(nb.y, nb.x)
        while (a1 < a0 - 1e-9) a1 += TAU
        for (s in 0..steps) {
            val t = a0 + ((a1 - a0) * s) / steps
            out.add(Floor(c.x + radius * jsCos(t), c.y + radius * jsSin(t)))
        }
    }
    return out
}

/** The original's `frameFrom`: [f] made orthonormal round its axis. */
internal fun frameFrom(f: Frame): Frame {
    val a = unit3(f.a)
    var u: Point3 = unit3(sub3(f.u, mul3(a, dot3(f.u, a))))
    if (len3(u) < 1e-9) u = frameAlong(Point3(0.0, 0.0, 0.0), a).u
    val v = if (abs(dot3(cross3(a, u), f.v)) > 0.999) unit3(f.v) else cross3(a, u)
    return Frame(f.o, a, u, v)
}

/** Sample points on a recorded shape's surface, the original's `sampleShape`. */
fun sampleShape(shape: AuditShape, count: Int = 48): List<Point3> {
    val out = ArrayList<Point3>()
    when (shape) {
        is BodyShape -> {
            val f = frameFrom(shape.frame)
            for (knot in shape.poly) {
                if (knot.r < 1e-9) {
                    out.add(pointOf(f, knot.s, 0.0, 0.0))
                    continue
                }
                for (j in 0 until count) out.add(
                    pointOf(f, knot.s, knot.r, (j.toDouble() / count) * TAU)
                )
            }
        }
        is PrismShape -> {
            val f = shape.frame
            for (s in doubleArrayOf(shape.s0, shape.s1)) {
                for (p in shape.poly) out.add(
                    add3(add3(f.o, mul3(f.a, s)), add3(mul3(f.u, p.x), mul3(f.v, p.y)))
                )
            }
        }
        is BallShape -> ballSamples(shape, count, out)
        is BoxShape -> {
            for (sx in doubleArrayOf(-1.0, 1.0)) for (sy in doubleArrayOf(-1.0, 1.0)) for (sz in
                doubleArrayOf(-1.0, 1.0)) {
                out.add(
                    add3(
                        shape.o,
                        add3(
                            add3(
                                mul3(shape.axes[0], sx * shape.half[0]),
                                mul3(shape.axes[1], sy * shape.half[1]),
                            ),
                            mul3(shape.axes[2], sz * shape.half[2]),
                        ),
                    )
                )
            }
        }
        is TubeShape -> {
            val pts = shape.points
            for (i in pts.indices) {
                val t = unit3(sub3(pts[min(pts.size - 1, i + 1)], pts[max(0, i - 1)]))
                val f = frameAlong(pts[i], t)
                val ring = max(8, jsRound(count / 4.0).toInt())
                for (j in 0 until ring) out.add(
                    pointOf(f, 0.0, shape.radii[i], (j.toDouble() / ring) * TAU)
                )
            }
        }
    }
    return out
}

private fun ballSamples(shape: BallShape, count: Int, out: MutableList<Point3>) {
    val golden = PI * (3 - sqrt(5.0))
    val total = max(count * 3, 96)
    val flats = shape.flats
    for (i in 0 until total) {
        val y = 1 - (2 * (i + 0.5)) / total
        val rad = sqrt(1 - y * y)
        val t = golden * i
        val n = Point3(jsCos(t) * rad, jsSin(t) * rad, y)
        if (
            flats.any {
                it.n.x * n.x * shape.r + it.n.y * n.y * shape.r + it.n.z * n.z * shape.r >
                    it.d + 1e-9
            }
        )
            continue
        out.add(add3(shape.o, mul3(n, shape.r)))
    }
    for (flat in flats) {
        val f = frameAlong(add3(shape.o, mul3(flat.n, flat.d)), flat.n)
        val rho = sqrt(max(0.0, shape.r * shape.r - flat.d * flat.d))
        for (j in 0 until count) {
            val p = pointOf(f, 0.0, rho, (j.toDouble() / count) * TAU)
            val inside = flats.all {
                it.n.x * (p.x - shape.o.x) +
                    it.n.y * (p.y - shape.o.y) +
                    it.n.z * (p.z - shape.o.z) <= it.d + 1e-6
            }
            if (inside) out.add(p)
        }
    }
}

/** At most [most] of [points], evenly picked. */
internal fun <T> thinCloud(points: List<T>, most: Int = 64): List<T> {
    if (points.size <= most) return points
    val step = points.size.toDouble() / most
    return (0 until most).map { points[floor(it * step).toInt()] }
}

internal class Margin(val margin: Double, val d: Double)

internal fun marginOf(n: Point3, a: List<Point3>, b: List<Point3>): Margin {
    var hiA = Double.NEGATIVE_INFINITY
    for (p in a) {
        val v = n.x * p.x + n.y * p.y + n.z * p.z
        if (v > hiA) hiA = v
    }
    var loB = Double.POSITIVE_INFINITY
    for (p in b) {
        val v = n.x * p.x + n.y * p.y + n.z * p.z
        if (v < loB) loB = v
    }
    return Margin(loB - hiA, (loB + hiA) / 2)
}

/** The direction of the closest approach of two clouds' hulls, by Gilbert's iteration. */
internal fun closest(a: List<Point3>, b: List<Point3>, rounds: Int = 200): Point3 {
    var w = sub3(b[0], a[0])
    for (it in 0 until rounds) w = gilbertStep(a, b, w) ?: break
    return w
}

/** One step of Gilbert's iteration from [w], or null once it has converged. */
private fun gilbertStep(a: List<Point3>, b: List<Point3>, w: Point3): Point3? {
    if (len3(w) < 1e-12) return null
    var hi = Double.NEGATIVE_INFINITY
    var pa = a[0]
    for (p in a) {
        val v = dot3(p, w)
        if (v > hi) {
            hi = v
            pa = p
        }
    }
    var lo = Double.POSITIVE_INFINITY
    var pb = b[0]
    for (p in b) {
        val v = dot3(p, w)
        if (v < lo) {
            lo = v
            pb = p
        }
    }
    val s = sub3(pb, pa)
    val ds = sub3(w, s)
    val dd = dot3(ds, ds)
    if (dd < 1e-14) return null
    val t = max(0.0, min(1.0, dot3(w, ds) / dd))
    val next = sub3(w, mul3(ds, t))
    return if (len3(sub3(next, w)) < 1e-10) null else next
}

private fun centre(list: List<Point3>): Point3 {
    var sum = Point3(0.0, 0.0, 0.0)
    for (p in list) sum = add3(sum, p)
    return mul3(sum, 1.0 / list.size)
}

private fun bestOf(
    candidates: List<Point3>,
    a: List<Point3>,
    b: List<Point3>,
): Pair<Point3, Double> {
    var best: Pair<Point3, Double>? = null
    for (n in candidates.filter { len3(it) > 1e-9 }.map { unit3(it) }) {
        val margin = marginOf(n, a, b).margin
        if (best == null || margin > best.second) best = n to margin
    }
    return best!!
}

/** Walks [n0] round on a shrinking ring of [steps] while the margin grows. */
private fun climb(
    n0: Point3,
    m0: Double,
    a: List<Point3>,
    b: List<Point3>,
    steps: DoubleArray,
    rounds: Int,
): Pair<Point3, Double> {
    var n = n0
    var margin = m0
    for (step in steps) {
        for (round in 0 until rounds) {
            val f = frameAlong(Point3(0.0, 0.0, 0.0), n)
            var improved = false
            for (j in 0 until 8) {
                val t = (j.toDouble() / 8) * TAU
                val trial =
                    unit3(add3(n, add3(mul3(f.u, step * jsCos(t)), mul3(f.v, step * jsSin(t)))))
                val value = marginOf(trial, a, b).margin
                if (value > margin + 1e-9) {
                    margin = value
                    n = trial
                    improved = true
                }
            }
            if (!improved) break
        }
    }
    return n to margin
}

/** A quick separating plane: the best of the centres and [hints], refined only if tight. */
internal fun quickSeparate(
    a: List<Point3>,
    b: List<Point3>,
    hints: List<Point3> = emptyList(),
): Plane {
    val best =
        bestOf(
            listOf(sub3(centre(b), centre(a))) + hints.flatMap { listOf(it, mul3(it, -1.0)) },
            a,
            b,
        )
    if (best.second >= 0.1) return Plane(best.first, 0.0, best.second)
    val (n, margin) = climb(best.first, best.second, a, b, doubleArrayOf(0.3, 0.1, 0.03), 12)
    return Plane(n, 0.0, margin)
}

/** The separating plane with the largest margin found between two clouds: `separate`. */
fun separate(a: List<Point3>, b: List<Point3>, hints: List<Point3> = emptyList()): Plane {
    val best =
        bestOf(
            listOf(sub3(centre(b), centre(a)), closest(a, b)) +
                hints.flatMap { listOf(it, mul3(it, -1.0)) },
            a,
            b,
        )
    val (n, margin) =
        climb(best.first, best.second, a, b, doubleArrayOf(0.3, 0.1, 0.03, 0.01, 0.003, 0.001), 40)
    return Plane(n, marginOf(n, a, b).d, margin)
}
