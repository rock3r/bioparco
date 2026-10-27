package dev.sebastiano.wheelpicker

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.derivedStateOf
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Where the ruler sits, in stops: 0 is the first stop, 2.5 is halfway between the third and the
 * fourth. A drag moves it 1:1 and rubber-bands past the ends. A release always lands on a stop, a
 * flick coasting a little first. [motion] is how fast it is moving, in stops a second, for the
 * fringe; it falls back to 0 when the ruler holds still.
 */
internal class WheelScroll(private val scope: CoroutineScope, initialIndex: Int) {
    val position = Animatable(initialIndex.toFloat())
    val motion = Animatable(0f)

    /** The stop under the indicator. It changes once per stop, so composition may read it. */
    val selectedIndex = derivedStateOf {
        position.value.roundToInt().coerceIn(0, WheelStops.lastIndex)
    }

    private var raw = position.value
    private var settle: Job? = null
    private var calm: Job? = null
    private var wheelTarget: Float? = null

    fun beginDrag() {
        settle?.cancel()
        wheelTarget = null
        raw = position.value
        scope.launch { position.stop() }
    }

    fun dragBy(deltaStops: Float, speed: Float) {
        raw += deltaStops
        val next = rubberBand(raw, WheelStops.lastIndex)
        scope.launch { position.snapTo(next) }
        feel(speed)
    }

    fun release(velocity: Float) {
        val target = snapIndex(raw, velocity, WheelStops.lastIndex)
        settleTo(target, velocity)
    }

    fun select(index: Int) {
        wheelTarget = null
        settleTo(index.coerceIn(0, WheelStops.lastIndex), position.velocity)
    }

    fun step(by: Int) {
        val from = wheelTarget ?: selectedIndex.value.toFloat()
        val next = (from + by).coerceIn(0f, WheelStops.lastIndex.toFloat())
        wheelTarget = next
        settleTo(next.roundToInt(), position.velocity)
    }

    /**
     * A wheel notch is one stop; a trackpad's fractions add up until they cross the next one, so
     * the ruler still clicks from stop to stop.
     */
    fun scrollBy(deltaStops: Float) {
        val from = wheelTarget ?: selectedIndex.value.toFloat()
        val next = (from + deltaStops).coerceIn(0f, WheelStops.lastIndex.toFloat())
        wheelTarget = next
        val index = next.roundToInt()
        if (index != selectedIndex.value || abs(position.value - index) > SETTLED) {
            settleTo(index, position.velocity)
        }
    }

    private fun settleTo(index: Int, velocity: Float) {
        settle?.cancel()
        settle = scope.launch {
            position.animateTo(
                targetValue = index.toFloat(),
                animationSpec = spring(dampingRatio = SNAP_DAMPING, stiffness = SNAP_STIFFNESS),
                initialVelocity = velocity,
            ) {
                feel(abs(this.velocity))
            }
            raw = position.value
        }
    }

    /** The fringe follows the speed at once, then fades out once nothing has moved for a beat. */
    private fun feel(speed: Float) {
        calm?.cancel()
        calm = scope.launch {
            motion.snapTo(speed)
            delay(CALM_AFTER_MILLIS)
            motion.animateTo(0f, tween(CALM_FADE_MILLIS))
        }
    }

    private companion object {
        const val SNAP_DAMPING = 0.82f
        const val SNAP_STIFFNESS = 260f
        const val SETTLED = 0.001f
        const val CALM_AFTER_MILLIS = 40L
        const val CALM_FADE_MILLIS = 260
    }
}
