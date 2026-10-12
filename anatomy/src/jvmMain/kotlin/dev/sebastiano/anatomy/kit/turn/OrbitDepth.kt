// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.audit.AuditItem
import dev.sebastiano.anatomy.kit.audit.coverage
import dev.sebastiano.anatomy.kit.audit.overlapsOf
import dev.sebastiano.anatomy.kit.audit.sameRun
import dev.sebastiano.anatomy.kit.jsRound

/*
 * The orbit audit's stand-in check (`coverage`: a part's recorded shape covers too little of what
 * it draws at rest) and its depth oracle: in sampled states the live shapes and the static items
 * are ray-cast for true depth, and the runtime's order (`depth`) and the static layers (`layers`)
 * must agree with it.
 */

private val ZERO = Point3(0.0, 0.0, 0.0)

internal class OrbitDepth(private val o: OrbitRun) {
    private val result = o.result

    /** Each part's recorded shapes against its drawing at rest. */
    fun coverage() {
        o.say("coverage")
        if (!o.wants("coverage")) return
        val rest = TurnValues(o.t.groups.filter { it.kind != GroupKind.Free }.associate { it.name to 0.0 })
        val (_, frames) = o.emitAll(rest)
        val items =
            o.t.parts.map { part ->
                val run = o.runs[part.id]
                val f = frames[part.id]
                val drawing =
                    when (run) {
                        is FixedRun -> part.art
                        is TubeRun -> art { line(f.d[run.role.getValue("body")]) }
                        else -> art { line(f.d[run.role.getValue("fill")]) }
                    }
                AuditItem(part.name, 0.0, "", "", ZERO, part.shapes, drawing)
            }
        for (c in coverage(items, o.p, allow = o.options.cover)) {
            result["coverage"].add(Finding("${c.name} its shapes cover ${jsRound(c.share * 100).toLong()}% of its drawing"))
        }
    }

    /** The states the oracle checks: angles of the sweep, frames of each clip, and the reorders. */
    private fun states(): List<Pair<TurnValues, String>> {
        val out = ArrayList<Pair<TurnValues, String>>()
        val first = o.families.firstOrNull()
        if (first != null) {
            val count = if (o.quick) 6 else o.options.depthAngles
            for (i in 0 until count) {
                val x = first.from + ((first.to - first.from) * i) / count
                out.add(first.at(x) to o.label(first, x))
            }
        }
        for (family in o.families.drop(1)) {
            val count = if (o.quick) 3 else 12
            for (i in 0..count) {
                val x = family.from + ((family.to - family.from) * i) / count
                out.add(family.at(x) to o.label(family, x))
            }
        }
        for (change in result.changes.take(if (o.quick) 4 else o.options.depthEvents)) {
            out.add(change.family.at(change.x) to "${change.family.name} ${f3(change.x)}")
        }
        return out
    }

    /** The depth oracle over [states]: static layers and the runtime's order against true depth. */
    fun depth() {
        o.say("depth oracle")
        val states = states()
        var checked = 0
        val order = OrbitOrder(o)
        if (o.wants("depth")) {
            for ((values, tag) in states) {
                val state = order.check(values, "depth $tag", null)
                o.scene.layers.forEachIndexed { index, layer -> checked += layer(layer, state, state.orders[index], tag) }
            }
        }
        result.stats["depthStates"] = states.size.toDouble()
        result.stats["depthPairs"] = checked.toDouble()
    }

    private fun layer(layer: TurnLayer, state: OrderState, order: IntArray, tag: String): Int {
        val items = ArrayList<AuditItem>()
        val globals = ArrayList<Int>()
        val sides = ArrayList<String?>()
        for (global in layer.parts) {
            val part = o.t.parts[global]
            val pose = poseFor(state.cams, part.group)
            items.add(AuditItem(part.name, 0.0, "", "", ZERO, part.shapes.map { posed(it, pose) }, null, part.route, part.chunk))
            globals.add(global)
            sides.add(null)
        }
        val stack = o.t.order.ifEmpty { o.t.layers.map { it.name } }
        val ranks = stack.withIndex().associate { (i, name) -> name to i }
        for (item in o.statics) {
            val rank = ranks[item.layer] ?: continue
            if (item.shapes.isEmpty()) continue
            items.add(AuditItem(item.name, 0.0, "", "", ZERO, item.shapes))
            globals.add(-1)
            sides.add(if (rank < ranks.getValue(layer.name)) "below" else "above")
        }
        val sideOf = items.withIndex().associate { (i, item) -> item to sides[i] }
        val overlaps =
            overlapsOf(items, o.p, step = if (o.quick) 0.5 else 0.25, skip = { a, b -> sameRun(a, b) || (sideOf[a] != null && sideOf[b] != null) })
        for (pair in overlaps.pairs) {
            val sa = sides[pair.a]
            val sb = sides[pair.b]
            if ((sa == null) == (sb == null)) continue
            val sideIndex = if (sa != null) 0 else 1
            val fixed = if (sa != null) pair.a else pair.b
            val moving = if (sa != null) pair.b else pair.a
            val staticFront = pair.front[sideIndex].area >= overlaps.least
            val liveFront = pair.front[1 - sideIndex].area >= overlaps.least
            val side = sides[fixed]
            if (side == "below" && staticFront) {
                result["layers"].add(Finding("${items[fixed].name} (under ${layer.name}) is in front of ${items[moving].name} at $tag: ${f2(pair.front[sideIndex].area)} px²"))
            }
            if (side == "above" && liveFront) {
                result["layers"].add(Finding("${items[fixed].name} (over ${layer.name}) is behind ${items[moving].name} at $tag: ${f2(pair.front[1 - sideIndex].area)} px²"))
            }
        }
        val rank = HashMap<Int, Int>()
        order.forEachIndexed { at, global -> rank[global] = at }
        var checked = 0
        for (pair in overlaps.pairs) {
            if (sides[pair.a] != null || sides[pair.b] != null) continue
            checked++
            val aFront = pair.front[0].area >= overlaps.least
            val bFront = pair.front[1].area >= overlaps.least
            if (aFront == bFront) continue
            val front = if (aFront) pair.a else pair.b
            val back = if (aFront) pair.b else pair.a
            if (rank.getValue(globals[front]) < rank.getValue(globals[back])) {
                val area = pair.front[if (aFront) 0 else 1].area
                result["depth"].add(Finding("${layer.name} at $tag: ${items[front].name} is in front of ${items[back].name} but drawn first (${f2(area)} px²)"))
            }
        }
        return checked
    }
}
