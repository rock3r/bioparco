package dev.sebastiano.peelsticker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PeelFoldTest {
    /** A 100 × 100 square at the origin. */
    private val square = { dx: Float, dy: Float -> maxOf(0f, dx * 100f) + maxOf(0f, dy * 100f) }

    @Test
    fun `no drag, no fold`() {
        assertNull(peelFold(100f, 50f, 100f, 50f, square, size = 100f))
    }

    @Test
    fun `a grabbed edge follows the pointer`() {
        val fold = assertNotNull(peelFold(100f, 50f, 60f, 50f, square, size = 100f))
        assertEquals(1f, fold.dirX, EPSILON)
        assertEquals(0f, fold.dirY, EPSILON)
        val landing = fold.axisX + fold.dirX * fold.curve.project(fold.depth)
        assertEquals(60f, landing, 1e-2f)
    }

    @Test
    fun `an inner grab peels the far edge along the drag`() {
        val fold = assertNotNull(peelFold(50f, 50f, 30f, 50f, square, size = 100f))
        val landing = fold.axisX + fold.dirX * fold.curve.project(fold.depth)
        assertEquals(80f, landing, 1e-2f)
        assertTrue(fold.axisX < 100f)
    }

    @Test
    fun `a tiny drag barely lifts the edge`() {
        val fold = assertNotNull(peelFold(100f, 50f, 99.5f, 50f, square, size = 100f))
        assertTrue(fold.axisX > 99f, "axis at ${fold.axisX}")
    }

    @Test
    fun `the curl grows with the drag and then stops growing`() {
        val short = assertNotNull(peelFold(100f, 50f, 95f, 50f, square, size = 100f))
        val long = assertNotNull(peelFold(100f, 50f, 20f, 50f, square, size = 100f))
        val longer = assertNotNull(peelFold(100f, 50f, -200f, 50f, square, size = 100f))
        assertTrue(short.curve.tight < long.curve.tight)
        assertTrue(short.curve.loose < long.curve.loose)
        assertEquals(long.curve.tight, longer.curve.tight, EPSILON)
        assertEquals(long.curve.loose, longer.curve.loose, EPSILON)
    }

    @Test
    fun `the fold faces back towards the grab`() {
        val fold = assertNotNull(peelFold(0f, 0f, 30f, 40f, square, size = 100f))
        assertEquals(-0.6f, fold.dirX, EPSILON)
        assertEquals(-0.8f, fold.dirY, EPSILON)
    }

    @Test
    fun `the reach covers the sticker and everywhere the flap can land`() {
        for ((pointerX, pointerY) in listOf(60f to 50f, -150f to 50f, 20f to 180f, 99f to 50f)) {
            val fold = assertNotNull(peelFold(100f, 50f, pointerX, pointerY, square, size = 100f))
            val box = fold.reach(0f, 0f, 100f, 100f)
            val left = box[0]
            val top = box[1]
            val right = box[2]
            val bottom = box[3]
            assertTrue(left <= 0f && top <= 0f && right >= 100f && bottom >= 100f)
            // Every point of the sheet, wherever the curve puts it, lands inside.
            for (x in 0..100 step 5) for (y in 0..100 step 5) {
                val q = (x - fold.axisX) * fold.dirX + (y - fold.axisY) * fold.dirY
                if (q <= 0f) continue
                val shift = fold.curve.project(q) - q
                val landedX = x + fold.dirX * shift
                val landedY = y + fold.dirY * shift
                assertTrue(landedX in left..right && landedY in top..bottom, "($x, $y) lands off")
            }
        }
    }

    private companion object {
        const val EPSILON = 1e-4f
    }
}
