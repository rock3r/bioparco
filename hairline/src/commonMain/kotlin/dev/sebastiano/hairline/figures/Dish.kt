// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline.figures

import dev.sebastiano.hairline.Disposer
import dev.sebastiano.hairline.FigureEls
import dev.sebastiano.hairline.FigureHandle
import dev.sebastiano.hairline.Group
import dev.sebastiano.hairline.PathData
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
import dev.sebastiano.hairline.facing
import dev.sebastiano.hairline.fit
import dev.sebastiano.hairline.flatDot
import dev.sebastiano.hairline.g
import dev.sebastiano.hairline.hull
import dev.sebastiano.hairline.join
import dev.sebastiano.hairline.jsRound
import dev.sebastiano.hairline.open
import dev.sebastiano.hairline.path
import dev.sebastiano.hairline.place
import dev.sebastiano.hairline.poly
import dev.sebastiano.hairline.prism
import dev.sebastiano.hairline.proj
import dev.sebastiano.hairline.put
import dev.sebastiano.hairline.rad
import dev.sebastiano.hairline.ringAt
import dev.sebastiano.hairline.rings
import dev.sebastiano.hairline.rrect
import dev.sebastiano.hairline.run
import dev.sebastiano.hairline.seg
import dev.sebastiano.hairline.solid
import dev.sebastiano.hairline.spring
import dev.sebastiano.hairline.stepS
import dev.sebastiano.hairline.toFixed
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.tanh

/*
 * Dish: a parabolic dish on a two-axis gimbal. The pointer aims it, as if it stood in front of the
 * screen: azimuth and elevation each ride a spring. The slider is the azimuth reach, in degrees.
 */

private const val DISH_R = 26.0
private const val DISH_FOC = 17.0
private const val DISH_SK = 1.4
private const val DISH_DEP = 9.941176470588236
private const val DISH_ZE = 46.0
private const val DISH_W = 31.0
private const val DISH_T = 5.0
private const val DISH_ZB = 10.0
private const val DISH_ZY = 16.0
private const val DISH_AZ0 = 45.0
private const val DISH_EL0 = 40.0
private val dishRest = Vec2(-20.0, 30.0)
private val dishPivot = Vec3(0.0, 0.0, DISH_ZE)

private data class DishFrame(val th: Double, val h: Vec3, val e: Vec3, val a: Vec3, val u: Vec3)

private fun ax(p: Vec3, q: Vec3, s: Double) = Vec3(p.x + q.x * s, p.y + q.y * s, p.z + q.z * s)

private fun dot3(p: Vec3, q: Vec3) = p.x * q.x + p.y * q.y + p.z * q.z

private fun dishFrame(th: Double, ph: Double): DishFrame {
    val t = rad(th)
    val f = rad(ph)
    val c = cos(f)
    val s = sin(f)
    val h = Vec3(cos(t), sin(t), 0.0)
    val e = Vec3(-sin(t), cos(t), 0.0)
    return DishFrame(th, h, e, Vec3(h.x * c, h.y * c, s), Vec3(-h.x * s, -h.y * s, c))
}

private fun onDish(f: DishFrame, along: Double, rho: Double, psi: Double) =
    ax(ax(ax(dishPivot, f.a, along), f.e, rho * cos(psi)), f.u, rho * sin(psi))

private fun disc(c: Vec3, x: Vec3, y: Vec3, r: Double, n: Int) =
    List(n) { i -> ax(ax(c, x, r * cos(2 * PI * i / n)), y, r * sin(2 * PI * i / n)) }

private fun turn(ring: Ring, f: DishFrame): Ring = ring.map { q ->
    Sample(
        q.u * f.h.x + q.v * f.e.x,
        q.u * f.h.y + q.v * f.e.y,
        q.nu * f.h.x + q.nv * f.e.x,
        q.nu * f.h.y + q.nv * f.e.y,
    )
}

private class DishSide(
    val side: Double,
    val g: Group,
    val ring: PathNode,
    val arm: Solid,
    val trun: PathNode,
    val foot: Ring,
    val top: Ring,
    val inner: Ring,
) {
    var near: Boolean? = null
}

internal fun mountDish(els: FigureEls, value: Double): FigureHandle = DishFigure(els, value)

private class DishFigure(private val els: FigureEls, value: Double) : FigureHandle {
    private val bag = Disposer()
    private var reach = value
    private var over: Vec2? = null
    private val c = cam(45.0, 0.5, 2.5)
    private val p: Projector
    private val front: (Sample) -> Boolean
    private val view: Vec3
    private val g: Group
    private val beam: Solid
    private val beamRing: Pair<Ring, Ring>
    private val dishG: Group
    private val sides: List<DishSide>
    private val outline: PathNode
    private val seam: PathNode
    private val hubRing: PathNode
    private val rim: PathNode
    private val struts: PathNode
    private val feed: Solid
    private val index: dev.sebastiano.hairline.EllipseNode

    init {
        fitDishCamera()
        p = proj(c)
        front = facing(c)
        view = cameraDirection()
        g = els.svg.g()
        val (br, bi) = rings(-30.0, -30.0, 30.0, 30.0, 9.0, 2.0)
        put(solid(g), prism(p, front, br, bi, 0.0, 5.0))
        repeat(36) { i ->
            val t = i / 36.0 * 2 * PI
            val r = if (i % 9 == 0) 26.5 else 25.5
            place(
                flatDot(g, c, if (i % 9 == 0) 0.75 else 0.5, "dot off"),
                p(r * cos(t), r * sin(t), 5.0),
            )
        }
        put(solid(g), prism(p, front, circ(20.0, 48), circ(18.8, 48), 5.0, DISH_ZB))
        index = flatDot(g, c, 0.8, "dot m")
        beam = solid(g)
        beamRing = rings(-9.0, -(DISH_W + DISH_T / 2 + 1), 9.0, DISH_W + DISH_T / 2 + 1, 4.0, 1.4)
        dishG = g.g()
        sides = listOf(-1.0, 1.0).map(::makeSide)
        outline = dishG.path("sil")
        seam = dishG.path("nf lo")
        hubRing = dishG.path("nf lo")
        rim = dishG.path("nf hi")
        struts = dishG.path("nf")
        feed = solid(dishG)
    }

    private val az = spring(dishRest.x)
    private val el = spring(dishRest.y)
    private val loop =
        els.stage.register { dt, _ ->
            val a = stepS(az, dt)
            val b = stepS(el, dt)
            draw(az.x, el.x)
            a || b
        }

    init {
        bag.add(loop::unregister)
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

    private fun fitDishCamera() {
        val pts =
            arrayListOf(
                Vec3(-30.0, -30.0, 0.0),
                Vec3(30.0, 30.0, 0.0),
                Vec3(30.0, -30.0, 0.0),
                Vec3(-30.0, 30.0, 0.0),
            )
        listOf(Vec2(-25.0, 5.0), Vec2(-25.0, 75.0), Vec2(115.0, 5.0), Vec2(115.0, 75.0), dishRest)
            .forEach { aim ->
                val f = dishFrame(aim.x, aim.y)
                pts.addAll(disc(dishPivot, f.e, f.u, DISH_R, 16))
                pts.add(onDish(f, DISH_FOC - DISH_DEP + 3, 0.0, 0.0))
                listOf(-1.0, 1.0).forEach { s ->
                    pts.add(
                        ax(ax(dishPivot, f.e, s * (DISH_W + DISH_T / 2)), Vec3(0.0, 0.0, 1.0), 6.0)
                    )
                }
            }
        fit(c, pts, 200.0, 166.0)
    }

    private fun cameraDirection(): Vec3 {
        val o = p(0.0, 0.0, 0.0)
        val x = p(1.0, 0.0, 0.0)
        val y = p(0.0, 1.0, 0.0)
        val z = p(0.0, 0.0, 1.0)
        val r1 = Vec3(x.x - o.x, y.x - o.x, z.x - o.x)
        val r2 = Vec3(x.y - o.y, y.y - o.y, z.y - o.y)
        val cross =
            Vec3(r1.y * r2.z - r1.z * r2.y, r1.z * r2.x - r1.x * r2.z, r1.x * r2.y - r1.y * r2.x)
        val len = hypot(hypot(cross.x, cross.y), cross.z) * sign(cross.z)
        return Vec3(cross.x / len, cross.y / len, cross.z / len)
    }

    private fun makeSide(s: Double): DishSide {
        val sg = g.g()
        return DishSide(
            s,
            sg,
            sg.path("nf"),
            solid(sg),
            sg.path("sil"),
            rrect(-8.0, s * DISH_W - DISH_T / 2, 8.0, s * DISH_W + DISH_T / 2, 2.5),
            rrect(-5.5, s * DISH_W - DISH_T / 2, 5.5, s * DISH_W + DISH_T / 2, 2.5),
            rrect(-4.5, s * DISH_W - DISH_T / 2 + 1, 4.5, s * DISH_W + DISH_T / 2 - 1, 1.5),
        )
    }

    /** The seam seen through the aperture, with its ends cut where the sight line meets the rim. */
    private fun seen(f: DishFrame, rho: Double, n: Int): PathData {
        val along = rho * rho / (4 * DISH_FOC) - DISH_DEP
        val t = -along / dot3(f.a, view)
        val q = List(n) { i -> onDish(f, along, rho, 2 * PI * i / n) }
        val s = q.map { point ->
            ax(point, view, t).let { DISH_R - hypot(hypot(it.x, it.y), it.z - DISH_ZE) }
        }
        if (s.all { it > 0 }) return poly(q.map(::pv))
        val i0 =
            s.indices.firstOrNull { s[it] > 0 && s[(it + n - 1) % n] <= 0 } ?: return PathData.EMPTY
        fun cut(i: Int, j: Int) =
            pv(
                ax(
                    q[i],
                    Vec3(q[j].x - q[i].x, q[j].y - q[i].y, q[j].z - q[i].z),
                    s[i] / (s[i] - s[j]),
                )
            )
        val out = arrayListOf(cut((i0 + n - 1) % n, i0))
        var i = i0
        while (s[i] > 0) {
            out.add(pv(q[i]))
            i = (i + 1) % n
        }
        out.add(cut((i + n - 1) % n, i))
        return open(out)
    }

    private fun pv(q: Vec3) = p(q.x, q.y, q.z)

    private var drawn = ""

    private fun draw(th: Double, ph: Double) {
        val key = "${toFixed(th, 3)},${toFixed(ph, 3)}"
        if (key == drawn) return
        drawn = key
        val f = dishFrame(th, ph)
        place(index, p(18 * f.h.x, 18 * f.h.y, DISH_ZB))
        put(
            beam,
            prism(p, front, turn(beamRing.first, f), turn(beamRing.second, f), DISH_ZB, DISH_ZY),
        )
        sides.forEach { drawSide(it, f) }
        drawBowl(f)
    }

    private fun drawSide(sd: DishSide, f: DishFrame) {
        val near = sd.side * dot3(f.e, view) > 0
        if (near != sd.near) {
            sd.near = near
            if (near) {
                sd.g.append(sd.trun, sd.arm.g, sd.ring)
                dishG.after(sd.g)
            } else {
                sd.g.append(sd.ring, sd.arm.g, sd.trun)
                dishG.before(sd.g)
            }
        }
        put(
            sd.arm,
            PrismPaths(
                poly(
                    hull(
                        ringAt(p, turn(sd.foot, f), DISH_ZY) +
                            ringAt(p, turn(sd.top, f), DISH_ZE + 6)
                    )
                ),
                open(ringAt(p, run(turn(sd.inner, f), front), DISH_ZE + 6)),
            ),
        )
        val zz = Vec3(0.0, 0.0, 1.0)
        val out = ax(dishPivot, f.e, sd.side * (DISH_W + DISH_T / 2))
        sd.ring.d = poly(disc(out, f.h, zz, 3.4, 24).map(::pv))
        sd.trun.d =
            poly(
                hull(
                    (disc(ax(dishPivot, f.e, sd.side * DISH_R), f.h, zz, 2.2, 16) +
                            disc(
                                ax(dishPivot, f.e, sd.side * (DISH_W - DISH_T / 2)),
                                f.h,
                                zz,
                                2.2,
                                16,
                            ))
                        .map(::pv)
                )
            )
    }

    private fun drawBowl(f: DishFrame) {
        val lip = disc(dishPivot, f.e, f.u, DISH_R, 72)
        val back = ArrayList<Vec3>()
        listOf(0.0, 0.35, 0.6, 0.78, 0.9, 0.97, 1.0).forEach { fraction ->
            repeat(40) { i ->
                back.add(
                    onDish(
                        f,
                        fraction * fraction * DISH_R * DISH_R / (4 * DISH_FOC) - DISH_DEP - DISH_SK,
                        fraction * DISH_R,
                        2 * PI * i / 40,
                    )
                )
            }
        }
        outline.d = poly(hull((lip + back).map(::pv)))
        seam.d = seen(f, 0.62 * DISH_R, 48)
        hubRing.d = seen(f, 3.2, 20)
        rim.d = poly(lip.map(::pv))
        val mouth = onDish(f, DISH_FOC - DISH_DEP - 3, 0.0, 0.0)
        val tail = onDish(f, DISH_FOC - DISH_DEP + 3, 0.0, 0.0)
        struts.d =
            listOf(90.0, 210.0, 330.0)
                .map { seg(pv(onDish(f, 0.0, DISH_R, rad(it))), pv(mouth)) }
                .join()
        put(
            feed,
            PrismPaths(
                poly(
                    hull((disc(mouth, f.e, f.u, 3.6, 16) + disc(tail, f.e, f.u, 2.4, 16)).map(::pv))
                ),
                poly(disc(tail, f.e, f.u, 1.3, 16).map(::pv)),
            ),
        )
    }

    private fun retarget() {
        val point = over
        if (point != null) {
            val hub = p(0.0, 0.0, DISH_ZE)
            az.t = DISH_AZ0 - reach * tanh((point.x - hub.x) / 120)
            el.t = DISH_EL0 + reach / 2 * tanh((hub.y - point.y) / 90)
            els.read.textContent =
                "az ${jsRound((az.t + 360) % 360).toInt()} · el ${jsRound(el.t).toInt()}"
        } else {
            az.t = dishRest.x
            el.t = dishRest.y
            els.read.textContent = "rest"
        }
        loop.wake()
    }

    override fun set(value: Double) {
        reach = value
        if (over != null) retarget()
    }

    override fun destroy() = bag.dispose()
}
