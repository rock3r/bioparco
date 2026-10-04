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
import dev.sebastiano.hairline.Tween
import dev.sebastiano.hairline.Vec2
import dev.sebastiano.hairline.Vec3
import dev.sebastiano.hairline.cam
import dev.sebastiano.hairline.circ
import dev.sebastiano.hairline.clamp
import dev.sebastiano.hairline.facing
import dev.sebastiano.hairline.fit
import dev.sebastiano.hairline.g
import dev.sebastiano.hairline.path
import dev.sebastiano.hairline.poly
import dev.sebastiano.hairline.prism
import dev.sebastiano.hairline.proj
import dev.sebastiano.hairline.put
import dev.sebastiano.hairline.rings
import dev.sebastiano.hairline.seg
import dev.sebastiano.hairline.solid
import dev.sebastiano.hairline.tdone
import dev.sebastiano.hairline.tset
import dev.sebastiano.hairline.tval
import dev.sebastiano.hairline.tween
import dev.sebastiano.hairline.unproj
import kotlin.math.hypot

private const val D = 30.0
private const val FY = 58.0
private const val RW = 7.0
private const val RT = 2.6
private const val PR = 8.0
private const val PH = 2.4
private const val CR = 6.6
private const val CH = 6.0
private const val LIFT = 36.0
private const val STEP = 45.0
private const val BX0 = -15.0
private const val BX1 = 225.0
private const val BY0 = -16.0
private const val BY1 = 72.0
private const val PB = 6.0
private const val ZTOP = RT + PH + CH

private data class Seed(val lane: String, val n: Int, val x: Double, val y: Double, val parent: Int)

private class Commit(
    val seed: Seed,
    val ring: Ring,
    val inner: Ring,
    val el: Solid,
    val drop: PathNode,
) {
    var z: Tween = tween(0.0)
    var drawn = Double.NaN
}

private data class History(val seeds: List<Seed>, val main: List<Int>, val feature: List<Int>)

private fun history(): History {
    val seeds = ArrayList<Seed>()
    fun add(lane: String, n: Int, x: Double, y: Double, parent: Int): Int {
        seeds.add(Seed(lane, n, x, y, parent))
        return seeds.lastIndex
    }
    val main = ArrayList<Int>()
    repeat(8) { i -> main.add(add("main", i + 1, i * D, 0.0, if (i > 0) main[i - 1] else -1)) }
    val f1 = add("feature", 1, 2.5 * D, FY, main[1])
    val f2 = add("feature", 2, 3.5 * D, FY, f1)
    val f3 = add("feature", 3, 4.5 * D, FY, f2)
    return History(seeds, main, listOf(f1, f2, f3))
}

/** A cubic from a to b, leaving a along (ox, oy) and arriving at b level. */
private fun bez(a: Vec2, b: Vec2, ox: Double, oy: Double, ix: Double): List<Vec2> =
    List(15) { k ->
        val t = k / 14.0
        val u = 1 - t
        val w = doubleArrayOf(u * u * u, 3 * u * u * t, 3 * u * t * t, t * t * t)
        Vec2(
            w[0] * a.x + w[1] * (a.x + ox) + w[2] * (b.x - ix) + w[3] * b.x,
            w[0] * a.y + w[1] * (a.y + oy) + w[2] * b.y + w[3] * b.y,
        )
    }

/** A rail: the closed strip RW wide round a centre line of world points. */
private fun strip(points: List<Vec2>): List<Vec2> {
    val left = ArrayList<Vec2>()
    val right = ArrayList<Vec2>()
    points.forEachIndexed { i, q ->
        val a = points[maxOf(0, i - 1)]
        val b = points[minOf(points.lastIndex, i + 1)]
        val dx = b.x - a.x
        val dy = b.y - a.y
        val length = hypot(dx, dy)
        left.add(Vec2(q.x - dy / length * RW / 2, q.y + dx / length * RW / 2))
        right.add(Vec2(q.x + dy / length * RW / 2, q.y - dx / length * RW / 2))
    }
    return left + right.reversed()
}

internal fun mountBranches(els: FigureEls, value: Double): FigureHandle = BranchesFigure(els, value)

private class BranchesFigure(private val els: FigureEls, value: Double) : FigureHandle {
    private val bag = Disposer()
    private var reach = value
    private val c =
        cam(45.0, 0.5, 1.4).also { camera ->
            fit(
                camera,
                listOf(
                    Vec3(BX0, BY0, -PB),
                    Vec3(BX1, BY1, -PB),
                    Vec3(BX1, BY0, -PB),
                    Vec3(BX0, BY1, -PB),
                    Vec3(BX0, BY0, ZTOP + LIFT),
                    Vec3(BX1, BY0, ZTOP + LIFT),
                ),
                200.0,
                166.0,
            )
        }
    private val p = proj(c)
    private val front = facing(c)
    private val history = history()
    private val g = els.svg.g()
    private val commits = arrayOfNulls<Commit>(history.seeds.size)

    init {
        buildScene()
    }

    private val loop =
        els.stage.register { _, now ->
            var moving = false
            commits.filterNotNull().forEach { commit ->
                draw(commit, tval(commit.z, now))
                if (!tdone(commit.z, now)) moving = true
            }
            moving
        }
    private var active: Int? = null
    private var lit: Int? = null
    private var held = HashMap<Int, Pair<Double, Int>>()

    init {
        bag.add(loop::unregister)
        lift(history.feature[2], 2.2, 0.5, true)
        bag.add(
            els.stage.pointer(
                object : PointerHandlers {
                    override fun move(p: Vec2) = choose(hit(p))

                    override fun leave() = choose(null)
                }
            )
        )
        bag.add { els.svg.clear() }
    }

    private fun buildScene() {
        val (br, bi) = rings(BX0, BY0, BX1, BY1, 12.0, 2.2)
        put(solid(g), prism(p, front, br, bi, -PB, 0.0))
        buildRails()
        val pr = circ(PR, 24)
        val pi = circ(PR - 1.4, 24)
        val cr = circ(CR, 24)
        val ci = circ(CR - 1.2, 24)
        val order = history.seeds.indices.sortedBy { history.seeds[it].x + history.seeds[it].y }
        order.forEach { i ->
            val seed = history.seeds[i]
            put(solid(g), prism(p, front, at(pr, seed), at(pi, seed), RT, PH + RT))
            val drop = g.path("dash nf")
            commits[i] = Commit(seed, at(cr, seed), at(ci, seed), solid(g), drop)
        }
    }

    private fun buildRails() {
        fun xy(i: Int) = history.seeds[i].let { Vec2(it.x, it.y) }
        val main = history.main
        val f = history.feature
        val a = xy(main[1])
        val b = xy(f[0])
        val fork = bez(a, b, (b.x - a.x) * 0.3, (b.y - a.y) * 0.5, (b.x - a.x) * 0.45)
        val ma = xy(f[2])
        val mb = xy(main[6])
        val merge =
            bez(mb, ma, (ma.x - mb.x) * 0.3, (ma.y - mb.y) * 0.5, (ma.x - mb.x) * 0.45).reversed()
        listOf(listOf(xy(main[0]), xy(main[7])), fork + merge.drop(1)).forEach { line ->
            val shape = strip(line)
            g.path("lo", poly(shape.map { p(it.x, it.y, 0.0) }))
            g.path(d = poly(shape.map { p(it.x, it.y, RT) }))
        }
    }

    private fun at(ring: Ring, seed: Seed): Ring = ring.map {
        it.copy(u = it.u + seed.x, v = it.v + seed.y)
    }

    private fun chain(start: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        var i = start
        var k = 0
        while (i >= 0) {
            out.add(i to k++)
            i = commits[i]!!.seed.parent
        }
        return out
    }

    private fun draw(commit: Commit, z: Double) {
        if (z == commit.drawn) return
        commit.drawn = z
        val bottom = PH + RT + z
        put(commit.el, prism(p, front, commit.ring, commit.inner, bottom, bottom + CH))
        val s = commit.seed
        commit.drop.d = seg(p(s.x, s.y, PH + RT), p(s.x, s.y, bottom))
    }

    private fun lift(a: Int, radius: Double, depth: Double, instant: Boolean = false) {
        val now = els.stage.now()
        val want = LinkedHashMap<Int, Pair<Double, Int>>()
        chain(a).forEach { (i, k) ->
            if (k <= radius) want[i] = LIFT * depth * clamp(1 - k / (radius + 1), 0.0, 1.0) to k
        }
        commits.filterNotNull().forEachIndexed { i, commit ->
            val target = want[i] ?: (0.0 to (held[i]?.second ?: 0))
            if (instant) commit.z = tween(target.first)
            else tset(commit.z, target.first, now, target.second * STEP)
        }
        held = HashMap(want)
        if (lit != a) {
            lit?.let { commits[it]!!.el.sil.classList.remove("hi") }
            lit = a
            commits[a]!!.el.sil.classList.add("hi")
        }
        loop.wake()
    }

    private fun choose(a: Int?) {
        if (a == active) return
        active = a
        if (a == null) {
            lift(history.feature[2], 2.2, 0.5)
            els.read.textContent = "rest"
        } else {
            lift(a, reach, 1.0)
            val s = commits[a]!!.seed
            els.read.textContent = "${s.lane} · ${s.n}"
        }
    }

    private fun hit(point: Vec2): Int? {
        val q = unproj(c, point.x, point.y, ZTOP)
        if (q.x !in BX0..BX1 || q.y !in BY0..BY1) return null
        return commits.filterNotNull().indices.minByOrNull { i ->
            commits[i]!!.seed.let { hypot(it.x - q.x, it.y - q.y) }
        }
    }

    override fun set(value: Double) {
        reach = value
        active?.let { lift(it, reach, 1.0) }
    }

    override fun destroy() = bag.dispose()
}
