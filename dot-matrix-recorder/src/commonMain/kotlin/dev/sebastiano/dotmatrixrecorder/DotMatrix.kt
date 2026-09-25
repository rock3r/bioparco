package dev.sebastiano.dotmatrixrecorder

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/** Time for a dot to cover about two thirds of the way to its new level (~100 ms to settle). */
private const val DOT_EASE_MS = 35f
internal const val DIM_ALPHA = 0.22f
private const val SETTLED = 0.003f

// The recording stripes, measured frame by frame on the reference. Units are dot columns.
private const val STRIPE_SPEED = 6.6f // columns per second
private const val STRIPE_PERIOD = 7.7f // from one band to the next
private const val STRIPE_WIDTH = 3.5f
private const val STRIPE_SKEW = 0.33f // each lower row runs this far ahead of the one above
private const val STRIPE_EDGE = 0.35f
private const val STRIPE_FLOOR = 0.1f // unlit dots glow a little brighter while recording

/** How lit a dot is when the band's leading edge is [ahead] columns past it. */
private fun band(ahead: Float): Float =
    smoothstep(-STRIPE_EDGE, STRIPE_EDGE, ahead) *
        (1f - smoothstep(STRIPE_WIDTH - STRIPE_EDGE, STRIPE_WIDTH + STRIPE_EDGE, ahead))

private fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Where a dot sits along the stripe direction: lower rows come first. */
private fun stripeCoordinate(row: Int, col: Int): Float = col - STRIPE_SKEW * row

/**
 * Something that paints a 5×5 target into [brightness] and [presence] (25 slots each).
 *
 * Returns `true` while the target itself keeps moving, so the matrix keeps asking for frames.
 */
@Stable
fun interface DotProgram {
    fun render(brightness: FloatArray, presence: FloatArray): Boolean
}

fun DotGlyph.asProgram(): DotProgram = DotProgram { brightness, presence ->
    copyInto(brightness, presence)
    false
}

internal fun DotGlyph.copyInto(brightness: FloatArray, presence: FloatArray) {
    for (row in 0 until GRID) {
        for (col in 0 until GRID) {
            brightness[row * GRID + col] = brightness(row, col)
            presence[row * GRID + col] = presence(row, col)
        }
    }
}

/** 3, 2, 1 drawn on the record lens while [state] counts down, then a flash of every dot. */
fun countdownProgram(state: RecorderState, nowMs: () -> Long): DotProgram =
    DotProgram { brightness, presence ->
        val digit = state.countdownDigit(nowMs())
        (if (digit == null) DotGlyphs.Flash else DotGlyphs.digit(digit)).copyInto(
            brightness,
            presence,
        )
        true
    }

/**
 * Diagonal stripes scrolling left to right across the lens while recording. The first stripe enters
 * from the left at [startedAtMs], straight after the countdown flash.
 */
fun recordingStripesProgram(startedAtMs: Long, nowMs: () -> Long): DotProgram =
    DotProgram { brightness, presence ->
        val front = FIRST_STRIPE_COORDINATE + (nowMs() - startedAtMs) / 1_000f * STRIPE_SPEED
        for (row in 0 until GRID) {
            for (col in 0 until GRID) {
                val index = row * GRID + col
                presence[index] = DotGlyphs.Record.presence(row, col)
                val ahead = (front - stripeCoordinate(row, col)).mod(STRIPE_PERIOD)
                brightness[index] = maxOf(STRIPE_FLOOR, band(ahead))
            }
        }
        true
    }

/** One stripe crossing the lens when Record is hovered, then back to the resting lens. */
fun hoverSweepProgram(startedAtMs: Long, nowMs: () -> Long): DotProgram =
    DotProgram { brightness, presence ->
        val front = FIRST_STRIPE_COORDINATE + (nowMs() - startedAtMs) / 1_000f * STRIPE_SPEED
        if (front - STRIPE_WIDTH - STRIPE_EDGE > GRID - 1) {
            DotGlyphs.Record.copyInto(brightness, presence)
            return@DotProgram false
        }
        for (row in 0 until GRID) {
            for (col in 0 until GRID) {
                val index = row * GRID + col
                presence[index] = DotGlyphs.Record.presence(row, col)
                brightness[index] = band(front - stripeCoordinate(row, col))
            }
        }
        true
    }

private val FIRST_STRIPE_COORDINATE = stripeCoordinate(row = GRID - 1, col = 0) - STRIPE_EDGE

/** The resting lens. Its dim ring shimmers very slightly, like the reference. */
fun restingLensProgram(nowMs: () -> Long): DotProgram = DotProgram { brightness, presence ->
    DotGlyphs.Record.copyInto(brightness, presence)
    val seconds = nowMs() / 1_000f
    for (index in brightness.indices) {
        if (brightness[index] == 0f && presence[index] > 0f) {
            // A slow, per-dot twinkle between the dim level and a touch above it.
            val phase = (index * 2.399f) % (2f * PI.toFloat())
            brightness[index] = SHIMMER * (0.5f + 0.5f * sin(seconds * 1.7f + phase))
        }
    }
    true
}

private const val SHIMMER = 0.07f

/** The screenshot frame snapping shut and open again after [shotAtMs]. */
fun shutterProgram(shotAtMs: Long, nowMs: () -> Long): DotProgram =
    DotProgram { brightness, presence ->
        val age = nowMs() - shotAtMs
        val glyph =
            when {
                age < 0 -> DotGlyphs.Screenshot
                age < 90 -> DotGlyphs.Shutter
                age < 200 -> ShutterCore
                else -> DotGlyphs.Screenshot
            }
        glyph.copyInto(brightness, presence)
        age < 200
    }

private val ShutterCore = DotGlyph.parse(".....", ".....", "..#..", ".....", ".....")

/**
 * A 5×5 dot icon. Every dot eases on its own towards the level [program] asks for, so glyph swaps
 * dissolve instead of cutting.
 *
 * The frame loop only writes a draw-phase tick; nothing here recomposes per frame.
 */
@Composable
fun DotMatrix(
    program: DotProgram,
    tint: Color,
    modifier: Modifier = Modifier,
    dimAlpha: Float = DIM_ALPHA,
) {
    val levels = remember { DotLevels() }
    val tick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(program) {
        var lastNanos = -1L
        while (true) {
            val nanos = withFrameNanos { it }
            val dtMs = if (lastNanos < 0) 16f else (nanos - lastNanos) / 1_000_000f
            lastNanos = nanos
            val moving = program.render(levels.targetBrightness, levels.targetPresence)
            val settled = levels.step(dtMs, snap = !levels.initialised)
            tick.longValue = nanos
            if (!moving && settled) break
        }
    }
    Canvas(modifier) {
        tick.longValue // Redraw whenever the loop advances.
        val pitch = size.minDimension / GRID
        val radius = pitch * 0.34f
        val origin = Offset((size.width - pitch * GRID) / 2f, (size.height - pitch * GRID) / 2f)
        val dim = tint.copy(alpha = tint.alpha * dimAlpha)
        for (row in 0 until GRID) {
            for (col in 0 until GRID) {
                val index = row * GRID + col
                val shown = levels.presence[index]
                if (shown <= 0.01f) continue
                val lit = levels.brightness[index]
                val center =
                    Offset(origin.x + pitch * (col + 0.5f), origin.y + pitch * (row + 0.5f))
                drawCircle(
                    color = lerp(dim, tint, lit),
                    radius = radius,
                    center = center,
                    alpha = shown,
                )
            }
        }
    }
}

@Composable
fun DotMatrix(
    glyph: DotGlyph,
    tint: Color,
    modifier: Modifier = Modifier,
    dimAlpha: Float = DIM_ALPHA,
) {
    DotMatrix(
        program = remember(glyph) { glyph.asProgram() },
        tint = tint,
        modifier = modifier,
        dimAlpha = dimAlpha,
    )
}

private class DotLevels {
    val brightness = FloatArray(GRID * GRID)
    val presence = FloatArray(GRID * GRID)
    val targetBrightness = FloatArray(GRID * GRID)
    val targetPresence = FloatArray(GRID * GRID)
    var initialised = false
        private set

    /** Eases every dot towards its target. Returns `true` once nothing is visibly moving. */
    fun step(dtMs: Float, snap: Boolean): Boolean {
        val keep = if (snap) 0f else exp(-dtMs / DOT_EASE_MS)
        var settled = true
        for (i in brightness.indices) {
            brightness[i] = targetBrightness[i] + (brightness[i] - targetBrightness[i]) * keep
            presence[i] = targetPresence[i] + (presence[i] - targetPresence[i]) * keep
            if (
                abs(brightness[i] - targetBrightness[i]) > SETTLED ||
                    abs(presence[i] - targetPresence[i]) > SETTLED
            ) {
                settled = false
            }
        }
        initialised = true
        return settled
    }
}

internal val IconSize = 22.dp
