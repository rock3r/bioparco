// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.jsRound
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * The orbit audit's painter's order (`checkState`, `swapsOf` and the order scan): in every sampled
 * state, every pair that can overlap is checked against its plane (`unseparated`), the order
 * graph for cycles, the runtime's sort for forced picks, and every reorder between neighbouring
 * states for a swap of two parts that overlap on screen while they swap (`swaps`).
 */

/** One state's cameras, frames and painting orders. */
internal class OrderState(val cams: TurnCams, val frames: List<TurnFrame>, val orders: List<IntArray>)

/** A state kept for comparing with the next: orders, frames and where in the family. */
internal class Snapshot(val orders: List<IntArray>, val frames: List<OrbitFrame>, val x: Double)

private class Swap(val layer: String, val i: Int, val j: Int, val area: Double)

internal class OrbitOrder(private val o: OrbitRun) {
    private val runs = o.runs
    private val scene = o.scene
    private val result = o.result
    private val pairs = HashMap<Int, List<Pair<Int, Int>>>()
    private val flush =
        o.built.report.margins.filter { abs(it.margin) < 0.05 }.map { "${it.a}|${it.b}" }.toHashSet()

    private fun flushOf(a: String, b: String) = "$a|$b" in flush || "$b|$a" in flush

    /** The view-side of the plane between parts [i] and [j] (positive: [j] in front). */
    private fun relationOf(layer: TurnLayer, i: Int, j: Int, cams: TurnCams): Double {
        val plan = layer.planar
        for (p in plan.indices step 5) {
            val a = plan[p].toInt()
            val b = plan[p + 1].toInt()
            if ((a == i && b == j) || (a == j && b == i)) {
                val v = cams.of(runs[a].group).v
                val s = plan[p + 2] * v.x + plan[p + 3] * v.y + plan[p + 4] * v.z
                return if (a == i) s else -s
            }
        }
        val a = runs[i]
        val b = runs[j]
        val ca = cams.of(a.group)
        val cb = cams.of(b.group)
        val hit = turnGjk(a.cloud, ca, b.cloud, cb, null, 32, hintsOf(a, ca, b, cb, cams.world))
        val v = cams.world.v
        return hit.n.x * v.x + hit.n.y * v.y + hit.n.z * v.z
    }

    /** Every pair of [layer] that can have a relation: planes, dynamic, framed, groups, free. */
    private fun pairsOf(layer: TurnLayer): List<Pair<Int, Int>> =
        pairs.getOrPut(layer.index) {
            val list = ArrayList<Pair<Int, Int>>()
            for (p in layer.planar.indices step 5) list.add(layer.planar[p].toInt() to layer.planar[p + 1].toInt())
            for (p in layer.dynamic.indices step 2) list.add(layer.dynamic[p] to layer.dynamic[p + 1])
            for (p in layer.framed.indices step 6) list.add(layer.framed[p].toInt() to layer.framed[p + 1].toInt())
            for (gp in layer.groupPairs) for (i in gp.a) for (j in gp.b) list.add(i to j)
            val seen = list.map { (i, j) -> i * 65536 + j }.toHashSet()
            for (i in layer.free) {
                for (j in layer.parts) {
                    if (i == j || runs[i].group == runs[j].group) continue
                    val key = min(i, j) * 65536 + max(i, j)
                    if (!seen.add(key)) continue
                    list.add(min(i, j) to max(i, j))
                }
            }
            list
        }

    /** Emits and orders [values] from [previous] orders, and checks every relation in the state. */
    fun check(values: TurnValues, tag: String, previous: List<IntArray>?): OrderState {
        val (cams, frames) = o.emitAll(values)
        val orders = ArrayList<IntArray>()
        scene.layers.forEachIndexed { index, layer ->
            val stats = OrderStats()
            orders.add(turnOrder(layer, frames, cams, previous?.get(index), stats, scene))
            if (stats.forced > 0) result["forced"].add(Finding("${stats.forced} forced pick${if (stats.forced == 1) "" else "s"}", tag, layer.name))
            framedPlanes(layer, cams, frames, tag)
            groupPlanes(layer, cams, tag)
            graph(layer, cams, frames, tag, stats.unseparated > 0)
        }
        return OrderState(cams, frames, orders)
    }

    /** How far the posed clouds of [list] reach along [n]: the highest, or the lowest. */
    private fun along(list: IntArray, n: Point3, cams: TurnCams, high: Boolean): Double {
        var best = if (high) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
        for (index in list) {
            val run = runs[index]
            val c = cams.of(run.group)
            val r = c.r
            val q = run.cloud
            for (k in q.indices step 3) {
                val x = q[k]
                val y = q[k + 1]
                val z = q[k + 2]
                val v =
                    n.x * (r[0] * x + r[1] * y + r[2] * z + c.t.x) + n.y * (r[3] * x + r[4] * y + r[5] * z + c.t.y) +
                        n.z * (r[6] * x + r[7] * y + r[8] * z + c.t.z)
                best = if (high) max(best, v) else min(best, v)
            }
        }
        return best
    }

    private fun framedPlanes(layer: TurnLayer, cams: TurnCams, frames: List<TurnFrame>, tag: String) {
        val framed = layer.framed
        for (p in framed.indices step 6) {
            val i = framed[p].toInt()
            val j = framed[p + 1].toInt()
            if (turnDisjoint(frames[i], frames[j], 0.1)) continue
            val g = framed[p + 2].toInt()
            val n = t3Apply(if (g >= 0) cams[g].r else EYE, Point3(framed[p + 3], framed[p + 4], framed[p + 5]))
            val margin = along(intArrayOf(j), n, cams, false) - along(intArrayOf(i), n, cams, true)
            if (margin < -0.08) result["unseparated"].add(unseparated(tag, runs[i].name, runs[j].name, margin, true))
        }
    }

    private fun groupPlanes(layer: TurnLayer, cams: TurnCams, tag: String) {
        for (gp in layer.groupPairs) {
            val nw = t3Apply(if (gp.frame >= 0) cams[gp.frame].r else EYE, gp.n)
            val margin = along(gp.b, nw, cams, false) - along(gp.a, nw, cams, true)
            if (margin < -0.08) result["unseparated"].add(unseparated(tag, runs[gp.a[0]].name, runs[gp.b[0]].name, margin, true))
        }
    }

    private fun unseparated(tag: String, a: String, b: String, margin: Double, framed: Boolean) =
        Finding(
            "$tag  $a × $b margin ${f2(margin)} while their hulls overlap" +
                if (framed) " (framed plane: the build's poses() missed this motion; add it to poses())" else "",
            tag,
        )

    /** The order graph of the state: interpenetrating cross-group pairs, and cycles. */
    private fun graph(layer: TurnLayer, cams: TurnCams, frames: List<TurnFrame>, tag: String, unseparated: Boolean) {
        val n = layer.parts.size
        val adj = List(n) { ArrayList<Int>() }
        val indeg = IntArray(n)
        for ((i, j) in pairsOf(layer)) {
            if (turnDisjoint(frames[i], frames[j], 0.1)) continue
            val ri = runs[i]
            val rj = runs[j]
            if (unseparated && ri.group != rj.group) {
                val ca = cams.of(ri.group)
                val cb = cams.of(rj.group)
                val hit = turnGjk(ri.cloud, ca, rj.cloud, cb, null, 32, hintsOf(ri, ca, rj, cb, cams.world))
                if (hit.margin < -0.08) result["unseparated"].add(unseparated(tag, ri.name, rj.name, hit.margin, false))
            }
            val s = relationOf(layer, i, j, cams)
            if (s < 1e-7 && s > -1e-7) continue
            val from = layer.local.getValue(if (s > 0) i else j)
            val to = layer.local.getValue(if (s > 0) j else i)
            adj[from].add(to)
            indeg[to]++
        }
        val queue = ArrayList<Int>()
        for (v in 0 until n) if (indeg[v] == 0) queue.add(v)
        var seen = 0
        while (queue.isNotEmpty()) {
            val v = queue.removeAt(queue.size - 1)
            seen++
            for (w in adj[v]) if (--indeg[w] == 0) queue.add(w)
        }
        if (seen < n) cycle(layer, indeg, tag)
    }

    private fun cycle(layer: TurnLayer, indeg: IntArray, tag: String) {
        val stuck = layer.parts.indices.filter { indeg[it] > 0 }.map { runs[layer.parts[it]].name }
        var split: PairMargin? = null
        for (m in o.built.report.margins) {
            if (m.a in stuck && m.b in stuck && (split == null || m.margin > split.margin)) split = m
        }
        val plane = split?.let { o.built.planesByName["${it.a}|${it.b}"] }
        val text =
            if (split != null && plane != null) {
                "split ${split.b} on n = (${listOf(plane.n.x, plane.n.y, plane.n.z).joinToString(", ") { dev.sebastiano.anatomy.kit.audit.jsFixed(it, 3) }}), " +
                    "d = ${dev.sebastiano.anatomy.kit.audit.jsFixed(plane.d, 3)} (the plane of the cycle edge with the largest margin, ${split.a} × ${split.b})"
            } else {
                "split one of them (T.split) on the plane of the cycle edge with the largest margin"
            }
        result["cycles"].add(Finding(text, tag, layer.name, stuck.take(8)))
    }

    /** The pairs that swap between snapshots [a] and [b], with how much they overlap. */
    private fun swapsOf(a: Snapshot, b: Snapshot): List<Swap> {
        val list = ArrayList<Swap>()
        scene.layers.forEachIndexed { index, layer ->
            val pa = a.orders[index]
            val pb = b.orders[index]
            val rankA = HashMap<Int, Int>()
            pa.forEachIndexed { at, g -> rankA[g] = at }
            val rankB = HashMap<Int, Int>()
            pb.forEachIndexed { at, g -> rankB[g] = at }
            val moved = LinkedHashSet<Int>()
            for (at in pb.indices) if (pa[at] != pb[at]) moved.add(pb[at])
            for (i in moved) {
                for (j in layer.parts) {
                    if (i == j || (j in moved && j < i)) continue
                    if ((rankA.getValue(i) < rankA.getValue(j)) == (rankB.getValue(i) < rankB.getValue(j))) continue
                    val pi = o.t.parts[i]
                    val pj = o.t.parts[j]
                    if (pi.route != null && pi.route == pj.route && abs(pi.chunk - pj.chunk) <= 1) continue
                    val area = min(overlapArea(runs[i], a.frames[i], runs[j], a.frames[j]), overlapArea(runs[i], b.frames[i], runs[j], b.frames[j]))
                    list.add(Swap(layer.name, i, j, area))
                }
            }
        }
        return list
    }

    /** Bisects a swap between [previous] and [x] to where it happens, and records it if it shows. */
    private fun pinpoint(family: OrbitFamily, swap: Swap, previous: Snapshot, x: Double) {
        val layerIndex = scene.layers.indexOfFirst { it.name == swap.layer }
        val layer = scene.layers[layerIndex]
        val prior = previous.orders[layerIndex]
        fun probe(value: Double): Pair<Boolean, List<TurnFrame>> {
            val (cams, frames) = o.emitAll(family.at(value))
            val order = turnOrder(layer, frames, cams, prior, OrderStats(), scene)
            val at = HashMap<Int, Int>()
            order.forEachIndexed { index, g -> at[g] = index }
            return (at.getValue(swap.i) < at.getValue(swap.j)) to frames
        }
        val start = probe(previous.x).first
        var lo = previous.x
        var hi = x
        var frames: List<TurnFrame>? = null
        repeat(16) {
            val mid = (lo + hi) / 2
            val next = probe(mid)
            if (next.first == start) lo = mid
            else {
                hi = mid
                frames = next.second
            }
        }
        val shown = frames ?: probe(hi).second
        val area = overlapArea(runs[swap.i], OrbitFrame.of(shown[swap.i]), runs[swap.j], OrbitFrame.of(shown[swap.j]))
        val built = flushOf(runs[swap.i].name, runs[swap.j].name)
        if (area > if (built) 0.5 else 0.25) {
            result["swaps"].add(
                Finding("${swap.layer}: ${runs[swap.i].name} / ${runs[swap.j].name} swap at ${family.name} ${f3(hi)} while overlapping ${f2(area)} px²")
            )
        }
    }

    /** The order scan over every family, its random and event states, and the extra poses. */
    fun scan() {
        if (!o.wants("order")) {
            result.stats["orderStates"] = o.extra.size.toDouble()
            result.stats["changes"] = 0.0
            return
        }
        val changes = ArrayList<OrderChange>()
        var orderStates = 0
        for (family in o.families) {
            val xs = samplesOf(family, o.frameOf(family))
            o.say("order scan ${family.name}: ${xs.size} states")
            var previous: Snapshot? = null
            val local = ArrayList<OrderChange>()
            for (x in xs) {
                val state = check(family.at(x), o.label(family, x), previous?.orders)
                orderStates++
                val snapshot = Snapshot(state.orders.map { it.copyOf() }, state.frames.map { OrbitFrame.of(it) }, x)
                if (previous != null) {
                    val swaps = swapsOf(previous, snapshot)
                    for (swap in swaps) if (swap.area > 0.25) pinpoint(family, swap, previous, x)
                    if (swaps.isNotEmpty()) local.add(OrderChange(family, x, swaps.size, swaps.take(4).map { "${runs[it.i].name} / ${runs[it.j].name}" }))
                }
                previous = snapshot
            }
            changes.addAll(local)
            orderStates += extraStates(family, local)
        }
        for (values in o.extra.take(if (o.quick) 100 else o.extra.size)) check(values, "pose ${poseText(values)}", null)
        result.stats["orderStates"] = (orderStates + o.extra.size).toDouble()
        result.stats["changes"] = changes.size.toDouble()
        result.changes = changes.sortedWith { a, b -> b.count - a.count }.take(10)
    }

    /** Random states and the states just either side of every reorder. */
    private fun extraStates(family: OrbitFamily, local: List<OrderChange>): Int {
        val share = jsRound((if (o.quick) min(200, o.options.random) else o.options.random).toDouble() / o.families.size).toInt()
        var seed = 12345.0 + o.families.indexOf(family)
        fun rand(): Double {
            seed = (seed * 1103515245 + 12345) % 2147483648.0
            return seed / 2147483648.0
        }
        val delta = family.delta ?: if (family.periodic) 0.001 else family.frame / 50
        val events = local.flatMap { listOf(it.x - delta, it.x + delta) }
        o.say("order scan ${family.name}: $share random states, ${events.size} event states")
        for (i in 0 until share) {
            val x = family.from + rand() * (family.to - family.from)
            check(family.at(x), "random ${o.label(family, x)}", null)
        }
        val most = if (o.quick) 100 else 4000
        for (x in events.take(most)) check(family.at(x), "event ${o.label(family, x)}", null)
        return share + min(events.size, most)
    }

    private fun poseText(values: TurnValues): String =
        ("{" + values.numbers.entries.joinToString(",") { (k, v) -> "\"$k\":${jsNumber(v)}" } + "}").take(60)
}
