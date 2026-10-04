// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline.figures

import dev.sebastiano.hairline.Disposer
import dev.sebastiano.hairline.FigureEls
import dev.sebastiano.hairline.FigureHandle
import dev.sebastiano.hairline.PathData
import dev.sebastiano.hairline.PointerHandlers
import dev.sebastiano.hairline.Ring
import dev.sebastiano.hairline.Sample
import dev.sebastiano.hairline.Vec2
import dev.sebastiano.hairline.Vec3
import dev.sebastiano.hairline.cam
import dev.sebastiano.hairline.circ
import dev.sebastiano.hairline.clamp
import dev.sebastiano.hairline.fillet
import dev.sebastiano.hairline.fit
import dev.sebastiano.hairline.g
import dev.sebastiano.hairline.hull
import dev.sebastiano.hairline.jsRound
import dev.sebastiano.hairline.open
import dev.sebastiano.hairline.path
import dev.sebastiano.hairline.poly
import dev.sebastiano.hairline.proj
import dev.sebastiano.hairline.put
import dev.sebastiano.hairline.rad
import dev.sebastiano.hairline.run
import dev.sebastiano.hairline.solid
import dev.sebastiano.hairline.spring
import dev.sebastiano.hairline.stepS
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

private const val W = 56.0
private const val DEPTH = 20.0
private const val HEIGHT = 44.0
private const val BEVEL = 1.6
private const val TUBE = 4.2
private const val SPACING = 30.0
private const val XL = 13.0
private const val YC = DEPTH / 2
private const val ARM = 13.0
private const val SINK = 7.0
private const val LIFT = 11.0
private const val O1 = 0.42
private const val REST_TURN = 26.0
private const val R0 = 56.0
private const val R1 = 200.0
private const val SCALE = 2.75
private const val TMAX = 100.0
private val VIEW_X = sqrt(0.5) * sqrt(3.0)

private fun smooth(t: Double) = t * t * (3 - 2 * t)

/** A closed outline in the face's own (x, z), as ring samples with outward normals. */
private fun samples(points: List<Vec2>): Ring = points.mapIndexed { i, point ->
    val a = points[(i + points.size - 1) % points.size]
    val b = points[(i + 1) % points.size]
    val tx = b.x - a.x
    val tz = b.y - a.y
    val length = hypot(tx, tz).takeIf { it != 0.0 } ?: 1.0
    Sample(point.x, point.y, tz / length, -tx / length)
}

private fun cross(p: Vec2, q: Vec2, r: Vec2, s: Vec2): Vec2? {
    val e0 = q.x - p.x
    val e1 = q.y - p.y
    val f0 = s.x - r.x
    val f1 = s.y - r.y
    val g0 = r.x - p.x
    val g1 = r.y - p.y
    val d = e0 * f1 - e1 * f0
    if (d == 0.0) return null
    val t = (g0 * f1 - g1 * f0) / d
    val u = (g0 * e1 - g1 * e0) / d
    return if (t > 0 && t < 1 && u > 0 && u < 1) Vec2(p.x + t * e0, p.y + t * e1) else null
}

/** Cuts the loops an inner offset makes where the bar bends tighter than its radius on screen. */
private fun untangle(input: List<Vec2>): List<Vec2> {
    val line = input.toMutableList()
    for (i in 0 until line.size - 3) {
        var found = false
        for (j in line.size - 2 downTo i + 2) {
            val x = cross(line[i], line[i + 1], line[j], line[j + 1])
            if (x != null) {
                repeat(j - i) { line.removeAt(i + 1) }
                line.add(i + 1, x)
                found = true
                break
            }
        }
        if (found) continue
    }
    return line
}

private fun tube(q: List<Vec2>, rho: Double): PathData {
    val a = ArrayList<Vec2>()
    val back = ArrayList<Vec2>()
    q.forEachIndexed { i, center ->
        val before = q[maxOf(i - 1, 0)]
        val after = q[minOf(i + 1, q.lastIndex)]
        val u0x = center.x - before.x
        val u0y = center.y - before.y
        val u1x = after.x - center.x
        val u1y = after.y - center.y
        val l0 = hypot(u0x, u0y).takeIf { it != 0.0 } ?: 1.0
        val l1 = hypot(u1x, u1y).takeIf { it != 0.0 } ?: 1.0
        var tx = u0x / l0 + u1x / l1
        var ty = u0y / l0 + u1y / l1
        val length = hypot(tx, ty).takeIf { it != 0.0 } ?: 1.0
        tx /= length
        ty /= length
        a.add(Vec2(center.x - ty * rho, center.y + tx * rho))
        back.add(Vec2(center.x + ty * rho, center.y - tx * rho))
    }
    fun cap(center: Vec2) =
        (7 downTo 1).map { k ->
            val t = PI * k / 8
            Vec2(center.x + rho * cos(t), center.y + rho * 0.5 * sin(t))
        }
    return poly(untangle(a) + cap(q.last()) + untangle(back).reversed() + cap(q.first()))
}

internal fun mountPadlock(els: FigureEls, value: Double): FigureHandle = PadlockFigure(els, value)

private class PadlockFigure(private val els: FigureEls, value: Double) : FigureHandle {
    private val bag = Disposer()
    private val turn = spring(value)
    private var over = false

    private fun <R> line(p: (Double, Double, Double) -> R, lift: Double, theta: Double): List<R> {
        val ux = cos(rad(theta))
        val uy = -sin(rad(theta))
        val za = HEIGHT + ARM + lift
        val radius = SPACING / 2
        fun at(s: Double, z: Double) = p(XL + ux * s, YC + uy * s, z)
        val points = arrayListOf(at(SPACING, max(HEIGHT - SINK + lift, HEIGHT)), at(SPACING, za))
        for (i in 1 until 28) {
            val f = PI * i / 28
            points.add(at(radius + radius * cos(f), za + radius * sin(f)))
        }
        points.add(at(0.0, za))
        points.add(at(0.0, HEIGHT))
        return points
    }

    private val c =
        cam(45.0, 0.5, SCALE).also { camera ->
            val ext =
                arrayListOf(
                    Vec3(0.0, 0.0, 0.0),
                    Vec3(W, 0.0, 0.0),
                    Vec3(0.0, DEPTH, 0.0),
                    Vec3(W, DEPTH, 0.0),
                    Vec3(0.0, 0.0, HEIGHT),
                )
            listOf(0.0, 40.0, 80.0, TMAX).forEach { theta ->
                line(::Vec3, LIFT, theta).forEach { ext.add(it.copy(z = it.z + TUBE)) }
            }
            fit(camera, ext, 200.0, 170.0)
        }
    private val p = proj(c)
    private val rho = TUBE * SCALE
    private val g = els.svg.g()
    private val bar = g.path("sil")
    private val open = spring(restOpen(), eps = 0.002)
    private var drawnOpen = Double.NaN
    private var drawnTurn = Double.NaN

    init {
        buildBody()
        g.append(bar)
    }

    private val loop =
        els.stage.register { dt, _ ->
            val a = stepS(open, dt)
            val b = stepS(turn, dt)
            draw()
            a || b
        }

    init {
        bag.add(loop::unregister)
        retarget()
        val center = p(W / 2, DEPTH / 2, HEIGHT / 2)
        bag.add(
            els.stage.pointer(
                object : PointerHandlers {
                    override fun move(p: Vec2) {
                        over = true
                        open.t =
                            clamp(
                                (R1 - hypot(p.x - center.x, p.y - center.y)) / (R1 - R0),
                                0.0,
                                1.0,
                            )
                        retarget()
                    }

                    override fun leave() {
                        over = false
                        open.t = restOpen()
                        retarget()
                    }
                }
            )
        )
        bag.add { els.svg.clear() }
    }

    private fun buildBody() {
        val face =
            fillet(
                listOf(Vec2(0.0, 0.0), Vec2(W, 0.0), Vec2(W, HEIGHT), Vec2(0.0, HEIGHT)),
                listOf(14.0, 14.0, 7.0, 7.0),
                12,
            )
        val inner =
            samples(
                fillet(
                    listOf(
                        Vec2(BEVEL, BEVEL),
                        Vec2(W - BEVEL, BEVEL),
                        Vec2(W - BEVEL, HEIGHT - BEVEL),
                        Vec2(BEVEL, HEIGHT - BEVEL),
                    ),
                    listOf(14 - BEVEL, 14 - BEVEL, 7 - BEVEL, 7 - BEVEL),
                    12,
                )
            )
        put(
            solid(g),
            dev.sebastiano.hairline.PrismPaths(
                poly(hull(face.map { p(it.x, 0.0, it.y) } + face.map { p(it.x, DEPTH, it.y) })),
                open(run(inner) { it.nu * VIEW_X + it.nv > 0 }.map { p(it.u, DEPTH, it.v) }),
            ),
        )
        buildKeyway()
        val hole = circ(TUBE + 1.3, 20)
        listOf(XL, XL + SPACING).forEach { x ->
            g.path("nf lo", poly(hole.map { p(x + it.u, YC + it.v, HEIGHT) }))
        }
    }

    private fun buildKeyway() {
        val kx = W / 2
        val kz = HEIGHT * 0.56
        val kr = 4.4
        val kw = 1.8
        val bottom = kz - 12
        val a0 = asin(kw / kr)
        val key = ArrayList<Vec2>()
        for (i in 0..20) {
            val t = -PI / 2 + a0 + (2 * PI - 2 * a0) * i / 20
            key.add(Vec2(kx + kr * cos(t), kz + kr * sin(t)))
        }
        for (i in 0..6) {
            val t = PI + PI * i / 6
            key.add(Vec2(kx + kw * cos(t), bottom + kw * sin(t)))
        }
        g.path("nf", poly(key.map { p(it.x, DEPTH, it.y) }))
    }

    private fun pose(o: Double, max: Double) =
        LIFT * smooth(clamp(o / O1, 0.0, 1.0)) to max * smooth(clamp((o - O1) / (1 - O1), 0.0, 1.0))

    private fun restOpen() = O1 + (1 - O1) * (0.5 - sin(asin(1 - 2 * REST_TURN / turn.t) / 3))

    private fun draw() {
        if (open.x == drawnOpen && turn.x == drawnTurn) return
        drawnOpen = open.x
        drawnTurn = turn.x
        val pose = pose(open.x, turn.x)
        bar.d = tube(line(p::invoke, pose.first, pose.second), rho)
    }

    private fun retarget() {
        val pose = pose(open.t, turn.t)
        val shut = over && pose.first < SINK
        els.read.textContent =
            if (!over) "rest"
            else if (shut) "locked"
            else if (pose.second < 1) "open" else "${jsRound(pose.second).toInt()}°"
        bar.classList.toggle("hi", !shut)
        loop.wake()
    }

    override fun set(value: Double) {
        turn.t = value
        if (!over) open.t = restOpen()
        retarget()
    }

    override fun destroy() = bag.dispose()
}
