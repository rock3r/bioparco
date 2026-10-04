// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline.figures

import dev.sebastiano.hairline.Disposer
import dev.sebastiano.hairline.EllipseNode
import dev.sebastiano.hairline.FigureEls
import dev.sebastiano.hairline.FigureHandle
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
import dev.sebastiano.hairline.flatDot
import dev.sebastiano.hairline.g
import dev.sebastiano.hairline.place
import dev.sebastiano.hairline.prism
import dev.sebastiano.hairline.proj
import dev.sebastiano.hairline.put
import dev.sebastiano.hairline.rings
import dev.sebastiano.hairline.solid
import dev.sebastiano.hairline.spring
import dev.sebastiano.hairline.stepS
import dev.sebastiano.hairline.unproj
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow

/*
 * Terrain (Fig 9.2): a falloff in two dimensions. The pointer is projected back onto the ground,
 * and each of 81 pillars on a rounded plinth takes its height from its radial distance to it, each
 * on its own spring. At rest the field is a designed dune with two rises. A 3 × 3 dot mark rides
 * the lid of the pillar under the pointer, or the peak at rest; pillars above half height take the
 * bright stroke. The parameter is the radius, in cells.
 */

private const val N = 9
private const val CELL = 14.0
private const val FOOT = 11.0
private const val HMAX = 58.0
private const val EXT = N * CELL
private const val PB = 5.0

/** Linear's ratios, as a fraction of the radius: 1 → .31 at 42% → .09 at the edge and beyond. */
private fun falloff(u: Double): Double =
    when {
        u <= 0 -> 1.0
        u <= 0.417 -> 1 - u / 0.417 * 0.6875
        u <= 1 -> 0.3125 - (u - 0.417) / 0.583 * 0.2185
        else -> 0.094
    }

private class Col(
    val i: Int,
    val j: Int,
    val h0: Double,
    val ring: Ring,
    val inner: Ring,
    val sp: Spring,
    val el: Solid,
) {
    var drawn = Double.NaN
}

internal fun mountTerrain(els: FigureEls, value: Double): FigureHandle = TerrainFigure(els, value)

private class TerrainFigure(private val els: FigureEls, value: Double) : FigureHandle {
    private val bag = Disposer()
    private val c =
        cam(45.0, 0.5, 1.58).also {
            fit(
                it,
                listOf(
                    Vec3(-6.0, -6.0, -PB),
                    Vec3(EXT + 6, EXT + 6, -PB),
                    Vec3(EXT + 6, -6.0, -PB),
                    Vec3(-6.0, EXT + 6, -PB),
                    Vec3(0.0, 0.0, HMAX * 0.75),
                ),
                200.0,
                166.0,
            )
        }
    private val p = proj(c)
    private val front = facing(c)
    private var radius = value * CELL
    private var over: Vec2? = null

    private val g = els.svg.g()
    private val cols = ArrayList<Col>()

    init {
        buildPlinth()
        buildCols()
    }

    // The mark: a 3 × 3 of dots riding the lid of one pillar, moved in the paint order to just
    // after it, so the pillars in front still cover it.
    private val mark = g.g()
    private val md: List<EllipseNode> =
        List(9) { k -> flatDot(mark, c, 0.55, if (k == 4) "dot" else "dot m") }
    private val peak = cols.reduce { a, b -> if (b.h0 > a.h0) b else a }
    private val byCell = cols.associateBy { it.i * N + it.j }
    private var mc: Col? = null
    private var want = peak

    private val loop =
        els.stage.register { dt, _ ->
            var m = false
            for (col in cols) {
                if (stepS(col.sp, dt)) m = true
                drawCol(col)
            }
            drawMark()
            m
        }

    init {
        bag.add(loop::unregister)
        bag.add(
            els.stage.pointer(
                object : PointerHandlers {
                    override fun move(p: Vec2) {
                        over = unproj(c, p.x, p.y, 0.0)
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

    private fun buildPlinth() {
        val (pr, pi) = rings(-6.0, -6.0, EXT + 6, EXT + 6, 9.0, 2.2)
        put(solid(g), prism(p, front, pr, pi, -PB, 0.0))
    }

    /** Diagonal by diagonal from the back corner, so appending is painting back to front. */
    private fun buildCols() {
        for (s in 0..2 * (N - 1)) {
            for (i in 0 until N) {
                val j = s - i
                if (j < 0 || j >= N) continue
                val u = i.toDouble() / (N - 1)
                val v = j.toDouble() / (N - 1)
                val h0 =
                    4 +
                        25 * exp(-((u - 0.22).pow(2) + (v - 0.74).pow(2)) / 0.07) +
                        12 * exp(-((u - 0.8).pow(2) + (v - 0.26).pow(2)) / 0.035)
                val x0 = i * CELL + (CELL - FOOT) / 2
                val y0 = j * CELL + (CELL - FOOT) / 2
                val (ring, inner) = rings(x0, y0, x0 + FOOT, y0 + FOOT, 2.6, 0.9)
                cols.add(Col(i, j, h0, ring, inner, spring(h0, eps = 0.04), solid(g)))
            }
        }
    }

    /** The mark rides the lid of one pillar, moved in the paint order to just after it. */
    private fun drawMark() {
        if (want !== mc) {
            mc = want
            want.el.g.after(mark)
        }
        val col = want
        val cx = (col.i + 0.5) * CELL
        val cy = (col.j + 0.5) * CELL
        val h = max(0.6, col.sp.x)
        md.forEachIndexed { k, el ->
            place(el, p(cx + (k % 3 - 1) * 2.5, cy + (k / 3 - 1) * 2.5, h))
        }
    }

    /**
     * A pillar whose spring hasn't moved keeps its paths: most of the 81 are still on most frames.
     */
    private fun drawCol(col: Col) {
        val h = max(0.6, col.sp.x)
        if (h == col.drawn) return
        col.drawn = h
        put(col.el, prism(p, front, col.ring, col.inner, 0.0, h))
        col.el.sil.classList.toggle("hi", h > HMAX * 0.5)
    }

    private fun retarget() {
        val o = over
        for (col in cols) {
            if (o == null) {
                col.sp.t = col.h0
                continue
            }
            val dx = (col.i + 0.5) * CELL - o.x
            val dy = (col.j + 0.5) * CELL - o.y
            col.sp.t = HMAX * falloff(hypot(dx, dy) / radius)
        }
        if (o != null) {
            val i = clamp(floor(o.x / CELL), 0.0, N - 1.0).toInt()
            val j = clamp(floor(o.y / CELL), 0.0, N - 1.0).toInt()
            want = byCell.getValue(i * N + j)
            els.read.textContent = "cell $i·$j"
        } else {
            want = peak
            els.read.textContent = "rest"
        }
        loop.wake()
    }

    override fun set(value: Double) {
        radius = value * CELL
        if (over != null) retarget()
    }

    override fun destroy() {
        bag.dispose()
    }
}
