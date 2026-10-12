// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.jsHypot
import dev.sebastiano.anatomy.kit.jsRound
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * The geometry of round surfaces seen through a group camera (`t3Geo`, `t3At`, `t3Arcs`,
 * `t3Strip`, `t3Dome`, `t3ArcPoints`, `t3SeamArc`, `t3RibLines`): a frame's screen axes, the
 * angular arcs of each light band on a ring, strips between two rings, capsule domes as nested
 * hulls, and the visible arcs of seams.
 */

/** How a direction is seen: along the axis ([a]), across it ([flat]), and at what angle [phi]. */
internal class Seen(val a: Double, val flat: Double, val phi: Double)

/** A frame's screen axes and how it faces the view and the light. */
internal class Geo(
    val ox: Double,
    val oy: Double,
    val sa: Flat,
    val su: Flat,
    val sv: Flat,
    val aV: Double,
    val aL: Double,
    val view: Seen,
    val light: Seen,
)

internal fun t3Geo(fr: Frame, cam: TurnCam): Geo {
    val m = cam.m
    fun dot(a: Point3, b: Point3) = a.x * b.x + a.y * b.y + a.z * b.z
    fun lin(w: Point3) =
        Flat(m[0] * w.x + m[1] * w.y + m[2] * w.z, m[3] * w.x + m[4] * w.y + m[5] * w.z)
    val o = fr.o
    val aV = dot(fr.a, cam.v)
    val uV = dot(fr.u, cam.v)
    val vV = dot(fr.v, cam.v)
    val aL = dot(fr.a, cam.l)
    val uL = dot(fr.u, cam.l)
    val vL = dot(fr.v, cam.l)
    return Geo(
        cam.ox + m[0] * o.x + m[1] * o.y + m[2] * o.z,
        cam.oy + m[3] * o.x + m[4] * o.y + m[5] * o.z,
        lin(fr.a),
        lin(fr.u),
        lin(fr.v),
        aV,
        aL,
        Seen(aV, jsHypot(uV, vV), jsAtan2(vV, uV)),
        Seen(aL, jsHypot(uL, vL), jsAtan2(vL, uL)),
    )
}

/** The point at `(s, r, t)` on screen, into [buf] at [index]. */
internal fun t3At(geo: Geo, s: Double, r: Double, t: Double, buf: DoubleArray, index: Int) {
    val c = jsCos(t)
    val n = jsSin(t)
    buf[index] = geo.ox + s * geo.sa.x + r * (c * geo.su.x + n * geo.sv.x)
    buf[index + 1] = geo.oy + s * geo.sa.y + r * (c * geo.su.y + n * geo.sv.y)
}

internal fun t3AtFlat(geo: Geo, s: Double, r: Double, t: Double): Flat {
    val c = jsCos(t)
    val n = jsSin(t)
    return Flat(
        geo.ox + s * geo.sa.x + r * (c * geo.su.x + n * geo.sv.x),
        geo.oy + s * geo.sa.y + r * (c * geo.su.y + n * geo.sv.y),
    )
}

/** One visible arc of a ring in one light band; [full] for the whole ring. */
internal class Arc(val tone: Int, var from: Double, var to: Double, val full: Boolean = false)

/**
 * The visible arcs of a ring whose normal is `nr` across and `na` along the axis, cut where it
 * turns away and at each light band, labelled with their band.
 */
internal fun t3Arcs(nr: Double, na: Double, view: Seen, light: Seen): List<Arc> {
    val cuts = ArrayList<Double>()
    if (abs(nr) > 1e-9) {
        val visibleCut = -(na * view.a) / (nr * view.flat)
        if (visibleCut > -1 && visibleCut < 1) {
            val d = jsAcos(visibleCut)
            cuts.add(view.phi - d)
            cuts.add(view.phi + d)
        }
        for (b in T3_LB) {
            val c = (b - na * light.a) / (nr * light.flat)
            if (c > -1 && c < 1) {
                val d = jsAcos(c)
                cuts.add(light.phi - d)
                cuts.add(light.phi + d)
            }
        }
    }
    for (i in cuts.indices) {
        val w = ((cuts[i] % T3_TAU) + T3_TAU) % T3_TAU
        cuts[i] = jsRound(w * 1e9) / 1e9
    }
    cuts.sort()
    val sorted = ArrayList<Double>()
    for (c in cuts) if (sorted.isEmpty() || c != sorted.last()) sorted.add(c)
    fun label(angle: Double): Int {
        val facing = nr * view.flat * jsCos(angle - view.phi) + na * view.a
        if (facing <= 1e-9) return -1
        val score = nr * light.flat * jsCos(angle - light.phi) + na * light.a
        var tone = 0
        while (tone < T3_LB.size && score >= T3_LB[tone]) tone++
        return tone
    }
    if (sorted.isEmpty()) {
        val tone = label(0.0)
        return if (tone < 0) emptyList() else listOf(Arc(tone, 0.0, T3_TAU, full = true))
    }
    val merged = ArrayList<Arc>()
    for (index in sorted.indices) {
        val from = sorted[index]
        val to = if (index + 1 < sorted.size) sorted[index + 1] else sorted[0] + T3_TAU
        val tone = label((from + to) / 2)
        val last = merged.lastOrNull()
        if (last != null && last.tone == tone && abs(last.to - from) < 1e-9) last.to = to
        else merged.add(Arc(tone, from, to))
    }
    if (
        merged.size > 1 &&
            merged[0].tone == merged.last().tone &&
            abs(merged.last().to - T3_TAU - merged[0].from) < 1e-9
    ) {
        val last = merged.removeAt(merged.size - 1)
        merged[0].from = last.from - T3_TAU
    }
    return merged.filter { it.tone >= 0 }
}

/** The strip between two rings from angle [from] to [to], wound like every fill. */
internal fun t3Strip(
    geo: Geo,
    s0: Double,
    r0: Double,
    s1: Double,
    r1: Double,
    from: Double,
    to: Double,
    step: Double,
): IsoPath {
    val count = max(1, ceil((to - from) / step).toInt())
    val buf = DoubleArray(4 * count + 8)
    for (j in 0..count) {
        val t = from + ((to - from) * j) / count
        t3At(geo, s0, r0, t, buf, 2 * j)
        t3At(geo, s1, r1, t, buf, 2 * (2 * count + 1 - j))
    }
    return t3Path(t3Oriented(buf, 2 * count + 2), 2 * count + 2, true)
}

/** [buf]'s first [count] points reversed in place if they wind clockwise. */
internal fun t3Oriented(buf: DoubleArray, count: Int): DoubleArray {
    var area = 0.0
    for (i in 0 until count) {
        val j = (i + 1) % count
        area += buf[2 * i] * buf[2 * j + 1] - buf[2 * j] * buf[2 * i + 1]
    }
    if (area >= 0) return buf
    var i = 0
    var j = count - 1
    while (i < j) {
        val x = buf[2 * i]
        val y = buf[2 * i + 1]
        buf[2 * i] = buf[2 * j]
        buf[2 * i + 1] = buf[2 * j + 1]
        buf[2 * j] = x
        buf[2 * j + 1] = y
        i++
        j--
    }
    return buf
}

/** A screen point and its light score, for the dome's regions. */
private class Lit(val p: Flat, val lit: Double)

/**
 * A capsule's dome of radius [r] round [c], facing [e], as nested hulls, one per light band, added
 * to [sheets].
 */
internal fun t3Dome(
    cam: TurnCam,
    c: Point3,
    e: Point3,
    r: Double,
    count: Int,
    sheets: Array<IsoPath>,
) {
    val m = cam.m
    val v = cam.v
    val l = cam.l
    val sx = cam.ox + m[0] * c.x + m[1] * c.y + m[2] * c.z
    val sy = cam.oy + m[3] * c.x + m[4] * c.y + m[5] * c.z
    fun flat(n: Point3) =
        Flat(
            sx + r * (m[0] * n.x + m[1] * n.y + m[2] * n.z),
            sy + r * (m[3] * n.x + m[4] * n.y + m[5] * n.z),
        )
    fun dot(a: Point3, b: Point3) = a.x * b.x + a.y * b.y + a.z * b.z
    val table = t3Table(count)
    fun circle(w: Point3, value: Double, keep: (Point3) -> Boolean): List<Point3> {
        val (u, q) = t3Basis(w)
        val rad = sqrt(max(0.0, 1 - value * value))
        val out = ArrayList<Point3>()
        for (j in 0 until count) {
            val cs = table[2 * j] * rad
            val sn = table[2 * j + 1] * rad
            val n =
                Point3(
                    w.x * value + u.x * cs + q.x * sn,
                    w.y * value + u.y * cs + q.y * sn,
                    w.z * value + u.z * cs + q.z * sn,
                )
            if (keep(n)) out.add(n)
        }
        return out
    }
    val rim = circle(v, 0.0) { dot(it, e) >= -1e-9 }.map { Lit(flat(it), dot(it, l)) }
    val base = circle(e, 0.0) { dot(it, v) >= -1e-9 }.map { Lit(flat(it), dot(it, l)) }
    val corners = t3Meet(v, 0.0, e, 0.0).map { Lit(flat(it), dot(it, l)) }
    for (tone in 0 until 5) {
        val tau = if (tone == 0) Double.NEGATIVE_INFINITY else T3_LB[tone - 1]
        val candidates = ArrayList<Flat>()
        for (list in listOf(rim, base, corners)) {
            for (point in list) if (point.lit >= tau - 1e-9) candidates.add(point.p)
        }
        if (tone > 0) {
            for (n in circle(l, tau) { dot(it, v) >= -1e-9 && dot(it, e) >= -1e-9 }) {
                candidates.add(flat(n))
            }
            for (n in t3Meet(l, tau, v, 0.0)) if (dot(n, e) >= -1e-9) candidates.add(flat(n))
            for (n in t3Meet(l, tau, e, 0.0)) if (dot(n, v) >= -1e-9) candidates.add(flat(n))
        }
        if (candidates.size < 3) continue
        val region = t3Hull(candidates)
        if (region.size >= 3) sheets[tone] = sheets[tone] + t3PathOf(region, true)
    }
}

/** Points along the ring `(s, r)` from [from] to [to], at most 1.5 px apart, into [out]. */
internal fun t3ArcPoints(
    geo: Geo,
    s: Double,
    r: Double,
    from: Double,
    to: Double,
    k: Double,
    out: MutableList<Flat>,
    cuts: MutableSet<Flat>? = null,
) {
    val count =
        maxOf(
            2,
            ceil((abs(to - from) * 48) / T3_TAU).toInt(),
            ceil((abs(to - from) * max(r, 1e-6) * k) / 1.5).toInt(),
        )
    for (j in 0..count) {
        val t = from + ((to - from) * j) / count
        val p = t3AtFlat(geo, s, r, t)
        cuts?.add(p)
        out.add(p)
    }
}

/** The visible arc of a seam ring, trimmed 0.3 px short of each limb, or nothing. */
internal fun t3SeamArc(
    geo: Geo,
    s: Double,
    r: Double,
    slope: Double,
    k: Double,
    trim: Double = 0.3,
): IsoPath {
    val view = geo.view
    val norm = jsHypot(1.0, slope)
    var lo: Double
    var hi: Double
    if (view.flat < 1e-9) {
        if (!((-slope * view.a) / norm > 0)) return IsoPath.Empty
        lo = view.phi
        hi = view.phi + T3_TAU
    } else {
        val cc = (slope * view.a) / view.flat
        if (cc >= 1) return IsoPath.Empty
        if (cc <= -1) {
            lo = view.phi
            hi = view.phi + T3_TAU
        } else {
            val d = jsAcos(cc)
            lo = view.phi - d
            hi = view.phi + d
        }
    }
    val full = hi - lo >= T3_TAU - 1e-9
    val buf = DoubleArray(2 * 520)
    if (!full) {
        val span = hi - lo
        fun cutAt(from: Double, sign: Double): Double {
            t3At(geo, s, r, from, buf, 0)
            val px = buf[0]
            val py = buf[1]
            var a = 0.0
            var b = span * 0.25
            t3At(geo, s, r, from + sign * b, buf, 0)
            if (jsHypot(buf[0] - px, buf[1] - py) <= trim) return b
            repeat(18) {
                val mid = (a + b) / 2
                t3At(geo, s, r, from + sign * mid, buf, 0)
                if (jsHypot(buf[0] - px, buf[1] - py) < trim) a = mid else b = mid
            }
            return (a + b) / 2
        }
        val d0 = cutAt(lo, 1.0)
        val d1 = cutAt(hi, -1.0)
        lo += d0
        hi -= d1
    }
    val count = min(512, max(4, ceil(((hi - lo) * r * k) / 1.5).toInt()))
    for (j in 0..count) t3At(geo, s, r, lo + ((hi - lo) * j) / count, buf, 2 * j)
    return t3Path(buf, if (full) count else count + 1, full)
}

/** Knurl rows on a round surface, faded per rib: `t3RibLines`. */
internal fun t3RibLines(geo: Geo, ribs: RibSpec, r0: Double, r1: Double): FadedLines {
    val lines = FadedLines()
    val slope = (r1 - r0) / (ribs.s1 - ribs.s0).let { if (it == 0.0) 1.0 else it }
    val norm = jsHypot(1.0, slope)
    val view = geo.view
    val seg = DoubleArray(4)
    for (i in 0 until ribs.count) {
        val t = ribs.phase + (i.toDouble() / ribs.count) * T3_TAU
        val tm = t + ribs.twist / 2
        val facing = (view.flat * jsCos(tm - view.phi) - slope * view.a) / norm
        val alpha = t3Step(ribs.fade.first, ribs.fade.second, facing)
        if (alpha * TURN_LEVELS < 0.5) continue
        t3At(geo, ribs.s0, r0, t, seg, 0)
        t3At(geo, ribs.s1, r1, t + ribs.twist, seg, 2)
        lines.add(t3Path(seg, 2, false), alpha, i)
    }
    return lines
}

/** A thinned hull of at most [most] points grown to still contain every dropped point. */
internal fun t3Thin(points: List<Flat>, most: Int): List<Flat> {
    if (points.size <= most) return points
    val n = points.size
    val picks = IntArray(most) { ((it.toLong() * n) / most).toInt() }
    val thin = picks.map { points[it] }
    var err = 0.0
    for (j in 0 until most) {
        val a = thin[j]
        val b = thin[(j + 1) % most]
        val dx = b.x - a.x
        val dy = b.y - a.y
        val l = jsHypot(dx, dy).let { if (it == 0.0) 1.0 else it }
        val end = if (j + 1 < most) picks[j + 1] else picks[0] + n
        for (i in picks[j] + 1 until end) {
            val p = points[i % n]
            val out = ((p.x - a.x) * dy - (p.y - a.y) * dx) / l
            if (abs(out) > err) err = abs(out)
        }
    }
    return t3Grown(thin, err)
}

/** [poly] pushed out by [by] along its corner bisectors. */
internal fun t3Grown(poly: List<Flat>, by: Double): List<Flat> {
    if (!(by > 0)) return poly
    var area = 0.0
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[(i + 1) % poly.size]
        area += a.x * b.y - b.x * a.y
    }
    val sign = if (area >= 0) 1.0 else -1.0
    val n = poly.size
    val normals =
        poly.indices.map { i ->
            val a = poly[i]
            val b = poly[(i + 1) % n]
            val l = jsHypot(b.x - a.x, b.y - a.y).let { if (it == 0.0) 1.0 else it }
            Flat((sign * (b.y - a.y)) / l, (-sign * (b.x - a.x)) / l)
        }
    return poly.indices.map { i ->
        val p = poly[i]
        val na = normals[(i - 1 + n) % n]
        val nb = normals[i]
        val mx = na.x + nb.x
        val my = na.y + nb.y
        val dd = ((mx * mx + my * my) / 2).let { if (it == 0.0) 1.0 else it }
        Flat(p.x + (mx * by) / dd, p.y + (my * by) / dd)
    }
}

/** The frame's hull: [points] thinned to at most [most]. */
internal fun t3HullOut(f: TurnFrame, points: List<Flat>, most: Int = 32) {
    val thin = t3Thin(points, most)
    val buf = DoubleArray(2 * thin.size)
    thin.forEachIndexed { i, p ->
        buf[2 * i] = p.x
        buf[2 * i + 1] = p.y
    }
    f.setHull(buf, thin.size)
}

/** The screen box of [points]. */
internal fun t3BoxOf(points: List<Flat>, box: DoubleArray) {
    var x0 = Double.POSITIVE_INFINITY
    var y0 = Double.POSITIVE_INFINITY
    var x1 = Double.NEGATIVE_INFINITY
    var y1 = Double.NEGATIVE_INFINITY
    for (p in points) {
        if (p.x < x0) x0 = p.x
        if (p.x > x1) x1 = p.x
        if (p.y < y0) y0 = p.y
        if (p.y > y1) y1 = p.y
    }
    box[0] = x0
    box[1] = y0
    box[2] = x1
    box[3] = y1
}
