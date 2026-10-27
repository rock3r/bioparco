package dev.sebastiano.holocard

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A card's turn in degrees, as Compose's `graphicsLayer` takes it: [x] about the horizontal axis
 * (positive brings the top edge forward), [y] about the vertical one (positive sends the right edge
 * away).
 */
data class Tilt(val x: Float, val y: Float) {
    companion object {
        val Flat = Tilt(0f, 0f)
    }
}

/**
 * The tilt that makes the card's side under the pointer recede, as if pressed: [dx] and [dy] are
 * the pointer's offset from the card's centre. Past the card's edges it tilts no further.
 */
fun tiltToward(dx: Float, dy: Float, halfWidth: Float, halfHeight: Float, maxDegrees: Float): Tilt {
    val across = (dx / halfWidth).coerceIn(-1f, 1f)
    val down = (dy / halfHeight).coerceIn(-1f, 1f)
    return Tilt(x = down * maxDegrees, y = across * maxDegrees)
}

/**
 * A slightly underdamped spring for one tilt axis, stepped once a frame, so the card swings a touch
 * past where the pointer asks for and wobbles back. Frames are split into short sub-steps, and a
 * hitch counts as at most [MAX_STEP] seconds, so a stalled frame cannot fling the card.
 */
class TiltSpring(
    private val stiffness: Float = STIFFNESS,
    dampingRatio: Float = DAMPING_RATIO,
) {
    private val damping = 2f * dampingRatio * sqrt(stiffness)

    var value = 0f
        private set

    var velocity = 0f
        private set

    fun step(target: Float, seconds: Float) {
        var left = min(seconds, MAX_STEP)
        while (left > 0f) {
            val dt = min(left, SUB_STEP)
            val acceleration = -stiffness * (value - target) - damping * velocity
            velocity += acceleration * dt
            value += velocity * dt
            left -= dt
        }
    }

    fun isSettledAt(target: Float): Boolean =
        abs(value - target) < SETTLED_DEGREES && abs(velocity) < SETTLED_SPEED

    private companion object {
        const val STIFFNESS = 170f
        const val DAMPING_RATIO = 0.5f
        const val MAX_STEP = 0.1f
        const val SUB_STEP = 1f / 240f
        const val SETTLED_DEGREES = 0.02f
        const val SETTLED_SPEED = 0.05f
    }
}

/**
 * A slow Lissajous sweep for the hands-off demo: two incommensurate frequencies, so the card never
 * quite repeats a path, and each axis at most [AUTOPILOT_REACH] of [maxDegrees] so the pair stays
 * within [maxDegrees].
 */
fun autopilotTilt(seconds: Float, maxDegrees: Float): Tilt =
    Tilt(
        x = maxDegrees * AUTOPILOT_REACH * sin(seconds * 1.7f + 0.9f),
        y = maxDegrees * AUTOPILOT_REACH * sin(seconds * 1.1f),
    )

private const val AUTOPILOT_REACH = 0.7f
