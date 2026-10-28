package dev.sebastiano.layoutmorph

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * The carousel's position, in cards: card 2 is centred at 2. A drag moves it directly; let go, or
 * step, and it springs to a whole card.
 */
internal class CarouselDrive(private val count: Int, focus: Int) {
    var position = focus.toFloat()
        private set

    private var velocity = 0f
    private var target = focus
    private var dragging = false

    /** The card it is heading for, or the nearest while dragged. */
    val focused: Int
        get() = if (dragging) position.roundToInt().coerceIn(0, count - 1) else target

    /** Slides by [cards], up to a little past either end. */
    fun drag(cards: Float) {
        dragging = true
        velocity = 0f
        position = (position + cards).coerceIn(-OVERDRAG, count - 1 + OVERDRAG)
    }

    /** Lets go of a drag at [cardsPerSecond]: it lands on the card a short coast would reach. */
    fun release(cardsPerSecond: Float) {
        if (!dragging) return
        dragging = false
        target = (position + cardsPerSecond * FLING_REACH).roundToInt().coerceIn(0, count - 1)
        velocity = cardsPerSecond
    }

    fun focus(index: Int) {
        if (!dragging) target = index.coerceIn(0, count - 1)
    }

    fun stepFocus(delta: Int) = focus(target + delta)

    /** Advances [dt] seconds. Returns whether it is still moving. */
    fun advance(dt: Float): Boolean {
        if (dragging) return true
        if (abs(position - target) < REST && abs(velocity) < REST * VELOCITY_REST) {
            position = target.toFloat()
            velocity = 0f
            return false
        }
        val steps = (dt / MAX_SUBSTEP).toInt() + 1
        val h = dt / steps
        repeat(steps) {
            velocity += (-STIFFNESS * (position - target) - DAMPING * velocity) * h
            position += velocity * h
        }
        return true
    }

    private companion object {
        const val STIFFNESS = 150f
        val DAMPING = 2 * 0.9f * sqrt(STIFFNESS)
        const val REST = 0.0005f
        const val VELOCITY_REST = 10f
        const val MAX_SUBSTEP = 1f / 240f

        /** How far ahead (in seconds of travel) a flick lands the carousel. */
        const val FLING_REACH = 0.3f

        /** How far past either end the carousel can be dragged, in cards. */
        const val OVERDRAG = 0.4f
    }
}

/**
 * The helix's turn, in degrees. It drifts by itself; a drag turns it directly, and a throw carries
 * on and bleeds back into the drift, in the throw's direction.
 */
internal class HelixDrive {
    var spin = 0f
        private set

    /** Degrees a second. */
    var velocity = IDLE
        private set

    private var direction = 1f
    private var dragging = false

    fun drag(degrees: Float) {
        dragging = true
        spin += degrees
        velocity = 0f
    }

    fun release(degreesPerSecond: Float) {
        if (!dragging) return
        dragging = false
        velocity = degreesPerSecond
        if (velocity != 0f) direction = sign(velocity)
    }

    fun advance(dt: Float) {
        if (dragging) return
        velocity += (IDLE * direction - velocity) * (1 - exp(-dt / FRICTION))
        spin += velocity * dt
    }

    private companion object {
        /** The helix turns by itself this fast, in degrees a second. */
        const val IDLE = 12f

        /** How long a thrown helix takes to lose about two thirds of its extra speed, in s. */
        const val FRICTION = 1.2f
    }
}
