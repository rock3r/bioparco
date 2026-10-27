package dev.sebastiano.squeezeprogress

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import dev.sebastiano.bioparco.tracing.Tracing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Everything that moves. [progress], [look], [crumple], [drift] and [anchor] change every frame and
 * are only read while drawing; [percent] and [playing] are what composition sees, and they change a
 * few times a second at most.
 */
@Stable
internal class SqueezeMotion(private val scope: CoroutineScope) {
    private val clock =
        ProgressClock(
            runMillis = RUN_MILLIS,
            holdMillis = HOLD_MILLIS,
            rewindMillis = REWIND_MILLIS,
        )

    private val progressState = mutableFloatStateOf(0f)

    /** How full the bar is, 0..1. */
    val progress: Float
        get() = progressState.floatValue

    var percent by mutableIntStateOf(0)
        private set

    var playing by mutableStateOf(true)
        private set

    /** 0 is the blue playing look, 1 the orange paused one. */
    val look = Animatable(0f)

    /** 0 is a flat line, 1 the settled squiggle. A pause overshoots past 1, where loops cross. */
    val crumple = Animatable(0f)

    /** 0 → 1 as a released crumple slides back along the line and spreads out. */
    val drift = Animatable(1f)

    /** The progress the crumple belongs to, so it stays on its spot when the tip moves on. */
    var anchor by mutableFloatStateOf(0f)
        private set

    fun toggle() {
        Tracing.section(if (playing) "pause" else "resume") { if (playing) pause() else resume() }
    }

    private fun pause() {
        playing = false
        clock.playing = false
        anchor = clock.progress
        scope.launch { drift.snapTo(0f) }
        scope.launch { look.animateTo(1f, LookSpec) }
        scope.launch { crumple.animateTo(1f, CrumpleIn) }
    }

    private fun resume() {
        playing = true
        clock.playing = true
        scope.launch { look.animateTo(0f, LookSpec) }
        scope.launch { crumple.animateTo(0f, CrumpleOut) }
        scope.launch { drift.animateTo(1f, DriftSpec) }
    }

    /** Runs the clock on frame time while playing. Paused, it waits without asking for frames. */
    suspend fun run() {
        scope.launch { warmUp() }
        var last = withFrameNanos { it }
        var spareNanos = 0L
        while (true) {
            if (!playing) {
                snapshotFlow { playing }.first { it }
                last = withFrameNanos { it }
                spareNanos = 0L
            }
            val now = withFrameNanos { it }
            val nanos = now - last + spareNanos
            last = now
            spareNanos = nanos % NANOS_PER_MILLI
            Tracing.section("advance clock") { clock.advance(nanos / NANOS_PER_MILLI) }
            progressState.floatValue = clock.progress
            percent = clock.percent
        }
    }

    /**
     * Runs the pause and resume code once at startup, while the bar is moving anyway, so the first
     * real pause is not the first time the JVM sees it. Cold, that code (the crumple maths, the
     * springs) cost the first pause's frame tens of milliseconds; see the README.
     */
    private suspend fun warmUp() {
        Tracing.section("warm up crumple") {
            val probe = Crumple(centre = 0f, halfWidth = 1f, height = 1f, squeeze = 1f)
            crumpleX(0f, probe) + crumpleY(0f, probe)
        }
        val scratch = Animatable(0f)
        scratch.animateTo(1f, CrumpleIn)
        scratch.animateTo(0f, CrumpleOut)
        scratch.animateTo(1f, LookSpec)
        scratch.animateTo(0f, DriftSpec)
    }
}

/** A fill takes eight seconds, then holds full a moment and rewinds. */
private const val RUN_MILLIS = 8_000L

private const val HOLD_MILLIS = 900L

private const val REWIND_MILLIS = 600L

private const val NANOS_PER_MILLI = 1_000_000L

/** The colours swap in about a quarter of a second, as in the video. */
private val LookSpec = tween<Float>(durationMillis = 260, easing = FastOutSlowInEasing)

/**
 * Quick and bouncy: it overshoots by about a third, which crosses the loops, and settles in about a
 * quarter of a second, as in the video.
 */
private val CrumpleIn = spring<Float>(dampingRatio = 0.36f, stiffness = 650f)

/** Slow and soft: the squiggle shakes out into a low wave that dips once the other way. */
private val CrumpleOut = spring<Float>(dampingRatio = 0.5f, stiffness = 110f)

private val DriftSpec = tween<Float>(durationMillis = 1_400, easing = LinearOutSlowInEasing)
