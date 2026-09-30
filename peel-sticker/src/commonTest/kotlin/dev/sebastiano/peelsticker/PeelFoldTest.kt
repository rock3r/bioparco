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
    fun `the lifted outline holds everywhere the flap can land`() {
        for ((pointerX, pointerY) in listOf(60f to 50f, -150f to 50f, 20f to 180f, 40f to 20f)) {
            val fold = assertNotNull(peelFold(100f, 50f, pointerX, pointerY, square, size = 100f))
            val outline = fold.liftedOutline(0f, 0f, 100f, 100f)
            for (x in 0..100 step 5) for (y in 0..100 step 5) {
                val q = (x - fold.axisX) * fold.dirX + (y - fold.axisY) * fold.dirY
                if (q <= 0f) continue
                val shift = fold.curve.project(q) - q
                val landedX = x + fold.dirX * shift
                val landedY = y + fold.dirY * shift
                assertTrue(inside(outline, landedX, landedY), "($x, $y) lands outside")
            }
        }
    }

    @Test
    fun `a diamond's outline is tighter than its box's`() {
        val diamond = floatArrayOf(50f, 0f, 100f, 50f, 50f, 100f, 0f, 50f)
        val fold = assertNotNull(peelFold(100f, 50f, 40f, 50f, square, size = 100f))
        val outline = fold.liftedOutline(diamond)
        for (t in 0..20) {
            // Points along the diamond's right half, where it lifts.
            for ((x, y) in listOf(50f + t * 2.5f to t * 2.5f, 50f + t * 2.5f to 100f - t * 2.5f)) {
                val q = (x - fold.axisX) * fold.dirX + (y - fold.axisY) * fold.dirY
                if (q <= 0f) continue
                val shift = fold.curve.project(q) - q
                assertTrue(
                    inside(outline, x + fold.dirX * shift, y + fold.dirY * shift),
                    "($x, $y)",
                )
            }
        }
        assertTrue(area(outline) < area(fold.liftedOutline(0f, 0f, 100f, 100f)))
    }

    /** Inside a convex polygon, or on its edge, whichever way it winds. */
    private fun inside(polygon: FloatArray, x: Float, y: Float): Boolean {
        var sign = 0
        val n = polygon.size / 2
        for (i in 0 until n) {
            val ax = polygon[i * 2]
            val ay = polygon[i * 2 + 1]
            val bx = polygon[(i + 1) % n * 2]
            val by = polygon[(i + 1) % n * 2 + 1]
            val cross = (bx - ax) * (y - ay) - (by - ay) * (x - ax)
            if (cross > 1e-3f) {
                if (sign < 0) return false
                sign = 1
            }
            if (cross < -1e-3f) {
                if (sign > 0) return false
                sign = -1
            }
        }
        return true
    }

    private fun area(polygon: FloatArray): Float {
        var twice = 0f
        val n = polygon.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            twice += polygon[i * 2] * polygon[j * 2 + 1] - polygon[j * 2] * polygon[i * 2 + 1]
        }
        return kotlin.math.abs(twice) / 2f
    }

    private companion object {
        const val EPSILON = 1e-4f
    }
}
