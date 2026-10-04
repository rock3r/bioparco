// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline.figures

import dev.sebastiano.hairline.CircleNode
import dev.sebastiano.hairline.Disposer
import dev.sebastiano.hairline.FigureEls
import dev.sebastiano.hairline.FigureHandle
import dev.sebastiano.hairline.PathData
import dev.sebastiano.hairline.PathNode
import dev.sebastiano.hairline.PointerHandlers
import dev.sebastiano.hairline.PrismPaths
import dev.sebastiano.hairline.Projector
import dev.sebastiano.hairline.Ring
import dev.sebastiano.hairline.Solid
import dev.sebastiano.hairline.Spring
import dev.sebastiano.hairline.Vec2
import dev.sebastiano.hairline.Vec3
import dev.sebastiano.hairline.cam
import dev.sebastiano.hairline.circle
import dev.sebastiano.hairline.clamp
import dev.sebastiano.hairline.facing
import dev.sebastiano.hairline.fit
import dev.sebastiano.hairline.g
import dev.sebastiano.hairline.hull
import dev.sebastiano.hairline.jsRound
import dev.sebastiano.hairline.open
import dev.sebastiano.hairline.path
import dev.sebastiano.hairline.place
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
import kotlin.math.abs
import kotlin.math.max

private const val CB_N = 12
private const val CB_U = 10.4
private const val CB_BH = 9.6
private const val CB_W = 84.0
private const val CB_EAR = 4.0
private const val CB_FT = 2.0
private const val CB_D = 44.0
private const val CB_OUT = 26.0
private const val CB_X0 = -10.0
private const val CB_X1 = CB_W + 10
private const val CB_Z0 = 6.0
private const val CB_ZB = 11.0
private const val CB_ZT = CB_ZB + (CB_N - 1) * CB_U + CB_BH
private const val CB_H = CB_ZT + 6
private const val CB_LIT = 6
private val cbRest = listOf(0.0, 0.0, .2, 0.0, 0.0, .3, .58, .26, 0.0, 0.0, .13, 0.0)

/**
 * Cabinet: a server rack of twelve 1U blades between two drilled rails, on a plinth. Each blade is
 * a chassis behind a faceplate: ears with pull handles on the rails, four drive bays, a vent and a
 * status lamp. The pointer's height is read on the rack's closed front, and the blades near it
 * slide out along their rails, the farther the less, each on its own spring; the one under it comes
 * all the way and its lamp lights. At rest the last update has stopped part-way: blade 7 half out
 * and lit, a few others caught on the way. The slider is the reach, in blades.
 *
 * The pattern: a continuous field, as Keyboard's, over a stack: a spring per blade, a falloff by
 * distance, a hit test on the rest pose's planes.
 */
internal fun mountCabinet(els: FigureEls, value: Double): FigureHandle = CabinetFigure(els, value)

private fun cabinetSlab(
    p: Projector,
    ring: Ring,
    inset: Ring?,
    y0: Double,
    y1: Double,
): PrismPaths {
    fun at(r: Ring, y: Double) = r.map { p(it.u, y, it.v) }
    val crease = inset?.let { open(at(run(it) { q -> .375 * q.nu + .307 * q.nv > 0 }, y1)) }
    return PrismPaths(poly(hull(at(ring, y0) + at(ring, y1))), crease ?: PathData.EMPTY)
}

private class CabinetBlade(
    val i: Int,
    val z: Double,
    val body: Ring,
    val face: Ring,
    val faceIn: Ring,
    val chassis: Solid,
    val plate: Solid,
    val marks: PathNode,
    val lamp: CircleNode,
    val sp: Spring,
) {
    var drawn = Double.NaN
}

private class CabinetFigure(private val els: FigureEls, value: Double) : FigureHandle {
    private val bag = Disposer()
    private val c = cam(45.0, .5, 1.5)
    private val p: Projector
    private var reach = value
    private var over: Double? = null
    private var lit: CabinetBlade? = null
    private val blades = ArrayList<CabinetBlade>()
    private lateinit var basis: List<Vec2>

    init {
        val yo = CB_D + CB_FT + CB_OUT
        fit(
            c,
            listOf(
                Vec3(CB_X0, 0.0, 0.0),
                Vec3(CB_X1, 0.0, CB_H),
                Vec3(CB_X1, CB_D, 0.0),
                Vec3(CB_X0, 0.0, CB_H),
                Vec3(-CB_EAR, yo, CB_ZB),
                Vec3(-CB_EAR, yo, CB_ZT),
                Vec3(CB_W + CB_EAR, yo, CB_ZB),
            ),
            200.0,
            166.0,
        )
        p = proj(c)
        buildTree()
    }

    private val loop =
        els.stage.register { dt, _ ->
            var moving = false
            blades.forEach {
                if (stepS(it.sp, dt)) moving = true
                drawBlade(it)
            }
            moving
        }

    init {
        bag.add(loop::unregister)
        val origin = p(0.0, 0.0, 0.0)
        basis =
            listOf(p(1.0, 0.0, 0.0), p(0.0, 1.0, 0.0), p(0.0, 0.0, 1.0)).map {
                Vec2(it.x - origin.x, it.y - origin.y)
            }
        light(blades[CB_LIT])
        bag.add(
            els.stage.pointer(
                object : PointerHandlers {
                    override fun move(p: Vec2) {
                        over = hit(p)
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

    private fun buildTree() {
        val g = els.svg.g()
        val (pr, pi) = rings(CB_X0 + 3, 3.0, CB_X1 - 3, CB_D - 3, 3.0, 1.2)
        put(solid(g), prism(p, facing(c), pr, pi, 0.0, CB_Z0))
        val (br, bi) = rings(CB_X0, 0.0, CB_X1, CB_D, 3.5, 1.6)
        put(solid(g), prism(p, facing(c), br, bi, CB_Z0, CB_H))
        fun front(r: Ring) = r.map { p(it.u, CB_D, it.v) }
        fun side(r: Ring) = r.map { p(CB_X1, it.u, it.v) }
        g.path(
            "lo nf",
            poly(front(rrect(-7.6, CB_ZB - 2.2, CB_W + 7.6, CB_ZT + 2.2, 2.0))) +
                poly(side(rrect(5.0, CB_Z0 + 7, CB_D - 5, CB_H - 7, 3.0))),
        )
        repeat(CB_N) { i ->
            listOf(-5.8, CB_W + 5.8).forEach { x ->
                place(g.circle(.75, "dot off"), p(x, CB_D, CB_ZB + i * CB_U + CB_BH / 2))
            }
        }
        repeat(CB_N) { addBlade(g, it) }
    }

    private fun addBlade(g: dev.sebastiano.hairline.Group, i: Int) {
        val z = CB_ZB + i * CB_U
        val gi = g.g()
        blades +=
            CabinetBlade(
                i,
                z,
                rrect(0.0, z + .4, CB_W, z + CB_BH - .4, 1.6),
                rrect(-CB_EAR, z, CB_W + CB_EAR, z + CB_BH, 2.0),
                rrect(-CB_EAR + .8, z + .8, CB_W + CB_EAR - .8, z + CB_BH - .8, 1.2),
                solid(gi),
                solid(gi),
                gi.path("lo nf"),
                gi.circle(1.1, "dot off"),
                spring(CB_OUT * cbRest[i], eps = .02),
            )
    }

    private fun marks(b: CabinetBlade, y: Double): PathData {
        fun f(r: Ring) = poly(r.map { p(it.u, y, it.v) })
        var d = f(rrect(-3.4, b.z + 2, -1.2, b.z + CB_BH - 2, 1.0, 3))
        d += f(rrect(CB_W + 1.2, b.z + 2, CB_W + 3.4, b.z + CB_BH - 2, 1.0, 3))
        repeat(4) { k ->
            d += f(rrect(4 + k * 10.6, b.z + 2, 13.8 + k * 10.6, b.z + CB_BH - 2, 1.0, 3))
        }
        repeat(8) { k ->
            d += seg(p(50 + k * 2.6, y, b.z + 2.5), p(50 + k * 2.6, y, b.z + CB_BH - 2.5))
        }
        return d
    }

    private fun drawBlade(b: CabinetBlade) {
        val out = b.sp.x
        if (out == b.drawn) return
        b.drawn = out
        val yf = CB_D + out + CB_FT
        val zm = b.z + CB_BH / 2
        val ch = cabinetSlab(p, b.body, null, CB_D, CB_D + out + .5)
        put(b.chassis, PrismPaths(ch.sil, seg(p(CB_W, CB_D, zm), p(CB_W, CB_D + out, zm))))
        put(b.plate, cabinetSlab(p, b.face, b.faceIn, CB_D + out, yf))
        b.marks.d = marks(b, yf)
        place(b.lamp, p(CB_W - 5, yf, zm))
    }

    private fun light(b: CabinetBlade) {
        if (b === lit) return
        lit?.plate?.sil?.classList?.remove("hi")
        lit?.lamp?.classList?.set("dot off")
        lit = b
        b.plate.sil.classList.add("hi")
        b.lamp.classList.set("dot")
    }

    private fun onPlane(point: Vec2, axis: Int, fixed: Double): Vec3 {
        val origin = p(0.0, 0.0, 0.0)
        val free = listOf(0, 1, 2).filter { it != axis }
        val a = free[0]
        val k = free[1]
        val ea = basis[a]
        val ek = basis[k]
        val rx = point.x - origin.x - fixed * basis[axis].x
        val ry = point.y - origin.y - fixed * basis[axis].y
        val det = ea.x * ek.y - ea.y * ek.x
        val w = doubleArrayOf(0.0, 0.0, 0.0)
        w[axis] = fixed
        w[a] = (rx * ek.y - ry * ek.x) / det
        w[k] = (ea.x * ry - ea.y * rx) / det
        return Vec3(w[0], w[1], w[2])
    }

    private fun hit(point: Vec2): Double? {
        for (i in CB_N - 1 downTo 0) {
            val q = onPlane(point, 1, CB_D + CB_FT + CB_OUT * cbRest[i])
            val z = (q.z - CB_ZB - CB_BH / 2) / CB_U
            if (q.x >= -CB_EAR && q.x <= CB_W + CB_EAR && abs(z - i) <= .5) return z
        }
        val front = onPlane(point, 1, CB_D + CB_FT)
        val side = onPlane(point, 0, CB_X1)
        val z =
            when {
                front.x >= CB_X0 - CB_OUT - 4 &&
                    front.x <= CB_X1 &&
                    front.z >= -CB_OUT * .8 &&
                    front.z <= CB_H -> front.z
                side.y >= 0 && side.y <= CB_D && side.z >= 0 && side.z <= CB_H + 4 -> side.z
                else -> return null
            }
        return clamp((z - CB_ZB - CB_BH / 2) / CB_U, 0.0, (CB_N - 1).toDouble())
    }

    private fun retarget() {
        val hover = over
        if (hover == null) {
            blades.forEach { it.sp.t = CB_OUT * cbRest[it.i] }
            light(blades[CB_LIT])
            els.read.textContent = "rest"
        } else {
            blades.forEach {
                val u = max(0.0, abs(it.i - hover) - .5) / reach
                it.sp.t = CB_OUT * if (u >= 1) 0.0 else (1 - u) * (1 - u)
            }
            val active = jsRound(hover).toInt()
            light(blades[active])
            els.read.textContent = "blade ${active + 1}"
        }
        loop.wake()
    }

    override fun set(value: Double) {
        reach = value
        if (over != null) retarget()
    }

    override fun destroy() = bag.dispose()
}
