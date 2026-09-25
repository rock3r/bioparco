package dev.sebastiano.achievementbadge

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** A shockwave ring: its radius as a fraction of the star radius, and its opacity. */
internal data class Ring(val radius: Float, val alpha: Float)

/**
 * Everything the badge needs to draw one frame. [badgeFrame] computes it from the seconds since the
 * celebration started, so a replay is just a clock reset.
 */
internal data class BadgeFrame(
    val ghostScale: Float,
    val ghostAlpha: Float,
    val backScale: Float,
    val backRotation: Float,
    val frontScale: Float,
    val frontRotation: Float,
    val trophyScale: Float,
    val trophyOffset: Float,
    val trophyAlpha: Float,
    val handleScale: Float,
    val fanReveal: Float,
    val sparkleScale: Float,
    val goldRing: Ring,
    val pinkRing: Ring,
    val glowAlpha: Float,
    val raysAlpha: Float,
    val raysLength: Float,
    val raysRotation: Float,
    val neonTop: Float,
    val neonLow: Float,
    /** −1 when no sweep is running, else 0–1 across the badge. */
    val shine: Float,
    val floatOffset: Float,
    /** Seconds since the confetti burst, negative before it. */
    val confettiSeconds: Float,
)

/**
 * Timings measured frame by frame on the source video (60 fps). Beats, in order: an empty slot
 * fades in, the two stars spin and pop into it, the trophy rises, a ring bursts and the light fan
 * opens, confetti flies, the neon line draws itself, a shine sweeps across, and then it idles.
 */
internal object BadgeTimeline {
    const val GHOST_START = 0.26f
    const val BACK_START = 0.36f
    const val FRONT_START = 0.38f
    const val TROPHY_START = 0.66f
    const val HANDLES_START = 0.86f
    const val GOLD_RING_START = 0.95f
    const val FAN_START = 1.0f
    const val RAYS_START = 1.02f
    const val BURST = 1.05f
    const val PINK_RING_START = 1.08f
    const val NEON_START = 1.45f
    const val NEON_LOW_START = 1.72f
    const val SHINE_START = 1.8f
    const val SHINE_DURATION = 0.34f
    const val FLOAT_START = 2.0f

    /** After this, only the idle loops move: rays, float, sparkle pulse and twinkles. */
    const val SETTLED = 2.4f

    const val RAYS_DEGREES_PER_SECOND = 6f
    const val FLOAT_AMPLITUDE = 8f
    const val FLOAT_PERIOD = 3.2f
}

internal fun badgeFrame(seconds: Float): BadgeFrame {
    val t = seconds
    with(BadgeTimeline) {
        val ghostIn = progress(t, GHOST_START, 0.14f)
        val raysIn = progress(t, RAYS_START, 0.4f)
        val floatIn = progress(t, FLOAT_START, 0.8f)
        return BadgeFrame(
            ghostScale = 0.45f + 0.55f * easeOutBack(ghostIn, 1.2f),
            ghostAlpha = ghostIn * (1f - progress(t, 0.7f, 0.3f)),
            backScale = settle(t - BACK_START, from = 0.3f),
            backRotation = -75f * (1f - easeOutCubic(progress(t, BACK_START, 0.4f))),
            frontScale = settle(t - FRONT_START, from = 0.2f),
            frontRotation = -40f * (1f - easeOutCubic(progress(t, FRONT_START, 0.4f))),
            trophyScale = lerp(0.55f, 1f, easeOutBack(progress(t, TROPHY_START, 0.32f), 1.6f)),
            trophyOffset = 150f * (1f - easeOutBack(progress(t, TROPHY_START, 0.34f), 1.2f)),
            trophyAlpha = progress(t, TROPHY_START, 0.06f),
            handleScale = easeOutBack(progress(t, HANDLES_START, 0.18f), 2.2f),
            fanReveal = easeOutCubic(progress(t, FAN_START, 0.2f)),
            sparkleScale = sparkle(t),
            goldRing = ring(t, GOLD_RING_START, 0.75f, from = 1.0f, to = 1.64f, peak = 0.95f),
            pinkRing = ring(t, PINK_RING_START, 0.7f, from = 0.98f, to = 1.56f, peak = 0.8f),
            glowAlpha =
                progress(t, GOLD_RING_START, 0.12f) *
                    (1f - 0.7f * easeInOutSine(progress(t, 1.2f, 0.9f))),
            raysAlpha = raysIn * (0.9f + 0.1f * sin(2f * PI.toFloat() * (t - RAYS_START) / 2.4f)),
            raysLength = lerp(0.55f, 1f, easeOutCubic(raysIn)),
            raysRotation = if (t < RAYS_START) 0f else (t - RAYS_START) * RAYS_DEGREES_PER_SECOND,
            neonTop = easeInOutSine(progress(t, NEON_START, 0.3f)),
            neonLow = easeInOutSine(progress(t, NEON_LOW_START, 0.18f)),
            shine =
                if (t in SHINE_START..(SHINE_START + SHINE_DURATION)) {
                    easeInOutSine((t - SHINE_START) / SHINE_DURATION)
                } else {
                    -1f
                },
            floatOffset =
                -FLOAT_AMPLITUDE *
                    floatIn *
                    sin(2f * PI.toFloat() * (t - FLOAT_START) / FLOAT_PERIOD),
            confettiSeconds = t - BURST,
        )
    }
}

/** 0 before [start], 1 after [start] + [duration], linear in between. */
internal fun progress(t: Float, start: Float, duration: Float): Float =
    ((t - start) / duration).coerceIn(0f, 1f)

internal fun lerp(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

internal fun easeOutCubic(x: Float): Float {
    val inv = 1f - x
    return 1f - inv * inv * inv
}

internal fun easeInOutCubic(x: Float): Float =
    if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).let { it * it * it } / 2f

internal fun easeInOutSine(x: Float): Float = (1f - cos(PI.toFloat() * x)) / 2f

internal fun easeOutBack(x: Float, overshoot: Float = 1.70158f): Float {
    if (x <= 0f) return 0f
    if (x >= 1f) return 1f
    val c3 = overshoot + 1f
    val y = x - 1f
    return 1f + c3 * y * y * y + overshoot * y * y
}

/**
 * A springy pop from [from] to 1: an underdamped step response that overshoots by about 7% and is
 * at rest (exactly 1) after [SPRING_DURATION] seconds.
 */
private fun settle(elapsed: Float, from: Float): Float {
    if (elapsed <= 0f) return 0f
    if (elapsed >= SPRING_DURATION) return 1f
    val response = 1f - exp(-SPRING_DECAY * elapsed) * cos(SPRING_FREQUENCY * elapsed)
    // Fade the tail into exactly 1 so the idle state has no residual wobble.
    val tail = progress(elapsed, SPRING_DURATION - 0.3f, 0.3f)
    return lerp(lerp(from, 1f, response), 1f, tail)
}

private const val SPRING_DURATION = 1.1f
private const val SPRING_DECAY = 9f
private const val SPRING_FREQUENCY = 12f

private fun sparkle(t: Float): Float {
    val pop = easeOutBack(progress(t, BadgeTimeline.PINK_RING_START, 0.28f), 2.4f)
    val pulse = 1f + 0.08f * sin(2f * PI.toFloat() * (t - BadgeTimeline.SETTLED) / 1.6f)
    return if (t < BadgeTimeline.SETTLED) pop else pulse
}

private fun ring(
    t: Float,
    start: Float,
    duration: Float,
    from: Float,
    to: Float,
    peak: Float,
): Ring {
    val p = progress(t, start, duration)
    if (p <= 0f || p >= 1f) return Ring(from, 0f)
    val fadeIn = progress(p, 0f, 0.12f)
    val fadeOut = 1f - easeInOutSine(progress(p, 0.35f, 0.65f))
    return Ring(lerp(from, to, easeInOutCubic(p)), peak * fadeIn * fadeOut)
}
