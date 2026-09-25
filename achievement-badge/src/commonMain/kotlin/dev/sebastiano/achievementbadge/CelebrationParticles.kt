package dev.sebastiano.achievementbadge

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

internal enum class ConfettiShape {
    Dash,
    Dot,
    Triangle,
}

internal data class ConfettiState(val position: Offset, val rotation: Float, val alpha: Float) {
    fun distanceFromCenter(): Float = (position - BadgeArt.CENTER).getDistance()
}

/** One scrap of confetti. It leaves the badge edge along [angle], slows down, drifts, and fades. */
internal data class ConfettiPiece(
    val angle: Float,
    val spawnRadius: Float,
    val travel: Float,
    val spin: Float,
    val shape: ConfettiShape,
    val colorIndex: Int,
    val size: Float,
) {
    fun stateAt(seconds: Float): ConfettiState {
        val t = seconds.coerceAtLeast(0f)
        val distance = spawnRadius + travel * (1f - exp(-Confetti.DRAG * t))
        val position =
            Offset(
                BadgeArt.CENTER.x + distance * cos(angle),
                BadgeArt.CENTER.y + distance * sin(angle) + Confetti.GRAVITY * t * t / 2f,
            )
        val fade = 1f - progress(t, Confetti.FADE_START, Confetti.LIFETIME - Confetti.FADE_START)
        val popIn = progress(t, 0f, 0.08f)
        return ConfettiState(position, angle * RADIANS_TO_DEGREES_F + spin * t, fade * popIn)
    }
}

internal object Confetti {
    const val LIFETIME = 2.4f
    const val FADE_START = 1.1f
    const val DRAG = 3.2f
    const val GRAVITY = 70f
    const val COUNT = 60

    /** Pink, yellow, purple, orange, lilac: the badge's own colours. */
    const val COLORS = 5
}

/** The same scatter on every replay, so recordings are reproducible. */
internal fun confettiPieces(seed: Int = 2103473): List<ConfettiPiece> {
    val random = Random(seed)
    return List(Confetti.COUNT) { index ->
        val angle = (index + random.nextFloat() * 0.8f) / Confetti.COUNT * 2f * PI.toFloat()
        val shape =
            when (random.nextInt(10)) {
                in 0..5 -> ConfettiShape.Dash
                in 6..8 -> ConfettiShape.Dot
                else -> ConfettiShape.Triangle
            }
        ConfettiPiece(
            angle = angle,
            spawnRadius = BadgeArt.STAR_RADIUS * (0.5f + 0.3f * random.nextFloat()),
            travel = BadgeArt.STAR_RADIUS * (0.55f + 0.75f * random.nextFloat()),
            spin = (random.nextFloat() - 0.5f) * 540f,
            shape = shape,
            colorIndex = random.nextInt(Confetti.COLORS),
            size = 0.8f + 0.5f * random.nextFloat(),
        )
    }
}

/** A small four-point star that blinks somewhere around the badge. */
internal data class Twinkle(
    val position: Offset,
    val size: Float,
    val scale: Float,
    val colorIndex: Int,
)

private const val TWINKLE_START = 1.1f
private const val TWINKLE_EVERY = 0.28f
private const val TWINKLE_LIFE = 0.9f
private const val RADIANS_TO_DEGREES_F = (180.0 / PI).toFloat()

/** Twinkles alive at [seconds]: a new one every [TWINKLE_EVERY] seconds, each living for 0.9 s. */
internal fun twinklesAt(seconds: Float): List<Twinkle> {
    if (seconds < TWINKLE_START) return emptyList()
    val newest = ((seconds - TWINKLE_START) / TWINKLE_EVERY).toInt()
    val oldest = (newest - (TWINKLE_LIFE / TWINKLE_EVERY).toInt() - 1).coerceAtLeast(0)
    return (oldest..newest).mapNotNull { slot ->
        val age = seconds - (TWINKLE_START + slot * TWINKLE_EVERY)
        if (age < 0f || age >= TWINKLE_LIFE) return@mapNotNull null
        val random = Random(slot * 7919 + 13)
        val angle = random.nextFloat() * 2f * PI.toFloat()
        val radius = BadgeArt.STAR_RADIUS * (1.05f + 0.55f * random.nextFloat())
        Twinkle(
            position =
                Offset(
                    BadgeArt.CENTER.x + radius * cos(angle),
                    BadgeArt.CENTER.y + radius * sin(angle),
                ),
            size = 9f + 12f * random.nextFloat(),
            scale = sin(PI.toFloat() * age / TWINKLE_LIFE).coerceIn(0f, 1f),
            colorIndex = random.nextInt(3),
        )
    }
}
