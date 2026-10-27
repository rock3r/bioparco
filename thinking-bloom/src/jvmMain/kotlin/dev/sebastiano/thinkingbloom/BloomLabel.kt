package dev.sebastiano.thinkingbloom

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.sebastiano.bioparco.tracing.Tracing
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.random.Random

/** The three trailing dots, drawn apart from the word so they can bob on their own. */
internal const val DOTS = 3

/** Where a spot's blossom belongs: the word, or one of the dots. */
internal const val IN_WORD = -1

/**
 * A word and its dots, laid out once, with the blossoms scattered over their glyphs. Positions are
 * in pixels from the label's top left; [unit] is the font size in pixels.
 */
internal class BloomLabel(
    val word: TextLayoutResult,
    val dot: TextLayoutResult,
    val dotX: FloatArray,
    val width: Float,
    val height: Float,
    val unit: Float,
    val blossoms: Array<Blossom>,
)

/**
 * One flower. [size] is its full diameter in pixels; [lag] shifts when the band reaches it, so the
 * front is ragged rather than a straight line; [look] picks its colours from [BloomPalette] and
 * [turn] which way up its petals sit; [sway] offsets its wobble.
 */
internal class Blossom(
    val x: Float,
    val y: Float,
    val glyph: Int,
    val size: Float,
    val look: Int,
    val turn: Int,
    val lag: Float,
    val sway: Float,
)

internal fun buildBloomLabel(
    text: String,
    style: TextStyle,
    measurer: TextMeasurer,
    density: Density,
): BloomLabel =
    Tracing.section("bloom label") {
        val word = measurer.measure(text, style)
        val full = measurer.measure(text + ".".repeat(DOTS), style)
        val dot = measurer.measure(".", style)
        val dotX = FloatArray(DOTS) { full.getHorizontalPosition(text.length + it, true) }
        val width = full.size.width.toFloat()
        val height = full.size.height.toFloat()
        val unit = with(density) { style.fontSize.toPx() }
        val mask = glyphMask(word, dot, dotX, width, height, density)
        val random = Random(text.hashCode())
        val spots =
            scatter(mask.width, mask.height, unit * SPACING, random) { x, y -> mask.covers(x, y) }
        val blossoms =
            spots
                .map { spot ->
                    val glyph = dotX.indexOfLast { spot.x >= it }.takeIf { it >= 0 } ?: IN_WORD
                    blossomAt(spot, glyph, unit, random)
                }
                .shuffled(random)
                .toTypedArray()
        BloomLabel(word, dot, dotX, width, height, unit, blossoms)
    }

private fun blossomAt(spot: Spot, glyph: Int, unit: Float, random: Random): Blossom {
    // Mostly mid-sized, a few big ones; the dots' few blossoms run larger, as in the reference.
    val grow = if (glyph == IN_WORD) 1f else DOT_BLOSSOM_BOOST
    val size = unit * (MIN_SIZE + (MAX_SIZE - MIN_SIZE) * random.nextFloat().pow(1.6f)) * grow
    return Blossom(
        x = spot.x,
        y = spot.y,
        glyph = glyph,
        size = size,
        look = random.nextInt(BloomPalette.looks.size),
        turn = random.nextInt(FlowerSprites.TURNS),
        lag = (random.nextFloat() * 2f - 1f) * MAX_LAG,
        sway = random.nextFloat() * 2f * PI.toFloat(),
    )
}

/** Which pixels the plain label inks, so blossoms only grow out of the glyphs. */
private class GlyphMask(val width: Int, val height: Int, private val alpha: ByteArray) {
    fun covers(x: Float, y: Float): Boolean {
        val column = x.toInt().coerceIn(0, width - 1)
        val row = y.toInt().coerceIn(0, height - 1)
        return alpha[row * width + column].toInt() and 0xFF >= COVERED
    }
}

private fun glyphMask(
    word: TextLayoutResult,
    dot: TextLayoutResult,
    dotX: FloatArray,
    width: Float,
    height: Float,
    density: Density,
): GlyphMask {
    val w = ceil(width).toInt().coerceAtLeast(1)
    val h = ceil(height).toInt().coerceAtLeast(1)
    val bitmap = ImageBitmap(w, h)
    CanvasDrawScope().draw(
        density,
        LayoutDirection.Ltr,
        Canvas(bitmap),
        Size(w.toFloat(), h.toFloat()),
    ) {
        drawText(word, color = Color.Black)
        for (x in dotX) drawText(dot, color = Color.Black, topLeft = Offset(x, 0f))
    }
    val pixels = IntArray(w * h)
    bitmap.readPixels(pixels)
    return GlyphMask(w, h, ByteArray(pixels.size) { (pixels[it] ushr 24).toByte() })
}

/** Grid spacing between blossoms, in font sizes: two or three across a stroke. */
private const val SPACING = 0.035f

private const val MIN_SIZE = 0.12f
private const val MAX_SIZE = 0.26f
private const val DOT_BLOSSOM_BOOST = 1.25f
private const val MAX_LAG = 0.06f
private const val COVERED = 128
