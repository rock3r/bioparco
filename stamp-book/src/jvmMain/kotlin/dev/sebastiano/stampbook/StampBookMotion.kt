package dev.sebastiano.stampbook

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import dev.sebastiano.bioparco.tracing.Tracing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Everything that moves. [cover], [slideA] and [slideB] change every frame and are only read while
 * drawing; [open] and [swatch] change on a hover or a click.
 */
@Stable
internal class StampBookMotion(private val scope: CoroutineScope) {
    /** 0 is the cover shut, 1 swung open. */
    val cover = Animatable(0f)

    /** 0 is the pale stamp tucked in, 1 slid half out onto the board. */
    val slideA = Animatable(0f)

    /** 0 is the bird stamp tucked in, 1 slid all the way out. */
    val slideB = Animatable(0f)

    /** The bird stamp is all the way in and still, at its resting tilt. */
    val birdTucked: Boolean
        get() = slideB.value == 0f && slideB.velocity == 0f

    var open by mutableStateOf(false)
        private set

    /** The book's colourway. It changes at once, as in the video. */
    var swatch by mutableIntStateOf(0)
        private set

    private var moving: Job? = null

    /** Opens or closes the book. Opening, the stamps follow the cover; closing, they lead it. */
    fun show(open: Boolean) {
        if (open == this.open) return
        Tracing.section(if (open) "open" else "close") {
            this.open = open
            moving?.cancel()
            moving = scope.launch { if (open) opening() else closing() }
        }
    }

    fun pick(index: Int) {
        if (index == swatch) return
        Tracing.section("recolour") { swatch = index }
    }

    // Each part runs as a child of the transition's job, so reversing the transition cancels the
    // parts still waiting for their delay too, not only the ones already moving.
    private suspend fun opening() = coroutineScope {
        launch { cover.animateTo(1f, CoverOpen) }
        launch {
            waitMillis(A_DELAY_MILLIS)
            slideA.animateTo(1f, SlideAOut)
        }
        waitMillis(B_DELAY_MILLIS)
        slideB.animateTo(1f, SlideBOut)
    }

    private suspend fun closing() = coroutineScope {
        launch { slideB.animateTo(0f, SlideIn) }
        launch { slideA.animateTo(0f, SlideIn) }
        waitMillis(COVER_CLOSE_DELAY_MILLIS)
        cover.animateTo(0f, CoverClose)
    }

    /**
     * Runs the springs once at startup, so the first hover is not the first time the JVM sees them.
     */
    suspend fun warmUp() {
        val scratch = Animatable(0f)
        for (spec in listOf(CoverOpen, SlideAOut, SlideBOut)) {
            scratch.snapTo(0f)
            scratch.animateTo(1f, spec)
        }
        scratch.animateTo(0f, SlideIn)
        scratch.animateTo(1f, CoverClose)
    }
}

/** Waits on the frame clock rather than the wall clock, so scene-driven benchmarks see it too. */
private suspend fun waitMillis(millis: Long) {
    val start = withFrameNanos { it }
    var now = start
    while (now - start < millis * NANOS_PER_MILLI) now = withFrameNanos { it }
}

private const val NANOS_PER_MILLI = 1_000_000L

/** The pale stamp starts moving just after the cover, the bird a beat later, as in the video. */
private const val A_DELAY_MILLIS = 40L

private const val B_DELAY_MILLIS = 100L

/** Closing, the stamps tuck in first and the cover follows. */
private const val COVER_CLOSE_DELAY_MILLIS = 90L

/** Swings open in about half a second and settles without a visible bounce. */
private val CoverOpen = spring<Float>(dampingRatio = 0.78f, stiffness = 170f)

private val CoverClose = spring<Float>(dampingRatio = 0.9f, stiffness = 260f)

private val SlideAOut = spring<Float>(dampingRatio = 0.8f, stiffness = 150f)

/** Looser than the others: the bird stamp overshoots a touch and rocks back. */
private val SlideBOut = spring<Float>(dampingRatio = 0.62f, stiffness = 130f)

private val SlideIn = spring<Float>(dampingRatio = 1f, stiffness = 320f)
