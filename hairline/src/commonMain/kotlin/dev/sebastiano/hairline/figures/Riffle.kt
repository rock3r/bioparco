// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline.figures

import dev.sebastiano.hairline.CircleNode
import dev.sebastiano.hairline.Disposer
import dev.sebastiano.hairline.FigureEls
import dev.sebastiano.hairline.FigureHandle
import dev.sebastiano.hairline.PathNode
import dev.sebastiano.hairline.PointerHandlers
import dev.sebastiano.hairline.Tween
import dev.sebastiano.hairline.Vec2
import dev.sebastiano.hairline.circle
import dev.sebastiano.hairline.clamp
import dev.sebastiano.hairline.g
import dev.sebastiano.hairline.jsRound
import dev.sebastiano.hairline.path
import dev.sebastiano.hairline.place
import dev.sebastiano.hairline.rad
import dev.sebastiano.hairline.reflect
import dev.sebastiano.hairline.tdone
import dev.sebastiano.hairline.tset
import dev.sebastiano.hairline.tval
import dev.sebastiano.hairline.tween
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Riffle (Fig 9.1): a rounded tray holding eight cards. The card under the pointer stands up and
 * lifts; the ones in front lean forward and the ones behind lean back, staggered outwards from it
 * on the 700ms lift curve. A discrete figure, so every card runs on tweens.
 *
 * Selection never reads the posed cards: it comes from static oblique bands along the resting top
 * edges, so a card moving out from under the pointer can't flip the choice back and forth. The
 * bands are geometry only; nothing draws them. The arrow keys walk the cards and the read-out
 * names the one pulled by its number.
 */

private class Card(
    val n: Int,
    val t0: Double,
    val shape: List<Vec2>,
    val back: PathNode,
    val face: PathNode,
    val head: PathNode,
    val rules: PathNode,
    val punch: List<CircleNode>,
) {
    /** Lean in degrees, and lift. */
    val a: Tween = tween(RIFFLE_REST)
    val z: Tween = tween(0.0)
}

internal fun mountRiffle(els: FigureEls, value: Double): FigureHandle = RiffleFigure(els, value)

private class RiffleFigure(private val els: FigureEls, value: Double) : FigureHandle {
    private val bag = Disposer()
    private var stag = value
    private val scene = RiffleScene()
    private val p = scene.p
    private val g = els.svg.g()
    private val cards = ArrayList<Card>()

    init {
        val paths = tray(p, scene.front, scene.outer, scene.inner)
        reflect(g, p, scene.front, scene.outer, 0.0, 14.0)
        for ((d, cls) in paths.far) g.path(cls, d)
        for (i in 0 until RIFFLE_N) {
            val shape = card(i)
            val grp = g.g()
            val back = grp.path("lo")
            val face = grp.path("sil")
            val head = grp.path("nf")
            val rules = grp.path("nf lo")
            // the card's number, punched in a 4 × 2 grid on its tab
            val punch =
                List(8) { k -> grp.circle(1.05, "dot " + if (k == shape.n - 1) "m" else "off") }
            cards.add(Card(shape.n, shape.t0, shape.shape, back, face, head, rules, punch))
        }
        for ((d, cls) in paths.near) g.path(cls, d)
    }

    // hit bands: oblique strips along the RESTING top edges. They never move.
    private val c0 = top(0)
    private val dv = top(1).let { Vec2(it.x - c0.x, it.y - c0.y) }
    private val ex =
        p(1.0, 0.0, 0.0).let { px1 -> p(0.0, 0.0, 0.0).let { Vec2(px1.x - it.x, px1.y - it.y) } }
    private val half = RIFFLE_W / 2 + 6
    private val det = dv.x * ex.y - dv.y * ex.x

    private val loop =
        els.stage.register { _, now ->
            var moving = false
            cards.forEachIndexed { i, cd ->
                draw(i, tval(cd.a, now), tval(cd.z, now))
                if (!tdone(cd.a, now) || !tdone(cd.z, now)) moving = true
            }
            moving
        }

    private var act = -1

    init {
        bag.add(loop::unregister)
        bag.add(
            els.stage.pointer(
                object : PointerHandlers {
                    override fun move(p: Vec2) = setActive(hit(p))

                    override fun leave() = setActive(-1)
                }
            )
        )
        bag.add(els.stage.onKey(::onKey))
        bag.add(els.stage.onBlur { setActive(-1) })
        bag.add { els.svg.clear() }
    }

    private fun top(i: Int) =
        p(
            RIFFLE_W / 2,
            i * RIFFLE_G + RIFFLE_H * sin(rad(RIFFLE_REST)),
            RIFFLE_H * cos(rad(RIFFLE_REST)),
        )

    /** The card whose band holds the point, in the band's own (s, r) coordinates; -1 outside. */
    private fun hit(q: Vec2): Int {
        val qx = q.x - c0.x
        val qy = q.y - c0.y
        val s = (qx * ex.y - qy * ex.x) / det
        val r = (dv.x * qy - dv.y * qx) / det
        if (abs(r) > half || s < -0.5 || s > RIFFLE_N + 1) return -1
        return clamp(jsRound(s), 0.0, RIFFLE_N - 1.0).toInt()
    }

    private fun draw(i: Int, th: Double, lift: Double) {
        val cd = cards[i]
        val q = pose(p, i, cd.t0, cd.shape, th, lift)
        cd.back.d = q.back
        cd.face.d = q.face
        cd.head.d = q.head
        cd.rules.d = q.rules
        cd.punch.forEachIndexed { k, el -> place(el, q.punch[k]) }
    }

    private fun caption(a: Int) = if (a < 0) "rest" else (RIFFLE_N - a).toString().padStart(2, '0')

    /**
     * Pulls card a (-1 puts them all back). The stagger spreads out from the card pulled, or the
     * one let go.
     */
    private fun setActive(a: Int) {
        if (a == act) return
        val now = els.stage.now()
        val from = if (a >= 0) a else act
        act = a
        cards.forEachIndexed { i, cd ->
            val delay = abs(i - from) * stag
            val th =
                when {
                    a < 0 -> RIFFLE_REST
                    i < a -> RIFFLE_BACK
                    i > a -> RIFFLE_FWD
                    else -> 0.0
                }
            tset(cd.a, th, now, delay)
            tset(cd.z, if (a == i) RIFFLE_LIFT else 0.0, now, delay)
            cd.face.classList.toggle("hi", i == a)
            cd.head.classList.toggle("hi", i == a)
            cd.punch[cd.n - 1].classList.toggle("m", i != a)
        }
        els.read.textContent = caption(a)
        loop.wake()
    }

    private fun onKey(key: String): Boolean =
        when {
            key == "ArrowLeft" || key == "ArrowDown" -> {
                setActive(if (act < 0) RIFFLE_N - 1 else min(RIFFLE_N - 1, act + 1))
                true
            }
            key == "ArrowRight" || key == "ArrowUp" -> {
                setActive(if (act < 0) RIFFLE_N - 1 else max(0, act - 1))
                true
            }
            // Escape puts a pulled card back and claims the key; at rest it passes on.
            key == "Escape" && act >= 0 -> {
                setActive(-1)
                true
            }
            else -> false
        }

    override fun set(value: Double) {
        stag = value
    }

    override fun destroy() {
        bag.dispose()
    }
}
