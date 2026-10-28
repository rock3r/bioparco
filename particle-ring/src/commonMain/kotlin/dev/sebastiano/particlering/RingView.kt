package dev.sebastiano.particlering

import kotlin.math.exp
import kotlin.math.tanh

/**
 * How the hand turns the ring. A sideways drag spins it about its own axis, which stirs the
 * particles along the ring; let go and the spin coasts to a stop. An up and down drag tilts it
 * towards or away from the viewer, up to [MAX_TILT], and it springs back when let go.
 *
 * Drags are in ring units (the ring's radius is 1). They pile up between frames and [step] turns
 * them into motion, so the spin's speed at release is the hand's speed over the last frame.
 */
class RingView {
    /** How far the ring has been spun, in radians. */
    var spin = 0f
        private set

    /** How far the ring leans towards the viewer, in radians, beyond its resting slant. */
    var tilt = 0f
        private set

    private var spinVelocity = 0f
    private var tiltVelocity = 0f
    private var held = false
    private var pendingSpin = 0f
    private var reach = 0f

    fun press() {
        held = true
        spinVelocity = 0f
        tiltVelocity = 0f
        // Pick the tilt up where it is, so a grab mid-spring does not snap it.
        reach = MAX_TILT * atanh((tilt / MAX_TILT).coerceIn(-0.999f, 0.999f))
    }

    fun dragBy(dx: Float, dy: Float) {
        // Dragging right carries the near side of the ring to the right.
        pendingSpin -= dx * SPIN_PER_UNIT
        reach += dy * TILT_PER_UNIT
    }

    fun release() {
        held = false
    }

    fun step(dt: Float) {
        val step = dt.coerceIn(0f, MAX_STEP)
        if (held) {
            spin += pendingSpin
            if (step > 0f) {
                spinVelocity = (pendingSpin / step).coerceIn(-MAX_SPIN_SPEED, MAX_SPIN_SPEED)
            }
            // A rubber band: the tilt keeps following the hand, ever more slowly, up to the limit.
            tilt = MAX_TILT * tanh(reach / MAX_TILT)
        } else {
            spin += spinVelocity * step
            spinVelocity *= exp(-step / COAST_SECONDS)
            val force = -SPRING * tilt - DAMPING * tiltVelocity
            tiltVelocity += force * step
            tilt = (tilt + tiltVelocity * step).coerceIn(-MAX_TILT, MAX_TILT)
        }
        pendingSpin = 0f
    }

    private fun atanh(x: Float): Float = 0.5f * kotlin.math.ln((1f + x) / (1f - x))

    companion object {
        /** The furthest the ring tilts either way, in radians. */
        const val MAX_TILT = 0.5f

        /** Radians of spin for a drag of one ring radius. */
        private const val SPIN_PER_UNIT = 1.4f
        private const val TILT_PER_UNIT = 0.45f

        /** How long the spin takes to lose two thirds of its speed. */
        private const val COAST_SECONDS = 0.7f
        private const val SPRING = 70f

        // 0.55 of critical damping: the ring rocks once past level on its way back.
        private const val DAMPING = 2f * 0.55f * 8.3666f
        private const val MAX_STEP = 1f / 30f

        /** The fastest a flick sets the ring coasting, in radians a second. */
        private const val MAX_SPIN_SPEED = 8f
    }
}
