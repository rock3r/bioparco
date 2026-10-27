package dev.sebastiano.departureboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DotFontTest {
    @Test
    fun `every character the board shows has a glyph`() {
        for (char in ('A'..'Z') + ('0'..'9') + listOf(':', '-', '.', '\'', '/', '&')) {
            assertTrue(DotFont.has(char), "no glyph for '$char'")
            assertTrue(DotFont.glyph(char) != 0L, "'$char' is blank")
        }
    }

    @Test
    fun `a blank lights nothing, and unknown characters are blanks`() {
        assertEquals(0L, DotFont.glyph(' '))
        assertEquals(0L, DotFont.glyph('™'))
    }

    @Test
    fun `glyphs stay inside the five by seven cell`() {
        for (char in DotFont.CHARACTERS) {
            assertEquals(0L, DotFont.glyph(char) ushr (DotFont.COLUMNS * DotFont.ROWS), "$char")
        }
    }

    @Test
    fun `lit reads row by row from the top left`() {
        // L is the left column and the bottom row.
        assertTrue(DotFont.lit('L', row = 0, column = 0))
        assertTrue(!DotFont.lit('L', row = 0, column = 1))
        assertTrue(DotFont.lit('L', row = 6, column = 4))
    }

    @Test
    fun `only the letter O and the digit 0 share a shape`() {
        val shapes = DotFont.CHARACTERS.filter { it != ' ' }.groupBy { DotFont.glyph(it) }
        val shared = shapes.values.filter { it.size > 1 }
        assertEquals(listOf(listOf('O', '0')), shared.map { it.sorted().reversed() })
    }

    @Test
    fun `the scramble alphabet is letters and digits`() {
        assertEquals((('A'..'Z') + ('0'..'9')).joinToString(""), DotFont.SCRAMBLE)
    }
}
