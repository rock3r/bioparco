package dev.sebastiano.scrolleffects

import kotlin.test.Test
import kotlin.test.assertEquals

class SlotTargetTest {
    @Test
    fun `a step from rest goes to the next slot`() {
        assertEquals(3, SlotTarget().step(position = 2f, direction = 1))
        assertEquals(1, SlotTarget().step(position = 2.2f, direction = -1))
    }

    @Test
    fun `quick steps queue up instead of rounding the same in-flight position`() {
        val target = SlotTarget()
        assertEquals(1, target.step(position = 0f, direction = 1))
        assertEquals(2, target.step(position = 0.2f, direction = 1))
        assertEquals(3, target.step(position = 0.45f, direction = 1))
        assertEquals(2, target.step(position = 0.7f, direction = -1))
    }

    @Test
    fun `a drag forgets the queue and a settle sets it`() {
        val target = SlotTarget()
        target.step(position = 0f, direction = 1)
        target.step(position = 0.1f, direction = 1)
        target.clear()
        assertEquals(6, target.step(position = 5.3f, direction = 1))
        target.settleAt(9)
        assertEquals(8, target.step(position = 8.6f, direction = -1))
    }
}
