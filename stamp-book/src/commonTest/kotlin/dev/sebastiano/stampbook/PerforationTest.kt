package dev.sebastiano.stampbook

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerforationTest {
    @Test
    fun `each side gets a whole number of holes at an even pitch`() {
        val holes = Perforation.holes(width = 100f, height = 60f, pitch = 10f)

        val top = holes.filter { it.y == 0f }.map { it.x }
        val left = holes.filter { it.x == 0f }.map { it.y }
        assertEquals(10, top.size)
        assertEquals(6, left.size)
        top.zipWithNext { a, b -> b - a }.forEach { assertEquals(10f, it, TOLERANCE) }
        left.zipWithNext { a, b -> b - a }.forEach { assertEquals(10f, it, TOLERANCE) }
    }

    @Test
    fun `all four sides are perforated`() {
        val holes = Perforation.holes(width = 100f, height = 60f, pitch = 10f)

        assertEquals(10, holes.count { it.y == 60f })
        assertEquals(6, holes.count { it.x == 100f })
        assertEquals(32, holes.size)
    }

    @Test
    fun `corners fall midway between holes, so every corner is a whole tooth`() {
        val holes = Perforation.holes(width = 100f, height = 60f, pitch = 10f)

        val top = holes.filter { it.y == 0f }.map { it.x }.sorted()
        assertEquals(5f, top.first(), TOLERANCE)
        assertEquals(95f, top.last(), TOLERANCE)
        val right = holes.filter { it.x == 100f }.map { it.y }.sorted()
        assertEquals(5f, right.first(), TOLERANCE)
        assertEquals(55f, right.last(), TOLERANCE)
    }

    @Test
    fun `the pitch stretches to fit the side instead of leaving a gap`() {
        val top = Perforation.holes(width = 103f, height = 60f, pitch = 10f).filter { it.y == 0f }

        assertEquals(10, top.size)
        val xs = top.map { it.x }.sorted()
        xs.zipWithNext { a, b -> b - a }.forEach { assertEquals(10.3f, it, TOLERANCE) }
        assertEquals(103f - xs.last(), xs.first(), TOLERANCE)
    }

    @Test
    fun `a side shorter than the pitch still gets one hole, in its middle`() {
        val left = Perforation.holes(width = 100f, height = 6f, pitch = 10f).filter { it.x == 0f }

        assertEquals(1, left.size)
        assertEquals(3f, left.single().y, TOLERANCE)
    }

    @Test
    fun `no hole sits on a corner`() {
        val holes = Perforation.holes(width = 87f, height = 113f, pitch = 7.5f)

        val corners = listOf(0f to 0f, 87f to 0f, 0f to 113f, 87f to 113f)
        assertTrue(holes.none { hole -> corners.any { (x, y) -> hole.x == x && hole.y == y } })
    }

    private companion object {
        const val TOLERANCE = 1e-3f
    }
}
