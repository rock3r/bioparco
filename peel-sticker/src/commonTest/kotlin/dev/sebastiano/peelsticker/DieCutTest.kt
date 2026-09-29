package dev.sebastiano.peelsticker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DieCutTest {
    @Test
    fun `a dot grows into a disc as wide as the border`() {
        val size = 41
        val art = FloatArray(size * size)
        art[20 * size + 20] = 1f
        val cut = dieCut(art, size, size, border = 10f, fillet = 0f)
        assertEquals(1f, cut[20 * size + 20], EPSILON)
        assertEquals(1f, cut[20 * size + 29], EPSILON)
        assertEquals(0f, cut[20 * size + 32], EPSILON)
        // Round, not square: the diagonal at 8.5 px along each axis is 12 px away.
        assertEquals(0f, cut[(20 + 9) * size + 20 + 9], EPSILON)
    }

    @Test
    fun `the art itself is always inside the cut`() {
        val size = 16
        val art = FloatArray(size * size) { if (it % 7 == 0) 1f else 0f }
        val cut = dieCut(art, size, size, border = 2f, fillet = 1f)
        for (i in art.indices) if (art[i] >= 0.5f) assertEquals(1f, cut[i], EPSILON)
    }

    @Test
    fun `the border edge is anti-aliased`() {
        val size = 41
        val art = FloatArray(size * size)
        art[20 * size + 20] = 1f
        val cut = dieCut(art, size, size, border = 10.8f, fillet = 0f)
        val edge = cut[20 * size + 31]
        assertTrue(edge > 0f && edge < 1f, "edge coverage $edge")
    }

    @Test
    fun `a fillet rounds the inside corner of an L`() {
        val size = 60
        // An L: a column on the left and a row at the bottom, each 10 px thick.
        val art = FloatArray(size * size) { i -> if (i % size < 10 || i / size >= 50) 1f else 0f }
        val sharp = dieCut(art, size, size, border = 5f, fillet = 0f)
        val round = dieCut(art, size, size, border = 5f, fillet = 12f)
        // Just off the inside corner at (15, 45), a plain offset leaves a sharp notch...
        val corner = 43 * size + 17
        assertEquals(0f, sharp[corner], EPSILON)
        // ...and the fillet fills it.
        assertEquals(1f, round[corner], EPSILON)
        // Straight edges far from the corner keep the same border, to within half a pixel.
        val row = 10 * size
        assertEquals(
            (10 until 30).sumOf { sharp[row + it].toDouble() },
            (10 until 30).sumOf { round[row + it].toDouble() },
            0.5,
        )
    }

    @Test
    fun `a fillet that grows past the edge still comes back round`() {
        val size = 30
        val art = FloatArray(size * size)
        art[25 * size + 15] = 1f
        val cut = dieCut(art, size, size, border = 3f, fillet = 6f)
        assertEquals(1f, cut[27 * size + 15], EPSILON)
        assertEquals(0f, cut[28 * size + 18], EPSILON)
        assertEquals(0f, cut[29 * size + 15], EPSILON)
    }

    private companion object {
        const val EPSILON = 1e-4f
    }
}
