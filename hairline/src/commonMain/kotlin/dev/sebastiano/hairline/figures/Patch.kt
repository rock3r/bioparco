// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline.figures

import dev.sebastiano.hairline.Disposer
import dev.sebastiano.hairline.FigureEls
import dev.sebastiano.hairline.FigureHandle
import dev.sebastiano.hairline.PathData
import dev.sebastiano.hairline.PathNode
import dev.sebastiano.hairline.PointerHandlers
import dev.sebastiano.hairline.PrismPaths
import dev.sebastiano.hairline.Projector
import dev.sebastiano.hairline.Ring
import dev.sebastiano.hairline.Sample
import dev.sebastiano.hairline.Solid
import dev.sebastiano.hairline.Spring
import dev.sebastiano.hairline.Vec2
import dev.sebastiano.hairline.Vec3
import dev.sebastiano.hairline.cam
import dev.sebastiano.hairline.clamp
import dev.sebastiano.hairline.facing
import dev.sebastiano.hairline.fillet
import dev.sebastiano.hairline.fit
import dev.sebastiano.hairline.g
import dev.sebastiano.hairline.hull
import dev.sebastiano.hairline.open
import dev.sebastiano.hairline.path
import dev.sebastiano.hairline.poly
import dev.sebastiano.hairline.prism
import dev.sebastiano.hairline.proj
import dev.sebastiano.hairline.put
import dev.sebastiano.hairline.rings
import dev.sebastiano.hairline.rrect
import dev.sebastiano.hairline.run
import dev.sebastiano.hairline.seg
import dev.sebastiano.hairline.solid
import dev.sebastiano.hairline.spring
import dev.sebastiano.hairline.stepS
import dev.sebastiano.hairline.toFixed
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/*
 * Patch: a 2U rack patch panel, its face plate running out past a shallow body into two rack ears.
 * Twenty-four RJ45 jacks sit in two modules. The pointer lifts the cable under it and its
 * neighbours lean away, each on its own springs. The slider is the falloff radius, in ports.
 */

private const val PATCH_NC = 12
private const val PATCH_PITCH = 15.0
private const val PATCH_GAP = 8.0
private const val PATCH_EAR = 15.0
private const val PATCH_X0 = 20.0
private const val PATCH_W = 228.0
private const val PATCH_H = 44.0
private const val PATCH_T = 2.6
private const val PATCH_DEEP = 18.0
private const val PATCH_ZT = 30.0
private const val PATCH_ZB = 12.5
private const val PATCH_JW = 5.0
private const val PATCH_JH = 4.2
private const val PATCH_JD = 2.4
private const val PATCH_PL = 4.0
private const val PATCH_BL = 5.0
private const val PATCH_OUT = 6.0
private const val PATCH_LEAN = 10.0
private const val PATCH_LZ = 7.0
private const val PATCH_LIFT = 9.0
private const val PATCH_SCALE = 1.5
private const val PATCH_REST = 7

private class Port(
    val n: Int,
    val row: Int,
    val col: Int,
    val cx: Double,
    val cz: Double,
    val full: Boolean,
) {
    lateinit var jack: PathNode
    val pull = spring(0.0, eps = 0.002)
    val lx = spring(0.0)
    val lz = spring(0.0)
    lateinit var plug: Solid
    lateinit var latch: PathNode
    lateinit var boot: Solid
    lateinit var cable: PathNode
    var drawn = ""
}

private fun patchJit(n: Int) = Vec2(((n * 7) % 5 - 2).toDouble(), ((n * 11) % 7 - 3).toDouble())

private fun patchFalloff(d: Double, radius: Double) =
    if (d == 0.0) 0.0 else clamp(1 - (d - 1) / radius, 0.0, 1.0)

/** A closed tube round a run of screen points, w wide each side, with round caps. */
private fun tube(q: List<Vec2>, w: Double): PathData {
    val left = ArrayList<Vec2>()
    val right = ArrayList<Vec2>()
    val normals = ArrayList<Vec2>()
    q.forEachIndexed { i, p ->
        val a = q[maxOf(0, i - 1)]
        val b = q[minOf(q.lastIndex, i + 1)]
        val len = hypot(b.x - a.x, b.y - a.y).takeIf { it != 0.0 } ?: 1.0
        val t = Vec2((b.x - a.x) / len, (b.y - a.y) / len)
        normals.add(t)
        left.add(Vec2(p.x - t.y * w, p.y + t.x * w))
        right.add(Vec2(p.x + t.y * w, p.y - t.x * w))
    }
    return poly(
        left +
            tubeCap(q.last(), normals.last(), w) +
            right.reversed() +
            tubeCap(q.first(), normals.first(), -w)
    )
}

private fun tubeCap(c: Vec2, t: Vec2, w: Double): List<Vec2> =
    (1 until 6).map { k ->
        val th = k / 6.0 * PI
        Vec2(c.x + w * (-t.y * cos(th) + t.x * sin(th)), c.y + w * (t.x * cos(th) + t.y * sin(th)))
    }

internal fun mountPatch(els: FigureEls, value: Double): FigureHandle = PatchFigure(els, value)

private class PatchFigure(private val els: FigureEls, value: Double) : FigureHandle {
    private val bag = Disposer()
    private val c = cam(45.0, 0.5, PATCH_SCALE)
    private val tipZ = PATCH_ZT + 27 + PATCH_LIFT + PATCH_LZ
    private val lowZ = PATCH_ZB - 26 - PATCH_LZ
    private val p: Projector
    private val front: (Sample) -> Boolean
    private var radius = value
    private var over = -1
    private var lit: Port? = null
    private val g = els.svg.g()
    private val ports = ArrayList<Port>()
    private lateinit var plugG: dev.sebastiano.hairline.Group
    private lateinit var cabG: dev.sebastiano.hairline.Group

    init {
        fitCamera()
        p = proj(c)
        front = facing(c)
        buildPanel()
        buildPorts()
        buildConnections()
        aim(PATCH_REST, 1.6, 0.55)
        ports.flatMap(::springs).forEach { it.x = it.t }
        light(ports[PATCH_REST])
        els.read.textContent = "rest"
    }

    private val loop =
        els.stage.register { dt, _ ->
            var moving = false
            ports.forEach { pt ->
                springs(pt).forEach { if (stepS(it, dt)) moving = true }
                if (pt.full) drawPort(pt)
            }
            moving
        }

    init {
        bag.add(loop::unregister)
        bag.add(
            els.stage.pointer(
                object : PointerHandlers {
                    override fun move(p: Vec2) {
                        val next = hit(p)
                        if (next != over) {
                            over = next
                            retarget()
                        }
                    }

                    override fun leave() {
                        over = -1
                        retarget()
                    }
                }
            )
        )
        bag.add { els.svg.clear() }
    }

    private fun fitCamera() {
        fit(
            c,
            listOf(
                Vec3(0.0, -PATCH_DEEP, PATCH_H),
                Vec3(PATCH_W, -PATCH_DEEP, PATCH_H),
                Vec3(0.0, 0.0, 0.0),
                Vec3(PATCH_W, 0.0, 0.0),
                Vec3(PATCH_X0, 34.0, tipZ),
                Vec3(PATCH_W - PATCH_X0, 34.0, lowZ),
                Vec3(PATCH_X0, 34.0, lowZ),
            ),
            200.0,
            166.0,
        )
    }

    private fun at(ring: Ring, cx: Double, y: Double, cz: Double) = ring.map {
        p(cx + it.u, y, cz + it.v)
    }

    private fun seen(q: Sample) = 0.612 * q.nu + 0.5 * q.nv > 0

    private fun slab(ring: Ring, inner: Ring, cx: Double, cz: Double, y0: Double, y1: Double) =
        PrismPaths(
            poly(hull(at(ring, cx, y0, cz) + at(ring, cx, y1, cz))),
            open(at(run(inner, ::seen), cx, y1, cz)),
        )

    private fun flat(ring: Ring) = ring.map { Vec2(it.u, it.v) }

    private fun face(pts: List<Vec2>, cls: String) =
        g.path(cls, poly(pts.map { p(it.x, 0.0, it.y) }))

    private fun buildPanel() {
        val (br, bi) =
            rings(PATCH_EAR + 2, -PATCH_DEEP, PATCH_W - PATCH_EAR - 2, -PATCH_T, 3.0, 1.6)
        put(solid(g), prism(p, front, br, bi, 3.0, PATCH_H - 3))
        put(
            solid(g),
            slab(
                rrect(0.0, 0.0, PATCH_W, PATCH_H, 3.0),
                rrect(0.7, 0.7, PATCH_W - 0.7, PATCH_H - 0.7, 2.3),
                0.0,
                0.0,
                -PATCH_T,
                0.0,
            ),
        )
        for (ex in listOf(PATCH_EAR / 2 + 1, PATCH_W - PATCH_EAR / 2 - 1)) for (ez in
            listOf(8.0, PATCH_H - 8)) face(
            flat(rrect(ex - 3.4, ez - 1.5, ex + 3.4, ez + 1.5, 1.5, 3)),
            "nf",
        )
    }

    private fun buildPorts() {
        repeat(2 * PATCH_NC) { n ->
            val row = if (n < PATCH_NC) 0 else 1
            val col = n % PATCH_NC
            ports.add(
                Port(
                    n,
                    row,
                    col,
                    PATCH_X0 + (col + 0.5) * PATCH_PITCH + if (col >= 6) PATCH_GAP else 0.0,
                    if (row == 1) PATCH_ZB else PATCH_ZT,
                    n !in listOf(2, 10, 18),
                )
            )
        }
        for (m in listOf(0, 6)) {
            val a = ports[m].cx - PATCH_PITCH / 2 + 1
            val b = ports[m + 5].cx + PATCH_PITCH / 2 - 1
            face(
                flat(rrect(a, PATCH_ZB - PATCH_JH - 3, b, PATCH_ZT + PATCH_JH + 4.3, 2.4)),
                "lo nf",
            )
        }
        val jack =
            fillet(
                listOf(
                    Vec2(-5.0, -4.2),
                    Vec2(5.0, -4.2),
                    Vec2(5.0, 4.2),
                    Vec2(1.9, 4.2),
                    Vec2(1.9, 5.9),
                    Vec2(-1.9, 5.9),
                    Vec2(-1.9, 4.2),
                    Vec2(-5.0, 4.2),
                ),
                listOf(1.0, 1.0, 1.0, 0.4, 0.5, 0.5, 0.4, 1.0),
            )
        ports.forEach { pt ->
            pt.jack = face(jack.map { Vec2(pt.cx + it.x, pt.cz + it.y) }, "nf")
            g.path(
                "lo nf",
                open(
                    listOf(
                            Vec2(-PATCH_JW + PATCH_JD, PATCH_JH),
                            Vec2(-PATCH_JW + PATCH_JD, -PATCH_JH + 0.816 * PATCH_JD),
                            Vec2(PATCH_JW, -PATCH_JH + 0.816 * PATCH_JD),
                        )
                        .map { p(pt.cx + it.x, 0.0, pt.cz + it.y) }
                ),
            )
        }
    }

    private fun buildConnections() {
        plugG = g.g()
        cabG = g.g()
        ports.sortedWith(compareBy<Port> { it.col }.thenBy { it.row }).forEach { pt ->
            if (pt.full) {
                pt.plug = solid(plugG)
                pt.latch = pt.plug.g.path("lo nf")
                pt.boot = solid(plugG)
                pt.cable = cabG.path("sil")
            }
        }
    }

    private fun springs(pt: Port): List<Spring> = listOf(pt.pull, pt.lx, pt.lz)

    private fun drawPort(pt: Port) {
        val key = springs(pt).joinToString { toFixed(it.x, 3) }
        if (key == pt.drawn) return
        pt.drawn = key
        val y1 = PATCH_PL + PATCH_OUT * pt.pull.x
        val y2 = y1 + PATCH_BL
        val plug = rrect(-4.3, -3.5, 4.3, 3.5, 1.2, 3)
        val plugIn = rrect(-3.6, -2.8, 3.6, 2.8, 0.6, 3)
        put(pt.plug, slab(plug, plugIn, pt.cx, pt.cz, 0.0, y1))
        pt.latch.d = seg(p(pt.cx, 0.6, pt.cz + 3.5), p(pt.cx, y1 - 0.8, pt.cz + 3.5))
        put(
            pt.boot,
            PrismPaths(
                poly(
                    hull(
                        at(rrect(-3.7, -3.0, 3.7, 3.0, 1.6, 3), pt.cx, y1, pt.cz) +
                            at(rrect(-2.4, -2.4, 2.4, 2.4, 2.3, 3), pt.cx, y2, pt.cz)
                    )
                ),
                PathData.EMPTY,
            ),
        )
        pt.cable.d = tube(cableCurve(pt, y2), 1.8 * PATCH_SCALE)
    }

    private fun cableCurve(pt: Port, y2: Double): List<Vec2> {
        val up = if (pt.row == 1) -1.0 else 1.0
        val out = if (pt.row == 1) 6.0 else 2.0
        val lean = if (pt.row == 1) 2.0 else 3.0
        val jit = patchJit(pt.n)
        val p0 = Vec3(pt.cx, y2 - 1.5, pt.cz)
        val p1 =
            if (pt.row == 1) Vec3(pt.cx + 0.5, y2 + out + 4 * pt.pull.x, pt.cz)
            else Vec3(pt.cx, y2 + 1 + 2 * pt.pull.x, pt.cz + 6)
        val p3 =
            Vec3(
                pt.cx + lean + jit.x + pt.lx.x,
                y2 + out + 1 + PATCH_LIFT * pt.pull.x,
                pt.cz + up * (24 + jit.y) + pt.lz.x + PATCH_LIFT * pt.pull.x,
            )
        val p2 = Vec3(p3.x - 0.6, p3.y - 1, p3.z - up * 12)
        return (0..16).map { i ->
            val s = i / 16.0
            val a = (1 - s).pow(3)
            val b = 3 * (1 - s).pow(2) * s
            val cc = 3 * (1 - s) * s * s
            val d = s.pow(3)
            p(
                a * p0.x + b * p1.x + cc * p2.x + d * p3.x,
                a * p0.y + b * p1.y + cc * p2.y + d * p3.y,
                a * p0.z + b * p1.z + cc * p2.z + d * p3.z,
            )
        }
    }

    private fun aim(active: Int, radius: Double, depth: Double) {
        val selected = ports[active]
        ports.forEach { pt ->
            val dx = (pt.col - selected.col).toDouble()
            val dr = (pt.row - selected.row).toDouble()
            val d = hypot(dx, dr)
            val f = patchFalloff(d, radius) * depth
            pt.pull.t = if (pt === selected) depth else 0.0
            pt.lx.t = if (d != 0.0) clamp(dx / d * f * PATCH_LEAN, -PATCH_LEAN, PATCH_LEAN) else 0.0
            pt.lz.t = if (d != 0.0) clamp(-dr / d * f * PATCH_LZ, -PATCH_LZ, PATCH_LZ) else 0.0
        }
    }

    private fun light(pt: Port) {
        if (pt === lit) return
        fun mark(q: Port, on: Boolean) =
            (if (q.full) listOf(q.plug.sil, q.boot.sil, q.cable) else listOf(q.jack)).forEach {
                it.classList.toggle("hi", on)
            }
        lit?.let { mark(it, false) }
        lit = pt
        mark(pt, true)
    }

    private fun hit(q: Vec2): Int {
        val o = p(0.0, 2.5, 0.0)
        val ux = p(1.0, 2.5, 0.0)
        val uz = p(0.0, 2.5, 1.0)
        val ex = Vec2(ux.x - o.x, ux.y - o.y)
        val ez = Vec2(uz.x - o.x, uz.y - o.y)
        val det = ex.x * ez.y - ex.y * ez.x
        val qx = q.x - o.x
        val qy = q.y - o.y
        val x = (qx * ez.y - qy * ez.x) / det
        val z = (ex.x * qy - ex.y * qx) / det
        if (
            x < PATCH_X0 - 2 ||
                x > PATCH_W - PATCH_X0 + 2 ||
                z < PATCH_ZB - PATCH_JH - 6 ||
                z > PATCH_ZT + PATCH_JH + 8
        )
            return -1
        return ports
            .minBy { abs(it.cx - x) + if (abs(it.cz - z) > (PATCH_ZT - PATCH_ZB) / 2) 1e3 else 0.0 }
            .n
    }

    private fun retarget() {
        if (over >= 0) {
            aim(over, radius, 1.0)
            light(ports[over])
            els.read.textContent = "port ${(over + 1).toString().padStart(2, '0')}"
        } else {
            aim(PATCH_REST, 1.6, 0.55)
            light(ports[PATCH_REST])
            els.read.textContent = "rest"
        }
        loop.wake()
    }

    override fun set(value: Double) {
        radius = value
        if (over >= 0) retarget()
    }

    override fun destroy() = bag.dispose()
}
