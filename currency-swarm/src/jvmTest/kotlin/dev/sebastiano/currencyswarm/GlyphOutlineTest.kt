package dev.sebastiano.currencyswarm

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlyphOutlineTest {
    @Test
    fun theBundledFontDrawsEverySign() {
        for (currency in Currencies) {
            val outline = GlyphOutline.of(currency.symbol)
            assertTrue(outline.width > 0.3f, "${currency.symbol} is ${outline.width} wide")
            assertTrue(outline.height > 0.8f, "${currency.symbol} is ${outline.height} tall")
        }
    }

    @Test
    fun aCapitalHeightSignIsOneUnitTall() {
        // ¥ sits on the baseline and reaches cap height, so it is exactly one unit tall.
        assertEquals(1f, GlyphOutline.of("¥").height, 0.03f)
    }

    @Test
    fun samplesFallInsideTheGlyph() {
        val outline = GlyphOutline.of("₩")
        val points = outline.sample(800, Random(9))
        assertEquals(1_600, points.size)
        for (i in 0 until 800) {
            assertTrue(outline.contains(points[i * 2], points[i * 2 + 1]), "sample $i is outside")
        }
    }

    @Test
    fun samplesCoverTheWholeGlyph() {
        val outline = GlyphOutline.of("$")
        val points = outline.sample(2_000, Random(10))
        val xs = points.filterIndexed { i, _ -> i % 2 == 0 }
        val ys = points.filterIndexed { i, _ -> i % 2 == 1 }
        // The outline is centred on the origin.
        val left = -outline.width / 2f
        val top = -outline.height / 2f
        assertTrue(abs(xs.min() - left) < 0.05f, "nothing near the left edge")
        assertTrue(abs(ys.min() - top) < 0.05f, "nothing near the top")
        assertTrue(abs(xs.max() - (left + outline.width)) < 0.05f, "nothing near the right edge")
        assertTrue(abs(ys.max() - (top + outline.height)) < 0.05f, "nothing near the bottom")
    }
}
