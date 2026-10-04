package dev.sebastiano.hairline

import kotlin.test.Test
import kotlin.test.assertEquals

/** The original stylesheet's cascade: zero specificity everywhere, so the later rule wins. */
class StyleTest {
    private fun paint(cls: String, circle: Boolean = false, inGhost: Boolean = false) =
        paintOf(ClassList(cls), circle, inGhost)

    @Test
    fun plainShapesArePlatesWithAMidStroke() {
        assertEquals(Paint(Ink.Plate, Ink.Mid, dashed = false, fillTransitions = false), paint(""))
    }

    @Test
    fun laterStrokeRulesWin() {
        assertEquals(Ink.Edge, paint("sil").stroke)
        assertEquals(Ink.Hi, paint("sil hi").stroke)
        assertEquals(Ink.Lo, paint("hi lo").stroke)
    }

    @Test
    fun noFillAndNoStroke() {
        assertEquals(null, paint("nf lo").fill)
        assertEquals(Ink.Lo, paint("nf lo").stroke)
        assertEquals(Ink.Plate, paint("fo").fill)
        assertEquals(null, paint("fo").stroke)
        assertEquals(true, paint("nf dash").dashed)
    }

    @Test
    fun dotsAreFilledAndTheirFillEases() {
        assertEquals(
            Paint(Ink.Hi, null, dashed = false, fillTransitions = true),
            paint("dot", circle = true),
        )
        assertEquals(Ink.Edge, paint("dot m").fill)
        assertEquals(Ink.Lo, paint("dot off").fill)
        // .dot.off comes after .dot.m in the sheet
        assertEquals(Ink.Lo, paint("dot m off").fill)
        assertEquals(null, paint("dot hi").stroke)
    }

    @Test
    fun reflectionsAreBareMidStrokes() {
        assertEquals(
            Paint(null, Ink.Mid, dashed = false, fillTransitions = false),
            paint("sil", inGhost = true),
        )
    }
}
