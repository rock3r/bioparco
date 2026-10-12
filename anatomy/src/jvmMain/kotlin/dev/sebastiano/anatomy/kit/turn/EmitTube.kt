// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.jsHypot
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/*
 * A live tube chunk, `t3PrepareTube` and `t3EmitTube`: the kit's `tubePieces` drawn from a route in
 * the group's rest-local coordinates. Its body is a ribbon of half-width `r·k` round the projected
 * centreline (with elliptical caps at real ends), its shine and shade stripes run onto the caps,
 * and its rings follow the cross-section at stations along the whole route.
 */

/** A route by arc length: `t3RouteOf`. */
internal class TubeLine(val flat: DoubleArray) {
    val n = flat.size / 3
    val along = DoubleArray(n)
    val total: Double

    init {
        for (i in 1 until n) {
            along[i] =
                along[i - 1] +
                    jsHypot(
                        flat[3 * i] - flat[3 * i - 3],
                        flat[3 * i + 1] - flat[3 * i - 2],
                        flat[3 * i + 2] - flat[3 * i - 1],
                    )
        }
        total = along[n - 1]
    }

    fun point(at: Double): Point3 {
        if (at <= 0) return Point3(flat[0], flat[1], flat[2])
        if (at >= along[n - 1]) return Point3(flat[3 * n - 3], flat[3 * n - 2], flat[3 * n - 1])
        var lo = 0
        var hi = n - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) shr 1
            if (along[mid] <= at) lo = mid else hi = mid
        }
        val span = (along[hi] - along[lo]).let { if (it == 0.0) 1.0 else it }
        val t = (at - along[lo]) / span
        return Point3(
            flat[3 * lo] + (flat[3 * hi] - flat[3 * lo]) * t,
            flat[3 * lo + 1] + (flat[3 * hi + 1] - flat[3 * lo + 1]) * t,
            flat[3 * lo + 2] + (flat[3 * hi + 2] - flat[3 * lo + 2]) * t,
        )
    }

    fun cut(from: Double, to: Double): List<Point3> {
        val out = ArrayList<Point3>()
        for (i in 1 until n) {
            val start = along[i - 1]
            val end = along[i]
            if (end >= from && start <= to) {
                val length = (end - start).let { if (it == 0.0) 1.0 else it }
                val t0 = max(0.0, (from - start) / length)
                val t1 = min(1.0, (to - start) / length)
                fun at(t: Double) =
                    Point3(
                        flat[3 * i - 3] + (flat[3 * i] - flat[3 * i - 3]) * t,
                        flat[3 * i - 2] + (flat[3 * i + 1] - flat[3 * i - 2]) * t,
                        flat[3 * i - 1] + (flat[3 * i + 2] - flat[3 * i - 1]) * t,
                    )
                if (out.isEmpty()) out.add(at(t0))
                out.add(at(t1))
            }
        }
        return out
    }

    fun tangent(at: Double): Point3 {
        val a = point(max(0.0, at - 0.6))
        val b = point(min(total, at + 0.6))
        return t3Unit(Point3(b.x - a.x, b.y - a.y, b.z - a.z))
    }
}

/** A stretch of the centreline: points and their tangents (not unit), flat `x, y, z`. */
internal class TubeStretch(val count: Int, val p3: DoubleArray, val d3: DoubleArray)

/** A sample on a ring: the centre, the tangent, and the angle round the front. */
internal class RingSample(val c: Point3, val t: Point3, val theta: Double)

/** A prepared tube chunk. */
class TubeRun
internal constructor(index: Int, part: PartData, unit: Double, spec: TubeSpec, source: RouteData) :
    TurnRun(index, part, unit) {
    val r: Double = source.r
    val wide: Boolean = source.wide
    val hue = source.hue
    internal val body: TubeStretch
    internal val stripe: TubeStretch
    internal val capped: BooleanArray
    internal val tips: List<Point3>
    internal val ringSegments: List<Pair<RingSample, RingSample>>?
    internal val ringFade: Pair<Double, Double>?

    init {
        val route = TubeLine(source.points)
        val total = route.total
        val step = max(0.05, min(source.spacing, total / 4) * 0.5)
        val lead = 0.5
        fun partOf(start: Double, end: Double): TubeStretch {
            val points = route.cut(max(0.0, start), min(total, end))
            var walked = max(0.0, start)
            val count = points.size
            val p3 = DoubleArray(3 * count)
            val d3 = DoubleArray(3 * count)
            for (i in 0 until count) {
                if (i > 0) {
                    val (x, y, z) = points[i]
                    val q = points[i - 1]
                    walked += jsHypot(x - q.x, y - q.y, z - q.z)
                }
                val a = route.point(max(0.0, min(total, walked - step)))
                val b = route.point(max(0.0, min(total, walked + step)))
                p3[3 * i] = points[i].x
                p3[3 * i + 1] = points[i].y
                p3[3 * i + 2] = points[i].z
                d3[3 * i] = b.x - a.x
                d3[3 * i + 1] = b.y - a.y
                d3[3 * i + 2] = b.z - a.z
            }
            return TubeStretch(count, p3, d3)
        }
        val openStart = spec.open0 && spec.from > 0
        val openEnd = spec.open1 && spec.to < total
        body =
            partOf(
                if (openStart) spec.from - lead else spec.from,
                if (openEnd) spec.to + lead else spec.to,
            )
        stripe =
            if (openStart || openEnd) {
                partOf(
                    if (openStart) spec.from - 2 * lead else spec.from,
                    if (openEnd) spec.to + 2 * lead else spec.to,
                )
            } else body
        val end0 = !spec.open0 && (spec.from <= 1e-6 || spec.gap0)
        val end1 = !spec.open1 && (spec.to >= total - 1e-6 || spec.gap1)
        capped =
            booleanArrayOf(
                end0 && (spec.gap0 || spec.caps.first),
                end1 && (spec.gap1 || spec.caps.second),
            )
        val p = body.p3
        val last = body.count - 1
        fun vec(i: Int, j: Int) =
            t3Unit(
                Point3(
                    p[3 * i] - p[3 * j],
                    p[3 * i + 1] - p[3 * j + 1],
                    p[3 * i + 2] - p[3 * j + 2],
                )
            )
        tips = listOf(vec(1, 0), vec(last, last - 1))
        val rings = source.rings
        ringFade = rings?.fade
        ringSegments = rings?.let {
            val lo = if (openStart) spec.from - 2 * lead else spec.from
            val hi = if (openEnd) spec.to + 2 * lead else spec.to
            ringSegmentsOf(route, it, lo, hi, openStart, openEnd, r)
        }
    }

    override fun cloudOf(): DoubleArray {
        val out = ArrayList<Double>()
        val b = body
        val stride = max(1, floor(b.count.toDouble() / 8).toInt())
        var i = 0
        while (i < b.count) {
            val t = t3Unit(Point3(b.d3[3 * i], b.d3[3 * i + 1], b.d3[3 * i + 2]))
            val (u, v) = t3Basis(t)
            ringCloud(
                Frame(Point3(b.p3[3 * i], b.p3[3 * i + 1], b.p3[3 * i + 2]), t, u, v),
                0.0,
                r,
                8,
                out,
            )
            i += stride
        }
        return out.toDoubleArray()
    }

    override fun axesOf(): List<Point3> = tips.toList()

    override fun emit(cam: TurnCam, f: TurnFrame) = TubeEmit(this, cam, f).run()
}

/** The ring segments over `lo…hi` of [route]: `t3PrepareTube`'s rings. */
private fun ringSegmentsOf(
    route: TubeLine,
    rings: dev.sebastiano.anatomy.kit.Rings,
    lo: Double,
    hi: Double,
    openStart: Boolean,
    openEnd: Boolean,
    r: Double,
): List<Pair<RingSample, RingSample>> {
    val total = route.total
    val pitch = rings.pitch
    val twist = rings.twist
    val margin = if (twist != 0.0) 0.0 else min(pitch * 0.35, (hi - lo) / 2)
    val from = max(lo, if (!openStart) lo + margin else lo)
    val to = min(hi, if (!openEnd) hi - margin else hi)
    val segments = ArrayList<Pair<RingSample, RingSample>>()
    if (!(to > from)) return segments
    val hands =
        if (twist != 0.0) (if (rings.cross) listOf(1.0, -1.0) else listOf(1.0)) else listOf(0.0)
    val reach = kotlin.math.abs(twist) * r
    val first = ceil((from - reach - rings.phase) / pitch).toInt()
    val lastIndex = floor((to + reach - rings.phase) / pitch).toInt()
    fun surface(t: Double, theta: Double): RingSample {
        val at = max(0.0, min(total, t))
        return RingSample(route.point(at), route.tangent(at), theta)
    }
    val steps = rings.steps
    for (index in first..lastIndex) {
        val station = rings.phase + index * pitch
        for (hand in hands) {
            val samples =
                (0..steps).map { k ->
                    val theta = (k.toDouble() / steps) * Math.PI
                    Pair(station + hand * twist * r * (k.toDouble() / steps - 0.5), theta)
                }
            for (k in 0 until steps) {
                var a = samples[k]
                var b = samples[k + 1]
                if ((a.first < from && b.first < from) || (a.first > to && b.first > to)) continue
                fun clipTo(
                    x: Pair<Double, Double>,
                    y: Pair<Double, Double>,
                    edge: Double,
                ): Pair<Double, Double> {
                    val span = (y.first - x.first).let { if (it == 0.0) 1e-9 else it }
                    return Pair(edge, x.second + ((y.second - x.second) * (edge - x.first)) / span)
                }
                if (a.first < from) a = clipTo(a, b, from)
                if (b.first < from) b = clipTo(b, a, from)
                if (a.first > to) a = clipTo(a, b, to)
                if (b.first > to) b = clipTo(b, a, to)
                segments.add(surface(a.first, a.second) to surface(b.first, b.second))
            }
        }
    }
    return segments
}

private val T3_SHINE: Flat = jsHypot(-0.55, -0.835).let { l -> Flat(-0.55 / l, -0.835 / l) }

/** One frame of the centreline on screen: a point, its normal and the half-width. */
internal class SideFrame(val p: Flat, val nx: Double, val ny: Double, val half: Double)

/** The projected ribbon: centreline, sides, stripes, `t3Sides`. */
private class TubeSides(stretch: TubeStretch, cam: TurnCam, r: Double) {
    val n = stretch.count
    val flat = DoubleArray(2 * n)
    val left = ArrayList<Flat>(n)
    val right = ArrayList<Flat>(n)
    val shineA = ArrayList<Flat>(n)
    val shineB = ArrayList<Flat>(n)
    val shadeA = ArrayList<Flat>(n)
    val shadeB = ArrayList<Flat>(n)
    val frames = ArrayList<SideFrame>(n)

    init {
        val m = cam.m
        val half = r * cam.k
        val p3 = stretch.p3
        val d3 = stretch.d3
        for (i in 0 until n) {
            val x = p3[3 * i]
            val y = p3[3 * i + 1]
            val z = p3[3 * i + 2]
            val px = cam.ox + m[0] * x + m[1] * y + m[2] * z
            val py = cam.oy + m[3] * x + m[4] * y + m[5] * z
            val dx = m[0] * d3[3 * i] + m[1] * d3[3 * i + 1] + m[2] * d3[3 * i + 2]
            val dy = m[3] * d3[3 * i] + m[4] * d3[3 * i + 1] + m[5] * d3[3 * i + 2]
            val l = jsHypot(dx, dy).let { if (it == 0.0) 1.0 else it }
            val nx = -dy / l
            val ny = dx / l
            flat[2 * i] = px
            flat[2 * i + 1] = py
            frames.add(SideFrame(Flat(px, py), nx, ny, half))
            left.add(Flat(px + nx * half, py + ny * half))
            right.add(Flat(px - nx * half, py - ny * half))
            val toward = nx * T3_SHINE.x + ny * T3_SHINE.y
            val shineAt = half * 0.44 * toward
            val shineHalf = half * 0.24
            shineA.add(Flat(px + nx * (shineAt + shineHalf), py + ny * (shineAt + shineHalf)))
            shineB.add(Flat(px + nx * (shineAt - shineHalf), py + ny * (shineAt - shineHalf)))
            val shadeAt = -half * 0.62 * toward
            val shadeHalf = half * 0.2
            shadeA.add(Flat(px + nx * (shadeAt + shadeHalf), py + ny * (shadeAt + shadeHalf)))
            shadeB.add(Flat(px + nx * (shadeAt - shadeHalf), py + ny * (shadeAt - shadeHalf)))
        }
    }
}

/** A cap ring, the half drawn as an edge, and the other half: `t3TubeCap`. */
private class CapRing(val ring: List<Flat>, val arc: List<Flat>, val inner: List<Flat>)

private fun tubeCap(
    cam: TurnCam,
    centre: Point3,
    tangent: Point3,
    radius: Double,
    inward: Flat,
): CapRing {
    val v = cam.v
    val m = cam.m
    var n1 =
        Point3(
            tangent.y * v.z - tangent.z * v.y,
            tangent.z * v.x - tangent.x * v.z,
            tangent.x * v.y - tangent.y * v.x,
        )
    if (jsHypot(n1.x, n1.y, n1.z) < 1e-6) {
        val helper =
            if (kotlin.math.abs(tangent.z) > 0.9) Point3(1.0, 0.0, 0.0) else Point3(0.0, 0.0, 1.0)
        n1 =
            Point3(
                helper.y * tangent.z - helper.z * tangent.y,
                helper.z * tangent.x - helper.x * tangent.z,
                helper.x * tangent.y - helper.y * tangent.x,
            )
    }
    n1 = t3Unit(n1)
    val n2 =
        t3Unit(
            Point3(
                tangent.y * n1.z - tangent.z * n1.y,
                tangent.z * n1.x - tangent.x * n1.z,
                tangent.x * n1.y - tangent.y * n1.x,
            )
        )
    val steps = 28
    fun proj(p: Point3) =
        Flat(
            cam.ox + m[0] * p.x + m[1] * p.y + m[2] * p.z,
            cam.oy + m[3] * p.x + m[4] * p.y + m[5] * p.z,
        )
    val ring =
        (0 until steps).map { index ->
            val angle = (index.toDouble() / steps) * T3_TAU
            val cs = radius * jsCos(angle)
            val sn = radius * jsSin(angle)
            proj(
                Point3(
                    centre.x + n1.x * cs + n2.x * sn,
                    centre.y + n1.y * cs + n2.y * sn,
                    centre.z + n1.z * cs + n2.z * sn,
                )
            )
        }
    val c = proj(centre)
    val probe = proj(Point3(centre.x + n2.x, centre.y + n2.y, centre.z + n2.z))
    val along = (probe.x - c.x) * inward.x + (probe.y - c.y) * inward.y
    val from = if (along > 0) steps / 2 else 0
    fun half(start: Int) = (0..steps / 2).map { ring[(start + it) % steps] }
    return CapRing(ring, half(from), half((from + steps / 2) % steps))
}

/** Where a stripe from offset [from] to [to] runs onto a cap's visible half: `t3StripeEnd`. */
private fun stripeEnd(half: List<Flat>, frame: SideFrame, from: Double, to: Double): List<Flat> {
    val centre = frame.p
    fun offset(p: Flat) = (p.x - centre.x) * frame.nx + (p.y - centre.y) * frame.ny
    val values = half.map { offset(it) }
    val lo = min(from, to)
    val hi = max(from, to)
    val points = ArrayList<Flat>()
    fun cross(k: Int, level: Double): Flat {
        val t =
            (level - values[k]) / (values[k + 1] - values[k]).let { if (it == 0.0) 1e-9 else it }
        return Flat(
            half[k].x + (half[k + 1].x - half[k].x) * t,
            half[k].y + (half[k + 1].y - half[k].y) * t,
        )
    }
    for (k in half.indices) {
        if (values[k] >= lo && values[k] <= hi) points.add(half[k])
        if (k < half.size - 1) {
            for (level in doubleArrayOf(lo, hi)) {
                if ((values[k] - level) * (values[k + 1] - level) < 0) points.add(cross(k, level))
            }
        }
    }
    val keyed = points.map { it to offset(it) }
    val sorted =
        if (from > to) keyed.sortedWith { x, y -> sgn(y.second - x.second) }
        else keyed.sortedWith { x, y -> sgn(x.second - y.second) }
    return sorted.map { it.first }
}

private fun sgn(d: Double): Int = if (d < 0) -1 else if (d > 0) 1 else 0

private class Tip(val half: List<Flat>, val frame: SideFrame)

/** One frame of a tube chunk. */
private class TubeEmit(val run: TubeRun, val cam: TurnCam, val f: TurnFrame) {
    val sides = TubeSides(run.body, cam, run.r)
    val bands = if (run.stripe === run.body) sides else TubeSides(run.stripe, cam, run.r)
    val tips = arrayOfNulls<Tip>(2)
    val quads = ArrayList<List<Flat>>()

    fun run() {
        val n = sides.n
        val left = sides.left
        val right = sides.right
        val loop = left + right.reversed()
        var area = 0.0
        for (i in loop.indices) {
            val a = loop[i]
            val b = loop[(i + 1) % loop.size]
            area += a.x * b.y - b.x * a.y
        }
        val sign = sign(area)
        var body = t3PathOf(loop, true)
        var edges = t3PathOf(left, false) + t3PathOf(right, false)
        val last = n - 1
        for (i in 0 until last) quads.add(listOf(left[i], left[i + 1], right[i + 1], right[i]))
        for (side in 0 until 2) {
            if (!run.capped[side]) continue
            val at = if (side == 1) last else 0
            val other = if (side == 1) last - 1 else 1
            val tangent = run.tips[side]
            val outward = if (side == 1) tangent else Point3(-tangent.x, -tangent.y, -tangent.z)
            val inward =
                Flat(
                    sides.flat[2 * other] - sides.flat[2 * at],
                    sides.flat[2 * other + 1] - sides.flat[2 * at + 1],
                )
            val p3 = run.body.p3
            val centre = Point3(p3[3 * at], p3[3 * at + 1], p3[3 * at + 2])
            val cap = tubeCap(cam, centre, tangent, run.r, inward)
            var ringArea = 0.0
            for (i in cap.ring.indices) {
                val a = cap.ring[i]
                val b = cap.ring[(i + 1) % cap.ring.size]
                ringArea += a.x * b.y - b.x * a.y
            }
            body += t3PathOf(if (sign(ringArea) == sign) cap.ring else cap.ring.reversed(), true)
            edges += t3PathOf(cap.arc, false)
            val v = cam.v
            val open = outward.x * v.x + outward.y * v.y + outward.z * v.z > 0
            if (open) edges += t3PathOf(cap.inner, false)
            tips[side] = Tip(if (open) cap.inner else cap.arc, sides.frames[at])
            quads.add(cap.ring)
        }
        f.d[run.role.getValue("body")] = body
        f.d[run.role.getValue("edges")] = edges
        if (run.wide) stripes()
        if (run.ringSegments != null) rings()
        t3BoxOf(left + right, f.box)
        for (q in quads) {
            for (p in q) {
                if (p.x < f.box[0]) f.box[0] = p.x
                if (p.y < f.box[1]) f.box[1] = p.y
                if (p.x > f.box[2]) f.box[2] = p.x
                if (p.y > f.box[3]) f.box[3] = p.y
            }
        }
        val list =
            f.quadList?.takeIf { it.size == quads.size }
                ?: MutableList(quads.size) { QuadBuf(DoubleArray(0), 0) }
        f.quadList = list
        quads.forEachIndexed { qi, q ->
            val entry = list[qi]
            if (entry.buf.size < 2 * q.size) entry.buf = DoubleArray(2 * q.size)
            q.forEachIndexed { i, p ->
                entry.buf[2 * i] = p.x
                entry.buf[2 * i + 1] = p.y
            }
            entry.n = q.size
        }
        f.quadN = quads.size
        f.hullN = 0
    }

    fun stripes() {
        fun toward(frame: SideFrame) = frame.nx * T3_SHINE.x + frame.ny * T3_SHINE.y
        fun shineOffsets(frame: SideFrame) =
            Pair(
                frame.half * 0.44 * toward(frame) + frame.half * 0.24,
                frame.half * 0.44 * toward(frame) - frame.half * 0.24,
            )
        fun shadeOffsets(frame: SideFrame) =
            Pair(
                -frame.half * 0.62 * toward(frame) + frame.half * 0.2,
                -frame.half * 0.62 * toward(frame) - frame.half * 0.2,
            )
        fun band(
            a: List<Flat>,
            b: List<Flat>,
            offsets: (SideFrame) -> Pair<Double, Double>,
        ): IsoPath {
            val head =
                tips[1]?.let {
                    stripeEnd(it.half, it.frame, offsets(it.frame).first, offsets(it.frame).second)
                }
            val tail =
                tips[0]?.let {
                    stripeEnd(it.half, it.frame, offsets(it.frame).second, offsets(it.frame).first)
                }
            return t3PathOf(a + head.orEmpty() + b.reversed() + tail.orEmpty(), true)
        }
        f.d[run.role.getValue("shine")] = band(bands.shineA, bands.shineB, ::shineOffsets)
        f.d[run.role.getValue("shade")] = band(bands.shadeA, bands.shadeB, ::shadeOffsets)
    }

    fun rings() {
        val lines = FadedLines()
        val v = cam.v
        val m = cam.m
        val r = run.r
        val seg = DoubleArray(4)
        fun surface(sample: RingSample, index: Int): Double? {
            val t = sample.t
            var n1 = Point3(t.y * v.z - t.z * v.y, t.z * v.x - t.x * v.z, t.x * v.y - t.y * v.x)
            val l1 = jsHypot(n1.x, n1.y, n1.z)
            if (l1 < 1e-6) return null
            n1 = Point3(n1.x / l1, n1.y / l1, n1.z / l1)
            var n2 =
                t3Unit(
                    Point3(
                        t.y * n1.z - t.z * n1.y,
                        t.z * n1.x - t.x * n1.z,
                        t.x * n1.y - t.y * n1.x,
                    )
                )
            if (n2.x * v.x + n2.y * v.y + n2.z * v.z < 0) n2 = Point3(-n2.x, -n2.y, -n2.z)
            val cs = jsCos(sample.theta)
            val sn = jsSin(sample.theta)
            val normal = Point3(n1.x * cs + n2.x * sn, n1.y * cs + n2.y * sn, n1.z * cs + n2.z * sn)
            val c = sample.c
            val p = Point3(c.x + normal.x * r, c.y + normal.y * r, c.z + normal.z * r)
            seg[index] = cam.ox + m[0] * p.x + m[1] * p.y + m[2] * p.z
            seg[index + 1] = cam.oy + m[3] * p.x + m[4] * p.y + m[5] * p.z
            return normal.x * v.x + normal.y * v.y + normal.z * v.z
        }
        val fade = run.ringFade
        run.ringSegments!!.forEachIndexed { index, (a, b) ->
            val fa = surface(a, 0)
            val fb = surface(b, 2)
            if (fa == null || fb == null) return@forEachIndexed
            val alpha = if (fade != null) t3Step(fade.first, fade.second, (fa + fb) / 2) else 1.0
            if (alpha * TURN_LEVELS < 0.5) return@forEachIndexed
            lines.add(t3Path(seg, 2, false), alpha, index)
        }
        f.levels = t3Faded(f, run, "r", lines)
    }
}
