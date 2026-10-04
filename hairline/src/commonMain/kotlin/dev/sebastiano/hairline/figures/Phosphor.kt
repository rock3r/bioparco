// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline.figures

import dev.sebastiano.hairline.Disposer
import dev.sebastiano.hairline.EllipseNode
import dev.sebastiano.hairline.FigureEls
import dev.sebastiano.hairline.FigureHandle
import dev.sebastiano.hairline.PointerHandlers
import dev.sebastiano.hairline.Vec2
import dev.sebastiano.hairline.Vec3
import dev.sebastiano.hairline.cam
import dev.sebastiano.hairline.clamp
import dev.sebastiano.hairline.extremes
import dev.sebastiano.hairline.facing
import dev.sebastiano.hairline.fit
import dev.sebastiano.hairline.flatDot
import dev.sebastiano.hairline.g
import dev.sebastiano.hairline.join
import dev.sebastiano.hairline.jsRound
import dev.sebastiano.hairline.lerp
import dev.sebastiano.hairline.path
import dev.sebastiano.hairline.place
import dev.sebastiano.hairline.poly
import dev.sebastiano.hairline.prism
import dev.sebastiano.hairline.proj
import dev.sebastiano.hairline.put
import dev.sebastiano.hairline.reducedMotion
import dev.sebastiano.hairline.reflect
import dev.sebastiano.hairline.ringAt
import dev.sebastiano.hairline.rings
import dev.sebastiano.hairline.rrect
import dev.sebastiano.hairline.seg
import dev.sebastiano.hairline.solid
import dev.sebastiano.hairline.unproj
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max

private const val PHOSPHOR_N = 7
private const val PITCH = 10.0
private const val MARGIN = 8.0
private const val EXT = MARGIN * 2 + PITCH * (PHOSPHOR_N - 1)
private const val TILE_TOP = 5.0
private const val DOT_RADIUS = 2.5
private const val DROP = 15.0
private const val BASE_THICKNESS = 4.0
private const val BASE_OVERHANG = 7.0
private const val FRAME_MS = 110.0
private const val RESUME = 1200.0
private const val RAMP = 400.0

/** How close to the still a dot must be to count as settled: well under one step of its opacity. */
private const val SETTLED = 5e-4

private val phosphorLoop =
    listOf(
        listOf(0, 0, 0, 8, 0, 0, 0),
        listOf(0, 0, 8, 20, 8, 0, 0),
        listOf(0, 28, 34, 34, 34, 28, 0),
        listOf(28, 34, 65, 65, 65, 34, 28),
        listOf(65, 0, 0, 0, 0, 0, 65),
        listOf(0, 0, 0, 0, 0, 0, 0),
        listOf(0, 0, 0, 0, 0, 0, 0),
        listOf(64, 0, 0, 0, 0, 0, 0),
        listOf(32, 64, 0, 0, 0, 0, 0),
        listOf(16, 32, 64, 0, 0, 0, 0),
        listOf(8, 16, 32, 64, 0, 0, 0),
        listOf(4, 8, 16, 32, 64, 0, 0),
        listOf(2, 4, 8, 16, 32, 64, 0),
        listOf(1, 2, 4, 8, 16, 32, 64),
        listOf(0, 1, 2, 4, 8, 16, 32),
        listOf(0, 0, 1, 2, 4, 8, 16),
        listOf(0, 0, 0, 1, 2, 4, 8),
        listOf(0, 0, 0, 0, 1, 2, 4),
        listOf(0, 0, 0, 0, 0, 1, 2),
        listOf(0, 0, 0, 0, 0, 0, 1),
        listOf(0, 0, 0, 0, 0, 0, 0),
        listOf(0, 0, 0, 0, 0, 0, 0),
    )
private val splash =
    listOf(
        Triple(0, 0, 1.0),
        Triple(1, 0, .45),
        Triple(-1, 0, .45),
        Triple(0, 1, .45),
        Triple(0, -1, .45),
    )

private fun lit(frame: Int, row: Int, col: Int) =
    (phosphorLoop[frame][row] shr (PHOSPHOR_N - 1 - col)) and 1

internal fun mountPhosphor(els: FigureEls, value: Double): FigureHandle = PhosphorFigure(els, value)

/**
 * Phosphor (Fig 9.4): a 7 × 7 dot matrix you can paint on. Each dot holds an intensity that decays
 * with persistence τ. At idle a loop of frames excites the dots; painting fades back to it.
 */
private class PhosphorFigure(private val els: FigureEls, value: Double) : FigureHandle {
    private val bag = Disposer()
    private var tau = value
    private var clock = 0.0
    private var lastFrame = -1
    private var leftAt = -1e9
    private var painting = false
    private var prev: Vec2? = null
    private val c = cam(45.0, .5, 2.12)
    private val zBase = -DROP - BASE_THICKNESS
    private val p: dev.sebastiano.hairline.Projector
    private val front: (dev.sebastiano.hairline.Sample) -> Boolean
    private val g = els.svg.g()
    private val intensity = FloatArray(PHOSPHOR_N * PHOSPHOR_N)
    private val dots = ArrayList<EllipseNode>()

    init {
        fit(
            c,
            listOf(
                Vec3(-BASE_OVERHANG, -BASE_OVERHANG, zBase),
                Vec3(EXT + BASE_OVERHANG, EXT + BASE_OVERHANG, zBase - 6),
                Vec3(EXT + BASE_OVERHANG, -BASE_OVERHANG, zBase),
                Vec3(-BASE_OVERHANG, EXT + BASE_OVERHANG, zBase),
                Vec3(0.0, 0.0, TILE_TOP),
            ),
            200.0,
            160.0,
        )
        p = proj(c)
        front = facing(c)
        buildScene()
    }

    private val loop = els.stage.register(::tick)

    init {
        bag.add(loop::unregister)
        bag.add(
            els.stage.pointer(
                object : PointerHandlers {
                    override fun move(p: Vec2) = paint(p)

                    override fun leave() {
                        painting = false
                        prev = null
                        leftAt = els.stage.now()
                        clock = 0.0
                        lastFrame = -1
                        loop.wake()
                    }
                }
            )
        )
        bag.add { els.svg.clear() }
    }

    private fun buildScene() {
        val (br, bi) =
            rings(
                -BASE_OVERHANG,
                -BASE_OVERHANG,
                EXT + BASE_OVERHANG,
                EXT + BASE_OVERHANG,
                11.0,
                1.8,
            )
        reflect(g, p, front, br, zBase, 12.0)
        put(solid(g), prism(p, front, br, bi, zBase, -DROP))
        val (tr, ti) = rings(0.0, 0.0, EXT, EXT, 7.0, 1.3)
        g.path(
            "nf dash",
            extremes(p, tr).toList().map { seg(p(it.u, it.v, 0.0), p(it.u, it.v, -DROP)) }.join(),
        )
        put(solid(g), prism(p, front, tr, ti, 0.0, TILE_TOP))
        g.path("nf lo", poly(ringAt(p, rrect(3.5, 3.5, EXT - 3.5, EXT - 3.5, 4.5, 5), TILE_TOP)))
        repeat(PHOSPHOR_N) { row ->
            repeat(PHOSPHOR_N) { col ->
                dots.add(
                    flatDot(g, c, DOT_RADIUS, "dot").also {
                        place(it, p(MARGIN + col * PITCH, MARGIN + row * PITCH, TILE_TOP))
                    }
                )
            }
        }
    }

    private fun excite(x: Double, y: Double, amount: Double) {
        val col = jsRound((x - MARGIN) / PITCH).toInt()
        val row = jsRound((y - MARGIN) / PITCH).toInt()
        for ((dr, dc, scale) in splash) {
            val rr = row + dr
            val cc = col + dc
            if (rr in 0 until PHOSPHOR_N && cc in 0 until PHOSPHOR_N) {
                val i = rr * PHOSPHOR_N + cc
                intensity[i] = max(intensity[i].toDouble(), amount * scale).toFloat()
            }
        }
    }

    private fun tick(dt: Double, now: Double): Boolean {
        val decay = exp(-dt * 1000 / tau)
        // With reduced motion there is no loop: the dots settle on a composed still, the ripple's
        // widest ring. Unlike the original, which asks for frames forever, it sleeps once there.
        var fading = true
        if (reducedMotion() && !painting) {
            fading = false
            intensity.indices.forEach { i ->
                val still = lit(3, i / PHOSPHOR_N, i % PHOSPHOR_N) * .8
                intensity[i] = max(intensity[i] * decay, still).toFloat()
                if (intensity[i] - still > SETTLED) fading = true
            }
        } else {
            intensity.indices.forEach { intensity[it] = (intensity[it] * decay).toFloat() }
            animateLoop(dt, now)
        }
        intensity.indices.forEach {
            dots[it].opacity = .14 + .86 * clamp(intensity[it].toDouble(), 0.0, 1.0)
        }
        return fading
    }

    private fun animateLoop(dt: Double, now: Double) {
        val gain = if (painting) 0.0 else clamp((now - leftAt - RESUME) / RAMP, 0.0, 1.0)
        if (gain > 0) {
            clock += dt * 1000
            val frame = floor(clock / FRAME_MS).toInt() % phosphorLoop.size
            if (frame != lastFrame) {
                lastFrame = frame
                repeat(PHOSPHOR_N) { r ->
                    repeat(PHOSPHOR_N) { c ->
                        if (lit(frame, r, c) != 0)
                            intensity[r * PHOSPHOR_N + c] =
                                max(intensity[r * PHOSPHOR_N + c].toDouble(), gain).toFloat()
                    }
                }
            }
            els.read.textContent =
                "loop · ${(frame + 1).toString().padStart(2, '0')}/${phosphorLoop.size}"
        } else els.read.textContent = if (painting) "paint" else "afterglow"
    }

    private fun paint(point: Vec2) {
        painting = true
        val q = unproj(c, point.x, point.y, TILE_TOP)
        val old = prev
        if (old == null) excite(q.x, q.y, 1.0)
        else {
            val n = ceil(hypot(q.x - old.x, q.y - old.y) / 3).toInt()
            for (k in 1..n) excite(
                lerp(old.x, q.x, k.toDouble() / n),
                lerp(old.y, q.y, k.toDouble() / n),
                1.0,
            )
        }
        prev = q
        loop.wake()
    }

    override fun set(value: Double) {
        tau = value
    }

    override fun destroy() = bag.dispose()
}
