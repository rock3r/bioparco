package dev.sebastiano.knockoutbadge

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FoldTest {
    private val corner = Point(300f, 0f)

    @Test
    fun `the corner lands on the grip`() {
        val fold = Fold(corner, grip = Point(220f, 90f))
        assertNear(Point(220f, 90f), fold.reflect(corner))
    }

    @Test
    fun `points on the crease stay put`() {
        val fold = Fold(corner, grip = Point(220f, 90f))
        val middle = Point(260f, 45f)
        assertNear(middle, fold.reflect(middle))
    }

    @Test
    fun `reflecting twice is a no-op`() {
        val fold = Fold(corner, grip = Point(180f, 40f))
        val p = Point(37f, 211f)
        assertNear(p, fold.reflect(fold.reflect(p)))
    }

    @Test
    fun `the corner side is lifted, the far side stays down`() {
        val fold = Fold(corner, grip = Point(240f, 60f))
        assertTrue(fold.isLifted(Point(295f, 5f)))
        assertFalse(fold.isLifted(Point(10f, 400f)))
        assertFalse(fold.isLifted(Point(240f, 60f)))
    }

    @Test
    fun `the affine reflection agrees with reflect`() {
        val fold = Fold(corner, grip = Point(200f, 130f))
        val m = fold.reflection()
        for (p in listOf(Point(0f, 0f), Point(300f, 0f), Point(120f, 333f))) {
            val mapped = Point(m[0] * p.x + m[2] * p.y + m[4], m[1] * p.x + m[3] * p.y + m[5])
            assertNear(fold.reflect(p), mapped)
        }
    }

    @Test
    fun `the lifted side polygon lies on the lifted side and covers the corner`() {
        val fold = Fold(corner, grip = Point(230f, 70f))
        val polygon = fold.liftedSide(reach = 1_000f)
        assertEquals(4, polygon.size)
        // Two corners on the crease, which fold onto themselves, and two beyond it.
        assertNear(polygon[0], fold.reflect(polygon[0]))
        assertNear(polygon[3], fold.reflect(polygon[3]))
        assertTrue(fold.isLifted(polygon[1]))
        assertTrue(fold.isLifted(polygon[2]))
        assertTrue(contains(polygon, corner))
        assertFalse(contains(polygon, Point(10f, 400f)))
    }

    @Test
    fun `a grip inside the badge's quadrant and in reach is taken as it is`() {
        assertEquals(Point(200f, 80f), gripFor(corner, Point(200f, 80f), maxReach = 400f))
    }

    @Test
    fun `a grip past the reach is pulled back along the same direction`() {
        val grip = gripFor(corner, Point(0f, 400f), maxReach = 250f)
        assertEquals(250f, hypot(grip.x - corner.x, grip.y - corner.y), 0.01f)
        // Same direction as the pointer: (-300, 400) is (-0.6, 0.8).
        assertEquals(-0.6f, (grip.x - corner.x) / 250f, 0.001f)
        assertEquals(0.8f, (grip.y - corner.y) / 250f, 0.001f)
    }

    @Test
    fun `a grip outside the badge is held to the corner's edges`() {
        val grip = gripFor(corner, Point(360f, -50f), maxReach = 250f)
        assertEquals(corner, grip)
        val right = gripFor(corner, Point(380f, 120f), maxReach = 250f)
        assertEquals(Point(300f, 120f), right)
    }

    private fun contains(polygon: List<Point>, p: Point): Boolean {
        // Convex polygon: p is inside if it is on the same side of every edge.
        var sign = 0f
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            val cross = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
            if (abs(cross) < 1e-3f) continue
            if (sign == 0f) sign = cross else if (sign * cross < 0f) return false
        }
        return true
    }

    private fun assertNear(expected: Point, actual: Point) {
        assertEquals(expected.x, actual.x, 0.01f, "x of $actual")
        assertEquals(expected.y, actual.y, 0.01f, "y of $actual")
    }
}
