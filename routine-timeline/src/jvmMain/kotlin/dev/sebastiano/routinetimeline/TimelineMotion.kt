package dev.sebastiano.routinetimeline

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.sebastiano.bioparco.tracing.Tracing
import java.time.LocalTime
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Everything that moves. [time] and [listScroll] change every frame and are read while drawing or
 * in layer and offset lambdas; composition only sees which rows exist and what their labels say.
 *
 * With a [fixedNow] the clock is frozen there, so recordings and benchmarks look the same on every
 * run. Otherwise now follows the system clock, and the dial follows now until it is touched.
 */
@Stable
internal class TimelineMotion(private val scope: CoroutineScope, fixedNow: Int?) {
    val routine = Routine.Default
    private val frozen = fixedNow != null
    private val startedAt = System.nanoTime()
    private val startMinute = fixedNow?.toDouble() ?: systemMinute()

    /** Where now is, as a running minute: [startMinute] plus the minutes since launch. */
    private var nowAnchor = startMinute

    /** The running minute the dial shows. Scrubbing pushes it past midnight or below zero. */
    val time = Animatable(startMinute.toFloat())

    /** The list's position, in ordinals: it springs after [Routine.focusAt]. */
    val listScroll = Animatable(routine.focusAt(startMinute).toFloat())

    /** True while the dial follows now; any touch lets go of it, Now takes it back. */
    var following by mutableStateOf(true)
        private set

    private var stepTarget: Double? = null
    private var wheelRest = 0f

    val now: Double
        get() =
            if (frozen) nowAnchor
            else nowAnchor + (System.nanoTime() - startedAt) / NANOS_PER_MINUTE

    /** How far the dial is from now, in minutes of the day, for the Now button's look. */
    fun awayFromNow(): Double {
        val shown = time.value.toDouble()
        return abs(shown - nearestEquivalent(minuteOfDay(now), shown))
    }

    suspend fun run() = coroutineScope {
        launch {
            snapshotFlow { routine.focusAt(time.value.toDouble()) }
                .collectLatest { listScroll.animateTo(it.toFloat(), ListSpring) }
        }
        if (!frozen) {
            while (true) {
                delay(TICK_MILLIS)
                if (following && !time.isRunning) time.snapTo(now.toFloat())
            }
        }
    }

    /** A pointer took hold of the dial: stop whatever it was doing. */
    fun grab() {
        following = false
        stepTarget = null
        scope.launch { time.stop() }
    }

    fun turnBy(minutes: Double) {
        scope.launch { time.snapTo(time.value + minutes.toFloat()) }
    }

    /** The pointer let go at [velocity] minutes a second: coast to a stop. */
    fun fling(velocity: Float) {
        Tracing.section("fling") {
            val v = velocity.coerceIn(-MAX_FLING, MAX_FLING)
            scope.launch { time.animateDecay(v, FlingDecay) }
        }
    }

    /** One step to the next quarter hour, later for 1 and earlier for -1. Steps queue up. */
    fun step(direction: Int) {
        following = false
        val target = quarterStep(stepTarget ?: time.value.toDouble(), direction)
        stepTarget = target
        scope.launch {
            try {
                time.animateTo(target.toFloat(), StepSpring)
                // A spring stops within its threshold of the target: land on it, so the clock
                // reads the quarter hour and not a minute before it.
                time.snapTo(target.toFloat())
            } finally {
                if (stepTarget == target) stepTarget = null
            }
        }
    }

    /** A wheel turns a quarter hour a notch; trackpads add up their small deltas first. */
    fun wheel(deltaY: Float) {
        wheelRest += deltaY
        while (wheelRest >= 1f) {
            wheelRest -= 1f
            step(1)
        }
        while (wheelRest <= -1f) {
            wheelRest += 1f
            step(-1)
        }
    }

    /** Spins the dial the short way back to now, then follows the clock again. */
    fun backToNow() {
        Tracing.section("back to now") {
            stepTarget = null
            val current = now
            val target = nearestEquivalent(minuteOfDay(current), time.value.toDouble())
            // Keep now in the same day as the dial, so the clock ticks on from where it lands.
            nowAnchor += target - current
            following = false
            scope.launch {
                time.animateTo(target.toFloat(), NowSpring)
                time.snapTo(target.toFloat())
                following = true
            }
        }
    }

    private companion object {
        const val NANOS_PER_MINUTE = 60e9
        const val TICK_MILLIS = 1_000L
        const val MAX_FLING = 4_000f

        val ListSpring = spring<Float>(dampingRatio = 0.86f, stiffness = 260f)
        val StepSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 320f)
        val NowSpring =
            spring<Float>(dampingRatio = 0.8f, stiffness = 42f, visibilityThreshold = 0.01f)
        val FlingDecay = exponentialDecay<Float>(frictionMultiplier = 1.6f)

        fun systemMinute(): Double = LocalTime.now().toSecondOfDay() / 60.0
    }
}
