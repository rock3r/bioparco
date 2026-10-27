package dev.sebastiano.holocard

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReliefTest {
    private val disc = Sdf { x, y -> hypot(x, y) - 0.8f }

    @Test
    fun theMiddleOfAShapeFacesTheViewer() {
        val relief = bakeRelief(disc, SIZE, puff = 0.45f)
        val (nx, ny, coverage) = relief.at(SIZE / 2, SIZE / 2)
        assertNear(128, nx, 2)
        assertNear(128, ny, 2)
        assertEquals(255, coverage)
    }

    @Test
    fun edgesLeanOutwards() {
        val relief = bakeRelief(disc, SIZE, puff = 0.45f)
        // Just inside the right and top rims. Image rows run downwards.
        val right = relief.at(SIZE / 2 + (0.78f * SIZE / 2).toInt(), SIZE / 2)
        assertTrue(right.first > 220, "the right rim should face right, got ${right.first}")
        assertNear(128, right.second, 4)

        val top = relief.at(SIZE / 2, SIZE / 2 - (0.78f * SIZE / 2).toInt())
        assertTrue(top.second < 36, "the top rim should face up, got ${top.second}")
        assertNear(128, top.first, 4)
    }

    @Test
    fun outsideTheShapeIsEmpty() {
        val relief = bakeRelief(disc, SIZE, puff = 0.45f)
        assertEquals(0, relief.at(2, 2).third)
        assertEquals(0, relief.at(SIZE - 3, SIZE / 2).third)
    }

    @Test
    fun theOutlineIsAntialiased() {
        val relief = bakeRelief(disc, SIZE, puff = 0.45f)
        val row = SIZE / 2
        val coverages = (SIZE / 2 until SIZE).map { relief.at(it, row).third }
        assertTrue(coverages.any { it in 1..254 }, "expected a soft edge, got $coverages")
    }

    @Test
    fun everyMascotFitsItsBoxAndFillsItsMiddle() {
        for (mascot in Mascot.entries) {
            val relief = bakeRelief(mascot.sdf, SIZE, puff = Mascot.PUFF)
            for (i in 0 until SIZE) {
                for (edge in listOf(relief.at(i, 0), relief.at(0, i), relief.at(i, SIZE - 1))) {
                    assertEquals(0, edge.third, "$mascot touches its box at $i")
                }
                assertEquals(0, relief.at(SIZE - 1, i).third, "$mascot touches its box at $i")
            }
            assertEquals(255, relief.at(SIZE / 2, SIZE / 2).third, "$mascot has a hole")
        }
    }

    @Test
    fun theBoundsWrapEveryCoveredPixel() {
        val relief =
            bakeRelief(Sdf { x, y -> hypot(x - 0.25f, y + 0.5f) - 0.2f }, SIZE, puff = 0.45f)
        val bounds = coverageBounds(relief, SIZE)
        for (row in 0 until SIZE) {
            for (column in 0 until SIZE) {
                val inside =
                    column in bounds.left until bounds.right &&
                        row in bounds.top until bounds.bottom
                if (!inside) assertEquals(0, relief.at(column, row).third, "missed $column, $row")
            }
        }
        // A disc 0.2 across a -1..1 box of 96 pixels is about 20 pixels wide.
        assertTrue(bounds.right - bounds.left in 19..23, "bounds are $bounds")
        assertTrue(bounds.bottom - bounds.top in 19..23, "bounds are $bounds")
    }

    @Test
    fun anEmptyReliefHasEmptyBounds() {
        val bounds = coverageBounds(bakeRelief(Sdf { _, _ -> 1f }, SIZE, puff = 0.45f), SIZE)
        assertEquals(0, bounds.right - bounds.left)
    }

    private fun assertNear(expected: Int, actual: Int, tolerance: Int) =
        assertTrue(
            abs(expected - actual) <= tolerance,
            "expected $expected ± $tolerance, got $actual",
        )

    /** The normal's x and y, and the coverage, at one pixel, each 0..255. */
    private fun ByteArray.at(x: Int, y: Int): Triple<Int, Int, Int> {
        val i = (y * SIZE + x) * 4
        return Triple(
            this[i].toInt() and 0xFF,
            this[i + 1].toInt() and 0xFF,
            this[i + 2].toInt() and 0xFF,
        )
    }

    private companion object {
        const val SIZE = 96
    }
}
