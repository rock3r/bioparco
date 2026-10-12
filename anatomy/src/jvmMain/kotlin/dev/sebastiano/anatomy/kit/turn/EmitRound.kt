// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Projection
import dev.sebastiano.anatomy.kit.jsHypot
import dev.sebastiano.anatomy.kit.jsRound
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/*
 * A live round, `t3PrepareRound` and `t3EmitRound`: a cylinder or cone frustum on any axis, with
 * flat, domed or open ends. Its side is shaded with analytic light bands (strips between the end
 * rings), a dome with nested hulls; its outline is the two visible end arcs joined at the limbs.
 */

/** A seam ring on a round: where, its radius, and the side's slope. */
internal class SeamRing(val s: Double, val r: Double, val slope: Double)

/** A prepared round. */
class RoundRun
internal constructor(index: Int, part: PartData, unit: Double, spec: RoundSpec, p: Projection) :
    TurnRun(index, part, unit) {
    val frame: Frame = spec.frame
    val s0 = spec.s0
    val s1 = spec.s1
    val r0 = spec.r0
    val r1 = spec.r1
    val ends: List<RoundEnd> = spec.ends
    internal val g = (r1 - r0) / (s1 - s0).let { if (it == 0.0) 1.0 else it }
    internal val nr: Double
    internal val na: Double
    internal val fine: Int
    internal val capCount: Int
    internal val domeCount: Int
    internal val seams: List<SeamRing>
    internal val ribs: RibSpec?
    internal var cutS: Double? = null

    init {
        val k = p.scale * T3_ROOT
        val norm = jsHypot(1.0, g)
        nr = 1 / norm
        na = -g / norm
        val around = T3_TAU * max(r0, r1) * k * unit
        val small = around < 30
        fine = max(if (small) 16 else 24, min(96, ceil(around / 2.2).toInt()))
        capCount = max(if (small) 16 else 24, min(160, ceil(around / 1.5).toInt()))
        domeCount = max(if (small) 12 else 16, min(48, jsRound(fine * 0.75).toInt()))
        seams = (part.seams ?: emptyList()).map { SeamRing(it, r0 + g * (it - s0), g) }
        ribs =
            part.ribs?.let {
                it.copy(
                    r0 = it.r0 ?: (r0 + g * (it.s0 - s0)),
                    r1 = it.r1 ?: (r0 + g * (it.s1 - s0)),
                )
            }
        for (cut in spec.cuts) {
            val along = cut.n.x * frame.a.x + cut.n.y * frame.a.y + cut.n.z * frame.a.z
            val o = frame.o
            val sCut = (cut.d - (cut.n.x * o.x + cut.n.y * o.y + cut.n.z * o.z)) / along
            if (abs(sCut - s0) < 1e-6 || abs(sCut - s1) < 1e-6) cutS = sCut
        }
    }

    override fun cloudOf(): DoubleArray {
        val out = ArrayList<Double>()
        ringCloud(frame, s0, r0, 24, out)
        ringCloud(frame, s1, r1, 24, out)
        for (end in 0 until 2) {
            if (ends[end] != RoundEnd.Dome) continue
            val s = if (end == 1) s1 else s0
            val r = if (end == 1) r1 else r0
            val sign = if (end == 1) 1.0 else -1.0
            for (b in doubleArrayOf(0.5, 1.0, 1.35)) ringCloud(
                frame,
                s + sign * r * jsSin(b),
                r * jsCos(b),
                6,
                out,
            )
            val f = frame
            out.add(f.o.x + f.a.x * (s + sign * r))
            out.add(f.o.y + f.a.y * (s + sign * r))
            out.add(f.o.z + f.a.z * (s + sign * r))
        }
        return out.toDoubleArray()
    }

    override fun axesOf(): List<Point3> = listOf(frame.a)

    override fun emit(cam: TurnCam, f: TurnFrame) {
        val geo = t3Geo(frame, cam)
        val k = cam.k
        val step = T3_TAU / fine
        val sheets = Array(5) { IsoPath.Empty }
        for (arc in t3Arcs(nr, na, geo.view, geo.light)) {
            sheets[arc.tone] =
                sheets[arc.tone] + t3Strip(geo, s0, r0, s1, r1, arc.from, arc.to, step)
        }
        for (end in 0 until 2) {
            if (ends[end] != RoundEnd.Dome) continue
            val s = if (end == 1) s1 else s0
            val sign = if (end == 1) 1.0 else -1.0
            val a = frame.a
            val c = Point3(frame.o.x + a.x * s, frame.o.y + a.y * s, frame.o.z + a.z * s)
            t3Dome(
                cam,
                c,
                Point3(a.x * sign, a.y * sign, a.z * sign),
                if (end == 1) r1 else r0,
                domeCount,
                sheets,
            )
        }
        for (t in 0 until 5) f.d[role.getValue("s$t")] = sheets[t]
        val cuts: MutableSet<Flat> = Collections.newSetFromMap(IdentityHashMap())
        val pts = roundOutline(this, geo, k, cuts)
        val outline = t3PathOf(pts, true)
        f.d[role.getValue("fill")] = outline
        f.d[role.getValue("edge")] =
            if (cutS != null) {
                val buf = DoubleArray(2 * pts.size)
                pts.forEachIndexed { j, p ->
                    buf[2 * j] = p.x
                    buf[2 * j + 1] = p.y
                }
                val skip =
                    BooleanArray(pts.size) { j ->
                        pts[j] in cuts && pts[(j + 1) % pts.size] in cuts
                    }
                t3Broken(buf, pts.size, skip)
            } else outline
        t3BoxOf(pts, f.box)
        t3HullOut(f, pts)
        roundCaps(this, geo, k, f)
        role["inner"]?.let { f.d[it] = innerOf(geo, k) }
        val ribs = ribs
        if (ribs != null) {
            val lines =
                if (TurnState.detail < 0.01) FadedLines()
                else t3RibLines(geo, ribs, ribs.r0!!, ribs.r1!!)
            f.levels = t3Faded(f, this, "r", lines)
        }
        emitPlanes(this, cam, f)
        emitDots(this, cam, f)
    }

    private fun innerOf(geo: Geo, k: Double): IsoPath {
        var inner = IsoPath.Empty
        for (seam in seams) inner += t3SeamArc(geo, seam.s, seam.r, seam.slope, k)
        val rs = ribs
        if (rs != null && rs.seams != RibSeams.None) {
            val r0 = rs.r0!!
            val r1 = rs.r1!!
            val slope = (r1 - r0) / (rs.s1 - rs.s0).let { if (it == 0.0) 1.0 else it }
            if (rs.seams != RibSeams.Last) inner += t3SeamArc(geo, rs.s0, r0, slope, k)
            if (rs.seams != RibSeams.First) inner += t3SeamArc(geo, rs.s1, r1, slope, k)
        }
        return inner
    }
}

/** Adds a ring of [count] points to a flat cloud. */
internal fun ringCloud(f: Frame, s: Double, r: Double, count: Int, out: MutableList<Double>) {
    for (j in 0 until count) {
        val c = jsCos((j.toDouble() / count) * T3_TAU)
        val n = jsSin((j.toDouble() / count) * T3_TAU)
        out.add(f.o.x + f.a.x * s + r * (c * f.u.x + n * f.v.x))
        out.add(f.o.y + f.a.y * s + r * (c * f.u.y + n * f.v.y))
        out.add(f.o.z + f.a.z * s + r * (c * f.u.z + n * f.v.z))
    }
}

/** A semicircle of screen radius [rk] round [c] from [from], `count` steps, into [out]. */
private fun halfCircle(c: Flat, rk: Double, from: Double, count: Int, out: MutableList<Flat>) {
    for (j in 0..count) {
        val t = from + (PI * j) / count
        out.add(Flat(c.x + rk * jsCos(t), c.y + rk * jsSin(t)))
    }
}

/** `t3RoundOutline`: the round's outline points; points on a cut rim go into [cuts]. */
private fun roundOutline(run: RoundRun, geo: Geo, k: Double, cuts: MutableSet<Flat>): List<Flat> {
    val domes = run.ends.count { it == RoundEnd.Dome }
    if (domes == 2) return capsuleOutline(run, geo, k)
    if (domes == 1) return halfCapsuleOutline(run, geo, k)
    return sideOutline(run, geo, k, cuts)
}

/** A round with one dome: the hull of both end rims and the dome's half circle. */
private fun halfCapsuleOutline(run: RoundRun, geo: Geo, k: Double): List<Flat> {
    val pts = ArrayList<Flat>()
    for (end in 0 until 2) {
        val s = if (end == 1) run.s1 else run.s0
        val r = if (end == 1) run.r1 else run.r0
        t3ArcPoints(geo, s, r, 0.0, T3_TAU, k, pts)
        if (run.ends[end] != RoundEnd.Dome) continue
        val sign = if (end == 1) 1.0 else -1.0
        val c = Flat(geo.ox + s * geo.sa.x, geo.oy + s * geo.sa.y)
        val rk = r * k
        val beta = jsAtan2(sign * geo.sa.y, sign * geo.sa.x)
        halfCircle(c, rk, beta - PI / 2, max(24, ceil((PI * rk) / 1.5).toInt()), pts)
    }
    return t3Hull(pts)
}

/**
 * A flat-ended round: one end's rim seen end-on, or both rims' visible arcs joined by the sides.
 */
private fun sideOutline(run: RoundRun, geo: Geo, k: Double, cuts: MutableSet<Flat>): List<Flat> {
    val view = geo.view
    val out = ArrayList<Flat>()
    val c =
        if (view.flat < 1e-9) (if (run.na * view.a > 0) -2.0 else 2.0)
        else -(run.na * view.a) / (run.nr * view.flat)
    val cutS = run.cutS
    fun cutAt(s: Double) = cutS != null && abs(cutS - s) < 1e-6
    if (c <= -1 || c >= 1) {
        val capFirst = -geo.aV > 0
        val keepFirst = if (c <= -1) !capFirst else capFirst
        val s = if (keepFirst) run.s0 else run.s1
        val r = if (keepFirst) run.r0 else run.r1
        t3ArcPoints(geo, s, r, 0.0, T3_TAU, k, out, if (cutAt(s)) cuts else null)
        out.removeAt(out.size - 1)
        return out
    }
    val d = jsAcos(c)
    val lo = view.phi - d
    val hi = view.phi + d
    val cuts0 = if (cutAt(run.s0)) cuts else null
    val cuts1 = if (cutAt(run.s1)) cuts else null
    if (-geo.aV > 0) t3ArcPoints(geo, run.s0, run.r0, hi, lo + T3_TAU, k, out, cuts0)
    else t3ArcPoints(geo, run.s0, run.r0, lo, hi, k, out, cuts0)
    val tail = ArrayList<Flat>()
    if (geo.aV > 0) t3ArcPoints(geo, run.s1, run.r1, hi, lo + T3_TAU, k, tail, cuts1)
    else t3ArcPoints(geo, run.s1, run.r1, lo, hi, k, tail, cuts1)
    val first = out.last()
    val a = tail.first()
    val b = tail.last()
    if (jsHypot(a.x - first.x, a.y - first.y) > jsHypot(b.x - first.x, b.y - first.y))
        tail.reverse()
    out.addAll(tail)
    return out
}

/** A capsule's outline: two semicircles joined by the sides. */
private fun capsuleOutline(run: RoundRun, geo: Geo, k: Double): List<Flat> {
    val c0 = Flat(geo.ox + run.s0 * geo.sa.x, geo.oy + run.s0 * geo.sa.y)
    val c1 = Flat(geo.ox + run.s1 * geo.sa.x, geo.oy + run.s1 * geo.sa.y)
    val rk = run.r0 * k
    val dx = c1.x - c0.x
    val dy = c1.y - c0.y
    val l = jsHypot(dx, dy)
    val count = max(24, ceil((PI * rk) / 1.5).toInt())
    val out = ArrayList<Flat>()
    if (l < 1e-6) {
        for (j in 0 until 2 * count) {
            val t = (j.toDouble() / (2 * count)) * T3_TAU
            out.add(Flat(c1.x + rk * jsCos(t), c1.y + rk * jsSin(t)))
        }
        return out
    }
    val beta = jsAtan2(dy, dx)
    halfCircle(c1, rk, beta - PI / 2, count, out)
    halfCircle(c0, rk, beta + PI / 2, count, out)
    return out
}

/** The flat end caps: their rings, tones and widths, and the crease along each visible rim. */
private fun roundCaps(run: RoundRun, geo: Geo, k: Double, f: TurnFrame) {
    var crease = IsoPath.Empty
    for (end in 0 until 2) {
        if (run.ends[end] != RoundEnd.Flat) continue
        val sign = if (end == 1) 1.0 else -1.0
        val s = if (end == 1) run.s1 else run.s0
        val r = if (end == 1) run.r1 else run.r0
        val arc =
            capOf(run.role.getValue("k$end"), sign, s, r, geo, k, f, run.capCount, run.na, run.nr)
        if (arc != null && run.role["crease"] != null) crease += arc
    }
    run.role["crease"]?.let { f.d[it] = crease }
}

/**
 * One flat cap of radius [r] at [s] (`k0`/`k1`), shared by rounds and lathes: its ring, tone and
 * width, and the visible rim's crease (null when the rim is out of sight). [na] and [nr] are the
 * side's normal at the rim.
 */
internal fun capOf(
    role: Int,
    sign: Double,
    s: Double,
    r: Double,
    geo: Geo,
    k: Double,
    f: TurnFrame,
    capCount: Int,
    na: Double,
    nr: Double,
    skipZero: Boolean = false,
): IsoPath? {
    val face = sign * geo.aV
    if (!(face > 1e-9) || (skipZero && r <= 1e-9)) {
        f.d[role] = IsoPath.Empty
        return null
    }
    val buf = DoubleArray(2 * capCount)
    val table = t3Table(capCount)
    for (j in 0 until capCount) {
        val c = table[2 * j]
        val q = table[2 * j + 1]
        buf[2 * j] = geo.ox + s * geo.sa.x + r * (c * geo.su.x + q * geo.sv.x)
        buf[2 * j + 1] = geo.oy + s * geo.sa.y + r * (c * geo.su.y + q * geo.sv.y)
    }
    f.d[role] = t3Path(buf, capCount, true)
    f.q[role] = t3Level(sign * geo.aL, T3_LB)
    val minor = 2 * r * k * abs(face)
    f.w[role] = if (minor < 0.4) jsRound(minor * 100) / 100 else -1.0
    val view = geo.view
    val lo: Double
    val hi: Double
    if (view.flat < 1e-9) {
        if (!(na * view.a > 1e-9)) return null
        lo = 0.0
        hi = T3_TAU
    } else {
        val cc = -(na * view.a) / (nr * view.flat)
        if (cc >= 1) return null
        if (cc <= -1) {
            lo = view.phi
            hi = view.phi + T3_TAU
        } else {
            val d = jsAcos(cc)
            lo = view.phi - d
            hi = view.phi + d
        }
    }
    val count = min(400, max(4, ceil(((hi - lo) * r * k) / 1.5).toInt()))
    val arc = DoubleArray(2 * count + 2)
    for (j in 0..count) t3At(geo, s, r, lo + ((hi - lo) * j) / count, arc, 2 * j)
    return t3Path(arc, count + 1, false)
}
