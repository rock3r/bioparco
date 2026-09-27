package dev.sebastiano.componentanatomy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The stage's state. It follows the beat clock until the user does something, then it holds what
 * the user picked until they go "back to groovin'".
 *
 * Continuous values ([tilt], [explode], [pulse], the orbit) are read only in draw and layout
 * lambdas. Composition only sees the discrete [state] and [outlined].
 */
@Stable
class AnatomyStage(private val scope: CoroutineScope) {
    /** The beat clock. Written every frame. */
    var beats by mutableDoubleStateOf(0.0)
        private set

    var following by mutableStateOf(true)
        private set

    var musicOn by mutableStateOf(false)
        private set

    private val timeline by derivedStateOf { AnatomyTimeline.at(beats) }
    private val followedState by derivedStateOf { timeline.state }
    private val followedOutlined by derivedStateOf { timeline.outlined }

    private var manualState by mutableStateOf(AnatomyState.Normal)
    private var manualOutlined by mutableStateOf(false)
    var manualExploded by mutableStateOf(true)
        private set

    private val manualTilt = Animatable(0f)
    private val manualExplode = Animatable(0f)
    private val follow = Animatable(1f)
    private val orbitYaw = Animatable(0f)
    private val orbitPitch = Animatable(0f)
    private var dragging by mutableStateOf(false)

    private val openNow by derivedStateOf { explode > HALF }

    /** Whether the stack reads as open right now. Changes only when it crosses halfway. */
    val looksExploded: Boolean
        get() = if (following) openNow else manualExploded

    val state: AnatomyState
        get() = if (following) followedState else manualState

    val outlined: Boolean
        get() = if (following) followedOutlined else manualOutlined

    val tilt: Float
        get() = lerp(manualTilt.value, timeline.tilt, follow.value)

    val explode: Float
        get() = lerp(manualExplode.value, timeline.explode, follow.value)

    /** The beat, felt: 1 on each beat, fading before the next. */
    val pulse: Float
        get() = timeline.pulse

    val yawDegrees: Float
        get() = BASE_YAW * tilt + orbitYaw.value

    val pitchDegrees: Float
        get() = BASE_PITCH * tilt + orbitPitch.value

    val beatInBar: Int
        get() = (beats.mod(AnatomyTimeline.BEATS_PER_BAR.toDouble())).toInt()

    private var player: GroovePlayer? = null
    private var wallBase = 0.0
    private var wallStart = -1L

    fun pick(state: AnatomyState) = takeOver { manualState = state }

    fun pickOutlined(outlined: Boolean) = takeOver { manualOutlined = outlined }

    fun setExploded(exploded: Boolean) = takeOver {
        manualExploded = exploded
        val target = if (exploded) 1f else 0f
        scope.launch { manualExplode.animateTo(target, SPRING) }
        scope.launch { manualTilt.animateTo(target, SPRING) }
    }

    fun orbit(dxDegrees: Float, dyDegrees: Float) {
        if (!dragging) {
            takeOver {}
            dragging = true
        }
        scope.launch {
            orbitYaw.snapTo((orbitYaw.value + dxDegrees).coerceIn(-MAX_ORBIT_YAW, MAX_ORBIT_YAW))
            orbitPitch.snapTo(
                (orbitPitch.value - dyDegrees).coerceIn(-MAX_ORBIT_PITCH, MAX_ORBIT_PITCH)
            )
        }
    }

    fun endOrbit() {
        dragging = false
    }

    /** Hands the stage back to the beat clock, blending over rather than jumping. */
    fun backToGroovin() {
        if (following) return
        following = true
        scope.launch { follow.animateTo(1f, tween(BLEND_MILLIS)) }
        scope.launch { orbitYaw.animateTo(0f, SPRING) }
        scope.launch { orbitPitch.animateTo(0f, SPRING) }
    }

    fun setMusic(on: Boolean, pcm: suspend () -> ShortArray) {
        musicOn = on
        if (on) {
            scope.launch {
                val groove = GroovePlayer(pcm())
                if (!musicOn) return@launch
                player?.stop()
                // Carry on from the beat we are on, so the picture does not skip.
                withContext(Dispatchers.IO) { groove.start(beats) }
                player = groove
            }
        } else {
            player?.stop()
            player = null
            rebaseWallClock(beats)
        }
    }

    fun release() {
        player?.stop()
        player = null
    }

    internal fun tick(frameNanos: Long) {
        val heard = player?.beats()
        if (heard != null) {
            beats = heard
            wallStart = -1L
            return
        }
        if (wallStart < 0) {
            wallStart = frameNanos
            wallBase = beats
        }
        beats = wallBase + (frameNanos - wallStart) / NANOS_PER_SECOND * BEATS_PER_SECOND
    }

    private fun rebaseWallClock(from: Double) {
        wallBase = from
        wallStart = -1L
    }

    private fun takeOver(change: () -> Unit) {
        if (following) {
            // Start the manual values from what is on screen, so nothing jumps.
            val frame = timeline
            manualState = frame.state
            manualOutlined = frame.outlined
            manualExploded = frame.explode > HALF
            val shownTilt = tilt
            val shownExplode = explode
            scope.launch { manualTilt.snapTo(shownTilt) }
            scope.launch { manualExplode.snapTo(shownExplode) }
            scope.launch { follow.snapTo(0f) }
            following = false
        }
        change()
    }

    private companion object {
        const val BASE_YAW = 34f
        // Negative: the camera looks down on the stack. Front layers come down and towards you,
        // and each layer's far edge rises. A positive pitch is a view from below, and the eye
        // then reads the whole stack inside out.
        const val BASE_PITCH = -24f
        const val MAX_ORBIT_YAW = 40f
        const val MAX_ORBIT_PITCH = 30f
        const val BLEND_MILLIS = 450
        const val HALF = 0.5f
        const val NANOS_PER_SECOND = 1_000_000_000.0
        val SPRING = spring<Float>(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow)

        fun lerp(from: Float, to: Float, t: Float) = from + (to - from) * t
    }
}

@Composable
fun rememberAnatomyStage(): AnatomyStage {
    val scope = rememberCoroutineScope()
    val stage = remember { AnatomyStage(scope) }
    LaunchedEffect(stage) { while (true) withFrameNanos(stage::tick) }
    DisposableEffect(stage) { onDispose { stage.release() } }
    return stage
}
