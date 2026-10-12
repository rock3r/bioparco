// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.jsHypot
import dev.sebastiano.anatomy.kit.jsRound
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * A live prism, `t3PreparePrism` and `t3EmitPrism`: a convex polygon extruded along an axis,
 * re-projected every frame. Its silhouette is the far rim along the faces turned away and the near
 * rim along the rest; each side face is a quad with the kit's tone (`EB` for upright prisms, `LB`
 * otherwise) through a smoothed staircase; an upright prism's top is its plan placed by one matrix.
 */

private val FLOOR_FRAME =
    Frame(
        Point3(0.0, 0.0, 0.0),
        Point3(0.0, 0.0, 1.0),
        Point3(1.0, 0.0, 0.0),
        Point3(0.0, 1.0, 0.0),
    )

/** A prepared prism. */
class PrismRun internal constructor(index: Int, part: PartData, unit: Double, spec: PrismSpec) :
    TurnRun(index, part, unit) {
    val n = spec.ring.size / 2
    val up = spec.up
    val ring: DoubleArray = spec.ring
    internal val nx = DoubleArray(n)
    internal val ny = DoubleArray(n)
    val frame: Frame = if (spec.up) FLOOR_FRAME else spec.frame!!
    val s0 = if (spec.up) spec.z0 else spec.s0
    val s1 = if (spec.up) spec.z1 else spec.s1
    internal val lo = DoubleArray(3 * n)
    internal val hi = DoubleArray(3 * n)
    internal val n3 = DoubleArray(3 * n)
    internal val sharp = BooleanArray(n)
    internal val inset: DoubleArray?
    internal val seams: List<Double>? = part.seams?.takeIf { it.isNotEmpty() }
    internal val cut: List<Cut>? = spec.cuts.takeIf { it.isNotEmpty() }

    init {
        for (i in 0 until n) {
            val j = (i + 1) % n
            val dx = ring[2 * j] - ring[2 * i]
            val dy = ring[2 * j + 1] - ring[2 * i + 1]
            val l = jsHypot(dx, dy).let { if (it == 0.0) 1.0 else it }
            nx[i] = dy / l
            ny[i] = -dx / l
        }
        val f = frame
        val o = doubleArrayOf(f.o.x, f.o.y, f.o.z)
        val a = doubleArrayOf(f.a.x, f.a.y, f.a.z)
        val u = doubleArrayOf(f.u.x, f.u.y, f.u.z)
        val v = doubleArrayOf(f.v.x, f.v.y, f.v.z)
        for (i in 0 until n) {
            val x = ring[2 * i]
            val y = ring[2 * i + 1]
            for (c in 0 until 3) {
                val base = o[c] + u[c] * x + v[c] * y
                lo[3 * i + c] = base + a[c] * s0
                hi[3 * i + c] = base + a[c] * s1
                n3[3 * i + c] = u[c] * nx[i] + v[c] * ny[i]
            }
        }
        for (i in 0 until n) {
            val p = (i - 1 + n) % n
            sharp[i] = nx[p] * nx[i] + ny[p] * ny[i] < jsCos(0.35)
        }
        inset =
            if (spec.bevel == 0.0) null
            else
                DoubleArray(2 * n).also { inset ->
                    for (i in 0 until n) {
                        val p = (i - 1 + n) % n
                        val mx = nx[p] + nx[i]
                        val my = ny[p] + ny[i]
                        val l = jsHypot(mx, my).let { if (it == 0.0) 1.0 else it }
                        inset[2 * i] = ring[2 * i] - (mx / l) * spec.bevel
                        inset[2 * i + 1] = ring[2 * i + 1] - (my / l) * spec.bevel
                    }
                }
    }

    override fun cloudOf(): DoubleArray = lo + hi

    override fun axesOf(): List<Point3> {
        val out = arrayListOf(frame.a)
        for (i in 0 until n) {
            val m = Point3(n3[3 * i], n3[3 * i + 1], n3[3 * i + 2])
            if (out.all { abs(it.x * m.x + it.y * m.y + it.z * m.z) < 0.9995 }) out.add(m)
        }
        return out
    }

    override fun emit(cam: TurnCam, f: TurnFrame) {
        PrismEmit(this, cam, f).run()
        emitPlanes(this, cam, f)
        emitDots(this, cam, f)
    }
}

/** One frame of a prism; holds the projected rims between the steps. */
private class PrismEmit(val run: PrismRun, val cam: TurnCam, val f: TurnFrame) {
    val n = run.n
    val m = cam.m
    val bot = DoubleArray(2 * n)
    val top = DoubleArray(2 * n)
    val a = run.frame.a
    val aV = a.x * cam.v.x + a.y * cam.v.y + a.z * cam.v.z
    val facing = DoubleArray(n)
    val sil = DoubleArray(4 * n + 8)
    val silAt = IntArray(2 * n + 4)
    var count = 0
    var i0 = -1
    var i1 = -1

    fun on(i: Int) = facing[((i % n) + n) % n] > 1e-9

    fun run() {
        project()
        silhouette()
        faces()
        if (run.up) topMatrix() else caps()
        crease()
        bevel()
        seams()
    }

    fun project() {
        val lo = run.lo
        val hi = run.hi
        for (i in 0 until n) {
            val k = 3 * i
            bot[2 * i] = cam.ox + m[0] * lo[k] + m[1] * lo[k + 1] + m[2] * lo[k + 2]
            bot[2 * i + 1] = cam.oy + m[3] * lo[k] + m[4] * lo[k + 1] + m[5] * lo[k + 2]
            top[2 * i] = cam.ox + m[0] * hi[k] + m[1] * hi[k + 1] + m[2] * hi[k + 2]
            top[2 * i + 1] = cam.oy + m[3] * hi[k] + m[4] * hi[k + 1] + m[5] * hi[k + 2]
        }
        val v = cam.v
        val n3 = run.n3
        for (i in 0 until n) facing[i] = n3[3 * i] * v.x + n3[3 * i + 1] * v.y + n3[3 * i + 2] * v.z
    }

    fun silhouette() {
        val near = if (aV >= 0) top else bot
        val far = if (aV >= 0) bot else top
        for (i in 0 until n) {
            if (on(i) && !on(i - 1)) {
                i0 = i
                break
            }
        }
        i1 = i0
        if (i0 < 0) {
            for (i in 0 until n) push(near, i, i)
        } else {
            while (on(i1)) i1++
            for (j in i0..i1) push(far, j % n, -1 - j % n)
            for (j in i1..i0 + n) push(near, j % n, j % n)
            val last = 2 * count - 2
            if (count > 2 && abs(sil[0] - sil[last]) < 1e-9 && abs(sil[1] - sil[last + 1]) < 1e-9) {
                count--
            }
        }
        val outline = t3Path(sil, count, true)
        f.d[run.role.getValue("fill")] = outline
        f.d[run.role.getValue("edge")] = if (run.cut != null) cutEdge() else outline
        boxOf(sil, count, f.box)
        f.setHull(sil, count)
    }

    fun push(rim: DoubleArray, i: Int, at: Int) {
        sil[2 * count] = rim[2 * i]
        sil[2 * count + 1] = rim[2 * i + 1]
        silAt[count] = at
        count++
    }

    /** The outline without the edges that lie in a cut plane: those aren't stroked. */
    fun cutEdge(): IsoPath {
        val cuts = run.cut!!
        val nearSet = if (aV >= 0) run.hi else run.lo
        val farSet = if (aV >= 0) run.lo else run.hi
        fun at(index: Int): Point3 {
            val a = silAt[index]
            val set = if (a < 0) farSet else nearSet
            val v = if (a < 0) -1 - a else a
            return Point3(set[3 * v], set[3 * v + 1], set[3 * v + 2])
        }
        fun inPlane(p: Point3, cut: Cut) =
            abs(cut.n.x * p.x + cut.n.y * p.y + cut.n.z * p.z - cut.d) < 1e-3
        val flags = BooleanArray(count)
        for (j in 0 until count) {
            val p = at(j)
            val q = at((j + 1) % count)
            flags[j] = cuts.any { inPlane(p, it) } && cuts.any { inPlane(p, it) && inPlane(q, it) }
        }
        return t3Broken(sil, count, flags)
    }

    fun faces() {
        val quad = DoubleArray(8)
        for (i in 0 until n) {
            val role = run.role.getValue("f$i")
            if (!(facing[i] > 1e-6)) {
                f.d[role] = IsoPath.Empty
                continue
            }
            val j = (i + 1) % n
            quad[0] = top[2 * i]
            quad[1] = top[2 * i + 1]
            quad[2] = top[2 * j]
            quad[3] = top[2 * j + 1]
            quad[4] = bot[2 * j]
            quad[5] = bot[2 * j + 1]
            quad[6] = bot[2 * i]
            quad[7] = bot[2 * i + 1]
            f.d[role] = t3Path(quad, 4, true)
            f.q[role] =
                if (run.up) {
                    t3Level((run.nx[i] * cam.lx + run.ny[i] * cam.ly + 1) / 2, T3_EB)
                } else {
                    val n3 = run.n3
                    val l = cam.l
                    t3Level(n3[3 * i] * l.x + n3[3 * i + 1] * l.y + n3[3 * i + 2] * l.z, T3_LB)
                }
            val e1 = jsHypot(quad[2] - quad[0], quad[3] - quad[1])
            val e2 = jsHypot(quad[6] - quad[0], quad[7] - quad[1])
            val area =
                abs(
                    (quad[2] - quad[0]) * (quad[7] - quad[1]) -
                        (quad[3] - quad[1]) * (quad[6] - quad[0])
                )
            val wide = area / max(e1, e2).let { if (it == 0.0) 1.0 else it }
            f.w[role] = if (wide < 0.4) jsRound(wide * 100) / 100 else -1.0
        }
    }

    fun topMatrix() {
        val z = run.s1
        f.t[run.role.getValue("top")] =
            Affine(m[0], m[3], m[1], m[4], cam.ox + m[2] * z, cam.oy + m[5] * z)
    }

    fun caps() {
        val l = cam.l
        for (side in 0 until 2) {
            val role = run.role.getValue("f${n + side}")
            val sign = if (side == 1) 1.0 else -1.0
            val face = sign * aV
            if (!(face > 1e-6)) {
                f.d[role] = IsoPath.Empty
                continue
            }
            val ring = if (side == 1) top else bot
            f.d[role] = t3Path(ring, n, true)
            f.q[role] = t3Level(sign * (a.x * l.x + a.y * l.y + a.z * l.z), T3_LB)
            val minor = minorOf(ring)
            f.w[role] = if (minor < 0.4) jsRound(minor * 100) / 100 else -1.0
        }
    }

    /** The cap's narrowest width across any of its edges. */
    fun minorOf(ring: DoubleArray): Double {
        var minor = Double.POSITIVE_INFINITY
        for (i in 0 until n) {
            val j = (i + 1) % n
            val ex = ring[2 * j] - ring[2 * i]
            val ey = ring[2 * j + 1] - ring[2 * i + 1]
            val l = jsHypot(ex, ey)
            if (l < 1e-9) continue
            var lo2 = Double.POSITIVE_INFINITY
            var hi2 = Double.NEGATIVE_INFINITY
            for (q in 0 until n) {
                val v =
                    ((ring[2 * q] - ring[2 * i]) * ey - (ring[2 * q + 1] - ring[2 * i + 1]) * ex) /
                        l
                if (v < lo2) lo2 = v
                if (v > hi2) hi2 = v
            }
            minor = min(minor, hi2 - lo2)
        }
        return minor
    }

    /** The rim from `i0` to `i1` of [ring], open. */
    fun rimOf(ring: DoubleArray): IsoPath {
        val rim = DoubleArray(2 * n + 4)
        var c = 0
        for (j in i0..i1) {
            val i = j % n
            rim[2 * c] = ring[2 * i]
            rim[2 * c + 1] = ring[2 * i + 1]
            c++
        }
        return t3Path(rim, c, false)
    }

    fun crease() {
        val role = run.role["crease"] ?: return
        var crease = IsoPath.Empty
        if (run.up) {
            if (i0 >= 0) crease = rimOf(top)
        } else {
            val seg = DoubleArray(4)
            for (i in 0 until n) {
                val p = (i - 1 + n) % n
                if (run.sharp[i] && facing[i] > 1e-6 && facing[p] > 1e-6) {
                    seg[0] = bot[2 * i]
                    seg[1] = bot[2 * i + 1]
                    seg[2] = top[2 * i]
                    seg[3] = top[2 * i + 1]
                    crease += t3Path(seg, 2, false)
                }
            }
            val capOn = booleanArrayOf(-aV > 1e-6, aV > 1e-6)
            for (side in 0 until 2) {
                if (!capOn[side] || i0 < 0) continue
                crease += rimOf(if (side == 1) top else bot)
            }
        }
        f.d[role] = crease
    }

    fun bevel() {
        val inset = run.inset ?: return
        if (run.role["v0"] == null) return
        val lines = FadedLines()
        if (TurnState.detail < 0.01) {
            f.levels = t3Faded(f, run, "v", lines)
            return
        }
        val capSide = aV >= 0
        val s = if (capSide) run.s1 else run.s0
        val capAlpha = if (run.up) 1.0 else t3Step(0.02, 0.2, abs(aV))
        val fr = run.frame
        val ins = DoubleArray(2 * n)
        for (i in 0 until n) {
            val x = inset[2 * i]
            val y = inset[2 * i + 1]
            val px = fr.o.x + fr.a.x * s + fr.u.x * x + fr.v.x * y
            val py = fr.o.y + fr.a.y * s + fr.u.y * x + fr.v.y * y
            val pz = fr.o.z + fr.a.z * s + fr.u.z * x + fr.v.z * y
            ins[2 * i] = cam.ox + m[0] * px + m[1] * py + m[2] * pz
            ins[2 * i + 1] = cam.oy + m[3] * px + m[4] * py + m[5] * pz
        }
        val rim = if (capSide) top else bot
        val seg = DoubleArray(4)
        for (i in 0 until n) {
            val p = (i - 1 + n) % n
            val ai = capAlpha * t3Step(0.0, 0.14, facing[i])
            val ap = capAlpha * t3Step(0.0, 0.14, facing[p])
            if (ai > 0.01) {
                val j = (i + 1) % n
                seg[0] = ins[2 * i]
                seg[1] = ins[2 * i + 1]
                seg[2] = ins[2 * j]
                seg[3] = ins[2 * j + 1]
                lines.add(t3Path(seg, 2, false), ai, 2 * i)
            }
            val corner = ai * (1 - ap) + ap * (1 - ai)
            if (corner > 0.01) {
                seg[0] = ins[2 * i]
                seg[1] = ins[2 * i + 1]
                seg[2] = rim[2 * i]
                seg[3] = rim[2 * i + 1]
                lines.add(t3Path(seg, 2, false), corner, 2 * i + 1)
            }
        }
        f.levels = t3Faded(f, run, "v", lines)
    }

    fun seams() {
        val seams = run.seams ?: return
        val role = run.role["inner"] ?: return
        var inner = IsoPath.Empty
        if (i0 >= 0) {
            val fr = run.frame
            val ring = run.ring
            for (s in seams) {
                val rim = DoubleArray(2 * n + 4)
                var c = 0
                for (j in i0..i1) {
                    val i = j % n
                    val x = ring[2 * i]
                    val y = ring[2 * i + 1]
                    val px = fr.o.x + fr.a.x * s + fr.u.x * x + fr.v.x * y
                    val py = fr.o.y + fr.a.y * s + fr.u.y * x + fr.v.y * y
                    val pz = fr.o.z + fr.a.z * s + fr.u.z * x + fr.v.z * y
                    rim[2 * c] = cam.ox + m[0] * px + m[1] * py + m[2] * pz
                    rim[2 * c + 1] = cam.oy + m[3] * px + m[4] * py + m[5] * pz
                    c++
                }
                inner += t3Path(rim, c, false)
            }
        }
        f.d[role] = inner
    }
}

/** The screen box of the first [count] points of [buf]. */
internal fun boxOf(buf: DoubleArray, count: Int, box: DoubleArray) {
    var x0 = Double.POSITIVE_INFINITY
    var y0 = Double.POSITIVE_INFINITY
    var x1 = Double.NEGATIVE_INFINITY
    var y1 = Double.NEGATIVE_INFINITY
    for (i in 0 until count) {
        val x = buf[2 * i]
        val y = buf[2 * i + 1]
        if (x < x0) x0 = x
        if (x > x1) x1 = x
        if (y < y0) y0 = y
        if (y > y1) y1 = y
    }
    box[0] = x0
    box[1] = y0
    box[2] = x1
    box[3] = y1
}

/**
 * The original's `t3Broken`: a closed outline drawn as open runs that skip every edge [skip] flags
 * (an edge in a cut plane).
 */
internal fun t3Broken(buf: DoubleArray, count: Int, skip: BooleanArray): IsoPath {
    var start = -1
    for (j in 0 until count) {
        if (skip[(j - 1 + count) % count] && !skip[j]) {
            start = j
            break
        }
    }
    if (start < 0) return if (skip[0]) IsoPath.Empty else t3Path(buf, count, true)
    var out = IsoPath.Empty
    var run = ArrayList<Int>()
    fun flush(close: Int) {
        run.add(close)
        out +=
            t3PathOf(
                run.map { dev.sebastiano.anatomy.kit.Flat(buf[2 * it], buf[2 * it + 1]) },
                false,
            )
        run = ArrayList()
    }
    for (step in 0 until count) {
        val j = (start + step) % count
        if (skip[j]) {
            if (run.isNotEmpty()) flush(j % count)
            continue
        }
        run.add(j)
    }
    if (run.isNotEmpty()) flush((start + count) % count)
    return out
}
