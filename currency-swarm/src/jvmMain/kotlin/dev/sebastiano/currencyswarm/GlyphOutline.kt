package dev.sebastiano.currencyswarm

import kotlin.random.Random
import org.jetbrains.skia.Data
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Path
import org.jetbrains.skia.Typeface

/**
 * One sign's outline from Inter Bold, in glyph units: the font's cap height is 1, and the outline's
 * bounds are centred on the origin. Inter ships inside Jewel, so every machine draws the same
 * shapes, whatever fonts it has installed.
 */
class GlyphOutline
private constructor(
    private val path: Path,
    private val unitsPerPixel: Float,
    private val originX: Float,
    private val originY: Float,
    val width: Float,
    val height: Float,
) {
    /** Whether the point (`x`, `y`), in glyph units, is inside the outline. */
    fun contains(x: Float, y: Float): Boolean =
        path.contains(x / unitsPerPixel + originX, y / unitsPerPixel + originY)

    /** [count] points spread evenly at random over the outline's ink, as `x, y` pairs. */
    fun sample(count: Int, random: Random): FloatArray {
        val points = FloatArray(count * 2)
        var found = 0
        while (found < count) {
            val x = (random.nextFloat() - 0.5f) * width
            val y = (random.nextFloat() - 0.5f) * height
            if (contains(x, y)) {
                points[found * 2] = x
                points[found * 2 + 1] = y
                found++
            }
        }
        return points
    }

    companion object {
        private const val FONT_RESOURCE = "fonts/inter/Inter-Bold.ttf"
        private const val FONT_SIZE = 200f

        private val font: Font by lazy {
            val bytes =
                checkNotNull(
                        GlyphOutline::class.java.classLoader.getResourceAsStream(FONT_RESOURCE)
                    ) {
                        "$FONT_RESOURCE is missing: it comes with Jewel's standalone theme"
                    }
                    .use { it.readBytes() }
            val typeface: Typeface = FontMgr.default.makeFromData(Data.makeFromBytes(bytes))!!
            Font(typeface, FONT_SIZE)
        }

        /** The outline of [symbol], which must be a single sign Inter has. */
        fun of(symbol: String): GlyphOutline {
            val glyphs = font.getStringGlyphs(symbol)
            require(glyphs.size == 1 && glyphs[0] != 0.toShort()) {
                "Inter Bold cannot draw '$symbol'"
            }
            val path = checkNotNull(font.getPath(glyphs[0])) { "'$symbol' has no outline" }
            val bounds = path.bounds
            val unitsPerPixel = 1f / font.metrics.capHeight
            return GlyphOutline(
                path = path,
                unitsPerPixel = unitsPerPixel,
                originX = (bounds.left + bounds.right) / 2f,
                originY = (bounds.top + bounds.bottom) / 2f,
                width = bounds.width * unitsPerPixel,
                height = bounds.height * unitsPerPixel,
            )
        }
    }
}
