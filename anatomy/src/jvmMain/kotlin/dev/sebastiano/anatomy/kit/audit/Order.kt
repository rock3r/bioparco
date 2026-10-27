// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.audit

import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Projection

/*
 * Painter's keys settled from geometry: every one-sided overlap becomes a rule `keyFront − keyBack
 * ≥ gap + shareBack · liftBack − shareFront · liftFront`, linear in the keys, so one longest-path
 * relaxation settles every state at once. Then the final order is checked in every state.
 */

/** [item]'s sort key in [state]: `key + lift · share`, `lift` being its move toward the viewer. */
fun keyAt(item: AuditItem, state: ExplodeState, v: Point3): Double =
    item.key + liftOf(item, v) * shareOf(item, state)

/** Item [front] must be drawn [need] later in key than item [back]. */
class OrderRule(
    val front: Int,
    val back: Int,
    var weight: Double,
    var pair: OverlapPair,
    var need: Double,
)

/** The settled [keys], how many [rules] there were, the interlocked [cycles], and [dropped]. */
class OrderSolution(
    val keys: List<Double>,
    val rules: Int,
    val cycles: List<OverlapPair>,
    val dropped: List<OrderRule>,
)

private fun rulesOf(
    items: List<AuditItem>,
    overlaps: Overlaps,
    gap: Double,
    cycles: MutableList<OverlapPair>,
): List<OrderRule> {
    val strongest = LinkedHashMap<Pair<Int, Int>, OrderRule>()
    for (pair in overlaps.pairs) {
        val aFront = pair.front[0].area >= overlaps.least
        val bFront = pair.front[1].area >= overlaps.least
        if (aFront && bFront) {
            cycles.add(pair)
            continue
        }
        val front = if (aFront) pair.a else pair.b
        val back = if (aFront) pair.b else pair.a
        val weight = pair.front[if (aFront) 0 else 1].area
        val need =
            gap + shareOf(items[back], pair.state) * liftOf(items[back], overlaps.v) -
                shareOf(items[front], pair.state) * liftOf(items[front], overlaps.v)
        val prior = strongest[front to back]
        if (prior == null) {
            strongest[front to back] = OrderRule(front, back, weight, pair, need)
        } else {
            prior.weight = maxOf(prior.weight, weight)
            if (need > prior.need) {
                prior.need = need
                prior.pair = pair
            }
        }
    }
    return strongest.values.toList()
}

/**
 * Turns every one-sided pair of [overlaps] into a rule and relaxes the keys until all hold, at most
 * [rounds] rounds (default: one per item, plus one). A loop that can't be met drops its weakest
 * rule. Pairs in front of each other in different places are `cycles`.
 */
fun orderKeys(
    items: List<AuditItem>,
    overlaps: Overlaps,
    gap: Double = 0.02,
    rounds: Int = 0,
): OrderSolution {
    val start = items.map { it.key }
    val cycles = ArrayList<OverlapPair>()
    val rules = rulesOf(items, overlaps, gap, cycles)
    val limit = if (rounds != 0) rounds else items.size + 1
    val dropped = ArrayList<OrderRule>()
    var keys = start.toDoubleArray()
    var active = rules
    var attempt = 0
    var settled = false
    while (!settled && attempt < 400) {
        val relaxed = Relaxation(start, items.size)
        relaxed.run(active, limit)
        keys = relaxed.keys
        val unmet = active.filter { keys[it.front] - keys[it.back] < it.need - 1e-9 }
        val candidates = if (relaxed.changed) relaxed.loop().ifEmpty { unmet } else emptyList()
        if (candidates.isEmpty()) {
            settled = true
        } else {
            val victim = candidates.reduce { low, rule ->
                if (rule.weight < low.weight) rule else low
            }
            dropped.add(victim)
            active = active.filter { it !== victim }
        }
        attempt++
    }
    return OrderSolution(keys.toList(), rules.size, cycles, dropped)
}

private class Relaxation(start: List<Double>, val count: Int) {
    val keys = start.toDoubleArray()
    val by = arrayOfNulls<OrderRule>(count)
    var changed = true
    var last = -1

    fun run(active: List<OrderRule>, limit: Int) {
        var round = 0
        while (changed && round < limit) {
            changed = false
            for (rule in active) {
                if (keys[rule.front] - keys[rule.back] >= rule.need - 1e-9) continue
                keys[rule.front] = keys[rule.back] + rule.need
                by[rule.front] = rule
                last = rule.front
                changed = true
            }
            round++
        }
    }

    /** The loop of rules the last raised key leads back into, or none. */
    fun loop(): List<OrderRule> {
        val seen = HashMap<Int, Int>()
        var node = last
        var step = 0
        while (node != -1 && by[node] != null && node !in seen && step < count * 2) {
            seen[node] = step++
            node = by[node]!!.back
        }
        val loop = ArrayList<OrderRule>()
        if (node != -1 && node in seen) {
            var walk = node
            do {
                val rule = by[walk]!!
                loop.add(rule)
                walk = rule.back
            } while (walk != node && loop.size <= count)
        }
        return loop
    }
}

/** One pair drawn the wrong way round in one state. */
class DepthProblem(
    val state: ExplodeState,
    val label: String,
    val front: String,
    val back: String,
    val frontKey: Double,
    val backKey: Double,
    val cells: Int,
    val area: Double,
    val against: Double,
    val cycle: Boolean,
    val depth: Double,
    val at: Flat?,
    val unorderable: Boolean,
)

/** Wrong pairs that fail, wrong pairs only listed, and items drawn with no recorded shape. */
class DepthReport(
    val problems: List<DepthProblem>,
    val passing: List<DepthProblem> = emptyList(),
    val missing: List<String> = emptyList(),
)

/**
 * Checks the final order of [items] in every state of [overlaps]. At rest ([rest], default the
 * states 0 and 1) and in every replayed frame every wrong pair is a problem; in the uniform states
 * only a clean misorder counts. [allow] takes `patternA to patternB` pairs to excuse.
 */
fun depthOrder(
    items: List<AuditItem>,
    projection: Projection,
    overlaps: Overlaps = overlapsOf(items, projection),
    solved: OrderSolution? = null,
    rest: List<ExplodeState> = overlaps.states.filter { it == Uniform(0.0) || it == Uniform(1.0) },
    allow: List<Pair<String, String>> = emptyList(),
): DepthReport {
    val v = overlaps.v
    val dropped = solved?.dropped.orEmpty().map { it.front to it.back }.toSet()
    val missing = items.filter { it.shapes.isEmpty() }.map { it.name }
    val ranks = ranksOf(items, overlaps.states, v)
    val problems = ArrayList<DepthProblem>()
    val passing = ArrayList<DepthProblem>()
    for (pair in overlaps.pairs) {
        val rank = ranks.getValue(pair.state)
        val aFirst = rank[pair.a] < rank[pair.b]
        val wrong = pair.front[if (aFirst) 0 else 1]
        val right = pair.front[if (aFirst) 1 else 0]
        if (wrong.area < overlaps.least) continue
        val frontIndex = if (aFirst) pair.a else pair.b
        val backIndex = if (aFirst) pair.b else pair.a
        val front = items[frontIndex]
        val back = items[backIndex]
        val excused = allowedPair(allow, front.name, back.name)
        val cycle = right.area >= overlaps.least
        val entry =
            DepthProblem(
                pair.state,
                labelOf(pair.state),
                front.name,
                back.name,
                keyAt(front, pair.state, v),
                keyAt(back, pair.state, v),
                wrong.all,
                wrong.area,
                right.area,
                cycle,
                wrong.depth,
                wrong.at,
                pair.state !in rest && (frontIndex to backIndex) in dropped,
            )
        val atRest = pair.state in rest
        val replayed = pair.state is Staged
        val fails = !excused && (atRest || replayed || (!cycle && !entry.unorderable))
        (if (fails) problems else passing).add(entry)
    }
    return DepthReport(problems, passing, missing)
}

/** Each item's place in painter's order in each of [states]. */
private fun ranksOf(
    items: List<AuditItem>,
    states: List<ExplodeState>,
    v: Point3,
): Map<ExplodeState, IntArray> {
    val ranks = HashMap<ExplodeState, IntArray>()
    for (state in states) {
        val order =
            items.indices.sortedWith(compareBy<Int> { keyAt(items[it], state, v) }.thenBy { it })
        val rank = IntArray(items.size)
        order.forEachIndexed { position, index -> rank[index] = position }
        ranks[state] = rank
    }
    return ranks
}

/** True when one of [allow]'s pattern pairs matches [a] and [b], either way round. */
internal fun allowedPair(allow: List<Pair<String, String>>, a: String?, b: String?): Boolean =
    allow.any { (x, y) ->
        (matches(listOf(x), a) && matches(listOf(y), b)) ||
            (matches(listOf(y), a) && matches(listOf(x), b))
    }

/**
 * True when [name] matches one of [list]: an entry ending in `*` matches by prefix; otherwise the
 * name itself, or the name followed by `.`, `:` or `#`.
 */
internal fun matches(list: List<String>, name: String?): Boolean =
    name != null &&
        list.any { entry ->
            if (entry.endsWith("*")) name.startsWith(entry.dropLast(1))
            else
                name == entry ||
                    name.startsWith("$entry.") ||
                    name.startsWith("$entry:") ||
                    name.startsWith("$entry#")
        }

/** Neighbouring chunks of one route, which the overlap pass skips. */
fun sameRun(a: AuditItem, b: AuditItem): Boolean =
    a.route != null &&
        a.route.isNotEmpty() &&
        a.route == b.route &&
        kotlin.math.abs(a.chunk - b.chunk) <= 1
