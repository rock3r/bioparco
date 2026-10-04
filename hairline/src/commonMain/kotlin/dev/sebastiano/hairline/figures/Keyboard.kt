// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline.figures

import dev.sebastiano.hairline.Disposer
import dev.sebastiano.hairline.FigureEls
import dev.sebastiano.hairline.FigureHandle
import dev.sebastiano.hairline.PathNode
import dev.sebastiano.hairline.PointerHandlers
import dev.sebastiano.hairline.Ring
import dev.sebastiano.hairline.Solid
import dev.sebastiano.hairline.Spring
import dev.sebastiano.hairline.Vec2
import dev.sebastiano.hairline.Vec3
import dev.sebastiano.hairline.cam
import dev.sebastiano.hairline.clamp
import dev.sebastiano.hairline.facing
import dev.sebastiano.hairline.fit
import dev.sebastiano.hairline.g
import dev.sebastiano.hairline.hull
import dev.sebastiano.hairline.open
import dev.sebastiano.hairline.path
import dev.sebastiano.hairline.poly
import dev.sebastiano.hairline.prism
import dev.sebastiano.hairline.proj
import dev.sebastiano.hairline.put
import dev.sebastiano.hairline.ringAt
import dev.sebastiano.hairline.rings
import dev.sebastiano.hairline.rrect
import dev.sebastiano.hairline.run
import dev.sebastiano.hairline.seg
import dev.sebastiano.hairline.solid
import dev.sebastiano.hairline.spring
import dev.sebastiano.hairline.stepS
import dev.sebastiano.hairline.unproj
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max

private const val U = 14.0
private const val PAD = 0.8
private const val TAPER = 1.8
private const val Z0 = 1.0
private const val TRAVEL = 8.0
private const val BZ = 6.0
private const val CASE = 8.0
private const val KEYBOARD_W = 15.0
private val rowH = listOf(12.6, 11.3, 10.4, 11.0, 11.8)

private fun letters(s: String) = s.split(" ").map { 1.0 to "key $it" }

private val rows =
    listOf(
        listOf(1.0 to "esc") + letters("1 2 3 4 5 6 7 8 9 0 - =") + (2.0 to "bksp"),
        listOf(1.5 to "tab") + letters("q w e r t y u i o p [ ]") + (1.5 to "key \\"),
        listOf(1.75 to "caps") + letters("a s d f g h j k l ; '") + (2.25 to "enter"),
        listOf(2.25 to "shift l") + letters("z x c v b n m , . /") + (2.75 to "shift r"),
        listOf(
            1.5 to "ctrl l",
            1.0 to "super",
            1.5 to "alt l",
            7.0 to "space",
            1.5 to "alt r",
            1.0 to "fn",
            1.5 to "ctrl r",
        ),
    )

private class Key(
    val r: Int,
    val name: String,
    val x0: Double,
    val x1: Double,
    val y0: Double,
    val y1: Double,
    val h: Double,
    val foot: Ring,
    val top: Ring,
    val inner: Ring,
    val el: Solid,
    val sp: Spring,
    val bump: PathNode?,
) {
    var drawn = Double.NaN
}

private data class KeyOver(val at: Vec2, val key: Key)

internal fun mountKeyboard(els: FigureEls, value: Double): FigureHandle = KeyboardFigure(els, value)

private class KeyboardFigure(private val els: FigureEls, value: Double) : FigureHandle {
    private val bag = Disposer()
    private val c =
        cam(45.0, .5, 1.42).also {
            fit(
                it,
                listOf(
                    Vec3(-BZ, -BZ, -CASE),
                    Vec3(KEYBOARD_W * U + BZ, 5 * U + BZ, -CASE),
                    Vec3(KEYBOARD_W * U + BZ, -BZ, -CASE),
                    Vec3(-BZ, 5 * U + BZ, -CASE),
                    Vec3(0.0, 0.0, rowH[0]),
                ),
                200.0,
                166.0,
            )
        }
    private val p = proj(c)
    private val front = facing(c)
    private var radius = value
    private var over: KeyOver? = null
    private var lit: Key? = null
    private val g = els.svg.g()
    private val keys = ArrayList<Key>()

    init {
        val (cr, ci) = rings(-BZ, -BZ, KEYBOARD_W * U + BZ, 5 * U + BZ, 9.0, 2.2)
        put(solid(g), prism(p, front, cr, ci, -CASE, 0.0))
        buildKeys()
    }

    private val enter = keys.first { it.name == "enter" }
    private val restAt = Vec2((enter.x0 + enter.x1) / 2, (enter.y0 + enter.y1) / 2)

    init {
        sink(restAt, enter, 1.6, .6)
        keys.forEach { it.sp.x = it.sp.t }
    }

    private val loop =
        els.stage.register { dt, _ ->
            var m = false
            keys.forEach {
                if (stepS(it.sp, dt)) m = true
                draw(it)
            }
            m
        }

    init {
        bag.add(loop::unregister)
        light(enter)
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

    private fun buildKeys() {
        rows.forEachIndexed { r, row ->
            var x = 0.0
            row.forEach { (w, name) ->
                val x0 = x * U
                val x1 = (x + w) * U
                val y0 = r * U
                val y1 = (r + 1) * U
                val t = PAD + TAPER
                val el = solid(g)
                keys.add(
                    Key(
                        r,
                        name,
                        x0,
                        x1,
                        y0,
                        y1,
                        rowH[r],
                        rrect(x0 + PAD, y0 + PAD, x1 - PAD, y1 - PAD, 2.6),
                        rrect(x0 + t, y0 + t, x1 - t, y1 - t, 2.0),
                        rrect(x0 + t + .9, y0 + t + .9, x1 - t - .9, y1 - t - .9, 1.2),
                        el,
                        spring(rowH[r], eps = .02),
                        if (name == "key f" || name == "key j") el.g.path("lo nf") else null,
                    )
                )
                x += w
            }
        }
    }

    private fun sink(at: Vec2, pressed: Key, r: Double, depth: Double) {
        keys.forEach {
            val dx = max(it.x0 - at.x, max(0.0, at.x - it.x1))
            val dy = max(it.y0 - at.y, max(0.0, at.y - it.y1))
            val f = if (it === pressed) 1.0 else clamp(1 - hypot(dx, dy) / (r * U), 0.0, 1.0)
            it.sp.t = it.h - TRAVEL * depth * f
        }
    }

    private fun draw(k: Key) {
        val h = k.sp.x
        if (h == k.drawn) return
        k.drawn = h
        put(
            k.el,
            dev.sebastiano.hairline.PrismPaths(
                poly(hull(ringAt(p, k.foot, Z0) + ringAt(p, k.top, h))),
                open(ringAt(p, run(k.inner, front), h)),
            ),
        )
        k.bump?.d =
            seg(
                p((k.x0 + k.x1) / 2 - 2.2, k.y1 - PAD - TAPER - 2.4, h),
                p((k.x0 + k.x1) / 2 + 2.2, k.y1 - PAD - TAPER - 2.4, h),
            )
    }

    private fun light(k: Key) {
        if (k === lit) return
        lit?.el?.sil?.classList?.remove("hi")
        lit = k
        k.el.sil.classList.add("hi")
    }

    private fun keyAt(r: Int, x: Double) = keys.first { it.r == r && x >= it.x0 && x < it.x1 }

    private fun hit(s: Vec2): KeyOver? {
        for (r in rows.indices.reversed()) {
            val q = unproj(c, s.x, s.y, rowH[r])
            if (q.y >= r * U && q.y < (r + 1) * U && q.x >= 0 && q.x < KEYBOARD_W * U)
                return KeyOver(q, keyAt(r, q.x))
        }
        val q = unproj(c, s.x, s.y, rowH[2])
        if (q.x < -BZ || q.x > KEYBOARD_W * U + BZ || q.y < -BZ || q.y > 5 * U + BZ) return null
        val at = Vec2(clamp(q.x, 0.0, KEYBOARD_W * U - .01), clamp(q.y, 0.0, 5 * U - .01))
        return KeyOver(at, keyAt(floor(at.y / U).toInt(), at.x))
    }

    private fun retarget() {
        val o = over
        if (o == null) {
            sink(restAt, enter, 1.6, .6)
            light(enter)
            els.read.textContent = "rest"
        } else {
            sink(o.at, o.key, radius, 1.0)
            light(o.key)
            els.read.textContent = o.key.name
        }
        loop.wake()
    }

    override fun set(value: Double) {
        radius = value
        if (over != null) retarget()
    }

    override fun destroy() = bag.dispose()
}
