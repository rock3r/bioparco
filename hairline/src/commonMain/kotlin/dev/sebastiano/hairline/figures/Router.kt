// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline.figures

import dev.sebastiano.hairline.Disposer
import dev.sebastiano.hairline.FigureEls
import dev.sebastiano.hairline.FigureHandle
import dev.sebastiano.hairline.PointerHandlers
import dev.sebastiano.hairline.PrismPaths
import dev.sebastiano.hairline.Ring
import dev.sebastiano.hairline.Solid
import dev.sebastiano.hairline.Spring
import dev.sebastiano.hairline.Vec2
import dev.sebastiano.hairline.Vec3
import dev.sebastiano.hairline.cam
import dev.sebastiano.hairline.circ
import dev.sebastiano.hairline.circle
import dev.sebastiano.hairline.clamp
import dev.sebastiano.hairline.facing
import dev.sebastiano.hairline.fit
import dev.sebastiano.hairline.flatDot
import dev.sebastiano.hairline.g
import dev.sebastiano.hairline.hull
import dev.sebastiano.hairline.lerp
import dev.sebastiano.hairline.open
import dev.sebastiano.hairline.place
import dev.sebastiano.hairline.poly
import dev.sebastiano.hairline.prism
import dev.sebastiano.hairline.proj
import dev.sebastiano.hairline.put
import dev.sebastiano.hairline.rad
import dev.sebastiano.hairline.ringAt
import dev.sebastiano.hairline.rrect
import dev.sebastiano.hairline.run
import dev.sebastiano.hairline.seg
import dev.sebastiano.hairline.solid
import dev.sebastiano.hairline.spring
import dev.sebastiano.hairline.stepS
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

private const val ROUTER_X1 = 124.0
private const val ROUTER_Y1 = 58.0
private const val ROUTER_H = 14.0
private const val ROUTER_T = 3.0
private const val ROUTER_YB = 11.0
private const val ROUTER_KH = 5.0
private const val ROUTER_KR = 4.6
private const val ROUTER_L = 56.0
private const val ROUTER_R0 = 3.3
private const val ROUTER_R1 = 2.1
private const val ROUTER_MAX = 44.0
private val ROUTER_XS = listOf(18.0, 48.0, 78.0, 108.0)
private val ROUTER_REST = listOf(-9.0, 3.0, -4.0, 32.0)
private val ROUTER_D = Vec2(kotlin.math.sqrt(0.5), -kotlin.math.sqrt(0.5))

/** Router: four independently sprung antennas aim toward the pointer. */
internal fun mountRouter(els: FigureEls, value: Double): FigureHandle = RouterFigure(els, value)

private data class Antenna(
    val i: Int,
    val el: Solid,
    val sp: Spring,
    val pivot: Vec2,
    var drawn: Double = Double.NaN,
)

private fun along(i: Int, th: Double, length: Double): Vec3 {
    val s = sin(rad(th))
    val c = cos(rad(th))
    return Vec3(
        ROUTER_XS[i] + ROUTER_D.x * s * length,
        ROUTER_YB + ROUTER_D.y * s * length,
        ROUTER_H + ROUTER_KH - 1 + c * length,
    )
}

private class RouterFigure(private val els: FigureEls, value: Double) : FigureHandle {
    private val bag = Disposer()
    private var radius = value
    private var over: Vec2? = null
    private var lit: Antenna? = null
    private val c = cam(45.0, 0.5, 1.78).also { fitRouter(it) }
    private val p = proj(c)
    private val front = facing(c)
    private val origin = p(0.0, 0.0, 0.0)
    private val diagonal = p(ROUTER_D.x, ROUTER_D.y, 0.0)
    private val vertical = p(0.0, 0.0, 1.0)
    private val sh = hypot(diagonal.x - origin.x, diagonal.y - origin.y)
    private val sz = origin.y - vertical.y
    private val g = els.svg.g()
    private val ants: List<Antenna>
    private val gap: Double

    init {
        buildBody()
        ants = buildAntennas()
        gap = abs(ants[1].pivot.x - ants[0].pivot.x) / sh
    }

    private val loop =
        els.stage.register { dt, _ ->
            var moving = false
            for (a in ants) {
                if (stepS(a.sp, dt)) moving = true
                draw(a)
            }
            moving
        }

    init {
        bag.add(loop::unregister)
        light(ants[3])
        bag.add(
            els.stage.pointer(
                object : PointerHandlers {
                    override fun move(p: Vec2) {
                        over = p
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

    private fun buildBody() {
        val foot = rrect(0.0, 0.0, ROUTER_X1, ROUTER_Y1, 13.0, 6)
        val top = rrect(ROUTER_T, ROUTER_T, ROUTER_X1 - ROUTER_T, ROUTER_Y1 - ROUTER_T, 10.0, 6)
        val inner = rrect(4.6, 4.6, ROUTER_X1 - 4.6, ROUTER_Y1 - 4.6, 8.4, 6)
        put(
            solid(g),
            PrismPaths(
                poly(hull(ringAt(p, foot, 0.0) + ringAt(p, top, ROUTER_H))),
                open(ringAt(p, run(inner, front), ROUTER_H)),
            ),
        )
        repeat(6) { k ->
            val z = ROUTER_H * 0.56
            val y = ROUTER_Y1 - ROUTER_T * (z / ROUTER_H) + 0.1
            place(g.circle(1.05, if (k == 0) "dot m" else "dot off"), p(34.0 + k * 9, y, z))
        }
        repeat(3) { j ->
            repeat(13 - j % 2) { k ->
                place(
                    flatDot(g, c, 0.5, "dot off"),
                    p(26.0 + (j % 2) * 3 + k * 6, 29.0 + j * 5.5, ROUTER_H),
                )
            }
        }
    }

    private fun buildAntennas(): List<Antenna> = ROUTER_XS.mapIndexed { i, x ->
        val shift = { ring: Ring -> ring.map { it.copy(u = it.u + x, v = it.v + ROUTER_YB) } }
        put(
            solid(g),
            prism(
                p,
                front,
                shift(circ(ROUTER_KR, 16)),
                shift(circ(ROUTER_KR - 1.1, 16)),
                ROUTER_H,
                ROUTER_H + ROUTER_KH,
            ),
        )
        Antenna(i, solid(g), spring(ROUTER_REST[i], eps = 0.05), project(along(i, 0.0, 0.0)))
    }

    private fun project(q: Vec3) = p(q.x, q.y, q.z)

    private fun disc(center: Vec2, radius: Double) =
        List(20) { k ->
            Vec2(
                center.x + radius * sh * cos(k * PI / 10),
                center.y + radius * sh * sin(k * PI / 10),
            )
        }

    private fun draw(a: Antenna) {
        val th = a.sp.x
        if (th == a.drawn) return
        a.drawn = th
        val b = project(along(a.i, th, 0.0))
        val t = project(along(a.i, th, ROUTER_L))
        val e = project(along(a.i, th, ROUTER_L * 0.21))
        val len = hypot(t.x - b.x, t.y - b.y)
        val n = Vec2(-(t.y - b.y) / len, (t.x - b.x) / len)
        val w = lerp(ROUTER_R0, ROUTER_R1, 0.21) * sh - 1.1
        put(
            a.el,
            PrismPaths(
                poly(hull(disc(b, ROUTER_R0) + disc(t, ROUTER_R1))),
                seg(Vec2(e.x + n.x * w, e.y + n.y * w), Vec2(e.x - n.x * w, e.y - n.y * w)),
            ),
        )
    }

    private fun light(a: Antenna) {
        if (a === lit) return
        lit?.el?.sil?.classList?.remove("hi")
        lit = a
        a.el.sil.classList.add("hi")
    }

    private fun retarget() {
        val at = over
        if (at == null) {
            ants.forEachIndexed { i, a -> a.sp.t = ROUTER_REST[i] }
            light(ants[3])
            els.read.textContent = "rest"
        } else retargetAt(at)
        loop.wake()
    }

    private fun retargetAt(at: Vec2) {
        val dxs = ants.map { (at.x - it.pivot.x) / sh }
        val d0 = dxs.minOf { abs(it) }
        val near = ants[dxs.indexOfFirst { abs(it) == d0 }]
        ants.forEachIndexed { i, a ->
            val dx = sign(dxs[i]) * max(abs(dxs[i]) - (ROUTER_R1 + 1), 0.0)
            val hz =
                max(
                    max((a.pivot.y - at.y) / sz, sqrt(max(ROUTER_L * ROUTER_L - dx * dx, 0.0))),
                    0.0,
                )
            val aim = atan2(dx, hz) * 180 / PI
            val falloff = clamp(1 - (abs(dxs[i]) - d0) / gap / radius, 0.1, 1.0)
            a.sp.t = clamp(aim, -ROUTER_MAX, ROUTER_MAX) * falloff
        }
        light(near)
        els.read.textContent =
            "antenna ${near.i + 1} · ${dev.sebastiano.hairline.jsRound(abs(near.sp.t)).toInt()}°"
    }

    override fun set(value: Double) {
        radius = value
        if (over != null) retarget()
    }

    override fun destroy() = bag.dispose()
}

private fun fitRouter(c: dev.sebastiano.hairline.Camera) {
    val pts =
        mutableListOf(
            Vec3(0.0, 0.0, 0.0),
            Vec3(ROUTER_X1, ROUTER_Y1, 0.0),
            Vec3(ROUTER_X1, 0.0, 0.0),
            Vec3(0.0, ROUTER_Y1, 0.0),
        )
    ROUTER_XS.indices.forEach { i ->
        listOf(-ROUTER_MAX, 0.0, ROUTER_MAX).forEach { pts.add(along(i, it, ROUTER_L + ROUTER_R1)) }
    }
    fit(c, pts, 200.0, 166.0)
}
