// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Projection
import dev.sebastiano.anatomy.kit.jsRound
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * A live ball with flats, `t3PrepareBall` and `t3EmitBall`: the limb circle clipped by the flats,
 * each flat a face ring, and the light bands as caps of the sphere clipped to the outline. The
 * costliest kind to draw.
 */

/** A prepared flat: its normal, offset, rim radius and rim basis. */
internal class BallCut(val n: Point3, val d: Double, val rho: Double, val u: Point3, val v: Point3)

/** A rim point of a flat: its direction from the centre, whether it survives the other flats. */
private class RimPoint(val w: Point3, val keep: Boolean, val p: Flat)

/** A prepared ball. */
class BallRun
internal constructor(index: Int, part: PartData, unit: Double, spec: BallSpec, p: Projection) :
    TurnRun(index, part, unit) {
    val c: Point3 = spec.c
    val r: Double = spec.r
    internal val flats: List<BallCut> =
        spec.flats.map { flat ->
            val n = t3Unit(flat.n)
            val rho = sqrt(max(0.0, spec.r * spec.r - flat.d * flat.d))
            val (u, v) = t3Basis(n)
            BallCut(n, flat.d, rho, u, v)
        }
    internal val limb: Int
    internal val rimCount: IntArray

    init {
        val k = p.scale * T3_ROOT
        limb = max(32, min(160, ceil((T3_TAU * spec.r * k * unit) / 1.5).toInt()))
        rimCount =
            flats
                .map { max(20, min(120, ceil((T3_TAU * it.rho * k * unit) / 1.5).toInt())) }
                .toIntArray()
    }

    override fun cloudOf(): DoubleArray {
        val out = ArrayList<Double>()
        val golden = PI * (3 - sqrt(5.0))
        for (i in 0 until 64) {
            val y = 1 - (2 * (i + 0.5)) / 64
            val rad = sqrt(1 - y * y)
            val w = Point3(jsCos(golden * i) * rad, jsSin(golden * i) * rad, y)
            val p = Point3(c.x + r * w.x, c.y + r * w.y, c.z + r * w.z)
            val inside = flats.none {
                (p.x - c.x) * it.n.x + (p.y - c.y) * it.n.y + (p.z - c.z) * it.n.z > it.d + 1e-9
            }
            if (inside) {
                out.add(p.x)
                out.add(p.y)
                out.add(p.z)
            }
        }
        for (flat in flats) {
            for (j in 0 until 12) {
                val cs = jsCos((j.toDouble() / 12) * T3_TAU) * flat.rho
                val sn = jsSin((j.toDouble() / 12) * T3_TAU) * flat.rho
                out.add(c.x + flat.n.x * flat.d + cs * flat.u.x + sn * flat.v.x)
                out.add(c.y + flat.n.y * flat.d + cs * flat.u.y + sn * flat.v.y)
                out.add(c.z + flat.n.z * flat.d + cs * flat.u.z + sn * flat.v.z)
            }
        }
        return out.toDoubleArray()
    }

    override fun axesOf(): List<Point3> = flats.map { it.n }

    override fun emit(cam: TurnCam, f: TurnFrame) {
        BallEmit(this, cam, f).run()
        emitPlanes(this, cam, f)
        emitDots(this, cam, f)
    }
}

private fun dot(a: Point3, b: Point3) = a.x * b.x + a.y * b.y + a.z * b.z

/** One frame of a ball. */
private class BallEmit(val run: BallRun, val cam: TurnCam, val f: TurnFrame) {
    val m = cam.m
    val v = cam.v
    val l = cam.l
    val r = run.r
    val flats = run.flats
    val cx = cam.ox + m[0] * run.c.x + m[1] * run.c.y + m[2] * run.c.z
    val cy = cam.oy + m[3] * run.c.x + m[4] * run.c.y + m[5] * run.c.z
    val table = t3Table(run.limb)
    val limb: List<Point3>
    val rims: List<List<RimPoint>>
    lateinit var hull: List<Flat>

    init {
        val (u, w) = t3Basis(v)
        limb =
            (0 until run.limb).map { j ->
                Point3(
                    u.x * table[2 * j] + w.x * table[2 * j + 1],
                    u.y * table[2 * j] + w.y * table[2 * j + 1],
                    u.z * table[2 * j] + w.z * table[2 * j + 1],
                )
            }
        rims = flats.mapIndexed { ci, cut ->
            val t = t3Table(run.rimCount[ci])
            (0 until run.rimCount[ci]).map { j ->
                val w =
                    Point3(
                        (cut.n.x * cut.d +
                            cut.rho * (cut.u.x * t[2 * j] + cut.v.x * t[2 * j + 1])) / r,
                        (cut.n.y * cut.d +
                            cut.rho * (cut.u.y * t[2 * j] + cut.v.y * t[2 * j + 1])) / r,
                        (cut.n.z * cut.d +
                            cut.rho * (cut.u.z * t[2 * j] + cut.v.z * t[2 * j + 1])) / r,
                    )
                RimPoint(w, inside(w, ci), flat(w))
            }
        }
    }

    fun flat(w: Point3) =
        Flat(
            cx + r * (m[0] * w.x + m[1] * w.y + m[2] * w.z),
            cy + r * (m[3] * w.x + m[4] * w.y + m[5] * w.z),
        )

    fun inside(w: Point3, skip: Int = -1): Boolean {
        for (i in flats.indices) if (i != skip && dot(w, flats[i].n) * r > flats[i].d + 1e-7)
            return false
        return true
    }

    fun run() {
        val sil = ArrayList<Flat>()
        for (w in limb) if (inside(w)) sil.add(flat(w))
        flats.forEachIndexed { ci, cut ->
            for (w in t3Meet(v, 0.0, cut.n, cut.d / r)) if (inside(w, ci)) sil.add(flat(w))
            for (point in rims[ci]) if (point.keep) sil.add(point.p)
        }
        hull = t3Hull(sil)
        val outline = t3PathOf(hull, true)
        f.d[run.role.getValue("fill")] = outline
        f.d[run.role.getValue("edge")] = outline
        f.d[run.role.getValue("s0")] = outline
        f.d[run.role.getValue("s1")] = capOf(T3_LB[1])
        f.d[run.role.getValue("s2")] = capOf(T3_LB[2])
        f.d[run.role.getValue("s3")] = capOf(T3_LB[3])
        var crease = IsoPath.Empty
        flats.forEachIndexed { ci, cut -> crease += flatFace(ci, cut) }
        run.role["crease"]?.let { f.d[it] = crease }
        t3BoxOf(hull, f.box)
        t3HullOut(f, hull)
    }

    /** The part of the visible ball lit at least [threshold], clipped to the outline. */
    fun capOf(threshold: Double): IsoPath {
        val points = ArrayList<Flat>()
        for (w in limb) if (dot(w, l) >= threshold && inside(w)) points.add(flat(w))
        val radius = sqrt(max(0.0, 1 - threshold * threshold))
        val (lu, lw) = t3Basis(l)
        for (j in 0 until run.limb) {
            val cs = table[2 * j]
            val sn = table[2 * j + 1]
            val w =
                Point3(
                    l.x * threshold + radius * (lu.x * cs + lw.x * sn),
                    l.y * threshold + radius * (lu.y * cs + lw.y * sn),
                    l.z * threshold + radius * (lu.z * cs + lw.z * sn),
                )
            if (dot(w, v) >= -1e-9) points.add(flat(w))
        }
        if (dot(l, v) > threshold) points.add(flat(v))
        for (w in t3Meet(l, threshold, v, 0.0)) if (inside(w)) points.add(flat(w))
        flats.forEachIndexed { ci, cut ->
            for (w in t3Meet(v, 0.0, cut.n, cut.d / r)) {
                if (inside(w, ci) && dot(w, l) >= threshold) points.add(flat(w))
            }
            for (w in t3Meet(l, threshold, cut.n, cut.d / r)) {
                if (inside(w, ci) && dot(w, v) >= -1e-9) points.add(flat(w))
            }
            for (point in rims[ci]) {
                if (point.keep && dot(point.w, l) >= threshold && dot(point.w, v) >= -1e-9)
                    points.add(point.p)
            }
        }
        if (points.size < 3) return IsoPath.Empty
        val region = t3ClipConvex(t3Hull(points), hull)
        return if (region.size >= 3) t3PathOf(region, true) else IsoPath.Empty
    }

    /** A flat's face, tone and width, and its rim's visible stretches as crease. */
    fun flatFace(ci: Int, cut: BallCut): IsoPath {
        val role = run.role.getValue("f$ci")
        val face = dot(cut.n, v)
        if (!(face > 1e-9)) {
            f.d[role] = IsoPath.Empty
            return IsoPath.Empty
        }
        val list = rims[ci]
        f.d[role] = t3PathOf(t3ClipConvex(list.map { it.p }, hull), true)
        f.q[role] = t3Level(dot(cut.n, l), T3_LB)
        val minor = 2 * cut.rho * cam.k * face
        f.w[role] = if (minor < 0.4) jsRound(minor * 100) / 100 else -1.0
        val seen = list.map { it.keep && dot(it.w, v) > 0 }
        val start = seen.indexOf(false)
        if (start < 0) return t3PathOf(list.map { it.p }, true)
        var crease = IsoPath.Empty
        var cur: ArrayList<Flat>? = null
        for (s in 1..list.size) {
            val i = (start + s) % list.size
            if (seen[i]) {
                (cur ?: ArrayList<Flat>().also { cur = it }).add(list[i].p)
            } else {
                val done = cur
                if (done != null && done.size > 1) crease += t3PathOf(done, false)
                cur = null
            }
        }
        val done = cur
        if (done != null && done.size > 1) crease += t3PathOf(done, false)
        return crease
    }
}
