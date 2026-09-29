package dev.sebastiano.peelsticker

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * The side view of a peeling sticker. Measured from the fold axis, the sheet at [depth] along the
 * peel first stands up around a [tight] curl, then rolls back over a [loose] one, and past that it
 * lies flipped and flat, face down.
 *
 * [project] is where a depth ends up seen from above: positive ahead of the axis, negative once the
 * sheet has come back over it. [frontDepth] and [backDepth] invert it for the renderer. The rising
 * curl shows the sticker's face; everything after its top shows the backing.
 */
internal class PeelCurve(val tight: Float, val loose: Float) {
    private val tightEnd = HALF_PI * tight
    private val looseEnd = tightEnd + HALF_PI * loose

    fun project(depth: Float): Float =
        when {
            depth <= 0f -> depth
            depth <= tightEnd -> tight * sin(depth / tight)
            depth <= looseEnd -> tight - loose + loose * cos((depth - tightEnd) / loose)
            else -> tight - loose - (depth - looseEnd)
        }

    fun height(depth: Float): Float =
        when {
            depth <= 0f -> 0f
            depth <= tightEnd -> tight * (1f - cos(depth / tight))
            depth <= looseEnd -> tight + loose * sin((depth - tightEnd) / loose)
            else -> tight + loose
        }

    /** The depth on the rising curl that shows at [q], face up, if the curl reaches there. */
    fun frontDepth(q: Float): Float? =
        if (tight <= 0f || q < 0f || q > tight) null else tight * asin(q / tight)

    /** The depth past the top of the curl that shows at [q], backing up, if any does. */
    fun backDepth(q: Float): Float? =
        when {
            q > tight -> null
            loose > 0f && q >= tight - loose ->
                tightEnd + loose * acos(((q - tight + loose) / loose).coerceIn(-1f, 1f))
            else -> looseEnd + (tight - loose - q)
        }

    /**
     * How deep a grabbed point must sit behind the axis to land [distance] back towards it. The
     * sheet never gets ahead of itself, so `depth - project(depth)` only grows, and a bisection
     * finds it.
     */
    fun depthFor(distance: Float): Float {
        if (distance <= 0f) return 0f
        var low = 0f
        var high = distance + tight + 1f
        repeat(BISECTION_STEPS) {
            val mid = (low + high) / 2f
            if (mid - project(mid) < distance) low = mid else high = mid
        }
        return (low + high) / 2f
    }

    private companion object {
        const val HALF_PI = (PI / 2).toFloat()
        const val BISECTION_STEPS = 40
    }
}
