// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline.figures

import dev.sebastiano.hairline.Disposer
import dev.sebastiano.hairline.FigureEls
import dev.sebastiano.hairline.FigureHandle
import dev.sebastiano.hairline.PathNode
import dev.sebastiano.hairline.PointerHandlers
import dev.sebastiano.hairline.PrismPaths
import dev.sebastiano.hairline.Projector
import dev.sebastiano.hairline.Ring
import dev.sebastiano.hairline.Sample
import dev.sebastiano.hairline.Solid
import dev.sebastiano.hairline.Vec2
import dev.sebastiano.hairline.Vec3
import dev.sebastiano.hairline.cam
import dev.sebastiano.hairline.circ
import dev.sebastiano.hairline.circle
import dev.sebastiano.hairline.clamp
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
import dev.sebastiano.hairline.proj
import dev.sebastiano.hairline.put
import dev.sebastiano.hairline.reducedMotion
import dev.sebastiano.hairline.rrect
import dev.sebastiano.hairline.run
import dev.sebastiano.hairline.seg
import dev.sebastiano.hairline.solid
import dev.sebastiano.hairline.tdone
import dev.sebastiano.hairline.tset
import dev.sebastiano.hairline.tval
import dev.sebastiano.hairline.tween
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

private const val R = 40.0
private const val DP = 14.0
private const val ZC = 64.0
private const val RD = 17.0
private const val DT = 4.0
private const val KN = 6.0
private const val KH = 6.0
private const val GAP = 10.0
private const val KL = 11.0
private const val KW = 12.0
private const val KD = 10.0
private const val BR = 3.6
private const val HX = -48.0
private const val HR = 4.6
private const val WX0 = -70.0
private const val WX1 = 70.0
private const val WZ1 = 130.0
private const val WT = 14.0
private const val COMBO = 40.0
private const val REST = 30.0
private const val STEP = 50.0
private const val WMAX = 150.0
private val THETA = listOf(72.0, 26.0, -20.0)
private val VIEW = Vec3(sqrt(0.5) * sqrt(0.75), sqrt(0.5) * sqrt(0.75), 0.5)

private fun dir(deg: Double) = Vec3(cos(deg * PI / 180), 0.0, sin(deg * PI / 180))

private fun wrap(d: Double) = (((d + 50) % 100) + 100) % 100 - 50

private fun bar(
    p: Projector,
    o: Vec3,
    e: Vec3,
    a: Vec3,
    b: Vec3,
    ring: Ring,
    inner: Ring,
    s0: Double,
    s1: Double,
): PrismPaths {
    fun at(q: Sample, s: Double) =
        p(
            o.x + e.x * s + a.x * q.u + b.x * q.v,
            o.y + e.y * s + a.y * q.u + b.y * q.v,
            o.z + e.z * s + a.z * q.u + b.z * q.v,
        )
    fun seen(q: Sample) =
        (a.x * q.nu + b.x * q.nv) * VIEW.x +
            (a.y * q.nu + b.y * q.nv) * VIEW.y +
            (a.z * q.nu + b.z * q.nv) * VIEW.z > 0
    return PrismPaths(
        poly(hull(ring.map { at(it, s0) } + ring.map { at(it, s1) })),
        open(run(inner, ::seen).map { at(it, s1) }),
    )
}

private class Bolt(val e: Vec3, val t: Vec3, val el: Solid) {
    val tw = tween(0.0)
    var drawn = Double.NaN
}

private class Spin(
    var a: Double = REST,
    var w: Double = 0.0,
    var mode: String = "rest",
    var target: Double = REST,
)

internal fun mountVault(els: FigureEls, value: Double): FigureHandle = VaultFigure(els, value)

private class VaultFigure(private val els: FigureEls, value: Double) : FigureHandle {
    private val bag = Disposer()
    private var coast = value
    private val y = Vec3(0.0, 1.0, 0.0)
    private val x = Vec3(1.0, 0.0, 0.0)
    private val z = Vec3(0.0, 0.0, 1.0)
    private val o = Vec3(0.0, 0.0, ZC)
    private val c =
        cam(45.0, 0.5, 1.5).also {
            fit(
                it,
                listOf(
                    Vec3(WX0, -WT, 0.0),
                    Vec3(WX1, -WT, 0.0),
                    Vec3(WX0, -WT, WZ1),
                    Vec3(WX1, -WT, WZ1),
                    Vec3(WX0, KD, 0.0),
                    Vec3(WX1, 0.0, 0.0),
                    Vec3(WX0, 0.0, WZ1),
                    Vec3(WX1, 0.0, WZ1),
                ),
                200.0,
                166.0,
            )
        }
    private val p = proj(c)
    private val g = els.svg.g()
    private lateinit var dial: Solid
    private lateinit var ticks: PathNode
    private lateinit var longs: PathNode
    private lateinit var combo: PathNode
    private val bolts = ArrayList<Bolt>()

    init {
        buildScene()
    }

    private val spin = Spin()
    private var raw = REST
    private var said = ""
    private var last: Double? = null
    private var lastT = 0.0
    private var lastMove = -1e9
    private var over = false
    private var opened = false
    private var from = 1
    private var lit: String? = null
    private var drawnA = Double.NaN
    private val loop = els.stage.register { dt, now -> tick(dt, now) }

    init {
        bag.add(loop::unregister)
        bindPointer()
        light()
        bag.add { els.svg.clear() }
    }

    private fun buildScene() {
        put(
            solid(g),
            bar(
                p,
                Vec3(0.0, 0.0, 0.0),
                y,
                x,
                z,
                rrect(WX0, 0.0, WX1, WZ1, 9.0, 6),
                rrect(WX0 + 2, 2.0, WX1 - 2, WZ1 - 2, 7.0, 6),
                -WT,
                0.0,
            ),
        )
        put(
            solid(g),
            bar(
                p,
                Vec3(HX, DP * .55, 0.0),
                z,
                x,
                y,
                circ(HR, 28),
                circ(HR - 1, 28),
                ZC - 26,
                ZC + 26,
            ),
        )
        listOf(-15.0, 15.0).forEach { dz ->
            put(
                solid(g),
                bar(
                    p,
                    Vec3(0.0, 0.0, ZC + dz),
                    y,
                    x,
                    z,
                    rrect(HX, -4.0, -R + 6, 4.0, 3.0, 4),
                    rrect(HX + 1, -3.0, -R + 5, 3.0, 2.0, 4),
                    1.0,
                    DP * .8,
                ),
            )
        }
        put(solid(g), bar(p, o, y, x, z, circ(R, 72), circ(R - 2.4, 72), 0.0, DP))
        g.path("nf lo", poly(circ(R - 8, 64).map { p(it.u, DP, ZC + it.v) }))
        g.path("nf", seg(p(0.0, DP, ZC + RD + 1.8), p(0.0, DP, ZC + RD + 6.5)))
        dial = solid(g)
        put(dial, bar(p, o, y, x, z, circ(RD, 56), circ(RD - 1.4, 56), DP, DP + DT))
        ticks = g.path("nf lo")
        longs = g.path("nf")
        combo = g.path("nf")
        put(solid(g), bar(p, o, y, x, z, circ(KN, 32), circ(KN - 1, 32), DP + DT, DP + DT + KH))
        buildBolts()
    }

    private fun buildBolts() {
        THETA.forEach { theta ->
            val e = dir(theta)
            val t = dir(theta + 90)
            val el = solid(g)
            val keep = solid(g)
            put(
                keep,
                bar(
                    p,
                    o,
                    y,
                    e,
                    t,
                    rrect(R + GAP, -KW / 2, R + GAP + KL, KW / 2, 3.0, 4),
                    rrect(R + GAP + 1.4, -KW / 2 + 1.4, R + GAP + KL - 1.4, KW / 2 - 1.4, 1.8, 4),
                    0.0,
                    KD,
                ),
            )
            listOf(-3.4, 3.4).forEach { s ->
                val center = R + GAP + KL / 2 + s
                place(keep.g.circle(0.9, "dot off"), p(e.x * center, KD, ZC + e.z * center))
            }
            bolts.add(Bolt(e, t, el))
        }
    }

    private fun drawBolt(b: Bolt, k: Double) {
        val s1 = lerp(R + GAP + 4.5, R + 1.2, k)
        if (s1 == b.drawn) return
        b.drawn = s1
        put(
            b.el,
            bar(p, Vec3(0.0, DP / 2, ZC), b.e, b.t, y, circ(BR, 24), circ(BR - .9, 24), R - .2, s1),
        )
    }

    private fun drawDial() {
        if (spin.a == drawnA) return
        drawnA = spin.a
        val tk = ArrayList<dev.sebastiano.hairline.PathData>()
        val lg = ArrayList<dev.sebastiano.hairline.PathData>()
        val zz = DP + DT
        for (m in 0 until 100 step 5) {
            val th = 90 + (m - spin.a) * 3.6
            val e = dir(th)
            val r0 = if (m % 10 != 0) RD - 4 else RD - 6.5
            val d =
                seg(p(e.x * r0, zz, ZC + e.z * r0), p(e.x * (RD - 1.8), zz, ZC + e.z * (RD - 1.8)))
            if (m == 40) combo.d = d else (if (m % 10 != 0) tk else lg).add(d)
        }
        ticks.d = tk.join()
        longs.d = lg.join()
    }

    private fun light() {
        val want = if (opened) "bolts" else if (over) "dial" else "combo"
        if (want == lit) return
        lit = want
        bolts.forEach { it.el.sil.classList.toggle("hi", want == "bolts") }
        dial.sil.classList.toggle("hi", want == "dial")
        combo.classList.toggle("hi", want == "combo")
    }

    private fun setOpen(on: Boolean, now: Double) {
        opened = on
        bolts.forEachIndexed { i, b -> tset(b.tw, if (on) 1.0 else 0.0, now, abs(i - from) * STEP) }
        light()
    }

    private fun stepSpin(dt: Double, now: Double): Boolean {
        if (spin.mode == "rest") return false
        val n = maxOf(1, ceil(dt * 240).toInt())
        val h = dt / n
        repeat(n) {
            if (spin.mode == "coast") {
                spin.w = spin.w * exp(-h * 1000 / coast) - 40 * sin(spin.a / 10 * 2 * PI) * h
                spin.a += spin.w * h
                if (abs(spin.w) < 12 && now - lastMove > 90) {
                    spin.mode = "settle"
                    spin.target = jsRound((spin.a + spin.w * .15) / 10) * 10
                }
            } else {
                spin.w += (-90 * (spin.a - spin.target) - 16 * spin.w) * h
                spin.a += spin.w * h
            }
        }
        if (spin.mode == "settle" && abs(spin.a - spin.target) < .01 && abs(spin.w) < .2) {
            spin.target = ((spin.target % 100) + 100) % 100
            spin.a = spin.target
            spin.w = 0.0
            spin.mode = "rest"
        }
        return spin.mode != "rest"
    }

    private fun tick(dt: Double, now: Double): Boolean {
        var moving = stepSpin(dt, now)
        val near = abs(wrap(spin.a - COMBO))
        if (!opened && near < .8 && abs(spin.w) < 6) setOpen(true, now)
        else if (opened && near > 2.5) setOpen(false, now)
        drawDial()
        bolts.forEach {
            drawBolt(it, tval(it.tw, now))
            if (!tdone(it.tw, now)) moving = true
        }
        val number = (((jsRound(spin.a).toInt() % 100) + 100) % 100).toString().padStart(2, '0')
        val say =
            if (over || spin.mode != "rest") "dial $number" + (if (opened) " · open" else "")
            else "rest"
        if (say != said) {
            said = say
            els.read.textContent = say
        }
        return moving
    }

    private fun onFace(point: Vec2): Vec2 {
        val p0 = p(0.0, DP, 0.0)
        val px = p(1.0, DP, 0.0)
        val pz = p(0.0, DP, 1.0)
        val ax = Vec2(px.x - p0.x, px.y - p0.y)
        val az = Vec2(pz.x - p0.x, pz.y - p0.y)
        val det = ax.x * az.y - ax.y * az.x
        val qx = point.x - p0.x
        val qy = point.y - p0.y
        return Vec2((qx * az.y - qy * az.x) / det, (ax.x * qy - ax.y * qx) / det - ZC)
    }

    private fun leave() {
        over = false
        last = null
        if (reducedMotion()) {
            raw = ((jsRound(spin.a / 10) * 10) % 100 + 100) % 100
            spin.a = raw
            spin.w = 0.0
            spin.mode = "rest"
        }
        light()
        loop.wake()
    }

    private fun move(point: Vec2) {
        val q = onFace(point)
        val radius = hypot(q.x, q.y)
        if (radius > R + GAP + KL + 10) {
            leave()
            return
        }
        val now = els.stage.now()
        val ang = atan2(q.y, q.x) * 180 / PI
        over = true
        from = THETA.indices.minByOrNull { abs(wrap((ang - THETA[it]) / 3.6)) } ?: 0
        last?.let { previous ->
            val da = -(((ang - previous + 540) % 360) - 180) / 3.6 * clamp(radius / 22, 0.0, 1.0)
            if (reducedMotion()) {
                raw += da
                spin.a = jsRound(raw / 10) * 10
                spin.mode = "rest"
            } else {
                spin.w =
                    clamp(lerp(spin.w, da / maxOf(.008, (now - lastT) / 1000), .35), -WMAX, WMAX)
                spin.mode = "coast"
                raw = spin.a
            }
            if (abs(da) > .05) lastMove = now
        }
        last = ang
        lastT = now
        light()
        loop.wake()
    }

    private fun bindPointer() {
        bag.add(
            els.stage.pointer(
                object : PointerHandlers {
                    override fun move(p: Vec2) = this@VaultFigure.move(p)

                    override fun leave() = this@VaultFigure.leave()
                }
            )
        )
    }

    override fun set(value: Double) {
        coast = value
        loop.wake()
    }

    override fun destroy() = bag.dispose()
}
