package dev.sebastiano.peelsticker

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PeelCurveTest {
    private val curve = PeelCurve(tight = 10f, loose = 40f)

    @Test
    fun `the sheet starts flat on the table`() {
        assertEquals(0f, curve.project(0f), EPSILON)
        assertEquals(0f, curve.height(0f), EPSILON)
    }

    @Test
    fun `the tight curl stands the sheet up`() {
        val top = (PI / 2).toFloat() * 10f
        assertEquals(10f, curve.project(top), EPSILON)
        assertEquals(10f, curve.height(top), EPSILON)
    }

    @Test
    fun `past both curls the sheet lies flipped and flat`() {
        val end = (PI / 2).toFloat() * (10f + 40f)
        assertEquals(10f - 40f, curve.project(end), EPSILON)
        assertEquals(10f - 40f - 25f, curve.project(end + 25f), EPSILON)
        assertEquals(50f, curve.height(end + 25f), EPSILON)
    }

    @Test
    fun `the front branch inverts the rising curl`() {
        for (depth in listOf(0f, 3f, 9f, 15.5f)) {
            assertEquals(depth, curve.frontDepth(curve.project(depth))!!, 1e-2f)
        }
        assertNull(curve.frontDepth(-1f))
        assertNull(curve.frontDepth(11f))
    }

    @Test
    fun `the back branch inverts everything past the top of the curl`() {
        for (depth in listOf(16f, 30f, 60f, 78f, 90f, 200f)) {
            assertEquals(depth, curve.backDepth(curve.project(depth))!!, 1e-2f)
        }
        assertNull(curve.backDepth(10.5f))
    }

    @Test
    fun `the grab depth brings the grabbed point to the pointer`() {
        for (distance in listOf(0f, 1f, 12f, 40f, 150f, 600f)) {
            val depth = curve.depthFor(distance)
            assertEquals(distance, depth - curve.project(depth), 1e-2f)
        }
    }

    @Test
    fun `without radii it is a plain fold`() {
        val fold = PeelCurve(tight = 0f, loose = 0f)
        assertEquals(-30f, fold.project(30f), EPSILON)
        assertEquals(50f, fold.depthFor(100f), 1e-2f)
        assertEquals(30f, fold.backDepth(-30f)!!, EPSILON)
        assertNull(fold.frontDepth(5f))
    }

    @Test
    fun `projection never runs ahead of the depth`() {
        var depth = 0f
        var previous = curve.project(0f)
        while (depth < 300f) {
            depth += 0.5f
            val q = curve.project(depth)
            assertTrue(depth - q >= -EPSILON)
            assertTrue(q - previous <= 0.5f + EPSILON)
            previous = q
        }
    }

    private companion object {
        const val EPSILON = 1e-3f
    }
}
