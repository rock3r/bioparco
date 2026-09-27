package dev.sebastiano.scrolleffects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Where the carousel is, in slots: 0 centres the first card, 1.5 is halfway between the second and
 * the third. Drags move it directly; everything else springs it to a whole slot.
 */
@Stable
class CarouselState internal constructor(private val scope: CoroutineScope) {
    private val animatable = Animatable(0f)
    private val target = SlotTarget()
    private var dragPosition = 0f
    private var dragStart = 0f
    private var scrollAccumulator = 0f

    /** The current position. Reading it in a draw lambda redraws without recomposing. */
    val position: Float
        get() = animatable.value

    /** Moves one card to the right for a positive [direction], to the left for a negative one. */
    fun step(direction: Int) {
        val slot = target.step(animatable.value, direction).toFloat()
        scope.launch { animatable.animateTo(slot, settleSpring) }
    }

    /** Wheel and trackpad scrolling. A mouse notch is one card; trackpad deltas add up to one. */
    fun scroll(delta: Float) {
        scrollAccumulator += delta
        if (abs(scrollAccumulator) >= 1f) {
            step(scrollAccumulator.sign.toInt())
            scrollAccumulator = 0f
        }
    }

    internal fun dragStarted() {
        dragStart = animatable.value
        target.clear()
        dragPosition = animatable.value
        scope.launch { animatable.stop() }
    }

    /** [slots] is the drag distance in slots; dragging right moves the cards right. */
    internal fun dragBy(slots: Float) {
        dragPosition -= slots
        val next = dragPosition
        scope.launch { animatable.snapTo(next) }
    }

    /** [velocity] in slots per second, positive when the cards were moving left. */
    internal fun dragEnded(velocity: Float) {
        val slot = settleTarget(dragPosition, velocity, dragStart)
        target.settleAt(slot.roundToInt())
        scope.launch { animatable.animateTo(slot, settleSpring, initialVelocity = velocity) }
    }

    private companion object {
        val settleSpring =
            spring<Float>(dampingRatio = 0.86f, stiffness = Spring.StiffnessVeryLow * 2.6f)
    }
}

@Composable
fun rememberCarouselState(): CarouselState {
    val scope = rememberCoroutineScope()
    return remember(scope) { CarouselState(scope) }
}
