// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline.figures

import dev.sebastiano.hairline.Disposer
import dev.sebastiano.hairline.EllipseNode
import dev.sebastiano.hairline.FigureEls
import dev.sebastiano.hairline.FigureHandle
import dev.sebastiano.hairline.PathNode
import dev.sebastiano.hairline.PointerHandlers
import dev.sebastiano.hairline.PrismPaths
import dev.sebastiano.hairline.Ring
import dev.sebastiano.hairline.Solid
import dev.sebastiano.hairline.Vec2
import dev.sebastiano.hairline.Vec3
import dev.sebastiano.hairline.cam
import dev.sebastiano.hairline.circ
import dev.sebastiano.hairline.clamp
import dev.sebastiano.hairline.facing
import dev.sebastiano.hairline.fillet
import dev.sebastiano.hairline.fit
import dev.sebastiano.hairline.flatDot
import dev.sebastiano.hairline.g
import dev.sebastiano.hairline.hull
import dev.sebastiano.hairline.join
import dev.sebastiano.hairline.open
import dev.sebastiano.hairline.path
import dev.sebastiano.hairline.place
import dev.sebastiano.hairline.poly
import dev.sebastiano.hairline.prism
import dev.sebastiano.hairline.proj
import dev.sebastiano.hairline.put
import dev.sebastiano.hairline.rings
import dev.sebastiano.hairline.rrect
import dev.sebastiano.hairline.seg
import dev.sebastiano.hairline.solid
import dev.sebastiano.hairline.spring
import dev.sebastiano.hairline.stepS
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

private const val EV_SX = 32.0
private const val EV_SY = 34.0
private const val EV_FH = 36.0
private const val EV_CH = 25.0
private const val EV_T = 2.5
private const val EV_NF = 4
private const val EV_TOP = 3 * EV_FH + EV_CH + 7
private val evCar = listOf(5.0, 7.0, 28.0, 27.0)
private const val EV_RX = 16.5
private val evCw = listOf(-8.0, 11.0, -2.0, 23.0)
private const val EV_CWH = 17.0
private const val EV_WALL = 2.0
private val evBase =
    fillet(
        listOf(
            Vec2(-16.0, -2.0),
            Vec2(33.0, -2.0),
            Vec2(33.0, -56.0),
            Vec2(76.0, -56.0),
            Vec2(76.0, 38.0),
            Vec2(-16.0, 38.0),
        ),
        listOf(3.0, 2.0, 4.0, 6.0, 6.0, 6.0),
    )
private val evFloor =
    fillet(
        listOf(
            Vec2(33.0, -56.0),
            Vec2(72.0, -56.0),
            Vec2(72.0, -6.0),
            Vec2(43.0, -6.0),
            Vec2(43.0, 34.0),
            Vec2(33.0, 34.0),
        ),
        listOf(4.0, 4.0, 4.0, 3.0, 3.0, 3.0),
    )
private val evCwx = (evCw[0] + evCw[2]) / 2
private val evSr = (EV_RX - evCwx) / 2
private val evSc = Vec3(evCwx + evSr, EV_SY / 2, EV_TOP + 3 + evSr + 2)
private const val EV_REST = 1.5 * EV_FH - 4
private val evNames = listOf("ground", "floor 1", "floor 2", "floor 3")

private data class Landing(val sil: PathNode, val dots: List<EllipseNode>)

internal fun mountElevator(els: FigureEls, value: Double): FigureHandle = ElevatorFigure(els, value)

private class ElevatorFigure(private val els: FigureEls, value: Double) : FigureHandle {
    private val bag = Disposer()
    private val c =
        cam(45.0, .5, 1.3).also {
            fit(
                it,
                listOf(
                    Vec3(-16.0, -56.0, -8.0),
                    Vec3(76.0, 38.0, -8.0),
                    Vec3(76.0, -56.0, -8.0),
                    Vec3(-16.0, 38.0, -8.0),
                    Vec3(evSc.x, evSc.y, evSc.z + evSr),
                ),
                200.0,
                166.0,
            )
        }
    private val p = proj(c)
    private val front = facing(c)
    private val g = els.svg.g()

    private fun block(b: List<Double>, r: Double, w: Double, z0: Double, z1: Double) {
        val (o, i) = rings(b[0], b[1], b[2], b[3], r, w)
        put(solid(g), prism(p, front, o, i, z0, z1))
    }

    private fun slab(q: List<Vec2>, z0: Double, z1: Double): PathNode {
        val n = q.size
        val near =
            q.indices.map { i ->
                val x = q[(i + 1) % n]
                x.y - q[i].y - (x.x - q[i].x) > 0
            }
        fun runOf(w: Boolean): List<Vec2> {
            val s = near.indices.first { i -> near[i] == w && near[(i + n - 1) % n] != w }
            val out = ArrayList<Vec2>()
            var i = s
            while (near[i % n] == w) {
                out += q[i % n]
                i++
            }
            out += q[(s + out.size) % n]
            return out
        }
        fun at(r: List<Vec2>, z: Double) = r.map { p(it.x, it.y, z) }
        val back = runOf(false)
        val fore = runOf(true)
        val sil = g.path("sil", poly(at(back, z1) + at(fore, z0)))
        g.path("nf lo", open(at(fore, z1)))
        return sil
    }

    private val cwo: Ring
    private val cwi: Ring
    private val cw: Solid
    private val cwBands: PathNode
    private val cwRope: PathNode
    private val co: Ring
    private val ci: Ring
    private val car: Solid
    private val door: PathNode
    private val split: PathNode
    private val ho: Ring
    private val hi: Ring
    private val head: Solid
    private val rope: PathNode
    private val spokes: PathNode
    private val lands = ArrayList<Landing>()
    private val sp = spring(EV_REST, k = value, c = 1.8 * sqrt(value))
    private var drawn = Double.NaN
    private var chosen: Int? = null

    init {
        slab(evBase, -8.0, -EV_T)
        block(listOf(-14 - EV_WALL, -EV_WALL, -14.0, EV_SY), 1.0, .5, -EV_T, EV_TOP)
        block(listOf(-14.0, -EV_WALL, EV_SX, 0.0), 1.0, .5, -EV_T, EV_TOP)
        block(listOf(EV_RX - 1.3, 3.0, EV_RX + 1.3, 5.4), .8, .5, -EV_T, EV_TOP)
        rings(evCw[0], evCw[1], evCw[2], evCw[3], 1.6, .8).also {
            cwo = it.first
            cwi = it.second
        }
        cw = solid(g)
        cwBands = g.path("nf lo")
        cwRope = g.path("nf")
        rings(evCar[0], evCar[1], evCar[2], evCar[3], 2.4, 1.1).also {
            co = it.first
            ci = it.second
        }
        car = solid(g)
        door = g.path("nf")
        split = g.path("nf lo")
        rings(EV_RX - 1.7, 5.4, EV_RX + 1.7, 28.6, .8, .5).also {
            ho = it.first
            hi = it.second
        }
        head = solid(g)
        rope = g.path("nf")
        block(listOf(EV_RX - 1.3, 28.6, EV_RX + 1.3, 31.0), .8, .5, -EV_T, EV_TOP)
        spokes = buildTop()
        buildFloors()
    }

    private fun buildTop(): PathNode {
        block(listOf(-14 - EV_WALL, -EV_WALL, EV_SX + 1, EV_SY + 1), 4.0, 1.4, EV_TOP, EV_TOP + 3)
        block(listOf(evCwx + 2, 4.0, EV_RX - 2, EV_SY / 2 - 3), 2.0, 1.0, EV_TOP + 3, evSc.z + 4)
        g.path(
            "nf",
            seg(p(EV_RX, EV_SY / 2, EV_TOP + 3), p(EV_RX, EV_SY / 2, evSc.z)) +
                seg(p(evCwx, EV_SY / 2, EV_TOP + 3), p(evCwx, EV_SY / 2, evSc.z)),
        )
        fun disc(y: Double, r: Double) = circ(r, 28).map { p(evSc.x + it.u, y, evSc.z + it.v) }
        put(
            solid(g),
            PrismPaths(
                poly(hull(disc(EV_SY / 2 - 1.8, evSr) + disc(EV_SY / 2 + 1.8, evSr))),
                poly(disc(EV_SY / 2 + 1.8, evSr - 1.4)),
            ),
        )
        val spokePath = g.path("nf lo")
        g.path("nf lo", poly(disc(EV_SY / 2 + 1.8, 2.6)))
        return spokePath
    }

    private fun buildFloors() {
        for (f in 0 until EV_NF) {
            val z = f * EV_FH
            val sil = slab(evFloor, z - EV_T, z)
            if (f < EV_NF - 1)
                for (y in listOf(-53.0, -15.0)) block(
                    listOf(62.0, y, 68.0, y + 6),
                    1.5,
                    .8,
                    z,
                    z + EV_FH - EV_T,
                )
            val dots =
                List(f + 1) { k ->
                    flatDot(g, c, .8, "dot off").also {
                        place(it, p(37.0, evCar[1] - 3 - k * 3, z))
                    }
                }
            lands += Landing(sil, dots)
        }
    }

    private val loop =
        els.stage.register { dt, _ ->
            val m = stepS(sp, dt)
            if (sp.x != drawn) {
                drawn = sp.x
                draw(sp.x)
            }
            m
        }
    private val base = p(evCar[2], EV_SY / 2, 0.0).y
    private val perZ = base - p(evCar[2], EV_SY / 2, 1.0).y

    init {
        bag.add(loop::unregister)
        choose(-1)
        bag.add(
            els.stage.pointer(
                object : PointerHandlers {
                    override fun move(p: Vec2) =
                        choose(
                            clamp(
                                    floor(((base - p.y) / perZ + 8) / EV_FH),
                                    0.0,
                                    (EV_NF - 1).toDouble(),
                                )
                                .toInt()
                        )

                    override fun leave() = choose(-1)
                }
            )
        )
        bag.add { els.svg.clear() }
    }

    private fun draw(z: Double) {
        val zc = 3 * EV_FH - z + 6
        val mid = (evCar[1] + evCar[3]) / 2
        put(cw, prism(p, front, cwo, cwi, zc, zc + EV_CWH))
        cwBands.d =
            listOf(5.0, 9.0, 13.0)
                .map { seg(p(evCw[0] + .6, evCw[3], zc + it), p(evCw[2] - .6, evCw[3], zc + it)) }
                .join()
        cwRope.d = seg(p(evCwx, EV_SY / 2, zc + EV_CWH), p(evCwx, EV_SY / 2, EV_TOP))
        put(car, prism(p, front, co, ci, z, z + EV_CH))
        door.d =
            poly(
                rrect(evCar[1] + 3.5, z + 1.2, evCar[3] - 3.5, z + EV_CH - 4, 1.2, 4).map {
                    p(evCar[2], it.u, it.v)
                }
            )
        split.d = seg(p(evCar[2], mid, z + 1.2), p(evCar[2], mid, z + EV_CH - 4))
        put(head, prism(p, front, ho, hi, z + EV_CH, z + EV_CH + 2.4))
        rope.d = seg(p(EV_RX, EV_SY / 2, z + EV_CH + 2.4), p(EV_RX, EV_SY / 2, EV_TOP))
        val a = z / evSr
        fun at(t: Double, r: Double) = p(evSc.x + cos(t) * r, EV_SY / 2 + 1.8, evSc.z + sin(t) * r)
        spokes.d =
            List(6) { k -> seg(at(a + k * PI / 3, 2.6), at(a + k * PI / 3, evSr - 1.4)) }.join()
    }

    private fun choose(f: Int) {
        if (f == chosen) return
        chosen = f
        sp.t = if (f < 0) EV_REST else f * EV_FH
        car.sil.classList.toggle("hi", f < 0)
        lands.forEachIndexed { i, l ->
            l.sil.classList.toggle("hi", i == f)
            l.dots.forEach { it.classList.set(if (i == f) "dot m" else "dot off") }
        }
        els.read.textContent = if (f < 0) "rest" else evNames[f]
        loop.wake()
    }

    override fun set(value: Double) {
        sp.k = value
        sp.c = 1.8 * sqrt(value)
        loop.wake()
    }

    override fun destroy() = bag.dispose()
}
