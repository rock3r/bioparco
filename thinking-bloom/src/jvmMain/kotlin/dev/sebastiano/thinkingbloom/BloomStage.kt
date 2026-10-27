package dev.sebastiano.thinkingbloom

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import dev.sebastiano.bioparco.tracing.Tracing
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * The label, centred, blooming on a loop. The clock is read only while drawing, so a frame redraws
 * the stage without recomposing it.
 */
@Composable
internal fun BloomStage(word: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val base = JewelTheme.defaultTextStyle.copy(fontWeight = FontWeight.Medium)
        // Fit the label to the stage, in whole pixels so a resize does not rebuild it every step,
        // and never below a readable size: a very narrow stage clips the label instead.
        val fontPx =
            with(density) {
                val wanted = FONT_SIZE.toPx()
                val room = maxWidth.toPx() * FILL
                val natural =
                    measurer.measure(word + ".".repeat(DOTS), base.copy(fontSize = FONT_SIZE))
                floor(wanted * minOf(1f, room / natural.size.width.coerceAtLeast(1)))
                    .coerceAtLeast(MIN_FONT.toPx())
            }
        val style =
            with(density) {
                base.copy(
                    color = BloomPalette.ink,
                    fontSize = fontPx.toSp(),
                    letterSpacing = (LETTER_SPACING * fontPx).toSp(),
                )
            }
        val label =
            remember(word, style, density) { buildBloomLabel(word, style, measurer, density) }
        val sprites = remember { FlowerSprites() }
        val clock = remember { mutableFloatStateOf(0f) }
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { clock.floatValue = (it - start) / NANOS }
        }
        Spacer(
            Modifier.fillMaxSize().testTag(ThinkingBloomTags.STAGE).drawBehind {
                drawBloom(label, clock.floatValue, sprites)
            }
        )
    }
}

internal fun DrawScope.drawBloom(label: BloomLabel, seconds: Float, sprites: FlowerSprites) {
    val origin = Offset((size.width - label.width) / 2f, (size.height - label.height) / 2f)
    val front = BloomMath.front(seconds)
    val lifts = FloatArray(DOTS) { dotLift(label, it, front) }
    Tracing.section("ink") { drawInk(label, origin, front, lifts) }
    Tracing.section("blossoms") { drawBlossoms(label, origin, front, seconds, lifts, sprites) }
}

/** The plain black label, faded out where the blossoms are full. */
private fun DrawScope.drawInk(label: BloomLabel, origin: Offset, front: Float, lifts: FloatArray) {
    val stops =
        Array(INK_STOPS) {
            val x = it / (INK_STOPS - 1f)
            x to BloomPalette.ink.copy(alpha = BloomMath.ink(BloomMath.phase(x, front)))
        }
    // The brush works in the text's own pixels, which start at the label's left edge.
    drawText(
        label.word,
        brush = Brush.horizontalGradient(*stops, startX = 0f, endX = label.width),
        topLeft = origin,
    )
    for (i in 0 until DOTS) {
        val ink = BloomMath.ink(BloomMath.phase(dotCentre(label, i), front))
        if (ink <= 0f) continue
        drawText(
            label.dot,
            color = BloomPalette.ink.copy(alpha = ink),
            topLeft = origin + Offset(label.dotX[i], lifts[i]),
        )
    }
}

/**
 * Each open blossom, as its pre-drawn sprite at its current whole-pixel size, on whole pixels: a
 * straight copy in software. The wobble moves blossoms a pixel at a time, which reads as shimmer.
 */
private fun DrawScope.drawBlossoms(
    label: BloomLabel,
    origin: Offset,
    front: Float,
    seconds: Float,
    lifts: FloatArray,
    sprites: FlowerSprites,
) {
    val sway = label.unit * SWAY
    for (blossom in label.blossoms) {
        val phase = BloomMath.phase(blossom.x / label.width, front) + blossom.lag
        val diameter = (blossom.size * BloomMath.bloom(phase)).roundToInt()
        if (diameter < 1) continue
        val lift = if (blossom.glyph == IN_WORD) 0f else lifts[blossom.glyph]
        val wave = seconds * SWAY_RATE + blossom.sway
        val half = FlowerSprites.cell(diameter) / 2f
        val x = (origin.x + blossom.x + sway * cos(wave) - half).roundToInt()
        val y = (origin.y + blossom.y + lift + sway * sin(wave * SWAY_TWIST) - half).roundToInt()
        drawImage(
            sprites.sprite(blossom.look, blossom.turn, diameter),
            topLeft = Offset(x.toFloat(), y.toFloat()),
        )
    }
}

/** A dot hops as the band passes it; each dot meets the band a little later than the last. */
private fun dotLift(label: BloomLabel, index: Int, front: Float): Float {
    val phase = BloomMath.phase(dotCentre(label, index), front)
    if (phase <= 0f || phase >= 1f) return 0f
    // Bobs that die away: up, a smaller down, settled before the band leaves.
    return -label.unit * HOP * sin(phase * 2f * PI.toFloat() * HOPS) * (1f - phase)
}

/** Where a dot's middle sits, in label widths. */
private fun dotCentre(label: BloomLabel, index: Int): Float =
    (label.dotX[index] + label.dot.size.width / 2f) / label.width

private val FONT_SIZE = 112.sp
private val MIN_FONT = 12.sp
private const val FILL = 0.84f
private const val LETTER_SPACING = -0.01f
private const val NANOS = 1_000_000_000f
private const val INK_STOPS = 33
private const val SWAY = 0.012f
private const val SWAY_RATE = 2.6f
private const val SWAY_TWIST = 1.3f
private const val HOP = 0.07f
private const val HOPS = 1.5f
