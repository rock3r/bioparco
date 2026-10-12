// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.jsHypot
import kotlin.math.max
import kotlin.math.min

/*
 * Separation on screen and in space: `t3Overlap` and `t3Apart` (screen boxes and convex hulls by
 * the separating axis), `t3Disjoint` (a part's footprint, a tube's quads), and the runtime's GJK
 * (`t3Support`, `t3Gjk`): the plane between two posed sample clouds with the largest margin found.
 */

/** Whether two screen boxes overlap by more than [pad]. */
internal fun t3Overlap(a: DoubleArray, b: DoubleArray, pad: Double): Boolean =
    a[0] < b[2] - pad && b[0] < a[2] - pad && a[1] < b[3] - pad && b[1] < a[3] - pad

/** Whether two convex polygons are apart by at least [pad] along one of their edge normals. */
internal fun t3Apart(a: DoubleArray, an: Int, b: DoubleArray, bn: Int, pad: Double): Boolean {
    for (pass in 0 until 2) {
        val p = if (pass == 1) b else a
        val pn = if (pass == 1) bn else an
        for (i in 0 until pn) {
            val j = (i + 1) % pn
            val nx = p[2 * j + 1] - p[2 * i + 1]
            val ny = p[2 * i] - p[2 * j]
            val l = jsHypot(nx, ny)
            if (l < 1e-9) continue
            var a0 = Double.POSITIVE_INFINITY
            var a1 = Double.NEGATIVE_INFINITY
            for (q in 0 until an) {
                val v = (a[2 * q] * nx + a[2 * q + 1] * ny) / l
                if (v < a0) a0 = v
                if (v > a1) a1 = v
            }
            var b0 = Double.POSITIVE_INFINITY
            var b1 = Double.NEGATIVE_INFINITY
            for (q in 0 until bn) {
                val v = (b[2 * q] * nx + b[2 * q + 1] * ny) / l
                if (v < b0) b0 = v
                if (v > b1) b1 = v
            }
            if (a1 < b0 + pad || b1 < a0 + pad) return true
        }
    }
    return false
}

/** Whether two parts' footprints are apart by [pad]: hulls, or each tube quad against each. */
fun turnDisjoint(fa: TurnFrame, fb: TurnFrame, pad: Double): Boolean {
    val qa = fa.quads
    val qb = fb.quads
    if (qa == null && qb == null) return t3Apart(fa.hull, fa.hullN, fb.hull, fb.hullN, pad)
    val listA = qa?.map { it to it.size / 2 } ?: listOf(fa.hull to fa.hullN)
    val listB = qb?.map { it to it.size / 2 } ?: listOf(fb.hull to fb.hullN)
    for ((a, an) in listA) for ((b, bn) in listB) if (!t3Apart(a, an, b, bn, pad)) return false
    return true
}

/** The cloud point furthest along [dir] (world), posed by `R`, `t`, into [world]; its support. */
internal fun t3Support(
    cloud: DoubleArray,
    r: DoubleArray,
    t: Point3,
    dir: Point3,
    world: DoubleArray,
): Double {
    var best = Double.NEGATIVE_INFINITY
    var at = 0
    val n = cloud.size / 3
    val dx = r[0] * dir.x + r[3] * dir.y + r[6] * dir.z
    val dy = r[1] * dir.x + r[4] * dir.y + r[7] * dir.z
    val dz = r[2] * dir.x + r[5] * dir.y + r[8] * dir.z
    for (i in 0 until n) {
        val v = cloud[3 * i] * dx + cloud[3 * i + 1] * dy + cloud[3 * i + 2] * dz
        if (v > best) {
            best = v
            at = i
        }
    }
    val x = cloud[3 * at]
    val y = cloud[3 * at + 1]
    val z = cloud[3 * at + 2]
    world[0] = r[0] * x + r[1] * y + r[2] * z + t.x
    world[1] = r[3] * x + r[4] * y + r[5] * z + t.y
    world[2] = r[6] * x + r[7] * y + r[8] * z + t.z
    return best + dir.x * t.x + dir.y * t.y + dir.z * t.z
}

/** A separating plane's normal (from A toward B, world) and its margin. */
class Separation(val n: Point3, val margin: Double)

/** Axes to try when GJK leaves a pair tight: each axis in its camera's frame, plus centroids. */
class GjkHints(val axes: List<Pair<Point3, TurnCam>>, val centroids: Pair<Point3, Point3>?)

/** The original's `TURN.hintsOf`. */
fun hintsOf(ri: TurnRun, ci: TurnCam, rj: TurnRun, cj: TurnCam, world: TurnCam): GjkHints =
    GjkHints(
        ri.axes.map { it to ci } + rj.axes.map { it to cj } + (Point3(0.0, 0.0, 1.0) to world),
        ri.centroid to rj.centroid,
    )

/** The world plane between two posed clouds with the largest margin found: `t3Gjk`. */
fun turnGjk(
    cloudA: DoubleArray,
    camA: TurnCam,
    cloudB: DoubleArray,
    camB: TurnCam,
    warm: Point3?,
    rounds: Int = 8,
    hints: GjkHints? = null,
): Separation = Gjk(cloudA, camA, cloudB, camB).solve(warm, rounds, hints)

private class Gjk(
    val cloudA: DoubleArray,
    val camA: TurnCam,
    val cloudB: DoubleArray,
    val camB: TurnCam,
) {
    val a = DoubleArray(3)
    val b = DoubleArray(3)
    var w = Point3(0.0, 0.0, 0.0)
    var margin = Double.NEGATIVE_INFINITY
    var best = Point3(0.0, 0.0, 0.0)

    fun gap(n: Point3): Double {
        val hiA = t3Support(cloudA, camA.r, camA.t, n, a)
        val loB = -t3Support(cloudB, camB.r, camB.t, Point3(-n.x, -n.y, -n.z), b)
        return loB - hiA
    }

    fun step(limit: Int) {
        for (it in 0 until limit) {
            val l = jsHypot(w.x, w.y, w.z)
            if (l < 1e-12) break
            val n = Point3(w.x / l, w.y / l, w.z / l)
            val value = gap(n)
            if (value > margin) {
                margin = value
                best = n
            }
            val s = Point3(b[0] - a[0], b[1] - a[1], b[2] - a[2])
            val ds = Point3(w.x - s.x, w.y - s.y, w.z - s.z)
            val dd = ds.x * ds.x + ds.y * ds.y + ds.z * ds.z
            if (dd < 1e-14) break
            val tt = max(0.0, min(1.0, (w.x * ds.x + w.y * ds.y + w.z * ds.z) / dd))
            w = Point3(w.x - ds.x * tt, w.y - ds.y * tt, w.z - ds.z * tt)
        }
    }

    fun solve(warm: Point3?, rounds: Int, hints: GjkHints?): Separation {
        if (warm != null && (warm.x != 0.0 || warm.y != 0.0 || warm.z != 0.0)) {
            w = warm
        } else {
            t3Support(cloudA, camA.r, camA.t, Point3(1.0, 0.0, 0.0), a)
            t3Support(cloudB, camB.r, camB.t, Point3(-1.0, 0.0, 0.0), b)
            w = Point3(b[0] - a[0], b[1] - a[1], b[2] - a[2])
        }
        best = w
        step(rounds)
        if (margin < 0) step(24)
        if (margin < 0.05 && hints != null) tryHints(hints)
        val l = jsHypot(best.x, best.y, best.z).let { if (it == 0.0) 1.0 else it }
        return Separation(Point3(best.x / l, best.y / l, best.z / l), margin)
    }

    fun tryHints(hints: GjkHints) {
        hints.centroids?.let { (ca, cb) ->
            val wa = place(camA, ca)
            val wb = place(camB, cb)
            val n = t3Unit(Point3(wb.x - wa.x, wb.y - wa.y, wb.z - wa.z))
            val value = gap(n)
            if (value > margin) {
                margin = value
                best = n
            }
        }
        for ((axis, cam) in hints.axes) {
            val w0 = t3Apply(cam.r, axis)
            for (sign in doubleArrayOf(1.0, -1.0)) {
                val n = Point3(w0.x * sign, w0.y * sign, w0.z * sign)
                val value = gap(n)
                if (value > margin) {
                    margin = value
                    best = n
                }
            }
        }
    }
}

/** A rest-local point placed by a camera's pose. */
internal fun place(cam: TurnCam, p: Point3): Point3 {
    val r = cam.r
    return Point3(
        r[0] * p.x + r[1] * p.y + r[2] * p.z + cam.t.x,
        r[3] * p.x + r[4] * p.y + r[5] * p.z + cam.t.y,
        r[6] * p.x + r[7] * p.y + r[8] * p.z + cam.t.z,
    )
}
