package dev.sebastiano.dotmatrixrecorder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DotGlyphTest {
    @Test
    fun parsesLitDimAndHiddenDots() {
        val glyph = DotGlyph.parse(" .#. ", "#...#", "#...#", "#...#", " ### ")
        assertEquals(1f, glyph.brightness(row = 0, col = 2))
        assertEquals(0f, glyph.brightness(row = 0, col = 1))
        assertEquals(1f, glyph.presence(row = 0, col = 1))
        assertEquals(0f, glyph.presence(row = 0, col = 0))
        assertEquals(0f, glyph.brightness(row = 0, col = 0))
    }

    @Test
    fun rejectsAnythingThatIsNotFiveByFive() {
        assertFailsWith<IllegalArgumentException> { DotGlyph.parse("#####") }
        assertFailsWith<IllegalArgumentException> {
            DotGlyph.parse("####", "#####", "#####", "#####", "#####")
        }
        assertFailsWith<IllegalArgumentException> {
            DotGlyph.parse("#x###", "#####", "#####", "#####", "#####")
        }
    }

    @Test
    fun countdownDigitsExistOnlyForThreeTwoOne() {
        for (digit in 1..3) DotGlyphs.digit(digit)
        assertFailsWith<IllegalArgumentException> { DotGlyphs.digit(0) }
        assertFailsWith<IllegalArgumentException> { DotGlyphs.digit(4) }
    }

    @Test
    fun redGlyphsKeepTheCornersHidden() {
        // The record icon is a round 21-dot grid. Anything drawn in it must fit that disc.
        val red = listOf(DotGlyphs.Record, DotGlyphs.Flash) + (1..3).map(DotGlyphs::digit)
        val corners = listOf(0 to 0, 0 to 4, 4 to 0, 4 to 4)
        for (glyph in red) {
            for ((row, col) in corners) assertEquals(0f, glyph.presence(row, col))
        }
    }
}
