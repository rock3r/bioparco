package dev.sebastiano.squeezeprogress

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrumpleTest {
    private val settled = Crumple(centre = 400f, halfWidth = 40f, height = 30f, squeeze = 1f)

    @Test
    fun `a relaxed crumple is a straight line`() {
        val flat = Crumple(centre = 400f, halfWidth = 40f, height = 0f, squeeze = 0f)
        for (s in samples(300f, 500f)) {
            assertEquals(s, crumpleX(s, flat), 0.0001f)
            assertEquals(0f, crumpleY(s, flat), 0.0001f)
        }
    }

    @Test
    fun `the line right of the crumple stays put, so the tip never moves`() {
        for (s in samples(440f, 600f)) {
            assertEquals(s, crumpleX(s, settled), 0.0001f)
            assertEquals(0f, crumpleY(s, settled), 0.0001f)
        }
    }

    @Test
    fun `the line left of the crumple slides right by the length the crumple takes up`() {
        val taken = crumpleX(300f, settled) - 300f
        assertTrue(taken > 0f, "expected the left of the line to slide right, got $taken")
        assertEquals(taken, crumpleX(100f, settled) - 100f, 0.0001f)
        assertEquals(0f, crumpleY(300f, settled), 0.0001f)
    }

    @Test
    fun `it rises, dips deeper, then rises a little, left to right`() {
        val lobes = listOf(-2f / 3f, 0f, 2f / 3f).map { u -> crumpleY(400f + u * 40f, settled) }
        // Screen y grows downwards: up is negative.
        assertTrue(lobes[0] < 0f, "first lobe should rise: $lobes")
        assertTrue(lobes[1] > 0f, "second lobe should dip: $lobes")
        assertTrue(lobes[2] < 0f, "third lobe should rise: $lobes")
        assertTrue(
            abs(lobes[2]) < abs(lobes[0]) * 0.7f,
            "third lobe should be the small one: $lobes",
        )
    }

    @Test
    fun `height is the tallest excursion`() {
        val tallest = samples(360f, 440f, count = 2_000).maxOf { abs(crumpleY(it, settled)) }
        assertEquals(30f, tallest, 0.3f)
    }

    @Test
    fun `fully squeezed, the strokes between lobes stand upright`() {
        // Where a lobe hands over to the next the line is steepest; there it should not move
        // sideways.
        for (u in listOf(-1f / 3f, 1f / 3f)) {
            val s = 400f + u * 40f
            val ds = 0.01f
            val dx = crumpleX(s + ds, settled) - crumpleX(s - ds, settled)
            val dy = crumpleY(s + ds, settled) - crumpleY(s - ds, settled)
            assertTrue(abs(dx) < abs(dy) * 0.05f, "stroke at u=$u is not upright: dx=$dx dy=$dy")
        }
    }

    @Test
    fun `fully squeezed, the first stroke rises upright out of the flat line`() {
        // From a tenth of the way up the first lobe to four fifths of it, the line should barely
        // move sideways: a stroke, not a ramp.
        val rising = samples(360f, 400f, count = 4_000)
        val low = rising.first { crumpleY(it, settled) <= -0.1f * 30f }
        val high = rising.first { crumpleY(it, settled) <= -0.8f * 0.95f * 30f }
        val across = crumpleX(high, settled) - crumpleX(low, settled)
        val up = crumpleY(low, settled) - crumpleY(high, settled)
        assertTrue(abs(across) < up * 0.2f, "first stroke leans: across=$across up=$up")
    }

    @Test
    fun `squeezed past one, the line folds back over itself`() {
        val overshoot = settled.copy(squeeze = 1.3f)
        val xs = samples(360f, 440f, count = 400).map { crumpleX(it, overshoot) }
        assertTrue(
            xs.zipWithNext().any { (a, b) -> b < a },
            "expected x to run backwards somewhere",
        )
        val relaxed =
            samples(360f, 440f, count = 400).map { crumpleX(it, settled.copy(squeeze = 0.6f)) }
        assertTrue(relaxed.zipWithNext().all { (a, b) -> b >= a }, "a half squeeze should not fold")
    }

    @Test
    fun `a negative crumple is the mirror image, for the ripple on release`() {
        val flipped = settled.copy(height = -30f)
        for (s in samples(360f, 440f)) {
            assertEquals(-crumpleY(s, settled), crumpleY(s, flipped), 0.0001f)
        }
    }

    private fun samples(from: Float, to: Float, count: Int = 64): List<Float> =
        (0..count).map { from + (to - from) * it / count }
}
