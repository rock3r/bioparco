package dev.sebastiano.particlering

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HeartShapeTest {
    private val outline = HeartShape.outline()

    @Test
    fun theOutlineIsOneUnitWideAndCentred() {
        val xs = outline.filterIndexed { i, _ -> i % 2 == 0 }
        val ys = outline.filterIndexed { i, _ -> i % 2 == 1 }
        assertEquals(-0.5f, xs.min(), 1e-3f)
        assertEquals(0.5f, xs.max(), 1e-3f)
        assertEquals(0f, (ys.min() + ys.max()) / 2f, 1e-3f)
        assertEquals(HeartShape.HEIGHT, ys.max() - ys.min(), 1e-3f)
    }

    @Test
    fun theHeartPointsDownWithADipBetweenItsLobes() {
        val ys = outline.filterIndexed { i, _ -> i % 2 == 1 }
        val bottom = ys.indexOf(ys.max())
        assertEquals(0f, outline[bottom * 2], 1e-3f)
        val nearTop = ys.min() + 0.04f
        assertFalse(HeartShape.contains(0f, nearTop), "the dip between the lobes is outside")
        assertTrue(HeartShape.contains(-0.25f, nearTop), "the left lobe reaches the top")
        assertTrue(HeartShape.contains(0.25f, nearTop), "the right lobe reaches the top")
    }

    @Test
    fun theMiddleIsInsideAndTheCornersAreNot() {
        assertTrue(HeartShape.contains(0f, 0f))
        val half = HeartShape.HEIGHT / 2f
        for ((x, y) in listOf(-0.48f to half - 0.02f, 0.48f to half - 0.02f)) {
            assertFalse(HeartShape.contains(x, y), "($x, $y) is a bottom corner")
        }
    }

    @Test
    fun samplesFallInsideAndCoverTheHeart() {
        val points = HeartShape.sample(1_500, Random(4))
        assertEquals(3_000, points.size)
        for (i in 0 until 1_500) {
            assertTrue(
                HeartShape.contains(points[i * 2], points[i * 2 + 1]),
                "sample $i is outside",
            )
        }
        val xs = points.filterIndexed { i, _ -> i % 2 == 0 }
        val ys = points.filterIndexed { i, _ -> i % 2 == 1 }
        assertTrue(abs(xs.min() + 0.5f) < 0.06f, "nothing near the left edge")
        assertTrue(abs(xs.max() - 0.5f) < 0.06f, "nothing near the right edge")
        assertTrue(abs(ys.max() - HeartShape.HEIGHT / 2f) < 0.08f, "nothing near the point")
    }
}
