// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import kotlin.math.abs
import kotlin.math.floor

/*
 * The live figure's state, after the original's live.js: one spring per assembly that takes the
 * engine apart in a staggered order (and back in the reverse one, rescheduling any spring still on
 * its way), the selected assembly, the tour, and the burn. One loop advances it a frame at a time;
 * the canvas reads it, so nothing recomposes while it moves.
 */

/** One assembly's spring: its share [x] of the way apart, its velocity, target and next goal. */
internal class PieceSpring {
    var x = 0.0
    var v = 0.0
    var target = 0.0
    var pending: Pair<Double, Double>? = null
}

/** One step of the tour: apart or not, which assembly is shown, whether to fire. */
internal data class TourStep(val apart: Boolean, val part: String?, val fire: Boolean)

internal class RaptorLive(
    private val ids: List<String> = Raptor.STAGGER,
    private val pieces: Map<String, String> = emptyMap(),
) {
    val springs: Map<String, PieceSpring> = ids.associateWith { PieceSpring() }
    val burn = RaptorBurn()
    var clock = 0.0
        private set

    var apart = false
        private set

    var selected: String? = null
        private set

    var touring = true
    var tourClock = -1.0
    /** True while the pointer is over the figure. */
    var inside = false
    /** Reduced motion: every spring jumps, the burn holds still. */
    var still = false

    /** Starts the explode ([on]) or the close-up, one assembly after another. */
    fun setApart(on: Boolean) {
        if (apart == on) return
        apart = on
        val goal = if (on) 1.0 else 0.0
        var index = 0
        for (id in if (on) ids else ids.reversed()) {
            val spring = springs[id] ?: continue
            spring.pending =
                if (spring.target == goal) null
                else goal to clock + if (still) 0.0 else index++ * Raptor.SPRING.stagger
        }
    }

    /** Selects an assembly to light; the stand can't be. */
    fun select(id: String?) {
        selected = if (id == "stand") null else id
    }

    /** The legend's position of the selection, 1-based, or 0. */
    val valueNow: Int
        get() = selected?.let { Raptor.ORDER.indexOf(it) + 1 } ?: 0

    fun ignite(fromTour: Boolean = false) {
        if (burn.on) return
        setApart(false)
        select(null)
        burn.ignite(fromTour)
    }

    /** I, Enter or the button: fire, or shut down a burn. */
    fun toggleFire() {
        if (burn.on) burn.shutdown() else ignite(false)
    }

    private fun steer(spring: PieceSpring, dt: Double, calm: Boolean): Boolean {
        val pending = spring.pending
        // A calm frame takes a goal still waiting its turn at once, so nothing is left halfway.
        if (pending != null && (calm || clock >= pending.second)) {
            spring.target = pending.first
            spring.pending = null
        }
        if (calm) {
            spring.x = spring.target
            spring.v = 0.0
            return false
        }
        val s = Raptor.SPRING
        repeat(s.substeps) {
            val h = dt / s.substeps
            spring.v += ((spring.target - spring.x) * s.stiffness - spring.v * s.damping) * h
            spring.x += spring.v * h
        }
        if (abs(spring.target - spring.x) < 2e-4 && abs(spring.v) < 2e-4) {
            spring.x = spring.target
            spring.v = 0.0
            return spring.pending != null
        }
        return true
    }

    /** One frame; returns true while anything moves, burns or tours. */
    fun tick(dt: Double, calm: Boolean): Boolean {
        clock += dt
        if (touring && !burn.on) {
            tourClock += dt
            val step = tourAt(tourClock)
            setApart(step.apart)
            select(step.part)
            if (step.fire) ignite(true)
        }
        var moving = false
        for (id in ids) if (steer(springs.getValue(id), dt, calm)) moving = true
        val home = springs.values.all { it.x < 0.002 && it.pending == null }
        val burning = burn.step(dt, clock, home, calm)
        if (burn.ended && burn.tour) tourClock = 0.0
        return moving || burning || touring
    }

    /** True while any spring is still on its way. */
    val moving: Boolean
        get() = springs.values.any { abs(it.x - it.target) > 0.002 || it.pending != null }

    /** The readout: the burn's while it burns, else what the engine is doing. */
    fun readout(): String {
        if (burn.on) return burn.say
        selected?.let {
            return pieces[it] ?: it
        }
        if (moving) return if (apart) "taking apart" else "closing up"
        return if (apart) "apart · 11 assemblies" else "1,630 kg · cold"
    }

    /** The accessible value: the selection's line, firing, apart, or assembled. */
    fun describe(): String =
        selected?.let { pieces[it] ?: it }
            ?: when {
                burn.on -> "firing"
                apart -> "apart · 11 assemblies"
                else -> "Assembled, cold"
            }

    /** Arrow keys, Home and End: the next, previous, first or last assembly in the legend. */
    fun step(delta: Int?, to: Int? = null) {
        if (burn.on) return
        val order = Raptor.ORDER
        val index = selected?.let { order.indexOf(it) } ?: -1
        val next = to ?: (index + (delta ?: 0)).coerceIn(0, order.size - 1)
        setApart(true)
        select(order[next])
    }

    companion object {
        const val TOUR_STEP = 1.5

        fun tourAt(time: Double): TourStep {
            val apartFrom = 0.4
            val listFrom = apartFrom + 0.9
            val listTo = listFrom + Raptor.ORDER.size * TOUR_STEP
            val fire = listTo + 1.4
            return when {
                time < apartFrom -> TourStep(false, null, false)
                time < listFrom -> TourStep(true, null, false)
                time < listTo ->
                    TourStep(
                        true,
                        Raptor.ORDER[floor((time - listFrom) / TOUR_STEP).toInt()],
                        false,
                    )
                time < fire -> TourStep(false, null, false)
                else -> TourStep(false, null, true)
            }
        }
    }
}
