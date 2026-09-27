package dev.sebastiano.scrolleffects

import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * The whole slot the carousel is heading for. Steps count from the pending slot, not from the
 * position the spring has reached, so quick key presses or wheel notches queue up rather than
 * rounding to the same slot and getting lost.
 */
class SlotTarget {
    private var pending: Int? = null

    /** The slot one step in [direction] from where the carousel is already going. */
    fun step(position: Float, direction: Int): Int {
        val next = (pending ?: position.roundToInt()) + direction.sign
        pending = next
        return next
    }

    /** Forgets the queue. A drag moves the carousel directly, so the next step starts from it. */
    fun clear() {
        pending = null
    }

    /** Records where a released drag settles. */
    fun settleAt(slot: Int) {
        pending = slot
    }
}
