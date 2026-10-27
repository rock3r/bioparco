// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.audit

import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Projection
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.cameraOf
import dev.sebastiano.anatomy.kit.dot3
import dev.sebastiano.anatomy.kit.jsHypot
import dev.sebastiano.anatomy.kit.mul3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/* The camera's basis, screen boxes, and depth along a camera ray by sphere tracing. */

/**
 * The camera's screen-right [r], screen-down [d] and toward-viewer [v] unit vectors, its scale [k]
 * and the screen [origin], for probes of your own.
 */
class CameraBasis(val r: Point3, val d: Point3, val v: Point3, val k: Double, val origin: Flat) {
    /** [p] on screen. */
    fun flat(p: Point3): Flat = Flat(origin.x + dot3(p, r) * k, origin.y + dot3(p, d) * k)

    /** The point on the plane through the world origin that lands at ([x], [y]). */
    internal fun rayStart(x: Double, y: Double): Point3 =
        add3(mul3(r, (x - origin.x) / k), mul3(d, (y - origin.y) / k))
}

fun cameraBasis(projection: Projection): CameraBasis {
    val c = cameraOf(projection)
    return CameraBasis(
        Point3(c.sinA, -c.cosA, 0.0),
        Point3(c.cosA * c.sinE, c.sinA * c.sinE, -c.cosE),
        Point3(c.cosA * c.cosE, c.sinA * c.cosE, c.sinE),
        c.k,
        projection.origin,
    )
}

/** A screen box. */
data class ScreenBox(val x0: Double, val y0: Double, val x1: Double, val y1: Double) {
    internal fun contains(x: Double, y: Double): Boolean = !(x < x0 || x > x1 || y < y0 || y > y1)
}

internal fun screenBox(shape: AuditShape, cam: CameraBasis, offset: Point3 = ZERO): ScreenBox {
    val (min, max) = shape.bounds
    var x0 = BIG
    var y0 = BIG
    var x1 = -BIG
    var y1 = -BIG
    fun visit(p: Point3) {
        val x = cam.origin.x + dot3(p, cam.r) * cam.k
        val y = cam.origin.y + dot3(p, cam.d) * cam.k
        x0 = min(x0, x)
        y0 = min(y0, y)
        x1 = max(x1, x)
        y1 = max(y1, y)
    }
    if (shape is TubeShape) {
        shape.points.forEachIndexed { index, p ->
            val r = shape.radii[index]
            val q = add3(p, offset)
            visit(add3(q, mul3(cam.r, r)))
            visit(add3(q, mul3(cam.r, -r)))
            visit(add3(q, mul3(cam.d, r)))
            visit(add3(q, mul3(cam.d, -r)))
        }
    } else {
        for (x in doubleArrayOf(min.x, max.x)) {
            for (y in doubleArrayOf(min.y, max.y)) {
                for (z in doubleArrayOf(min.z, max.z)) visit(add3(Point3(x, y, z), offset))
            }
        }
    }
    return ScreenBox(x0, y0, x1, y1)
}

/** Where the ray `c + t·v` is inside the box from [min] to [max], or null. */
private fun spanOf(c: Point3, v: Point3, min: Point3, max: Point3): DoubleArray? {
    var t0 = -BIG
    var t1 = BIG
    for (i in 0 until 3) {
        if (abs(v[i]) < 1e-12) {
            if (c[i] < min[i] || c[i] > max[i]) return null
            continue
        }
        val a = (min[i] - c[i]) / v[i]
        val b = (max[i] - c[i]) / v[i]
        t0 = max(t0, min(a, b))
        t1 = min(t1, max(a, b))
    }
    return if (t0 <= t1) doubleArrayOf(t0, t1) else null
}

internal fun tubeDistance(shape: TubeShape, p: Point3, segments: IntArray): Double {
    val points = shape.points
    val radii = shape.radii
    val last = points.size - 1
    var best = BIG
    for (index in segments) {
        val d = cappedSegment(p, points[index], points[index + 1], radii[index], radii[index + 1])
        if (d < best) best = d
        if (shape.round.first && index == 0) {
            best = min(best, roundEnd(p, points[0], radii[0]))
        }
        if (shape.round.second && (last == index || last == index + 1)) {
            best = min(best, roundEnd(p, points[last], radii[last]))
        }
    }
    return best
}

private fun roundEnd(p: Point3, q: Point3, r: Double): Double =
    jsHypot(p.x - q.x, p.y - q.y, p.z - q.z) - r

/** A body's frame against one view direction, cached between rays. */
internal class BodyView(
    val view: Point3,
    val aV: Double,
    val uV: Double,
    val vV: Double,
    val s0: Double,
    val s1: Double,
    val rm: Double,
)

private fun viewOfBody(shape: BodyShape, v: Point3): BodyView {
    val cached = shape.depthCache
    if (cached != null && cached.view == v) return cached
    val f = shape.frame
    val made =
        BodyView(
            v,
            dot3(f.a, v),
            dot3(f.u, v),
            dot3(f.v, v),
            shape.poly.minOf { it.s },
            shape.poly.maxOf { it.s },
            shape.poly.maxOf { it.r },
        )
    shape.depthCache = made
    return made
}

private fun bodyDepth(shape: BodyShape, c: Point3, v: Point3, eps: Double): Double? {
    val view = viewOfBody(shape, v)
    val f = shape.frame
    val q = Point3(c.x - f.o.x, c.y - f.o.y, c.z - f.o.z)
    val sc = dot3(q, f.a)
    val xc = dot3(q, f.u)
    val yc = dot3(q, f.v)
    val span = bodySpan(view, sc, xc, yc) ?: return null
    val lo = span[0]
    var t = span[1] + eps
    var step = 0
    while (step < 160 && t >= lo - eps) {
        var d =
            polyDistance(shape.poly, sc + t * view.aV, jsHypot(xc + t * view.uV, yc + t * view.vV))
        if (shape.meta.cut.isNotEmpty()) {
            d = max(d, -cutDistance(shape, Point3(c.x + v.x * t, c.y + v.y * t, c.z + v.z * t)))
        }
        if (d < eps) return t
        t -= max(d, eps * 0.5)
        step++
    }
    return null
}

/** Where a ray meets a body's bounding cylinder, or null. */
private fun bodySpan(view: BodyView, sc: Double, xc: Double, yc: Double): DoubleArray? {
    var lo = -BIG
    var hi = BIG
    if (abs(view.aV) > 1e-9) {
        val a = (view.s0 - sc) / view.aV
        val b = (view.s1 - sc) / view.aV
        lo = min(a, b)
        hi = max(a, b)
    } else if (sc < view.s0 || sc > view.s1) return null
    val qa = view.uV * view.uV + view.vV * view.vV
    if (qa > 1e-12) {
        val qb = 2 * (xc * view.uV + yc * view.vV)
        val qc = xc * xc + yc * yc - view.rm * view.rm
        val disc = qb * qb - 4 * qa * qc
        if (disc < 0) return null
        val root = sqrt(disc)
        lo = max(lo, (-qb - root) / (2 * qa))
        hi = min(hi, (-qb + root) / (2 * qa))
    } else if (xc * xc + yc * yc > view.rm * view.rm) return null
    return if (lo > hi) null else doubleArrayOf(lo, hi)
}

/**
 * How far along the camera ray from [c] toward the viewer [v] the nearest surface of [shape] is, or
 * null if the ray misses. [segments] limits a tube to those segments.
 */
internal fun rayDepth(
    shape: AuditShape,
    c: Point3,
    v: Point3,
    eps: Double,
    segments: IntArray?,
): Double? {
    if (shape is BodyShape) return bodyDepth(shape, c, v, eps)
    val span = spanOf(c, v, shape.bounds.min, shape.bounds.max) ?: return null
    var t = span[1] + eps
    var step = 0
    while (step < 160 && t >= span[0] - eps) {
        val p = Point3(c.x + v.x * t, c.y + v.y * t, c.z + v.z * t)
        val d =
            if (segments != null && shape is TubeShape) tubeDistance(shape, p, segments)
            else sdf(shape, p)
        if (d < eps) return t
        t -= max(d, eps * 0.5)
        step++
    }
    return null
}

/** The distance from ([x], [y]) to the screen segment [a]–[b]. */
internal fun flatDistance(x: Double, y: Double, a: Flat, b: Flat): Double {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val span = dx * dx + dy * dy
    val t = if (span > 0) max(0.0, min(1.0, ((x - a.x) * dx + (y - a.y) * dy) / span)) else 0.0
    return jsHypot(x - a.x - dx * t, y - a.y - dy * t)
}
