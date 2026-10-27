package dev.sebastiano.wheelpicker

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.skiaPaint
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import kotlin.math.ceil
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter

/**
 * Every readout glyph, laid out once and drawn into small images at [LEVELS] + 1 steps of blur,
 * from sharp to [maxBlurPx]. A rolling glyph then costs an image copy a frame instead of a text
 * layout and a live blur, which in software is several milliseconds a frame.
 */
internal class ReadoutGlyphs(
    private val measurer: TextMeasurer,
    private val style: TextStyle,
    private val density: Density,
    private val maxBlurPx: Float,
) {
    /** Clear space around each glyph, so the widest blur is not cut off. */
    val padding: Int = ceil(maxBlurPx * BLUR_REACH).toInt()

    private val glyphs = HashMap<Char, Glyph>()

    class Glyph(val width: Int, val height: Int, val steps: List<ImageBitmap>)

    fun warm(chars: String) {
        chars.forEach(::get)
    }

    /** The glyph for [char]; built on first use, so warm the ones the readout will show. */
    operator fun get(char: Char): Glyph = glyphs.getOrPut(char) { build(char) }

    private fun build(char: Char): Glyph {
        val layout = measurer.measure(char.toString(), style)
        val width = layout.size.width
        val height = layout.size.height
        val sharp = textImage(layout, density, padding)
        val steps =
            listOf(sharp) + (1..LEVELS).map { level -> blurred(sharp, maxBlurPx * level / LEVELS) }
        return Glyph(width, height, steps)
    }

    private fun blurred(sharp: ImageBitmap, radius: Float): ImageBitmap {
        val image = ImageBitmap(sharp.width, sharp.height)
        val sigma = radius * SIGMA_PER_RADIUS + SIGMA_OFFSET
        val paint = Paint()
        paint.skiaPaint.imageFilter = ImageFilter.makeBlur(sigma, sigma, FilterTileMode.DECAL)
        Canvas(image).drawImage(sharp, Offset.Zero, paint)
        return image
    }

    companion object {
        /** Blur steps past sharp. Neighbouring steps cross-fade, so eight read as continuous. */
        const val LEVELS = 8

        // Compose's own radius-to-sigma conversion for BlurEffect, so the look matches it.
        private const val SIGMA_PER_RADIUS = 0.57735f
        private const val SIGMA_OFFSET = 0.5f
        private const val BLUR_REACH = 1.5f
    }
}
