package dev.sebastiano.grabbystepper

import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.tanh

/** Rest layout of the pill, in pixels, origin at the control's top-left. */
data class RestLayout(
    val width: Float,
    val height: Float,
    val thumbDiameter: Float,
    val inset: Float,
) {
    val centerX: Float
        get() = width / 2f

    val centerY: Float
        get() = height / 2f

    val thumbRadius: Float
        get() = thumbDiameter / 2f

    val restLeft: Float
        get() = 0f

    val restRight: Float
        get() = width

    val restTop: Float
        get() = (height - thumbDiameter - inset * 2f) / 2f

    val restBottom: Float
        get() = restTop + thumbDiameter + inset * 2f
}

data class StretchRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float
        get() = right - left

    val height: Float
        get() = bottom - top

    val centerX: Float
        get() = (left + right) / 2f

    val centerY: Float
        get() = (top + bottom) / 2f

    val cornerRadius: Float
        get() = height / 2f
}

/**
 * How much of the thumb's offset the dark track is allowed to follow.
 *
 * Close in, the track reaches toward the thumb (elastic tether). Past
 * [detachStart] the follow fades out so the neck can break and the pill
 * settles back while the thumb keeps travelling.
 */
fun trackFollow(distance: Float, detachStart: Float, detachEnd: Float): Float {
    if (distance <= detachStart) return 1f
    if (distance >= detachEnd) return 0f
    val t = (distance - detachStart) / (detachEnd - detachStart)
    return (1f - t).coerceIn(0f, 1f)
}

fun stretchTrack(rest: RestLayout, offsetX: Float, offsetY: Float, follow: Float): StretchRect {
    val restTop = rest.restTop
    val restBottom = rest.restBottom
    val verticalReach = offsetY * 0.62f * follow
    return StretchRect(
        left = rest.restLeft + minOf(0f, offsetX),
        top = restTop + minOf(0f, verticalReach),
        right = rest.restRight + maxOf(0f, offsetX),
        bottom = restBottom + maxOf(0f, verticalReach),
    )
}

/** Soft rubber-band past [limit]: continues to move, but with diminishing travel. */
fun rubberband(value: Float, limit: Float): Float {
    if (limit <= 0f) return 0f
    val magnitude = abs(value)
    if (magnitude <= limit) return value
    val extra = magnitude - limit
    val damped = limit * tanh(extra / limit)
    return sign(value) * (limit + damped)
}

fun stepsFromDisplacement(displacement: Float, pxPerStep: Float): Int {
    if (pxPerStep <= 0f) return 0
    return (displacement / pxPerStep).toInt()
}

fun shouldResetOnRelease(offsetY: Float, threshold: Float): Boolean = offsetY >= threshold

/** Auto-repeat interval while the thumb is held off-center. Further = faster. */
fun tickIntervalMs(normalizedOffset: Float): Long {
    val amount = abs(normalizedOffset).coerceIn(0f, 1f)
    if (amount < HOLD_TICK_DEADZONE) return Long.MAX_VALUE
    val t = ((amount - HOLD_TICK_DEADZONE) / (1f - HOLD_TICK_DEADZONE)).coerceIn(0f, 1f)
    return (HOLD_TICK_SLOW_MS - (HOLD_TICK_SLOW_MS - HOLD_TICK_FAST_MS) * t).toLong()
}

fun applyStep(value: Int, delta: Int, floor: Int = 0): Int = (value + delta).coerceAtLeast(floor)

const val HOLD_TICK_DEADZONE = 0.18f
const val HOLD_TICK_SLOW_MS = 170f
const val HOLD_TICK_FAST_MS = 26f
const val INITIAL_TICK_FRACTION = 0.28f
