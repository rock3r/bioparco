package dev.sebastiano.honeycomb

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The field's pan. [dragBy] moves it 1:1, on a value that updates before the next pointer event. A
 * flick [settle]s with the library spring (response 0.4, damping 0.85) toward a coast point, not
 * toward a cell.
 */
internal class HoneycombPan(
    private val scope: CoroutineScope,
    private val layout: HoneycombLayout,
) {
    val offset = Animatable(Offset.Zero, Offset.VectorConverter)
    private var dragPosition = Offset.Zero
    private var settle: Job? = null

    fun beginDrag() {
        settle?.cancel()
        dragPosition = offset.value
        scope.launch { offset.stop() }
    }

    fun dragBy(delta: Offset) {
        dragPosition = clampPan(dragPosition + delta, layout.min, layout.max)
        val next = dragPosition
        scope.launch { offset.snapTo(next) }
    }

    fun settle(velocity: Offset) {
        if (velocity.getDistance() < FLING_MIN_PX_PER_SECOND) return
        val target =
            clampPan(
                coastTarget(dragPosition, velocity, SPRING_RESPONSE_SECONDS, SPRING_DAMPING),
                layout.min,
                layout.max,
            )
        val omega = (2.0 * PI / SPRING_RESPONSE_SECONDS).toFloat()
        settle = scope.launch {
            offset.animateTo(
                targetValue = target,
                animationSpec = spring(dampingRatio = SPRING_DAMPING, stiffness = omega * omega),
                initialVelocity = velocity,
            )
        }
    }

    private companion object {
        const val SPRING_RESPONSE_SECONDS = 0.4f
        const val SPRING_DAMPING = 0.85f
        const val FLING_MIN_PX_PER_SECOND = 80f
    }
}
