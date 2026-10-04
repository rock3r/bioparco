// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline.figures

import dev.sebastiano.hairline.Disposer
import dev.sebastiano.hairline.FigureEls
import dev.sebastiano.hairline.FigureHandle
import dev.sebastiano.hairline.Loop
import dev.sebastiano.hairline.PathData
import dev.sebastiano.hairline.PathNode
import dev.sebastiano.hairline.PointerHandlers
import dev.sebastiano.hairline.PrismPaths
import dev.sebastiano.hairline.Projector
import dev.sebastiano.hairline.Ring
import dev.sebastiano.hairline.Sample
import dev.sebastiano.hairline.Solid
import dev.sebastiano.hairline.Spring
import dev.sebastiano.hairline.Vec2
import dev.sebastiano.hairline.Vec3
import dev.sebastiano.hairline.cam
import dev.sebastiano.hairline.clamp
import dev.sebastiano.hairline.fillet
import dev.sebastiano.hairline.fit
import dev.sebastiano.hairline.g
import dev.sebastiano.hairline.jsRound
import dev.sebastiano.hairline.path
import dev.sebastiano.hairline.poly
import dev.sebastiano.hairline.prism
import dev.sebastiano.hairline.proj
import dev.sebastiano.hairline.put
import dev.sebastiano.hairline.rings
import dev.sebastiano.hairline.rrect
import dev.sebastiano.hairline.solid
import dev.sebastiano.hairline.spring
import dev.sebastiano.hairline.stepS
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private const val N = 24
private const val V = 7
private const val G = 11.0
private const val T = 2.1
private const val LH = 1.8
private const val CH = 3.9
private const val LIFT = 5.5
private const val HB = 3.4
private const val SLAB = 3.0
private const val W = 150.0
private const val PB = 17.0
private const val PAD = 11.0
private const val INSET = 2.5
private const val FIRST = 37
private const val VB = PB
private const val VA = PB + V * G
private const val H = VA + 17.0
private const val E = 4.0
private const val REST_TOP = N - V - 2.6
private const val REST_LINE = 18
private const val REST_R = 2.0

private val terminalHistory =
    listOf(
        "0 5 12",
        "2 7 3 9",
        "2 14",
        "4 6 10",
        "4 18",
        "2 3",
        "0 2",
        "0 2 6 8",
        "0 26",
        "0 20",
        "0 11 4",
        "2 9 12",
        "2 5 3 7",
        "4 16",
        "4 8 8",
        "2 4",
        "0 3 14",
        "0 24",
        "0 30",
        "0 17",
        "2 6 11",
        "2 13",
        "0 9 5",
        "0 4 7",
    )

private class TerminalSegment(val x0: Double, val x1: Double, val el: Solid)

private class TerminalRow(
    val j: Int,
    val segments: List<TerminalSegment>,
    val spring: Spring,
) {
    var drawn = ""
}

/**
 * Terminal: a terminal window floating upright as a thin rounded slab. A title bar with its three
 * round buttons across the top, a prompt bar across the foot with a chevron and a block cursor, and
 * between them the output: rows of rounded bars, indented and of different lengths, as code and
 * logs are. The pointer's height scrolls back through twenty-four lines of history on a spring;
 * lines leaving the top pass in behind the title bar, and new ones come up from behind the prompt
 * bar, cut where they meet it. The line under the pointer lifts off the slab, and its neighbours
 * less, each on its own spring. At rest the window is scrolled back half a line and one long line
 * is caught lifted, bright. The slider is how far the lift spreads, in lines.
 *
 * The slab stands in the x-z plane and faces +y, so the figure draws in its own frame: u across, v
 * up, w out of the face, P2(u, v, w) = P(u, w, v). The pattern: scrub and pick. One spring for the
 * scroll, a spring per line for its lift, a falloff by distance, and a hit test on the rest planes.
 */
internal fun mountTerminal(els: FigureEls, value: Double): FigureHandle = TerminalFigure(els, value)

private class TerminalFigure(private val els: FigureEls, value: Double) : FigureHandle {
    private val bag = Disposer()
    private val camera = cam(45.0, .5, 1.62)
    private lateinit var p: Projector
    private var radius = value
    private var over: Double? = null
    private var lit: List<PathNode> = emptyList()
    private val group = els.svg.g()
    private val rows = ArrayList<TerminalRow>()
    private val top = spring(REST_TOP, eps = .002)
    private lateinit var cursor: Solid
    private lateinit var chevron: PathNode
    private lateinit var loop: Loop

    init {
        configureCamera()
        buildSlabAndRows()
        buildPrompt()
        buildTitle()
        loop = els.stage.register { dt, _ -> tick(dt) }
        bag.add(loop::unregister)
        retarget()
        rows.forEach { it.spring.x = it.spring.t }
        installPointer()
        bag.add { els.svg.clear() }
    }

    private fun p2(u: Double, v: Double, w: Double) = p(u, w, v)

    private fun configureCamera() {
        val points =
            listOf(
                Vec3(0.0, -SLAB, 0.0),
                Vec3(W, -SLAB, 0.0),
                Vec3(0.0, -SLAB, H),
                Vec3(W, -SLAB, H),
                Vec3(W, HB + 2.8, 0.0),
                Vec3(0.0, HB + 1.3, H),
                Vec3(PAD, LIFT + LH, VA - 4),
            )
        fit(camera, points, 200.0, 166.0)
        p = proj(camera)
    }

    private fun front(q: Sample) = .612 * q.nu + .5 * q.nv > 0

    private fun cut(ring: Ring): Ring = ring.map { it.copy(v = clamp(it.v, VB, VA)) }

    private fun buildSlabAndRows() {
        val (outer, inner) = rings(0.0, 0.0, W, H, 9.0, 2.0)
        put(solid(group), prism(::p2, ::front, outer, inner, -SLAB, 0.0))
        terminalHistory.forEachIndexed { j, text ->
            val values = text.split(" ").map(String::toInt)
            var at = values.first().toDouble()
            val segments =
                values.drop(1).map { length ->
                    val x0 = PAD + at * CH
                    at += length + 1
                    TerminalSegment(x0, x0 + length * CH - 1.4, solid(group))
                }
            rows.add(TerminalRow(j, segments, spring(0.0, eps = .01)))
        }
    }

    private fun buildPrompt() {
        val (outer, inner) = rings(INSET, INSET, W - INSET, VB, 6.0, 1.4)
        put(solid(group), prism(::p2, ::front, outer, inner, 0.0, HB))
        val cy = (INSET + VB) / 2
        val points =
            fillet(
                listOf(
                    Vec2(0.0, 6.2),
                    Vec2(8.5, 0.0),
                    Vec2(0.0, -6.2),
                    Vec2(0.0, -3.1),
                    Vec2(4.25, 0.0),
                    Vec2(0.0, 3.1),
                ),
                listOf(.9, 1.1, .9, .5, .6, .5),
            )
        chevron = group.path("nf", poly(points.map { p2(PAD + it.x, cy + it.y, HB) }))
        val (kr, ki) = rings(PAD + 13, cy - 5.2, PAD + 13 + CH * 1.5, cy + 5.2, 1.2, .8)
        cursor = solid(group)
        put(cursor, prism(::p2, ::front, kr, ki, HB, HB + 2.8))
    }

    private fun buildTitle() {
        val (outer, inner) = rings(INSET, VA, W - INSET, H - INSET, 6.0, 1.4)
        put(solid(group), prism(::p2, ::front, outer, inner, 0.0, HB))
        repeat(3) { i ->
            val cx = PAD + i * 8.5
            val cv = (VA + H - INSET) / 2
            val (ring, inset) = rings(cx - 2.8, cv - 2.8, cx + 2.8, cv + 2.8, 2.8, .8)
            put(solid(group), prism(::p2, ::front, ring, inset, HB, HB + 1.3))
        }
    }

    private fun tick(dt: Double): Boolean {
        var moving = stepS(top, dt)
        rows.forEach {
            if (stepS(it.spring, dt)) moving = true
            drawRow(it, top.x)
        }
        return moving
    }

    private fun drawRow(row: TerminalRow, atTop: Double) {
        val key = "$atTop|${row.spring.x}"
        if (key == row.drawn) return
        row.drawn = key
        val vc = VA - (row.j - atTop + .5) * G
        val v0 = max(vc - T, VB)
        val v1 = min(vc + T, VA)
        val env =
            smooth(clamp((VA - vc - T) / E, 0.0, 1.0)) * smooth(clamp((vc - T - VB) / E, 0.0, 1.0))
        row.segments.forEach { drawSegment(it, vc, v0, v1, row.spring.x * env) }
    }

    private fun drawSegment(
        segment: TerminalSegment,
        vc: Double,
        v0: Double,
        v1: Double,
        w0: Double,
    ) {
        if (v1 - v0 < .05) {
            put(segment.el, PrismPaths(PathData.EMPTY, PathData.EMPTY))
            return
        }
        val ring = cut(rrect(segment.x0, vc - T, segment.x1, vc + T, T))
        val inner = cut(rrect(segment.x0 + .7, vc - T + .7, segment.x1 - .7, vc + T - .7, T - .7))
        put(segment.el, prism(::p2, ::front, ring, inner, w0, w0 + LH))
    }

    private fun onFace(screen: Vec2, w: Double): Vec2 {
        val o = p2(0.0, 0.0, w)
        val a = p2(1.0, 0.0, w)
        val b = p2(0.0, 1.0, w)
        val ax = a.x - o.x
        val ay = a.y - o.y
        val bx = b.x - o.x
        val by = b.y - o.y
        val det = ax * by - ay * bx
        return Vec2(
            ((screen.x - o.x) * by - (screen.y - o.y) * bx) / det,
            (ax * (screen.y - o.y) - ay * (screen.x - o.x)) / det,
        )
    }

    private fun hit(screen: Vec2): Double? {
        val bar = onFace(screen, HB)
        val inU = bar.x > INSET && bar.x < W - INSET
        if (inU && bar.y > INSET && bar.y < VB) return -1.0
        if (inU && bar.y > VA && bar.y < H - INSET) return 0.0
        val q = onFace(screen, 0.0)
        if (q.x < 0 || q.x > W || q.y < 0 || q.y > H) return null
        return if (q.y <= VB) -1.0 else clamp((VA - G / 2 - q.y) / ((V - 1) * G), 0.0, 1.0)
    }

    private fun retarget() {
        val target: Double
        val center: Int
        var spread = radius
        when (val current = over) {
            null -> {
                target = REST_TOP
                center = REST_LINE
                spread = REST_R
                els.read.textContent = "rest"
            }
            -1.0 -> {
                target = (N - V).toDouble()
                center = -1
                els.read.textContent = "prompt"
            }
            else -> {
                target = current * (N - V)
                center =
                    clamp(jsRound(current * (N - 1)), ceil(target), floor(target + V - 1)).toInt()
                els.read.textContent = "line ${FIRST + center}"
            }
        }
        top.t = target
        rows.forEach {
            it.spring.t =
                if (center < 0) 0.0 else LIFT * falloff(abs(it.j - center).toDouble(), spread)
        }
        light(
            if (center < 0) listOf(cursor.sil, chevron) else rows[center].segments.map { it.el.sil }
        )
        loop.wake()
    }

    private fun light(next: List<PathNode>) {
        lit.forEach { it.classList.remove("hi") }
        lit = next
        lit.forEach { it.classList.add("hi") }
    }

    private fun installPointer() {
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
    }

    override fun set(value: Double) {
        radius = value
        if (over != null) retarget()
    }

    override fun destroy() = bag.dispose()
}

private fun falloff(distance: Double, radius: Double) = clamp(1 - distance / radius, 0.0, 1.0)

private fun smooth(t: Double) = t * t * (3 - 2 * t)
