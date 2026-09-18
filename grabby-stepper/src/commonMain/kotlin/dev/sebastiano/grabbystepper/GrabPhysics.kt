package dev.sebastiano.grabbystepper

import kotlin.math.abs
import kotlin.math.hypot
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
 * Close in, the track reaches toward the thumb (elastic tether). Past [detachStart] the follow
 * fades out so the neck can break and the pill settles back while the thumb keeps travelling.
 */
fun trackFollow(distance: Float, detachStart: Float, detachEnd: Float): Float {
    if (distance <= detachStart) return 1f
    if (distance >= detachEnd) return 0f
    val t = (distance - detachStart) / (detachEnd - detachStart)
    return (1f - t).coerceIn(0f, 1f)
}

/**
 * Subtle rubber stretch — thumb travels farther than the chrome. Never 1:1 with [offsetX]/[offsetY]
 * (that made the pill explode).
 */
fun stretchTrack(rest: RestLayout, offsetX: Float, offsetY: Float, follow: Float): StretchRect {
    val restTop = rest.restTop
    val restBottom = rest.restBottom
    val maxX = rest.width * HORIZONTAL_STRETCH_CAP_FRACTION
    val maxY = rest.height * VERTICAL_STRETCH_CAP_FRACTION
    val reachX = (offsetX * HORIZONTAL_STRETCH_FOLLOW * follow).coerceIn(-maxX, maxX)
    val reachY = (offsetY * VERTICAL_STRETCH_FOLLOW * follow).coerceIn(-maxY, maxY)
    return StretchRect(
        left = rest.restLeft + minOf(0f, reachX),
        top = restTop + minOf(0f, reachY),
        right = rest.restRight + maxOf(0f, reachX),
        bottom = restBottom + maxOf(0f, reachY),
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

enum class PillHit {
    None,
    Thumb,
    Minus,
    Plus,
}

/**
 * Hit-test in rest-pill local coordinates (origin at the painted stadium's top-left). Overflow
 * gutters around the control must miss — only the visible pill responds.
 */
fun hitTestPill(localX: Float, localY: Float, layout: RestLayout): PillHit {
    if (localX !in 0f..layout.width || localY !in layout.restTop..layout.restBottom) {
        return PillHit.None
    }
    val onThumb =
        hypot(localX - layout.centerX, localY - layout.centerY) <=
            layout.thumbRadius + layout.inset * 2f
    if (onThumb) return PillHit.Thumb
    if (localX <= layout.width * 0.28f) return PillHit.Minus
    if (localX >= layout.width * 0.72f) return PillHit.Plus
    return PillHit.None
}

const val HOLD_TICK_DEADZONE = 0.18f
const val HOLD_TICK_SLOW_MS = 170f
const val HOLD_TICK_FAST_MS = 48f
const val INITIAL_TICK_FRACTION = 0.28f

/** Track follows only a fraction of thumb travel (subtle rubber). */
const val HORIZONTAL_STRETCH_FOLLOW = 0.22f
const val VERTICAL_STRETCH_FOLLOW = 0.38f
const val HORIZONTAL_STRETCH_CAP_FRACTION = 0.14f
const val VERTICAL_STRETCH_CAP_FRACTION = 0.45f
