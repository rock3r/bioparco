package dev.sebastiano.knockoutbadge

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

/**
 * Everything on the badge that moves. Pointer and key handlers set targets; [run] steps the springs
 * and the footage's clock once a frame, and publishes them for draw and `graphicsLayer` lambdas to
 * read, so nothing recomposes while the badge moves. The footage always plays, so the loop never
 * sleeps.
 */
@Stable
internal class BadgeMotion {
    /** The footage's clock, in seconds. */
    var seconds by mutableFloatStateOf(0f)
        private set

    var tiltX by mutableFloatStateOf(0f)
        private set

    var tiltY by mutableFloatStateOf(0f)
        private set

    /** 0 assembled, 1 apart. */
    var explode by mutableFloatStateOf(0f)
        private set

    /** Where the peeled corner is, in dp from the badge's top right corner. */
    var gripX by mutableFloatStateOf(-REST_CURL)
        private set

    var gripY by mutableFloatStateOf(REST_CURL)
        private set

    /** Whether the badge is apart, or on its way there. */
    var isApart by mutableStateOf(false)
        private set

    var isPeeling by mutableStateOf(false)
        private set

    private var tiltTarget = Tilt.Flat
    private var gripTargetX = -REST_CURL
    private var gripTargetY = REST_CURL
    private val springX = Spring(TILT_STIFFNESS, TILT_DAMPING)
    private val springY = Spring(TILT_STIFFNESS, TILT_DAMPING)
    private val explodeSpring = Spring(EXPLODE_STIFFNESS, EXPLODE_DAMPING)
    private val gripSpringX = Spring(GRIP_STIFFNESS, GRIP_DAMPING, initial = -REST_CURL)
    private val gripSpringY = Spring(GRIP_STIFFNESS, GRIP_DAMPING, initial = REST_CURL)

    /** Only a badge that is together, or nearly, can be peeled. */
    val canPeel: Boolean
        get() = !isApart && explodeSpring.value < PEELABLE

    /** The pointer is at ([dx], [dy]) from the badge's centre, without a button down. */
    fun hover(dx: Float, dy: Float, halfWidth: Float, halfHeight: Float) {
        if (isPeeling) return
        val near = dx * dx / (halfWidth * halfWidth) + dy * dy / (halfHeight * halfHeight) < REACH
        tiltTarget =
            if (near) tiltToward(dx, dy, halfWidth, halfHeight, TILT_DEGREES) else Tilt.Flat
    }

    fun leave() {
        if (!isPeeling) tiltTarget = Tilt.Flat
    }

    fun toggleApart() {
        isApart = !isApart
        isPeeling = false
        settleGrip()
    }

    /** The hand has the corner, at ([dx], [dy]) dp from where the corner sits. */
    fun peelTo(dx: Float, dy: Float, maxReach: Float) {
        if (!isPeeling && !canPeel) return
        isPeeling = true
        // Peeling needs the badge square to the hand, or the corner would slip from under it.
        tiltTarget = Tilt.Flat
        val grip = gripFor(Point(0f, 0f), Point(dx, dy), maxReach)
        gripTargetX = grip.x
        gripTargetY = grip.y
    }

    fun letGo() {
        if (!isPeeling) return
        isPeeling = false
        settleGrip()
    }

    private fun settleGrip() {
        val curl = if (isApart) 0f else REST_CURL
        gripTargetX = -curl
        gripTargetY = curl
    }

    suspend fun run() {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 0f else (now - last) / NANOS_PER_SECOND
                last = now
                step(dt)
            }
        }
    }

    private fun step(dt: Float) {
        seconds += dt
        val calm = 1f - explodeSpring.value.coerceIn(0f, 1f)
        springX.step(tiltTarget.x * calm, dt)
        springY.step(tiltTarget.y * calm, dt)
        explodeSpring.step(if (isApart) 1f else 0f, dt)
        if (isPeeling) {
            // The corner stays under the hand.
            gripSpringX.snapTo(gripTargetX)
            gripSpringY.snapTo(gripTargetY)
        } else {
            gripSpringX.step(gripTargetX, dt)
            gripSpringY.step(gripTargetY, dt)
        }
        settle(springX, tiltTarget.x * calm)
        settle(springY, tiltTarget.y * calm)
        settle(explodeSpring, if (isApart) 1f else 0f)
        if (!isPeeling) {
            settle(gripSpringX, gripTargetX)
            settle(gripSpringY, gripTargetY)
        }
        tiltX = springX.value
        tiltY = springY.value
        explode = explodeSpring.value
        gripX = gripSpringX.value
        gripY = gripSpringY.value
    }

    /** A settled spring snaps onto its target, so the values stop changing and nothing redraws. */
    private fun settle(spring: Spring, target: Float) {
        if (spring.isSettledAt(target)) spring.snapTo(target)
    }

    companion object {
        /** How far the corner curls on its own, in dp each way, to show it can be peeled. */
        const val REST_CURL = 17f
        const val TILT_DEGREES = 11f
        private const val REACH = 2.6f
        private const val PEELABLE = 0.02f
        private const val TILT_STIFFNESS = 140f
        private const val TILT_DAMPING = 0.55f
        private const val EXPLODE_STIFFNESS = 70f
        private const val EXPLODE_DAMPING = 0.78f
        private const val GRIP_STIFFNESS = 260f
        private const val GRIP_DAMPING = 0.75f
        private const val NANOS_PER_SECOND = 1_000_000_000f
    }
}
