package dev.sebastiano.scrolleffects

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Where card [index] sits relative to the focused slot when the carousel is at [position]. 0 is
 * centred, 1 is one slot to the right, -1 one slot to the left. The carousel loops, so the result
 * is always in `[-count / 2, count / 2)`.
 */
fun slotOffset(index: Int, position: Float, count: Int): Float {
    val raw = index - position
    val wrapped = raw - count * floor(raw / count + 0.5f)
    return if (wrapped >= count / 2f) wrapped - count else wrapped
}

/** The card index that is centred at [position]. */
fun focusedCard(position: Float, count: Int): Int = position.roundToInt().mod(count)

/**
 * The slot a released drag settles on. A flick carries the carousel on by its [velocity] (slots per
 * second), but never more than one slot past where the drag [started], so a hard throw still lands
 * on the neighbouring card, as in the original.
 */
fun settleTarget(position: Float, velocity: Float, started: Float): Float {
    val projected = position + velocity * FLING_SECONDS
    val nearest = projected.roundToInt().toFloat()
    val origin = started.roundToInt().toFloat()
    return nearest.coerceIn(origin - 1f, origin + 1f)
}

/** How far a flick travels, in seconds of its release velocity. */
private const val FLING_SECONDS = 0.16f

/** Hermite smoothstep, clamped. */
internal fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** A stable pseudo-random number in `[0, 1)` for the given integer coordinates. */
internal fun hash01(a: Int, b: Int = 0, c: Int = 0): Float {
    var h = a * 374_761_393 + b * 668_265_263 + c * 1_274_126_177
    h = (h xor (h ushr 13)) * 1_274_126_177
    h = h xor (h ushr 16)
    return (h ushr 8) / 16_777_216f
}

internal fun signOf(value: Float): Float = if (value < 0f) -1f else 1f
