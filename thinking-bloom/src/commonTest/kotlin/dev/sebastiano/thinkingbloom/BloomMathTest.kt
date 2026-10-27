package dev.sebastiano.thinkingbloom

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BloomMathTest {
    @Test
    fun `a loop starts with the band before the label and ends with it past the label`() {
        assertTrue(BloomMath.phase(x = 0f, front = BloomMath.front(0f)) <= 0f)
        val end = BloomMath.front(BloomMath.SWEEP_SECONDS)
        assertTrue(BloomMath.phase(x = 1f, front = end) >= 1f)
    }

    @Test
    fun `the band only moves forward during a sweep, then rests`() {
        var previous = BloomMath.front(0f)
        var seconds = 0f
        while (seconds < BloomMath.LOOP_SECONDS) {
            val current = BloomMath.front(seconds)
            assertTrue(current >= previous, "went back at $seconds s")
            previous = current
            seconds += 1f / 60f
        }
        val rest = BloomMath.front(BloomMath.SWEEP_SECONDS)
        assertEquals(rest, BloomMath.front((BloomMath.SWEEP_SECONDS + BloomMath.LOOP_SECONDS) / 2))
    }

    @Test
    fun `the band loops`() {
        for (seconds in listOf(0.3f, 1.1f, 2.0f)) {
            assertEquals(
                BloomMath.front(seconds),
                BloomMath.front(seconds + BloomMath.LOOP_SECONDS),
                absoluteTolerance = 1e-4f,
            )
        }
    }

    @Test
    fun `a blossom is gone outside the band and full in its middle`() {
        assertEquals(0f, BloomMath.bloom(-0.2f))
        assertEquals(0f, BloomMath.bloom(0f))
        assertEquals(0f, BloomMath.bloom(1f))
        assertEquals(0f, BloomMath.bloom(1.3f))
        assertTrue(BloomMath.bloom(0.55f) >= 0.99f)
    }

    @Test
    fun `blossoms arrive and leave as specks, never as negative sizes`() {
        val samples = (0..400).map { BloomMath.bloom(-0.2f + it * 1.4f / 400) }
        assertTrue(samples.all { it >= 0f })
        assertTrue(BloomMath.bloom(0.08f) < 0.35f, "too big on arrival")
        assertTrue(BloomMath.bloom(0.96f) < 0.35f, "too big on leaving")
        assertTrue(BloomMath.bloom(0.08f) > 0f, "no speck on arrival")
        assertTrue(BloomMath.bloom(0.96f) > 0f, "no speck on leaving")
    }

    @Test
    fun `the ink shows outside the band and hides under full blossoms`() {
        assertEquals(1f, BloomMath.ink(-0.5f))
        assertEquals(1f, BloomMath.ink(0f))
        assertEquals(1f, BloomMath.ink(1f))
        assertEquals(1f, BloomMath.ink(1.5f))
        assertEquals(0f, BloomMath.ink(0.55f))
        val samples = (0..200).map { BloomMath.ink(-0.2f + it * 1.4f / 200) }
        assertTrue(samples.all { it in 0f..1f })
    }

    @Test
    fun `scattered spots all land inside the shape`() {
        val inside = { x: Float, y: Float -> (x - 50f) * (x - 50f) + (y - 50f) * (y - 50f) < 900f }
        val spots = scatter(100, 100, spacing = 4f, random = Random(7), inside = inside)
        assertTrue(spots.isNotEmpty())
        assertTrue(spots.all { inside(it.x, it.y) })
    }

    @Test
    fun `scattering fills the shape at about one spot per cell`() {
        val spots = scatter(100, 60, spacing = 10f, random = Random(1)) { _, _ -> true }
        assertEquals(60, spots.size)
        assertTrue(spots.all { it.x in 0f..100f && it.y in 0f..60f })
    }

    @Test
    fun `scattering with no spacing scatters nothing instead of looping forever`() {
        assertTrue(scatter(10, 10, spacing = 0f, random = Random(1)) { _, _ -> true }.isEmpty())
        assertTrue(scatter(10, 10, spacing = -1f, random = Random(1)) { _, _ -> true }.isEmpty())
    }

    @Test
    fun `scattering is the same for the same seed`() {
        val first = scatter(80, 40, spacing = 6f, random = Random(3)) { x, _ -> x > 20f }
        val second = scatter(80, 40, spacing = 6f, random = Random(3)) { x, _ -> x > 20f }
        assertEquals(first, second)
    }
}
