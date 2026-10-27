// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import kotlin.math.abs
import kotlin.math.exp

/** Two exponential followers and an interruptible idle tour, from `live.tsx`. */
class SpotMotion {
    var goal = SpotRig.MERGE
        private set

    var lead = SpotRig.MERGE
        private set

    var shown = SpotRig.MERGE
        private set

    private var pace = 0.15
    private var touring = true
    private var engaged = false
    private var tourClock = 0.0
    private var nextAt = 1.8
    private var tourStep = 0

    fun aim(merge: Double, glide: Boolean = false) {
        goal = merge.coerceIn(0.0, SpotRig.MERGE_MOST)
        pace = if (glide) 0.56 else 0.15
        touring = false
    }

    fun engage(active: Boolean) {
        engaged = active
        if (active) touring = false
    }

    /** Called by the idle timer, not by a perpetually running frame loop. */
    fun resumeTour() {
        if (engaged) return
        touring = true
        tourClock = 0.0
        nextAt = 1.8
        tourStep = 0
    }

    private fun tour(dt: Double) {
        tourClock += dt
        if (tourClock < nextAt) return
        val stops = listOf(SpotRig.MERGE_MOST, 0.0, SpotRig.MERGE)
        val holds = listOf(2.4, 2.2, 3.8)
        goal = stops[tourStep % stops.size]
        nextAt += holds[tourStep % stops.size] + 1.4
        pace = 0.56
        tourStep++
    }

    fun tick(dt: Double, calm: Boolean): Boolean {
        if (calm) {
            touring = false
            lead = goal
            shown = goal
            return false
        }
        if (touring) tour(dt)
        val blend = 1 - exp(-dt / pace)
        lead += (goal - lead) * blend
        shown += (lead - shown) * blend
        val done = abs(goal - lead) < 0.004 && abs(goal - shown) < 0.004
        if (done) {
            lead = goal
            shown = goal
        }
        return touring || !done
    }
}
