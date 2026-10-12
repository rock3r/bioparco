// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import kotlin.math.sqrt

/*
 * The states the build samples (`statesOf`, `groupStates`, `tumbleStates`): the figure's own
 * reachable poses, a full turn for a root turn group, the range of a root hinge or slide, and
 * random tumbles for parts of a free group.
 */

/** The figure's poses, or a default sweep of every group when it gives none. */
internal fun statesOf(t: Turning, poses: List<TurnValues>): List<TurnValues> {
    if (poses.isNotEmpty()) return poses
    return (0 until 360 step 2).map { deg ->
        TurnValues(
            t.groups.associate { g ->
                val range = g.range
                g.name to
                    when {
                        g.kind == GroupKind.Turn -> deg.toDouble()
                        range != null ->
                            range.first + ((range.second - range.first) * (deg % 90)) / 90
                        else -> 0.0
                    }
            }
        )
    }
}

/**
 * Random rotations of [group]'s free root (and loose hinges), seeded as the original seeds them.
 */
internal fun tumbleStates(t: Turning, group: TurnGroup, count: Int = 160): List<TurnValues> {
    var root: TurnGroup? = group
    while (root != null && root.kind != GroupKind.Free) root = root.parent
    if (root == null) return emptyList()
    var seed = 977L
    fun rand(): Double {
        seed = (seed * 16807) % 2147483647
        return seed.toDouble() / 2147483647
    }
    return (0 until count).map {
        val u = rand()
        val a = rand() * TAU
        val b = rand() * TAU
        val q =
            doubleArrayOf(
                sqrt(1 - u) * jsSin(a),
                sqrt(1 - u) * jsCos(a),
                sqrt(u) * jsSin(b),
                sqrt(u) * jsCos(b),
            )
        val numbers = LinkedHashMap<String, Double>()
        for (g in t.groups) {
            val range = g.range
            if (g.kind == GroupKind.Hinge && g.loose && range != null) {
                numbers[g.name] = range.first + (range.second - range.first) * rand()
            }
        }
        TurnValues(numbers, mapOf(root.name to FreePose(q = q)))
    }
}

/** The states that move [group]: what its parts must be proved over. */
internal fun groupStates(
    t: Turning,
    group: TurnGroup?,
    states: List<TurnValues>,
    tumble: Boolean = false,
): List<TurnValues> {
    if (group == null) return listOf(TurnValues())
    val chain = group.ancestors()
    if (group.loose)
        return if (tumble) states + tumbleStates(t, group)
        else states.ifEmpty { listOf(TurnValues()) }
    if (chain.size == 1 && group.kind == GroupKind.Turn)
        return (0 until 360).map { TurnValues(mapOf(group.name to it.toDouble())) }
    if (chain.size == 1) {
        val (lo, hi) = group.range ?: (0.0 to 0.0)
        return (0..32).map { TurnValues(mapOf(group.name to lo + ((hi - lo) * it) / 32)) }
    }
    return states
}
