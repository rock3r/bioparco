// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline.figures

import dev.sebastiano.hairline.Disposer
import dev.sebastiano.hairline.FigureEls
import dev.sebastiano.hairline.FigureHandle
import dev.sebastiano.hairline.PathNode
import dev.sebastiano.hairline.PointerHandlers
import dev.sebastiano.hairline.Projector
import dev.sebastiano.hairline.Ring
import dev.sebastiano.hairline.Sample
import dev.sebastiano.hairline.Solid
import dev.sebastiano.hairline.Spring
import dev.sebastiano.hairline.Vec2
import dev.sebastiano.hairline.Vec3
import dev.sebastiano.hairline.cam
import dev.sebastiano.hairline.circ
import dev.sebastiano.hairline.clamp
import dev.sebastiano.hairline.extremes
import dev.sebastiano.hairline.facing
import dev.sebastiano.hairline.fillet
import dev.sebastiano.hairline.fit
import dev.sebastiano.hairline.g
import dev.sebastiano.hairline.hull
import dev.sebastiano.hairline.join
import dev.sebastiano.hairline.lerp
import dev.sebastiano.hairline.path
import dev.sebastiano.hairline.poly
import dev.sebastiano.hairline.prism
import dev.sebastiano.hairline.proj
import dev.sebastiano.hairline.put
import dev.sebastiano.hairline.ringAt
import dev.sebastiano.hairline.rings
import dev.sebastiano.hairline.rrect
import dev.sebastiano.hairline.seg
import dev.sebastiano.hairline.solid
import dev.sebastiano.hairline.spring
import dev.sebastiano.hairline.stepS
import kotlin.math.abs

private const val PHONE_W = 68.0
private const val PHONE_L = 140.0
private const val PHONE_R = 11.0
private const val GAP_MAX = 40.0
private const val GLASS_T = 2.0
private const val BOARD_T = 1.6
private const val CHIP_T = 1.8
private const val BATTERY_T = 7.0
private const val SHELL_T = 6.0
private const val BUMP_T = 2.2
private const val LENS_T = .9
private const val PHONE_STACK = GLASS_T + BOARD_T + CHIP_T + BATTERY_T + SHELL_T + BUMP_T + LENS_T
private const val PHONE_MIN = .08
private const val PHONE_PIVOT = 1
private val phoneNames = listOf("glass", "board", "battery", "shell")
private val phoneRest = listOf(.36, .3, 1.2)
private val boardPoints =
    listOf(
        Vec2(4.0, PHONE_L - 44),
        Vec2(PHONE_W - 4, PHONE_L - 44),
        Vec2(PHONE_W - 4, PHONE_L - 5),
        Vec2(36.0, PHONE_L - 5),
        Vec2(36.0, PHONE_L - 30),
        Vec2(4.0, PHONE_L - 30),
    )
private val batteryRect = listOf(9.0, 12.0, PHONE_W - 9, PHONE_L - 48)
private val chips =
    listOf(
        listOf(10.0, PHONE_L - 41, 26.0, PHONE_L - 34, 1.4),
        listOf(45.0, PHONE_L - 40, 54.0, PHONE_L - 35, 1.2),
        listOf(57.0, PHONE_L - 42, 62.0, PHONE_L - 36, 1.2),
        listOf(42.0, PHONE_L - 27, 56.0, PHONE_L - 13, 1.8),
    )
private val phoneRect =
    listOf(Vec2(0.0, 0.0), Vec2(PHONE_W, 0.0), Vec2(PHONE_W, PHONE_L), Vec2(0.0, PHONE_L))
private val phoneFootprints =
    listOf(
        phoneRect,
        boardPoints,
        listOf(
            Vec2(batteryRect[0], batteryRect[1]),
            Vec2(batteryRect[2], batteryRect[1]),
            Vec2(batteryRect[2], batteryRect[3]),
            Vec2(batteryRect[0], batteryRect[3]),
        ),
        phoneRect,
    )
private val phoneThickness = listOf(GLASS_T, BOARD_T, BATTERY_T, SHELL_T)

private fun moved(ring: Ring, x: Double, y: Double) = ring.map {
    Sample(it.u + x, it.v + y, it.nu, it.nv)
}

private fun phoneBases(gaps: List<Double>): List<Double> {
    val bases = mutableListOf(0.0)
    val thicknesses = listOf(GLASS_T, BOARD_T + CHIP_T, BATTERY_T)
    repeat(3) { bases.add(bases[it] + thicknesses[it] + gaps[it]) }
    val middle = (bases[3] + SHELL_T + BUMP_T + LENS_T) / 2
    return bases.map { it - middle }
}

private data class Chip(val ring: Ring, val inner: Ring, val height: Double, val solid: Solid)

private data class Button(val ring: Ring, val inner: Ring, val solid: Solid)

private data class Lens(
    val ring: Ring,
    val inner: Ring,
    val eye: Ring,
    val solid: Solid,
    val glass: PathNode,
)

internal fun mountPhone(els: FigureEls, value: Double): FigureHandle = PhoneFigure(els, value)

/**
 * Phone: a face-down phone taken apart into glass, board, battery and shell. Pointer x scrubs the
 * gaps; y picks a layer. Gaps open outwards from the selected layer.
 */
private class PhoneFigure(private val els: FigureEls, value: Double) : FigureHandle {
    private val bag = Disposer()
    private var gap = value
    private var share: Double? = null
    private var active = -1
    private var origin = PHONE_PIVOT
    private var lastPointer: Vec2? = null
    private val c = cam(45.0, .5, 1.5)
    private val p: Projector
    private val front: (Sample) -> Boolean
    private val g = els.svg.g()
    private val outer = rrect(0.0, 0.0, PHONE_W, PHONE_L, PHONE_R, 8)
    private val guide: PathNode
    private val guidePoints: List<Sample>

    private lateinit var glass: Solid
    private lateinit var display: PathNode
    private lateinit var glassLens: PathNode
    private lateinit var slit: PathNode
    private lateinit var cameraFront: Ring
    private lateinit var boardBack: PathNode
    private lateinit var boardFace: PathNode
    private lateinit var boardFillet: List<Vec2>
    private lateinit var chipEls: List<Chip>
    private lateinit var battery: Solid
    private lateinit var batteryRing: Ring
    private lateinit var batteryInner: Ring
    private lateinit var tabBack: PathNode
    private lateinit var tabFace: PathNode
    private lateinit var tab: List<Vec2>
    private lateinit var connector: Solid
    private lateinit var connectorRing: Ring
    private lateinit var connectorInner: Ring
    private lateinit var shell: Solid
    private lateinit var shellInner: Ring
    private lateinit var buttons: List<Button>
    private lateinit var bump: Solid
    private lateinit var bumpRing: Ring
    private lateinit var bumpInner: Ring
    private lateinit var lenses: List<Lens>
    private lateinit var flash: PathNode
    private lateinit var flashRing: Ring

    init {
        val height = (PHONE_STACK + 3 * GAP_MAX) / 2
        fit(
            c,
            listOf(
                Vec3(0.0, 0.0, -height),
                Vec3(PHONE_W + 2.2, PHONE_L, -height),
                Vec3(PHONE_W + 2.2, 0.0, -height),
                Vec3(0.0, PHONE_L, -height),
                Vec3(0.0, 0.0, height),
                Vec3(PHONE_W + 2.2, 0.0, height),
                Vec3(0.0, PHONE_L, height),
            ),
            200.0,
            166.0,
        )
        p = proj(c)
        front = facing(c)
        guidePoints = extremes(p, outer).toList().take(2)
        guide = g.path("nf dash")
        buildGlass()
        buildBoard()
        buildBattery()
        buildShell()
    }

    private val silhouettes
        get() = listOf(glass.sil, boardFace, battery.sil, shell.sil)

    private fun targets() = List(3) { gap * (share ?: phoneRest[it]) }

    private val gapSprings: List<Spring> = targets().map { spring(it, eps = .01) }
    private val drawn = MutableList(4) { Double.NaN }
    private val loop = els.stage.register { dt, _ -> tick(dt) }

    init {
        bag.add(loop::unregister)
        setActive(-1)
        bag.add(
            els.stage.pointer(
                object : PointerHandlers {
                    override fun move(p: Vec2) {
                        lastPointer = p
                        share = lerp(PHONE_MIN, 1.0, clamp((p.x - 60) / 280, 0.0, 1.0))
                        setActive(pick(p))
                    }

                    override fun leave() {
                        lastPointer = null
                        share = null
                        setActive(-1)
                    }
                }
            )
        )
        bag.add { els.svg.clear() }
    }

    private fun flat(points: List<Vec2>, z: Double) = poly(points.map { p(it.x, it.y, z) })

    private fun buildGlass() {
        val gg = g.g()
        glass = solid(gg)
        val displayPoints =
            listOf(
                Vec2(4.0, 4.0),
                Vec2(PHONE_W - 4, 4.0),
                Vec2(PHONE_W - 4, PHONE_L - 4),
                Vec2(PHONE_W / 2 + 12, PHONE_L - 4),
                Vec2(PHONE_W / 2 + 12, PHONE_L - 10),
                Vec2(PHONE_W / 2 - 12, PHONE_L - 10),
                Vec2(PHONE_W / 2 - 12, PHONE_L - 4),
                Vec2(4.0, PHONE_L - 4),
            )
        display = gg.path("nf")
        glassLens = gg.path("nf")
        slit = gg.path("nf lo")
        cameraFront = moved(circ(1.5, 16), PHONE_W / 2 + 6, PHONE_L - 7)
        boardFillet = fillet(displayPoints, listOf(7.5, 7.5, 7.5, 2.0, 3.5, 3.5, 2.0, 7.5))
    }

    private fun buildBoard() {
        val bg = g.g()
        boardFillet = fillet(boardPoints, listOf(4.0, 4.0, 4.0, 4.0, 3.0, 4.0))
        boardBack = bg.path("lo")
        boardFace = bg.path("sil")
        chipEls = chips.map { data ->
            val pair = rings(data[0], data[1], data[2], data[3], 1.5, .7)
            Chip(pair.first, pair.second, data[4], solid(bg))
        }
    }

    private fun buildBattery() {
        val tg = g.g()
        battery = solid(tg)
        rings(batteryRect[0], batteryRect[1], batteryRect[2], batteryRect[3], 6.0, 1.6).also {
            batteryRing = it.first
            batteryInner = it.second
        }
        tab =
            fillet(
                listOf(
                    Vec2(41.0, PHONE_L - 48),
                    Vec2(52.0, PHONE_L - 48),
                    Vec2(52.0, PHONE_L - 31),
                    Vec2(41.0, PHONE_L - 31),
                ),
                listOf(.5, .5, 2.0, 2.0),
            )
        tabBack = tg.path("lo")
        tabFace = tg.path()
        connector = solid(tg)
        rings(42.0, PHONE_L - 38, 51.0, PHONE_L - 32, 1.5, .7).also {
            connectorRing = it.first
            connectorInner = it.second
        }
    }

    private fun buildShell() {
        val sg = g.g()
        shell = solid(sg)
        shellInner = rrect(1.8, 1.8, PHONE_W - 1.8, PHONE_L - 1.8, PHONE_R - 1.8, 8)
        buttons =
            listOf(PHONE_L - 68 to PHONE_L - 58, PHONE_L - 54 to PHONE_L - 44).map { (y0, y1) ->
                val pair = rings(PHONE_W - .5, y0, PHONE_W + 2.2, y1, 1.2, .5)
                Button(pair.first, pair.second, solid(sg))
            }
        bump = solid(sg)
        bumpRing = rrect(7.0, PHONE_L - 33, 33.0, PHONE_L - 7, 7.0, 6)
        bumpInner = rrect(8.0, PHONE_L - 32, 32.0, PHONE_L - 8, 6.0, 6)
        lenses =
            listOf(Vec2(14.5, PHONE_L - 25.5), Vec2(25.5, PHONE_L - 14.5)).map { q ->
                val solid = solid(sg)
                Lens(
                    moved(circ(4.6, 24), q.x, q.y),
                    moved(circ(3.6, 24), q.x, q.y),
                    moved(circ(1.9, 16), q.x, q.y),
                    solid,
                    solid.g.path("nf lo"),
                )
            }
        flash = sg.path("nf")
        flashRing = moved(circ(1.7, 16), 25.5, PHONE_L - 25.5)
    }

    private fun tick(dt: Double): Boolean {
        val target = targets()
        val order = (0..2).sortedBy { abs(it + .5 - origin) }
        var moving = false
        for (j in order) {
            val d = j + .5 - origin
            val k = if (d > 0) j - 1 else j + 1
            gapSprings[j].t = if (abs(d) < 1) target[j] else target[j] + gapSprings[k].x - target[k]
            if (stepS(gapSprings[j], dt)) moving = true
        }
        val bases = phoneBases(gapSprings.map { it.x })
        var changed = false
        bases.forEachIndexed { i, z ->
            if (z != drawn[i]) {
                drawn[i] = z
                drawLayer(i, z)
                changed = true
            }
        }
        if (changed)
            guide.d =
                guidePoints
                    .map { seg(p(it.u, it.v, bases[3]), p(it.u, it.v, bases[0] + GLASS_T)) }
                    .join()
        return moving
    }

    private fun drawLayer(index: Int, z: Double) {
        when (index) {
            0 -> drawGlass(z)
            1 -> drawBoard(z)
            2 -> drawBattery(z)
            else -> drawShell(z)
        }
    }

    private fun drawGlass(z: Double) {
        val inner = rrect(1.2, 1.2, PHONE_W - 1.2, PHONE_L - 1.2, PHONE_R - 1.2, 8)
        put(glass, prism(p, front, outer, inner, z, z + GLASS_T))
        val top = z + GLASS_T
        val displayRing =
            fillet(
                listOf(
                    Vec2(4.0, 4.0),
                    Vec2(PHONE_W - 4, 4.0),
                    Vec2(PHONE_W - 4, PHONE_L - 4),
                    Vec2(PHONE_W / 2 + 12, PHONE_L - 4),
                    Vec2(PHONE_W / 2 + 12, PHONE_L - 10),
                    Vec2(PHONE_W / 2 - 12, PHONE_L - 10),
                    Vec2(PHONE_W / 2 - 12, PHONE_L - 4),
                    Vec2(4.0, PHONE_L - 4),
                ),
                listOf(7.5, 7.5, 7.5, 2.0, 3.5, 3.5, 2.0, 7.5),
            )
        display.d = flat(displayRing, top)
        glassLens.d = poly(ringAt(p, cameraFront, top))
        slit.d = seg(p(PHONE_W / 2 - 6, PHONE_L - 7, top), p(PHONE_W / 2 + 1.5, PHONE_L - 7, top))
    }

    private fun drawBoard(z: Double) {
        boardBack.d = flat(boardFillet, z)
        boardFace.d = flat(boardFillet, z + BOARD_T)
        chipEls.forEach {
            put(it.solid, prism(p, front, it.ring, it.inner, z + BOARD_T, z + BOARD_T + it.height))
        }
    }

    private fun drawBattery(z: Double) {
        put(battery, prism(p, front, batteryRing, batteryInner, z, z + BATTERY_T))
        tabBack.d = flat(tab, z + .8)
        tabFace.d = flat(tab, z + 1.4)
        put(connector, prism(p, front, connectorRing, connectorInner, z + 1.4, z + 2.6))
    }

    private fun drawShell(z: Double) {
        put(shell, prism(p, front, outer, shellInner, z, z + SHELL_T))
        buttons.forEach { put(it.solid, prism(p, front, it.ring, it.inner, z + 1.6, z + 4.4)) }
        val top = z + SHELL_T
        val upper = top + BUMP_T
        put(bump, prism(p, front, bumpRing, bumpInner, top, upper))
        lenses.forEach {
            put(it.solid, prism(p, front, it.ring, it.inner, upper, upper + LENS_T))
            it.glass.d = poly(ringAt(p, it.eye, upper + LENS_T))
        }
        flash.d = poly(ringAt(p, flashRing, upper))
    }

    private fun pick(point: Vec2): Int {
        val bases = phoneBases(targets())
        for (i in 3 downTo 0) {
            val footprint = phoneFootprints[i]
            val top = footprint.map { p(it.x, it.y, bases[i] + phoneThickness[i]) }
            val shape = if (i == 1) top else hull(top + footprint.map { p(it.x, it.y, bases[i]) })
            if (insidePhone(point, shape)) return i
        }
        return bases.indices.minBy {
            abs(p(PHONE_W / 2, PHONE_L / 2, bases[it] + phoneThickness[it]).y - point.y)
        }
    }

    private fun setActive(value: Int) {
        if (value >= 0) origin = value
        active = value
        val lit = if (value >= 0) value else PHONE_PIVOT
        silhouettes.forEachIndexed { i, path -> path.classList.toggle("hi", i == lit) }
        els.read.textContent = if (value >= 0) phoneNames[value] else "rest"
        loop.wake()
    }

    override fun set(value: Double) {
        gap = value
        lastPointer?.let { setActive(pick(it)) }
        loop.wake()
    }

    override fun destroy() = bag.dispose()
}

private fun insidePhone(point: Vec2, polygon: List<Vec2>): Boolean {
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[j]
        if (
            (a.y > point.y) != (b.y > point.y) &&
                point.x < (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x
        )
            inside = !inside
        j = i
    }
    return inside
}
