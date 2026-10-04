// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline.figures

import dev.sebastiano.hairline.Disposer
import dev.sebastiano.hairline.FigureEls
import dev.sebastiano.hairline.FigureHandle
import dev.sebastiano.hairline.PathData
import dev.sebastiano.hairline.PathNode
import dev.sebastiano.hairline.PointerHandlers
import dev.sebastiano.hairline.Sample
import dev.sebastiano.hairline.Vec2
import dev.sebastiano.hairline.Vec3
import dev.sebastiano.hairline.cam
import dev.sebastiano.hairline.clamp
import dev.sebastiano.hairline.facing
import dev.sebastiano.hairline.fit
import dev.sebastiano.hairline.g
import dev.sebastiano.hairline.hull
import dev.sebastiano.hairline.join
import dev.sebastiano.hairline.path
import dev.sebastiano.hairline.poly
import dev.sebastiano.hairline.prism
import dev.sebastiano.hairline.proj
import dev.sebastiano.hairline.put
import dev.sebastiano.hairline.rad
import dev.sebastiano.hairline.rings
import dev.sebastiano.hairline.rrect
import dev.sebastiano.hairline.seg
import dev.sebastiano.hairline.solid
import dev.sebastiano.hairline.tdone
import dev.sebastiano.hairline.tset
import dev.sebastiano.hairline.tval
import dev.sebastiano.hairline.tween
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

private const val LK_COLS = 6
private const val LK_ROWS = 2
private const val LK_W = 24.0
private const val LK_HR = 38.0
private const val LK_GAP = 1.2
private const val LK_FM = 3.0
private const val LK_ZP = 6.0
private const val LK_D = 26.0
private const val LK_T = 1.4
private const val LK_DEP = 23.0
private const val LK_BW = LK_COLS * LK_W + 2 * LK_FM
private const val LK_BH = LK_ROWS * LK_HR + 2 * LK_FM
private const val LK_DW = LK_W - 2 * LK_GAP
private const val LK_DH = LK_HR - 2 * LK_GAP
private const val LK_REST_I = 5
private const val LK_REST_A = 22.0
private const val LK_FAR = 120.0
private val lkOutline = rrect(0.0, 0.0, LK_DW, LK_DH, 1.8, 4)
private val lkVents =
    List(3) { k -> rrect(4.0, LK_DH - 6.6 - k * 3.2, LK_DW - 4, LK_DH - 5.2 - k * 3.2, .7, 2) }
private val lkPlate = rrect(LK_DW / 2 - 4.5, LK_DH - 19, LK_DW / 2 + 4.5, LK_DH - 15, 1.0, 3)
private val lkCup = rrect(LK_DW - 5.6, LK_DH / 2 - 7, LK_DW - 2.8, LK_DH / 2 + 2, 1.2, 3)
private val lkFrame = rrect(2.6, 2.6, LK_DW - 2.6, LK_DH - 2.6, 1.4, 3)

private fun area(q: List<Vec2>) =
    q.indices.sumOf { i ->
        val n = q[(i + 1) % q.size]
        q[i].x * n.y - n.x * q[i].y
    }

private class Locker(
    val n: Int,
    val x0: Double,
    val z0: Double,
    val plate: PathNode,
    val face: PathNode,
    val marks: PathNode,
    val cup: PathNode,
) {
    var drawn = Double.NaN
    var sign0 = 0.0
    var tw = tween(0.0)
}

internal fun mountLockers(els: FigureEls, value: Double): FigureHandle = LockersFigure(els, value)

private class LockersFigure(private val els: FigureEls, value: Double) : FigureHandle {
    private val bag = Disposer()
    private val c =
        cam(45.0, .5, 1.6).also {
            val sw = Vec2(cos(rad(LK_FAR)) * LK_DW, sin(rad(LK_FAR)) * LK_DW)
            fit(
                it,
                listOf(
                    Vec3(-4.0, -4.0, 0.0),
                    Vec3(LK_BW + 4, LK_D + 4, 0.0),
                    Vec3(LK_BW + 4, -4.0, 0.0),
                    Vec3(-4.0, LK_D + 4, 0.0),
                    Vec3(0.0, 0.0, LK_ZP + LK_BH),
                    Vec3(LK_BW, 0.0, LK_ZP + LK_BH),
                    Vec3(LK_FM + sw.x, LK_D + sw.y, LK_ZP + LK_FM),
                    Vec3(LK_FM + sw.x, LK_D + sw.y, LK_ZP + LK_BH - LK_FM),
                ),
                200.0,
                166.0,
            )
        }
    private val p = proj(c)
    private val front = facing(c)
    private var max = value
    private var act = -1
    private val g = els.svg.g()
    private val lockers = ArrayList<Locker>()

    init {
        val (pr, pi) = rings(-4.0, -4.0, LK_BW + 4, LK_D + 4, 5.0, 1.6)
        put(solid(g), prism(p, front, pr, pi, 0.0, LK_ZP))
        val (br, bi) = rings(0.0, 0.0, LK_BW, LK_D, 3.0, 1.4)
        put(solid(g), prism(p, front, br, bi, LK_ZP, LK_ZP + LK_BH))
        buildLockers()
        lockers.forEach {
            it.sign0 = sign(area(lkOutline.map { q -> p(it.x0 + q.u, LK_D + LK_T, it.z0 + q.v) }))
        }
    }

    private val loop =
        els.stage.register { _, now ->
            var moving = false
            lockers.forEach {
                drawDoor(it, tval(it.tw, now))
                if (!tdone(it.tw, now)) moving = true
            }
            moving
        }

    init {
        bag.add(loop::unregister)
        lockers.first { it.n == LK_REST_I }.tw = tween(LK_REST_A)
        choose(0, true)
        bag.add(
            els.stage.pointer(
                object : PointerHandlers {
                    override fun move(p: Vec2) = choose(kotlin.math.max(0, hit(p)))

                    override fun leave() = choose(0)
                }
            )
        )
        bag.add { els.svg.clear() }
    }

    private fun onFace(q: Vec2, y0: Double): Vec2 {
        val o = p(0.0, y0, 0.0)
        val a = p(1.0, y0, 0.0)
        val b = p(0.0, y0, 1.0)
        val ex = Vec2(a.x - o.x, a.y - o.y)
        val ez = Vec2(b.x - o.x, b.y - o.y)
        val d = ex.x * ez.y - ex.y * ez.x
        val x = q.x - o.x
        val y = q.y - o.y
        return Vec2((x * ez.y - y * ez.x) / d, (ex.x * y - ex.y * x) / d)
    }

    private fun through(a: Vec3, b: Vec3, x0: Double, z0: Double): PathData {
        val pa = p(a.x, a.y, a.z)
        val pb = p(b.x, b.y, b.z)
        val x = onFace(pa, LK_D)
        val y = onFace(pb, LK_D)
        var t0 = 0.0
        var t1 = 1.0
        val m = .9
        val lim =
            listOf(
                x.x - y.x to x.x - (x0 + m),
                y.x - x.x to x0 + LK_DW - m - x.x,
                x.y - y.y to x.y - (z0 + m),
                y.y - x.y to z0 + LK_DH - m - x.y,
            )
        for ((u, v) in lim) {
            if (u == 0.0) {
                if (v < 0) return PathData.EMPTY
            } else {
                val r = v / u
                if (u < 0) t0 = max(t0, r) else t1 = min(t1, r)
            }
        }
        if (t1 - t0 < .01) return PathData.EMPTY
        fun at(t: Double) = Vec2(pa.x + (pb.x - pa.x) * t, pa.y + (pb.y - pa.y) * t)
        return seg(at(t0), at(t1))
    }

    private fun buildLockers() {
        for (col in 0 until LK_COLS) for (row in 0 until LK_ROWS) {
            val x = LK_FM + col * LK_W + LK_GAP
            val z = LK_ZP + LK_FM + row * LK_HR + LK_GAP
            val q = g.g()
            q.path(d = poly(lkOutline.map { p(x + it.u, LK_D, z + it.v) }))
            val zs = z + LK_DH - 9.5
            val x1 = x + LK_DW
            q.path(
                "nf lo",
                listOf(
                        through(Vec3(x, LK_D, z), Vec3(x, LK_D - LK_DEP, z), x, z),
                        through(Vec3(x, LK_D - 1.6, zs), Vec3(x1, LK_D - 1.6, zs), x, z),
                        through(Vec3(x, LK_D - 1.6, zs), Vec3(x, LK_D - LK_DEP, zs), x, z),
                    )
                    .join(),
            )
            val plate = q.path("lo")
            val face = q.path("sil")
            val marks = q.path("nf lo")
            val cup = q.path("nf")
            val l = Locker((LK_ROWS - 1 - row) * LK_COLS + col + 1, x, z, plate, face, marks, cup)
            lockers += l
        }
    }

    private fun drawDoor(l: Locker, th: Double) {
        if (th == l.drawn) return
        l.drawn = th
        val cs = cos(rad(th))
        val sn = sin(rad(th))
        fun at(s: Double, q: Sample) =
            p(l.x0 + q.u * cs - s * sn, LK_D + LK_T / 2 + q.u * sn + s * cs, l.z0 + q.v)
        val fr = lkOutline.map { at(LK_T / 2, it) }
        val bk = lkOutline.map { at(-LK_T / 2, it) }
        val outward = area(fr) * l.sign0 > 0
        val s = if (outward) LK_T / 2 else -LK_T / 2
        l.plate.d = poly(hull(fr + bk))
        l.face.d = poly(if (outward) fr else bk)
        l.marks.d =
            (lkVents + (if (outward) listOf(lkPlate) else listOf(lkFrame)))
                .map { poly(it.map { q -> at(s, q) }) }
                .join()
        l.cup.d = if (outward) poly(lkCup.map { at(s, it) }) else PathData.EMPTY
    }

    private fun hit(q: Vec2): Int {
        val (x, z) = onFace(q, LK_D + LK_T)
        if (x < 0 || x > LK_BW || z < LK_ZP || z > LK_ZP + LK_BH) return -1
        val col = clamp(floor((x - LK_FM) / LK_W), 0.0, (LK_COLS - 1).toDouble()).toInt()
        val row = clamp(floor((z - LK_ZP - LK_FM) / LK_HR), 0.0, (LK_ROWS - 1).toDouble()).toInt()
        return (LK_ROWS - 1 - row) * LK_COLS + col + 1
    }

    private fun choose(n: Int, force: Boolean = false) {
        if (n == act && !force) return
        act = n
        val now = els.stage.now()
        val lit = if (n > 0) n else LK_REST_I
        lockers.forEach {
            tset(
                it.tw,
                if (it.n == n) max else if (n <= 0 && it.n == LK_REST_I) LK_REST_A else 0.0,
                now,
                0.0,
            )
            it.face.classList.toggle("hi", it.n == lit)
            it.cup.classList.toggle("hi", it.n == lit)
        }
        els.read.textContent = if (n > 0) "locker ${n.toString().padStart(2,'0')}" else "rest"
        loop.wake()
    }

    override fun set(value: Double) {
        max = value
        if (act > 0) choose(act, true)
    }

    override fun destroy() = bag.dispose()
}
