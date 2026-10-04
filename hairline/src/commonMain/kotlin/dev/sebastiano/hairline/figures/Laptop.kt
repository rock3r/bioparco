// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline.figures

import dev.sebastiano.hairline.CircleNode
import dev.sebastiano.hairline.Disposer
import dev.sebastiano.hairline.FigureEls
import dev.sebastiano.hairline.FigureHandle
import dev.sebastiano.hairline.Group
import dev.sebastiano.hairline.PathNode
import dev.sebastiano.hairline.PointerHandlers
import dev.sebastiano.hairline.PrismPaths
import dev.sebastiano.hairline.Ring
import dev.sebastiano.hairline.Solid
import dev.sebastiano.hairline.Vec2
import dev.sebastiano.hairline.Vec3
import dev.sebastiano.hairline.cam
import dev.sebastiano.hairline.circle
import dev.sebastiano.hairline.clamp
import dev.sebastiano.hairline.facing
import dev.sebastiano.hairline.fit
import dev.sebastiano.hairline.g
import dev.sebastiano.hairline.hull
import dev.sebastiano.hairline.join
import dev.sebastiano.hairline.jsRound
import dev.sebastiano.hairline.lerp
import dev.sebastiano.hairline.open
import dev.sebastiano.hairline.path
import dev.sebastiano.hairline.place
import dev.sebastiano.hairline.poly
import dev.sebastiano.hairline.prism
import dev.sebastiano.hairline.proj
import dev.sebastiano.hairline.put
import dev.sebastiano.hairline.rad
import dev.sebastiano.hairline.rings
import dev.sebastiano.hairline.rrect
import dev.sebastiano.hairline.run
import dev.sebastiano.hairline.seg
import dev.sebastiano.hairline.solid
import dev.sebastiano.hairline.spring
import dev.sebastiano.hairline.stepS
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val LAP_W = 150.0
private const val LAP_D = 106.0
private const val LAP_HB = 4.0
private const val LAP_T = 2.4
private const val LAP_HZ = LAP_HB + LAP_T / 2
private const val LAP_R = 7.0
private const val LAP_B = 1.1
private const val LAP_MIN = 15.0
private const val LAP_REST = 100.0
private const val LAP_BZ = 4.2
private const val LAP_CHIN = 7.5
private const val LAP_KU = 8.6
private const val LAP_KY = 6.0

private fun lidAt(th: Double): (Double, Double, Double) -> Vec3 {
    val c = cos(rad(th))
    val s = sin(rad(th))
    return { u, v, w -> Vec3(u, v * c - w * s, LAP_HZ + v * s + w * c) }
}

private data class Keyboard(val out: List<List<Double>>, val y1: Double, val x0: Double)

private fun keys(): Keyboard {
    val out = ArrayList<List<Double>>()
    val x0 = (LAP_W - 14.5 * LAP_KU) / 2
    fun row(y: Double, h: Double, widths: List<Double>) {
        var x = x0
        widths.forEach {
            out += listOf(x, y, x + it * LAP_KU, y + h)
            x += it * LAP_KU
        }
    }
    row(LAP_KY, LAP_KU * .6, List(14) { 14.5 / 14 })
    var y = LAP_KY + LAP_KU * .6
    listOf(
            List(13) { 1.0 } + 1.5,
            listOf(1.5) + List(13) { 1.0 },
            listOf(1.75) + List(11) { 1.0 } + 1.75,
            listOf(2.25) + List(10) { 1.0 } + 2.25,
        )
        .forEach {
            row(y, LAP_KU, it)
            y += LAP_KU
        }
    row(y, LAP_KU, listOf(1.0, 1.0, 1.0, 1.25, 5.0, 1.25, 1.0))
    val ax = x0 + 11.5 * LAP_KU
    val h = LAP_KU / 2
    out += listOf(ax, y + h, ax + LAP_KU, y + LAP_KU)
    out += listOf(ax + LAP_KU, y, ax + 2 * LAP_KU, y + h)
    out += listOf(ax + LAP_KU, y + h, ax + 2 * LAP_KU, y + LAP_KU)
    out += listOf(ax + 2 * LAP_KU, y + h, ax + 3 * LAP_KU, y + LAP_KU)
    return Keyboard(out, y + LAP_KU, x0)
}

internal fun mountLaptop(els: FigureEls, value: Double): FigureHandle = LaptopFigure(els, value)

private class LaptopFigure(private val els: FigureEls, value: Double) : FigureHandle {
    private val bag = Disposer()
    private var maxA = value
    private var over: Double? = null
    private val c =
        cam(45.0, .5, 1.3).also { camera ->
            val pts =
                mutableListOf(
                    Vec3(0.0, 0.0, 0.0),
                    Vec3(LAP_W, 0.0, 0.0),
                    Vec3(0.0, LAP_D, 0.0),
                    Vec3(LAP_W, LAP_D, 0.0),
                )
            for (th in listOf(LAP_MIN, 90.0, LAP_REST, 125.0)) for (u in
                listOf(0.0, LAP_W)) for (v in listOf(0.0, LAP_D)) for (w in
                listOf(-LAP_T / 2, LAP_T / 2)) pts += lidAt(th)(u, v, w)
            fit(camera, pts, 200.0, 166.0)
        }
    private val p = proj(c)
    private val front = facing(c)
    private val eye = makeEye()
    private val g = els.svg.g()
    private val lidG: Group
    private val lid: Solid
    private val scr: Group
    private val disp: PathNode
    private val bar: PathNode
    private val dock: PathNode
    private val win: PathNode
    private val strip: PathNode
    private val camera: CircleNode
    private val lights: List<CircleNode>
    private val lr: Ring
    private val li: Ring
    private var faced: Boolean? = null
    private var drawn = Double.NaN
    private val sp = spring(LAP_REST, eps = .05)

    init {
        val (br, bi) = rings(0.0, 0.0, LAP_W, LAP_D, LAP_R, LAP_B)
        put(solid(g), prism(p, front, br, bi, 0.0, LAP_HB))
        fun top(q: Ring) = poly(q.map { p(it.u, it.v, LAP_HB) })
        val kb = keys()
        g.path(
            "nf lo",
            kb.out
                .map { key ->
                    top(rrect(key[0] + .5, key[1] + .5, key[2] - .5, key[3] - .5, 1.1, 2))
                }
                .join(),
        )
        g.path(
            "nf",
            top(rrect(kb.x0 - 1.6, LAP_KY - 1.6, LAP_W - kb.x0 + 1.6, kb.y1 + 1.6, 3.0, 4)),
        )
        g.path("nf", top(rrect(LAP_W / 2 - 35, LAP_D - 45, LAP_W / 2 + 35, LAP_D - 5, 3.2, 4)))
        lidG = g.g()
        lid = solid(lidG)
        scr = lidG.g()
        lid.sil.classList.add("hi")
        rings(0.0, 0.0, LAP_W, LAP_D, LAP_R, LAP_B).also {
            lr = it.first
            li = it.second
        }
        disp = scr.path("nf")
        bar = scr.path("nf lo")
        dock = scr.path("nf lo")
        win = scr.path()
        strip = scr.path("nf lo")
        camera = scr.circle(.8, "dot off")
        lights = List(3) { scr.circle(.8, "dot off") }
    }

    private val loop =
        els.stage.register { dt, _ ->
            val m = stepS(sp, dt)
            drawLid(sp.x)
            m
        }
    private val yTop = p(LAP_W / 2, 0.0, LAP_HZ + LAP_D).y + 12
    private val yBot = p(LAP_W / 2, LAP_D, 0.0).y - 12

    init {
        bag.add(loop::unregister)
        drawLid(LAP_REST)
        bag.add(
            els.stage.pointer(
                object : PointerHandlers {
                    override fun move(p: Vec2) {
                        over = p.y
                        retarget()
                    }

                    override fun leave() {
                        over = null
                        retarget()
                    }
                }
            )
        )
        bag.add { els.svg.clear() }
    }

    private fun makeEye(): Vec3 {
        val o = p(0.0, 0.0, 0.0)
        val x = p(1.0, 0.0, 0.0)
        val y = p(0.0, 1.0, 0.0)
        val z = p(0.0, 0.0, 1.0)
        val a = Vec3(x.x - o.x, y.x - o.x, z.x - o.x)
        val b = Vec3(x.y - o.y, y.y - o.y, z.y - o.y)
        var e = Vec3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)
        if (e.z < 0) e = Vec3(-e.x, -e.y, -e.z)
        return e
    }

    private fun dot(a: Vec3) = a.x * eye.x + a.y * eye.y + a.z * eye.z

    private fun drawLid(th: Double) {
        if (th == drawn) return
        drawn = th
        val f = lidAt(th)
        val co = cos(rad(th))
        val si = sin(rad(th))
        fun at(u: Double, v: Double, w: Double) = f(u, v, w).let { p(it.x, it.y, it.z) }
        fun on(u: Double, v: Double) = at(u, v, -LAP_T / 2)
        fun plane(q: Ring, w: Double) = q.map { at(it.u, it.v, w) }
        val shows = dot(Vec3(0.0, si, -co)) > 0
        val wf = if (shows) -LAP_T / 2 else LAP_T / 2
        put(
            lid,
            PrismPaths(
                poly(hull(plane(lr, -LAP_T / 2) + plane(lr, LAP_T / 2))),
                open(plane(run(li) { dot(Vec3(it.nu, it.nv * co, it.nv * si)) > 0 }, wf)),
            ),
        )
        if (shows != faced) {
            faced = shows
            if (shows) lid.g.after(scr) else lid.g.before(scr)
        }
        fun face(q: Ring) = poly(q.map { on(it.u, it.v) })
        val vt = LAP_D - LAP_BZ
        val x0 = 30.0
        val x1 = 104.0
        val v0 = LAP_CHIN + 16
        val v1 = vt - 12
        disp.d = face(rrect(LAP_BZ, LAP_CHIN, LAP_W - LAP_BZ, vt, 3.0, 4))
        bar.d = seg(on(LAP_BZ + 1.5, vt - 4), on(LAP_W - LAP_BZ - 1.5, vt - 4))
        dock.d = face(rrect(LAP_W / 2 - 28, LAP_CHIN + 2.5, LAP_W / 2 + 28, LAP_CHIN + 8.5, 2.2, 4))
        win.d = face(rrect(x0, v0, x1, v1, 2.6, 4))
        strip.d = seg(on(x0, v1 - 6), on(x1, v1 - 6)) + seg(on(x0 + 20, v0), on(x0 + 20, v1 - 6))
        place(camera, on(LAP_W / 2, LAP_D - LAP_BZ / 2))
        lights.forEachIndexed { k, e -> place(e, on(x0 + 4 + k * 3.4, v1 - 3)) }
    }

    private fun retarget() {
        val y = over
        if (y == null) {
            sp.t = min(LAP_REST, maxA)
            els.read.textContent = "rest"
        } else {
            sp.t = lerp(maxA, LAP_MIN, clamp((y - yTop) / (yBot - yTop), 0.0, 1.0))
            els.read.textContent =
                if (sp.t <= LAP_MIN + .5) "shut"
                else if (sp.t >= maxA - .5) "open" else "lid ${jsRound(sp.t).toInt()}°"
        }
        loop.wake()
    }

    override fun set(value: Double) {
        maxA = value
        retarget()
    }

    override fun destroy() = bag.dispose()
}
