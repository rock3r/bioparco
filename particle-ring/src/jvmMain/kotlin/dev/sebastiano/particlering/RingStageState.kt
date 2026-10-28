package dev.sebastiano.particlering

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import dev.sebastiano.bioparco.tracing.Tracing

/** What the caption says in each phase. The stage lays them all out once and fades between them. */
internal val Captions: List<String> =
    listOf(
        "Ready to read your Health data",
        "Reading your workouts",
        "Reading your sleep",
        "Reading your heart rate",
        "Your highlights are ready",
    )

/**
 * Everything the stage animates. The particles move every frame, but only [frame] is state, and
 * only the draw and layer phases read it: the animation redraws, it never recomposes. [caption]
 * changes a handful of times a run, and only the node Spectre reads the caption from reads it.
 */
@Stable
internal class RingStageState(particleCount: Int = PARTICLE_COUNT) {
    val ring = Tracing.section("ring setup") { ParticleRing(particleCount) }
    val view = RingView()

    /** The time of the last frame stepped, in frame-clock nanoseconds. The canvas reads this. */
    val frame = mutableLongStateOf(0L)

    /** Which of [Captions] is on show, or -1 for none. Only Spectre's caption node reads it. */
    val caption = mutableIntStateOf(0)

    /** How much of each caption shows, stepped each frame: see [fadeCaptions]. */
    val captionAlpha = FloatArray(Captions.size)

    /** Seconds since the first frame. */
    var seconds = 0f
        private set

    /** Pixels per ring unit (the ring's radius), and where the ring's centre is, in the stage. */
    var unit = 1f
        private set

    var centre = Offset.Zero
        private set

    private var firstFrameNanos = -1L
    private var readingSince = Float.NaN
    private var held = false

    fun onSize(size: IntSize) {
        unit = ringUnit(size.width, size.height)
        centre = ringCentre(size.width, size.height)
    }

    /** Where [position], in stage pixels, is in ring units. */
    fun toRing(position: Offset): Offset = (position - centre) / unit

    fun step(frameNanos: Long) {
        if (firstFrameNanos < 0) firstFrameNanos = frameNanos
        val now = (frameNanos - firstFrameNanos) / NANOS_PER_SECOND
        val dt = now - seconds
        seconds = now
        Tracing.section("ring step") {
            view.step(dt)
            ring.step(now, view.spin, view.tilt)
        }
        // The ring reads for a while, then folds by itself, but never while the hand is on it.
        if (ring.phase == RingPhase.Ring) {
            if (readingSince.isNaN()) readingSince = now
            if (now - readingSince >= READ_SECONDS && !held) ring.advance(now)
        } else {
            readingSince = Float.NaN
        }
        val next = captionFor(now)
        if (next != caption.intValue) caption.intValue = next
        fadeCaptions(next, dt.coerceAtLeast(0f))
        frame.longValue = frameNanos
    }

    /** The old caption fades out, then the new one fades in: they never overlap. */
    private fun fadeCaptions(shown: Int, dt: Float) {
        var othersShowing = false
        for (i in captionAlpha.indices) {
            if (i == shown) continue
            captionAlpha[i] = (captionAlpha[i] - dt / CAPTION_FADE_OUT).coerceAtLeast(0f)
            if (captionAlpha[i] > 0f) othersShowing = true
        }
        if (shown >= 0 && !othersShowing) {
            captionAlpha[shown] = (captionAlpha[shown] + dt / CAPTION_FADE_IN).coerceAtMost(1f)
        }
    }

    private fun captionFor(now: Float): Int =
        when (ring.phase) {
            RingPhase.Icon -> 0
            RingPhase.Burst -> -1
            RingPhase.Ring ->
                if (readingSince.isNaN()) 1
                else 1 + minOf(2, ((now - readingSince) / CAPTION_SECONDS).toInt())
            RingPhase.Collapse -> -1
            RingPhase.Logo -> if (ring.logoShare(now) >= 1f) 4 else -1
        }

    /**
     * Plays a whole show on a throwaway ring, as fast as it goes. Until the JIT has compiled the
     * particles' maths, a step of the burst takes 3 ms instead of 0.1 ms, which cost the first
     * fifteen frames of every first burst. Run it off the main thread while the icon waits.
     */
    fun warmUp() {
        Tracing.section("ring warm-up") {
            val rehearsal = ParticleRing(ring.count, seed = WARM_UP_SEED)
            var now = 0f
            rehearsal.step(now)
            rehearsal.advance(now)
            while (rehearsal.phase != RingPhase.Logo) {
                now += WARM_UP_STEP
                rehearsal.step(now, spin = now, tilt = 0.1f)
                rehearsal.launchedShare(now)
                if (rehearsal.phase == RingPhase.Ring) rehearsal.advance(now)
            }
        }
    }

    /** A click: the icon starts the reading, the finished logo plays it again. */
    fun onTap(position: Offset) {
        val at = toRing(position)
        val hit =
            when (ring.phase) {
                RingPhase.Icon -> ParticleRing.isOnIcon(at.x, at.y)
                RingPhase.Logo -> ParticleRing.isOnLogo(at.x, at.y)
                else -> false
            }
        if (hit) ring.advance(seconds)
    }

    /** Space or Enter: start, fold the ring early, or play again. */
    fun advance() {
        ring.advance(seconds)
    }

    fun press() {
        held = true
        view.press()
    }

    /** The pointer moved by [delta] stage pixels while pressed. */
    fun dragBy(delta: Offset) {
        view.dragBy(delta.x / unit, delta.y / unit)
    }

    fun release() {
        held = false
        view.release()
    }

    companion object {
        /** About as many as the original shows. */
        const val PARTICLE_COUNT = 6_000
        private const val NANOS_PER_SECOND = 1_000_000_000f

        /** Each reading caption's turn, in seconds. */
        private const val CAPTION_SECONDS = 1.7f
        private const val READ_SECONDS = CAPTION_SECONDS * 3f
        private const val CAPTION_FADE_OUT = 0.2f
        private const val CAPTION_FADE_IN = 0.4f
        private const val WARM_UP_SEED = 5
        private const val WARM_UP_STEP = 1f / 60f
    }
}

/**
 * Pixels per ring unit in a stage [width] × [height]: the ring's radius is 27% of the width or a
 * fifth of the height, whichever is smaller.
 */
internal fun ringUnit(width: Int, height: Int): Float = minOf(width * 0.27f, height * 0.2f)

/** The ring's centre: two fifths of the way down, so the icon sits in the lower third. */
internal fun ringCentre(width: Int, height: Int): Offset = Offset(width / 2f, height * 0.4f)
