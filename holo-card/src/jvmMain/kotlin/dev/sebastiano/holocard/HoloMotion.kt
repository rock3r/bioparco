package dev.sebastiano.holocard

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.abs
import kotlinx.coroutines.channels.Channel

/**
 * Where the card is turned, and where it wants to be. Pointer handlers set the target; [run] steps
 * the springs once a frame and publishes [tiltX], [tiltY] and [lift] for layers and draw lambdas to
 * read. Once everything settles, the loop sleeps until the target moves again, so a resting card
 * costs no frames.
 */
@Stable
internal class HoloMotion {
    var tiltX by mutableFloatStateOf(0f)
        private set

    var tiltY by mutableFloatStateOf(0f)
        private set

    /** 1 while the card is held up off the table, springing back to 0 when let go. */
    var lift by mutableFloatStateOf(0f)
        private set

    var autopilot by mutableStateOf(false)
        private set

    private var target = Tilt.Flat
    private var grabbedAt: Offset? = null
    private var tiltAtGrab = Tilt.Flat
    private var autopilotClock = 0f
    private val springX = TiltSpring()
    private val springY = TiltSpring()
    private val liftSpring = TiltSpring(stiffness = LIFT_STIFFNESS, dampingRatio = LIFT_DAMPING)
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** The pointer moved to [offset] from the card's centre, without a button down. */
    fun hover(offset: Offset, half: Size) {
        if (grabbedAt != null) return
        val near = abs(offset.x) < half.width + REACH && abs(offset.y) < half.height + REACH
        aim(
            if (near) tiltToward(offset.x, offset.y, half.width, half.height, HOVER_DEGREES)
            else Tilt.Flat
        )
    }

    fun leave() {
        if (grabbedAt == null) aim(Tilt.Flat)
    }

    fun grab(offset: Offset) {
        grabbedAt = offset
        tiltAtGrab = Tilt(springX.value, springY.value)
        wake.trySend(Unit)
    }

    /** The held pointer is at [offset] from the card's centre: the card turns with the drag. */
    fun drag(offset: Offset, half: Size) {
        val from = grabbedAt ?: return
        val turn =
            tiltToward(offset.x - from.x, offset.y - from.y, half.width, half.height, DRAG_DEGREES)
        aim(
            Tilt(
                x = (tiltAtGrab.x + turn.x).coerceIn(-DRAG_DEGREES, DRAG_DEGREES),
                y = (tiltAtGrab.y + turn.y).coerceIn(-DRAG_DEGREES, DRAG_DEGREES),
            )
        )
    }

    fun release(offset: Offset, half: Size) {
        if (grabbedAt == null) return
        grabbedAt = null
        hover(offset, half)
    }

    fun toggleAutopilot() {
        autopilot = !autopilot
        if (!autopilot) target = Tilt.Flat
        wake.trySend(Unit)
    }

    private fun aim(tilt: Tilt) {
        target = tilt
        wake.trySend(Unit)
    }

    suspend fun run() {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val seconds = if (last < 0) 0f else (now - last) / NANOS_PER_SECOND
                last = now
                step(seconds)
            }
            if (isResting()) {
                last = -1L
                wake.receive()
            }
        }
    }

    private fun step(seconds: Float) {
        val held = grabbedAt != null
        if (autopilot) autopilotClock += seconds
        val goal =
            if (autopilot && !held) autopilotTilt(autopilotClock, AUTOPILOT_DEGREES) else target
        springX.step(goal.x, seconds)
        springY.step(goal.y, seconds)
        liftSpring.step(if (held) 1f else 0f, seconds)
        tiltX = springX.value
        tiltY = springY.value
        lift = liftSpring.value
    }

    private fun isResting(): Boolean =
        !autopilot &&
            grabbedAt == null &&
            springX.isSettledAt(target.x) &&
            springY.isSettledAt(target.y) &&
            liftSpring.isSettledAt(0f)

    companion object {
        /** Roughly the most the card turns; the foil reads the tilt as a share of this. */
        const val FULL_DEGREES = 26f
        const val HOVER_DEGREES = 14f
        const val DRAG_DEGREES = 34f
        const val AUTOPILOT_DEGREES = 22f

        /** How far past the card's edges the pointer still tilts it, in pixels. */
        private const val REACH = 120f
        private const val LIFT_STIFFNESS = 320f
        private const val LIFT_DAMPING = 0.62f
        private const val NANOS_PER_SECOND = 1_000_000_000f
    }
}
