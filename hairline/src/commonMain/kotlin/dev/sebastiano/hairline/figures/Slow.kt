// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline.figures

import dev.sebastiano.hairline.Disposer
import dev.sebastiano.hairline.EllipseNode
import dev.sebastiano.hairline.FigureEls
import dev.sebastiano.hairline.FigureHandle
import dev.sebastiano.hairline.Group
import dev.sebastiano.hairline.PathData
import dev.sebastiano.hairline.PointerHandlers
import dev.sebastiano.hairline.Solid
import dev.sebastiano.hairline.Vec2
import dev.sebastiano.hairline.Vec3
import dev.sebastiano.hairline.cam
import dev.sebastiano.hairline.clamp
import dev.sebastiano.hairline.facing
import dev.sebastiano.hairline.fit
import dev.sebastiano.hairline.flatDot
import dev.sebastiano.hairline.g
import dev.sebastiano.hairline.join
import dev.sebastiano.hairline.path
import dev.sebastiano.hairline.place
import dev.sebastiano.hairline.prism
import dev.sebastiano.hairline.proj
import dev.sebastiano.hairline.put
import dev.sebastiano.hairline.r2
import dev.sebastiano.hairline.reducedMotion
import dev.sebastiano.hairline.rings
import dev.sebastiano.hairline.seg
import dev.sebastiano.hairline.solid
import dev.sebastiano.hairline.spring
import dev.sebastiano.hairline.stepS
import dev.sebastiano.hairline.toFixed
import dev.sebastiano.hairline.unproj
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

private const val SLOW_L = 210.0
private const val SLOW_BW = 26.0
private const val SLOW_BT = 5.0
private const val SLOW_CUBE = 15.0
private const val SLOW_NC = 6
private const val SLOW_SPEED = 1.0 / 9
private const val SLOW_GATE = SLOW_L * 0.62
private const val SLOW_GH = 38.0

/** Slow — hover dilates the clock while crates continue along the belt. */
internal fun mountSlow(els: FigureEls, value: Double): FigureHandle = SlowFigure(els, value)

private data class Crate(val el: Solid, val dots: List<EllipseNode>)

private data class BeltItem(val id: Int, val x: Double, val size: Double, val serial: Int)

private data class GatePart(val el: Solid, val key: Double)

private data class DrawPart(val id: String, val key: Double, val g: Group)

private fun crateSize(u: Double): Double {
    val a = clamp(min(u, 1 - u) / 0.08, 0.0, 1.0)
    return a * a * (3 - 2 * a)
}

private class SlowFigure(private val els: FigureEls, value: Double) : FigureHandle {
    private val bag = Disposer()
    private var slow = value
    private var over: Vec2? = null
    private var clock = 2.4
    private val rate = spring(1.0, eps = 0.002)
    private val c = cam(45.0, 0.5, 1.8).also { fitSlow(it) }
    private val p = proj(c)
    private val front = facing(c)
    private val g = els.svg.g()
    private val slats: dev.sebastiano.hairline.PathNode
    private val layer: Group
    private val pool: List<Crate>
    private val gate: List<GatePart>
    private val lifts = List(SLOW_NC) { spring(0.0, eps = 0.03) }
    private var hot = -1
    private var order = ""

    init {
        val belt = rings(0.0, 0.0, SLOW_L, SLOW_BW, 5.0, 1.3)
        put(solid(g), prism(p, front, belt.first, belt.second, 0.0, SLOW_BT))
        slats = g.path("nf lo")
        layer = g.g()
        pool =
            List(SLOW_NC) {
                Crate(
                    solid(layer),
                    List(9) { flatDot(layer.children.last() as Group, c, 1.0, "dot off") },
                )
            }
        gate = buildGate()
    }

    private val loop = els.stage.register { dt, _ -> tick(dt) }

    init {
        bag.add(loop::unregister)
        bag.add(
            els.stage.pointer(
                object : PointerHandlers {
                    override fun move(point: Vec2) {
                        over = unproj(c, point.x, point.y, SLOW_BT)
                        rate.t = slow
                        loop.wake()
                    }

                    override fun leave() {
                        over = null
                        loop.wake()
                    }
                }
            )
        )
        bag.add { els.svg.clear() }
    }

    private fun buildGate(): List<GatePart> =
        listOf(
                doubleArrayOf(-8.0, -3.0, 0.0, SLOW_GH - 4, SLOW_GATE - 5.5),
                doubleArrayOf(
                    SLOW_BW + 3,
                    SLOW_BW + 8,
                    0.0,
                    SLOW_GH - 4,
                    SLOW_GATE + SLOW_BW + 5.5,
                ),
                doubleArrayOf(-8.0, SLOW_BW + 8, SLOW_GH - 4, SLOW_GH, SLOW_GATE + SLOW_BW + 8.5),
            )
            .map { q ->
                val el = solid(layer)
                val rs = rings(SLOW_GATE - 1.6, q[0], SLOW_GATE + 1.6, q[1], 1.4, 0.6)
                put(el, prism(p, front, rs.first, rs.second, q[2], q[3]))
                GatePart(el, q[4])
            }

    private fun tick(dt: Double): Boolean {
        var moving = stepS(rate, dt)
        if (over == null) rate.t = if (reducedMotion()) 0.0 else 1.0
        clock += dt * rate.x
        val items =
            List(SLOW_NC) { j ->
                val q = j.toDouble() / SLOW_NC + clock * SLOW_SPEED
                val u = q - floor(q)
                BeltItem(j, u * SLOW_L, crateSize(u), 141 + floor(q).toInt() * SLOW_NC + j)
            }
        hot = nearest(items)
        lifts.forEachIndexed { j, sp ->
            sp.t = if (j == hot) 11.0 else 0.0
            if (stepS(sp, dt)) moving = true
        }
        drawItems(items.sortedBy { it.x })
        drawSlats()
        if (reducedMotion() && over == null && rate.x == 0.0) return moving
        return true
    }

    private fun nearest(items: List<BeltItem>): Int {
        val pointer = over ?: return -1
        var best = 34.0
        var wanted = -1
        items.forEach {
            if (it.size > 0.6 && abs(it.x - pointer.x) < best) {
                best = abs(it.x - pointer.x)
                wanted = it.id
            }
        }
        return wanted
    }

    private fun drawItems(items: List<BeltItem>) {
        val draw =
            gate.mapIndexed { i, part -> DrawPart("g$i", part.key, part.el.g) }.toMutableList()
        var hotSerial = 0
        items.forEachIndexed { k, item ->
            val crate = pool[k]
            draw.add(DrawPart(k.toString(), item.x + SLOW_BW / 2, crate.el.g))
            if (drawCrate(crate, item)) {
                if (item.id == hot) hotSerial = item.serial
            }
        }
        val sorted = draw.sortedBy { it.key }
        val signature = sorted.joinToString(",") { it.id }
        if (signature != order) {
            order = signature
            sorted.forEach { layer.append(it.g) }
        }
        els.read.textContent =
            "rate ${toFixed(rate.x, 2)}×" +
                if (hot >= 0) " · #${hotSerial.toString().padStart(4, '0')}" else ""
    }

    private fun drawCrate(crate: Crate, item: BeltItem): Boolean {
        val size = SLOW_CUBE * item.size
        if (size < 0.3) {
            crate.el.g.hidden = true
            return false
        }
        crate.el.g.hidden = false
        val y0 = SLOW_BW / 2 - size / 2
        val z0 = SLOW_BT + lifts[item.id].x
        val rs =
            rings(
                item.x - size / 2,
                y0,
                item.x + size / 2,
                y0 + size,
                2.6 * item.size,
                0.9 * item.size,
            )
        put(crate.el, prism(p, front, rs.first, rs.second, z0, z0 + size))
        val isHot = item.id == hot
        crate.el.sil.classList.toggle("hi", isHot)
        crate.dots.forEachIndexed { bit, dot ->
            val pitch = size * 0.2
            place(
                dot,
                p(item.x + (bit % 3 - 1) * pitch, SLOW_BW / 2 + (bit / 3 - 1) * pitch, z0 + size),
            )
            dot.rx = r2(0.55 * item.size * c.s)
            dot.ry = r2(dot.rx * c.k)
            dot.classList.set(
                if ((item.serial shr bit) and 1 != 0) if (isHot) "dot" else "dot m" else "dot off"
            )
        }
        return true
    }

    private fun drawSlats() {
        val off = ((clock * SLOW_SPEED * SLOW_L) % 21 + 21) % 21
        val paths = ArrayList<PathData>()
        var x = off
        while (x < SLOW_L) {
            if (x > 5 && x < SLOW_L - 5)
                paths.add(seg(p(x, 4.0, SLOW_BT), p(x, SLOW_BW - 4, SLOW_BT)))
            x += 21
        }
        slats.d = paths.join()
    }

    override fun set(value: Double) {
        slow = value
        if (over != null) rate.t = value
        loop.wake()
    }

    override fun destroy() = bag.dispose()
}

private fun fitSlow(c: dev.sebastiano.hairline.Camera) =
    fit(
        c,
        listOf(
            Vec3(0.0, 0.0, 0.0),
            Vec3(SLOW_L, SLOW_BW, 0.0),
            Vec3(SLOW_L, 0.0, 0.0),
            Vec3(0.0, SLOW_BW, 0.0),
            Vec3(0.0, 0.0, SLOW_GH),
            Vec3(SLOW_GATE, -8.0, SLOW_GH),
        ),
        200.0,
        168.0,
    )
