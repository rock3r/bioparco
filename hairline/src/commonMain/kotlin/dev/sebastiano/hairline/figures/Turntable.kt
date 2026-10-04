// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline.figures

import dev.sebastiano.hairline.Disposer
import dev.sebastiano.hairline.EllipseNode
import dev.sebastiano.hairline.FigureEls
import dev.sebastiano.hairline.FigureHandle
import dev.sebastiano.hairline.PathData
import dev.sebastiano.hairline.PointerHandlers
import dev.sebastiano.hairline.Ring
import dev.sebastiano.hairline.Solid
import dev.sebastiano.hairline.Vec2
import dev.sebastiano.hairline.cam
import dev.sebastiano.hairline.circ
import dev.sebastiano.hairline.clamp
import dev.sebastiano.hairline.facing
import dev.sebastiano.hairline.flatDot
import dev.sebastiano.hairline.g
import dev.sebastiano.hairline.join
import dev.sebastiano.hairline.jsRound
import dev.sebastiano.hairline.lerp
import dev.sebastiano.hairline.open
import dev.sebastiano.hairline.path
import dev.sebastiano.hairline.place
import dev.sebastiano.hairline.prism
import dev.sebastiano.hairline.proj
import dev.sebastiano.hairline.put
import dev.sebastiano.hairline.r2
import dev.sebastiano.hairline.rad
import dev.sebastiano.hairline.reducedMotion
import dev.sebastiano.hairline.rings
import dev.sebastiano.hairline.seg
import dev.sebastiano.hairline.solid
import dev.sebastiano.hairline.spring
import dev.sebastiano.hairline.stepS
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

private const val TURNTABLE_RING = 70.0
private const val TURNTABLE_PT = 4.0
private const val TURNTABLE_WMAX = 540.0
private const val TURNTABLE_ON_INDEX = 14.0

/** Turntable — flick it round; coast and settle onto quarter-turn detents. */
internal fun mountTurntable(els: FigureEls, value: Double): FigureHandle =
    TurntableFigure(els, value)

private data class TableBlock(val block: TurntableBlock, val ring: Ring, val inner: Ring)

private data class AccentDot(val row: Int, val column: Int, val el: EllipseNode)

private class Spin {
    var angle = TURNTABLE_HOME
    var velocity = 0.0
    var mode = "rest"
    var target = TURNTABLE_HOME
}

private class TurntableFigure(private val els: FigureEls, value: Double) : FigureHandle {
    private val bag = Disposer()
    private var coast = value
    private val spin = Spin()
    private val elevation = spring(0.5, eps = 0.0005)
    private var lastX: Double? = null
    private var lastT = 0.0
    private var lastMove = -1e9
    private val c =
        cam(TURNTABLE_HOME, 0.5, 1.85).also {
            it.ox = 200.0
            it.oy = 184.0
        }
    private val platterRing = circ(TURNTABLE_RING)
    private val platterInner = circ(TURNTABLE_RING - 1.5)
    private val blocks = TURNTABLE_BLOCKS.map { b ->
        rings(b.x0, b.y0, b.x1, b.y1, 3.0, 1.1).let { TableBlock(b, it.first, it.second) }
    }
    private val tall = blocks.first { it.block.z1 >= 40 }
    private val tops =
        blocks.groupBy { it.block.column }.mapValues { it.value.maxBy { b -> b.block.z1 } }
    private val bases =
        blocks
            .filter { it.block.z0 == 0.0 }
            .associate {
                it.block.column to
                    Vec2((it.block.x0 + it.block.x1) / 2, (it.block.y0 + it.block.y1) / 2)
            }
    private val g = els.svg.g()
    private val platter = solid(g)
    private val ticks = g.path("nf lo")
    private val major = g.path("nf")
    private val north = flatDot(g, c, 1.7, "dot m")
    private val index = g.path("nf")
    private val pool = TURNTABLE_BLOCKS.map { solid(g) }
    private val accent = g.g()
    private val accentDots = buildAccent()
    private var accentAt: Solid? = null

    private val loop =
        els.stage.register { dt, now ->
            var moving = stepSpin(dt, now)
            if (stepS(elevation, dt)) moving = true
            draw()
            moving
        }

    init {
        bag.add(loop::unregister)
        bag.add(
            els.stage.pointer(
                object : PointerHandlers {
                    override fun move(p: Vec2) = pointerMove(p)

                    override fun leave() {
                        lastX = null
                        elevation.t = 0.5
                        if (reducedMotion()) spin.angle = turntableDetent(spin.angle)
                        loop.wake()
                    }
                }
            )
        )
        bag.add { els.svg.clear() }
    }

    private fun buildAccent(): List<AccentDot> {
        val out = ArrayList<AccentDot>()
        repeat(16) { index ->
            val row = index / 4
            val column = index % 4
            val edge = row % 3 == 0
            val side = column % 3 == 0
            if (!(edge && side))
                out.add(
                    AccentDot(
                        row,
                        column,
                        flatDot(accent, c, 0.6, if (edge || side) "dot m" else "dot"),
                    )
                )
        }
        return out
    }

    private fun stepSpin(dt: Double, now: Double): Boolean {
        if (spin.mode == "rest") return false
        val n = max(1, ceil(dt * 240).toInt())
        val h = dt / n
        repeat(n) {
            if (spin.mode == "coast") {
                spin.velocity *= exp(-h * 1000 / coast)
                spin.angle += spin.velocity * h
                if (abs(spin.velocity) < 45 && now - lastMove > 90) {
                    spin.mode = "settle"
                    spin.target = turntableDetent(spin.angle + spin.velocity * 0.2)
                }
            } else {
                spin.velocity += (-90 * (spin.angle - spin.target) - 16 * spin.velocity) * h
                spin.angle += spin.velocity * h
            }
        }
        if (
            spin.mode == "settle" &&
                abs(spin.angle - spin.target) < 0.02 &&
                abs(spin.velocity) < 0.3
        ) {
            spin.angle = spin.target
            spin.velocity = 0.0
            spin.mode = "rest"
        }
        return spin.mode != "rest"
    }

    private fun draw() {
        c.az = rad(spin.angle)
        c.k = elevation.x
        val p = proj(c)
        val front = facing(c)
        val co = cos(c.az)
        val si = sin(c.az)
        put(platter, prism(p, front, platterRing, platterInner, -TURNTABLE_PT, 0.0))
        drawTicks(p)
        place(north, p(0.0, -(TURNTABLE_RING - 17), 0.0))
        north.ry = r2(1.7 * c.s * c.k)
        val bottom = c.oy + TURNTABLE_RING * c.s * c.k + TURNTABLE_PT * c.s * sqrt(1 - c.k * c.k)
        index.d =
            open(
                listOf(
                    Vec2(c.ox - 4, r2(bottom + 10)),
                    Vec2(c.ox, r2(bottom + 4)),
                    Vec2(c.ox + 4, r2(bottom + 10)),
                )
            )
        drawBlocks(p, front, si, co, litColumn(si, co))
        drawAccent(p)
        val az = jsRound(((spin.angle % 360) + 360) % 360).toInt().toString().padStart(3, '0')
        val el = jsRound(asin(elevation.x) * 180 / PI).toInt()
        els.read.textContent = "az $az° · el $el°"
    }

    private fun drawTicks(p: dev.sebastiano.hairline.Projector) {
        val minor = ArrayList<PathData>()
        val majors = ArrayList<PathData>()
        for (d in 0 until 360 step 15) {
            val a = rad(d.toDouble())
            val long = d % 90 == 0
            val r0 = if (long) TURNTABLE_RING - 12 else TURNTABLE_RING - 8
            val line =
                seg(
                    p(cos(a) * r0, sin(a) * r0, 0.0),
                    p(cos(a) * (TURNTABLE_RING - 4.5), sin(a) * (TURNTABLE_RING - 4.5), 0.0),
                )
            (if (long) majors else minor).add(line)
        }
        ticks.d = minor.join()
        major.d = majors.join()
    }

    private fun litColumn(si: Double, co: Double): Int {
        if (lastX == null && spin.mode == "rest") return -1
        var lit = -1
        var best = TURNTABLE_ON_INDEX
        for ((column, q) in bases) {
            val off = abs(atan2(q.x * co - q.y * si, q.x * si + q.y * co)) * 180 / PI
            if (off < best) {
                best = off
                lit = column
            }
        }
        return lit
    }

    private fun drawBlocks(
        p: dev.sebastiano.hairline.Projector,
        front: (dev.sebastiano.hairline.Sample) -> Boolean,
        si: Double,
        co: Double,
        lit: Int,
    ) {
        turntableOrder(TURNTABLE_BLOCKS, si, co, c.k).forEachIndexed { i, j ->
            val block = blocks[j]
            put(pool[i], prism(p, front, block.ring, block.inner, block.block.z0, block.block.z1))
            pool[i].sil.classList.toggle("hi", block === tops[lit])
            if (block === tall && accentAt !== pool[i]) {
                accentAt = pool[i]
                pool[i].g.after(accent)
            }
        }
    }

    private fun drawAccent(p: dev.sebastiano.hairline.Projector) {
        val b = tall.block
        val cx = (b.x0 + b.x1) / 2
        val cy = (b.y0 + b.y1) / 2
        accentDots.forEach { dot ->
            place(dot.el, p(cx + (dot.column - 1.5) * 2.6, cy + (dot.row - 1.5) * 2.6, b.z1))
            dot.el.ry = r2(0.6 * c.s * c.k)
        }
    }

    private fun pointerMove(point: Vec2) {
        val now = els.stage.now()
        elevation.t = lerp(sin(rad(40.0)), sin(rad(20.0)), clamp(point.y / 320, 0.0, 1.0))
        lastX?.let { oldX ->
            val dx = point.x - oldX
            if (reducedMotion()) spin.angle -= dx * 0.9
            else {
                val velocity = dx / max(0.008, (now - lastT) / 1000)
                spin.velocity =
                    clamp(
                        lerp(spin.velocity, -velocity * 0.9, 0.35),
                        -TURNTABLE_WMAX,
                        TURNTABLE_WMAX,
                    )
                spin.mode = "coast"
            }
            if (abs(dx) > 0.5) lastMove = now
        }
        lastX = point.x
        lastT = now
        loop.wake()
    }

    override fun set(value: Double) {
        coast = value
        loop.wake()
    }

    override fun destroy() = bag.dispose()
}
