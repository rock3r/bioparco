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
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor

/** Time for a dot to cover about two thirds of the way to its new level. */
private const val DOT_EASE_MS = 55f
private const val DIM_ALPHA = 0.22f
private const val SETTLED = 0.003f

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

/** 3, 2, 1 drawn on the record lens while [state] counts down. */
fun countdownProgram(state: RecorderState, nowMs: () -> Long): DotProgram =
    DotProgram { brightness, presence ->
        DotGlyphs.digit(state.countdownDigit(nowMs()) ?: 1).copyInto(brightness, presence)
        true
    }

/** A soft band of light sweeping across the lens, left to right, while recording. */
fun recordingWaveProgram(nowMs: () -> Long): DotProgram = DotProgram { brightness, presence ->
    val seconds = nowMs() / 1_000f
    for (row in 0 until GRID) {
        for (col in 0 until GRID) {
            val index = row * GRID + col
            presence[index] = DotGlyphs.Record.presence(row, col)
            // The band leads with the middle row, so the front reads as a curve, not a wall.
            val phase = seconds * 0.8f - col * 0.16f + abs(row - 2) * 0.09f
            val x = (phase - floor(phase)) - 0.5f
            brightness[index] = exp(-(x * x) / 0.05f)
        }
    }
    true
}

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
fun DotMatrix(program: DotProgram, tint: Color, modifier: Modifier = Modifier) {
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
        val dim = tint.copy(alpha = tint.alpha * DIM_ALPHA)
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
fun DotMatrix(glyph: DotGlyph, tint: Color, modifier: Modifier = Modifier) {
    DotMatrix(program = remember(glyph) { glyph.asProgram() }, tint = tint, modifier = modifier)
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
