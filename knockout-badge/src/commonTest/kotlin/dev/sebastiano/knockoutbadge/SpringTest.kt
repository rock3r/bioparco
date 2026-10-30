package dev.sebastiano.knockoutbadge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpringTest {
    @Test
    fun `it settles on its target`() {
        val spring = Spring(stiffness = 120f, dampingRatio = 0.8f)
        repeat(120) { spring.step(target = 1f, seconds = 1f / 60f) }
        assertEquals(1f, spring.value, 0.01f)
        assertTrue(spring.isSettledAt(1f))
    }

    @Test
    fun `it is not settled while it moves`() {
        val spring = Spring(stiffness = 120f, dampingRatio = 0.8f)
        spring.step(target = 1f, seconds = 1f / 60f)
        assertFalse(spring.isSettledAt(1f))
    }

    @Test
    fun `a stalled frame counts as a short one`() {
        val stalled = Spring(stiffness = 120f, dampingRatio = 0.8f)
        stalled.step(target = 1f, seconds = 2f)
        val capped = Spring(stiffness = 120f, dampingRatio = 0.8f)
        capped.step(target = 1f, seconds = Spring.MAX_STEP)
        assertEquals(capped.value, stalled.value, 1e-6f)
    }

    @Test
    fun `snapping puts it at rest`() {
        val spring = Spring(stiffness = 120f, dampingRatio = 0.8f)
        spring.step(target = 1f, seconds = 0.05f)
        spring.snapTo(0.3f)
        assertEquals(0.3f, spring.value)
        assertTrue(spring.isSettledAt(0.3f))
    }
}
