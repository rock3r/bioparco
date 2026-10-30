package dev.sebastiano.knockoutbadge

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A damped spring for one value, stepped once a frame. Frames are split into short sub-steps, and a
 * hitch counts as at most [MAX_STEP] seconds, so a stalled frame cannot fling anything.
 */
class Spring(private val stiffness: Float, dampingRatio: Float, initial: Float = 0f) {
    private val damping = 2f * dampingRatio * sqrt(stiffness)

    var value = initial
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

    fun snapTo(value: Float) {
        this.value = value
        velocity = 0f
    }

    fun isSettledAt(target: Float, tolerance: Float = SETTLED): Boolean =
        abs(value - target) < tolerance && abs(velocity) < tolerance * SPEED_PER_DISTANCE

    companion object {
        const val MAX_STEP = 0.1f
        private const val SUB_STEP = 1f / 240f
        private const val SETTLED = 0.001f
        private const val SPEED_PER_DISTANCE = 10f
    }
}
