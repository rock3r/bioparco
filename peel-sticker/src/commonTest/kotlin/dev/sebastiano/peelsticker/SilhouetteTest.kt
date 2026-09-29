package dev.sebastiano.peelsticker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SilhouetteTest {
    /**
     * An L in a 4 × 4 mask:
     * ```
     * X...
     * X...
     * X...
     * XXXX
     * ```
     */
    private val ell =
        Silhouette(
            width = 4,
            height = 4,
            coverage = BooleanArray(16) { i -> i % 4 == 0 || i / 4 == 3 },
        )

    @Test
    fun `hit testing follows the mask, not its bounds`() {
        assertTrue(ell.contains(0.5f, 0.5f))
        assertTrue(ell.contains(3.5f, 3.5f))
        assertFalse(ell.contains(2.5f, 1.5f))
        assertFalse(ell.contains(-1f, 2f))
        assertFalse(ell.contains(4.5f, 3.5f))
    }

    @Test
    fun `the extent is how far the shape reaches in a direction`() {
        assertEquals(4f, ell.extent(1f, 0f), EPSILON)
        assertEquals(4f, ell.extent(0f, 1f), EPSILON)
        assertEquals(0f, ell.extent(-1f, 0f), EPSILON)
        assertEquals(0f, ell.extent(0f, -1f), EPSILON)
    }

    @Test
    fun `the diagonal extent skips the empty corner`() {
        val d = 0.70710677f
        // The far corner (4, 4) is on the L; the hollow corner (4, 0) is not.
        assertEquals(8f * d, ell.extent(d, d), EPSILON)
        assertEquals(1f * d, ell.extent(d, -d), EPSILON)
    }

    @Test
    fun `a grab just off the edge still catches it`() {
        // Right of the L's upright, in the hollow.
        assertFalse(ell.contains(1.6f, 1.5f))
        assertTrue(ell.near(1.6f, 1.5f, slop = 1f))
        assertFalse(ell.near(2.5f, 1.5f, slop = 1f))
        assertTrue(ell.near(0.5f, 0.5f, slop = 0f))
    }

    @Test
    fun `an empty mask reaches nowhere`() {
        val empty = Silhouette(2, 2, BooleanArray(4))
        assertEquals(0f, empty.extent(1f, 0f), EPSILON)
        assertFalse(empty.contains(1f, 1f))
    }

    private companion object {
        const val EPSILON = 1e-4f
    }
}
