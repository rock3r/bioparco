// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.audit

import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.dot3
import dev.sebastiano.anatomy.kit.jsRound
import dev.sebastiano.anatomy.kit.mul3
import kotlin.math.abs

/*
 * Explode states: every assembly at one share, or one share per assembly, replayed from the live
 * page's own staggered springs, with reversals and the prefix states behind the prefix rule.
 */

/** An explode state. */
sealed interface ExplodeState

/** Every assembly at [share]: the original's plain number state. */
data class Uniform(val share: Double) : ExplodeState

/** One share per assembly, from a replayed frame or a prefix state. */
class Staged(val label: String, val shares: Map<String, Double>) : ExplodeState {
    override fun toString(): String = label
}

/** The live page's springs: the stagger between starts and the spring constants. */
data class SpringSpec(
    val stagger: Double = 0.035,
    val stiffness: Double = 64.0,
    val damping: Double = 14.2,
    val substeps: Int = 4,
    val fps: Double = 60.0,
    val settle: Double = 1e-3,
)

/** [mover]'s share in [state], read from its `ride` (or `piece`). */
fun shareOf(mover: Mover, state: ExplodeState): Double =
    when (state) {
        is Uniform -> state.share
        is Staged -> (mover.ride ?: mover.piece)?.let { state.shares[it] } ?: 0.0
    }

/** [mover]'s offset in [state]: its move times its share. */
fun offsetOf(mover: Mover, state: ExplodeState): Point3 =
    mul3(mover.move ?: ZERO, shareOf(mover, state))

/** A state's printable label. */
fun labelOf(state: ExplodeState): String =
    when (state) {
        is Uniform -> "share ${jsString(jsRound(state.share * 1000) / 1000)}"
        is Staged -> state.label
    }

internal fun liftOf(mover: Mover, v: Point3): Double = dot3(mover.move ?: ZERO, v)

/**
 * The live page's springs replayed, opening from rest and closing from apart, one state per frame
 * until every spring settles; every [stride]th frame is kept. Each of [reversals] adds the paths a
 * quick hover makes: open, then close again after that many seconds, and the reverse.
 */
fun springStates(
    order: List<String>,
    spring: SpringSpec = SpringSpec(),
    stride: Int = 1,
    reversals: List<Double> = emptyList(),
): List<Staged> {
    val out = ArrayList<Staged>()
    val scripts = buildList {
        add(true to null)
        add(false to null)
        for (at in reversals) {
            add(true to at)
            add(false to at)
        }
    }
    for ((on, back) in scripts) SpringReplay(order, spring, on, back).run(stride, out)
    return out
}

private class SpringState(var x: Double, var target: Double) {
    var v = 0.0
    var pendingValue = 0.0
    var pendingAt = 0.0
    var pending = false
}

private class SpringReplay(
    val order: List<String>,
    val spring: SpringSpec,
    val on: Boolean,
    val back: Double?,
) {
    private val start = if (on) 0.0 else 1.0
    private val springs = LinkedHashMap<String, SpringState>()

    init {
        for (id in order) springs[id] = SpringState(start, start)
    }

    private val name =
        if (back == null) (if (on) "apart" else "together")
        else "${if (on) "apart" else "together"}, back at ${jsFixed(back, 2)} s,"

    fun toggle(value: Boolean, clock: Double) {
        val goal = if (value) 1.0 else 0.0
        var index = 0
        for (id in if (value) order else order.reversed()) {
            val s = springs.getValue(id)
            if (s.target == goal) {
                s.pending = false
            } else {
                s.pending = true
                s.pendingValue = goal
                s.pendingAt = clock + index++ * spring.stagger
            }
        }
    }

    fun run(stride: Int, out: MutableList<Staged>) {
        val dt = 1 / spring.fps
        var clock = 0.0
        var goal = if (on) 1.0 else 0.0
        toggle(on, 0.0)
        for (frame in 0 until 100000) {
            if (back != null && clock < back && clock + dt >= back) {
                toggle(!on, back)
                goal = if (on) 0.0 else 1.0
            }
            clock += dt
            for (s in springs.values) step(s, clock, dt)
            if (settled(clock, goal)) break
            if ((frame + 1) % stride == 0) {
                val shares = LinkedHashMap<String, Double>()
                for ((id, s) in springs) shares[id] = s.x
                out.add(Staged("$name ${jsFixed(clock, 3)} s", shares))
            }
        }
    }

    private fun step(s: SpringState, clock: Double, dt: Double) {
        if (s.pending && clock >= s.pendingAt) {
            s.target = s.pendingValue
            s.pending = false
        }
        for (index in 0 until spring.substeps) {
            val h = dt / spring.substeps
            s.v += ((s.target - s.x) * spring.stiffness - s.v * spring.damping) * h
            s.x += s.v * h
        }
    }

    private fun settled(clock: Double, goal: Double): Boolean =
        (back == null || clock >= back) &&
            springs.values.all {
                !it.pending && abs(it.x - goal) < spring.settle && abs(it.v) < spring.settle
            }
}

/** `0, 1/count … 1`: every assembly at one share. Only the key solver sees these. */
fun uniformStates(count: Int = 10): List<Uniform> =
    List(count + 1) { Uniform(it.toDouble() / count) }

/** A reversal [margin] either side of every stagger boundary, where the bad windows are. */
fun reversalsOf(
    order: List<String>,
    stagger: Double = 0.035,
    margin: Double = 0.008,
): List<Double> =
    order.indices.flatMap { listOf(it * stagger - margin, it * stagger + margin) }.filter { it > 0 }

/** The first `k − 1` assemblies out, the `k`th at each of [shares], the rest home. */
fun prefixStates(
    order: List<String>,
    shares: List<Double> = listOf(0.25, 0.5, 0.75, 1.0),
): List<Staged> {
    val out = ArrayList<Staged>()
    for (count in 1 until order.size) {
        for (s in shares) {
            val map = LinkedHashMap<String, Double>()
            order.forEachIndexed { index, id ->
                map[id] =
                    when {
                        index < count - 1 -> 1.0
                        index == count - 1 -> s
                        else -> 0.0
                    }
            }
            out.add(Staged("first ${count - 1} out, ${order[count - 1]} at ${jsString(s)}", map))
        }
    }
    return out
}

/**
 * The depth-solver states ([depth]: uniform shares, every [stride]th replayed frame with reversals
 * at every stagger boundary, prefix states) and the clearance [frames] (every replayed frame plus
 * the prefix states). `depth = [0]` and no frames when nothing comes apart.
 */
class ExplodeStates(
    val depth: List<ExplodeState>,
    val frames: List<ExplodeState>,
    val apart: Boolean,
)

fun explodeStates(
    order: List<String> = emptyList(),
    spring: SpringSpec = SpringSpec(),
    uniform: Int = 10,
    stride: Int = 4,
): ExplodeStates {
    if (order.isEmpty()) return ExplodeStates(listOf(Uniform(0.0)), emptyList(), false)
    val reversals = reversalsOf(order, spring.stagger)
    val prefix = prefixStates(order)
    return ExplodeStates(
        uniformStates(uniform) + springStates(order, spring, stride, reversals) + prefix,
        springStates(order, spring, 1, reversals) + prefix,
        true,
    )
}
