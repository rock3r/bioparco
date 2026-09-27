package dev.sebastiano.scrolleffects

import kotlin.test.Test
import kotlin.test.assertEquals

class CarouselMathTest {
    @Test
    fun `the focused card has offset zero and neighbours sit one slot away`() {
        assertEquals(0f, slotOffset(index = 2, position = 2f, count = 6))
        assertEquals(1f, slotOffset(index = 3, position = 2f, count = 6))
        assertEquals(-1f, slotOffset(index = 1, position = 2f, count = 6))
    }

    @Test
    fun `offsets wrap around the loop`() {
        assertEquals(-1f, slotOffset(index = 5, position = 0f, count = 6))
        assertEquals(1f, slotOffset(index = 0, position = 5f, count = 6))
        assertEquals(0.5f, slotOffset(index = 0, position = 5.5f, count = 6))
        assertEquals(-0.5f, slotOffset(index = 5, position = 5.5f, count = 6))
    }

    @Test
    fun `offsets stay in the half-open window around the centre`() {
        assertEquals(-3f, slotOffset(index = 3, position = 0f, count = 6))
        assertEquals(-3f, slotOffset(index = 0, position = 3f, count = 6))
        assertEquals(2f, slotOffset(index = 2, position = -6f, count = 6))
    }

    @Test
    fun `the focused card follows the nearest slot, even far from zero`() {
        assertEquals(0, focusedCard(position = 0.4f, count = 6))
        assertEquals(1, focusedCard(position = 0.6f, count = 6))
        assertEquals(5, focusedCard(position = -1f, count = 6))
        assertEquals(2, focusedCard(position = 14f, count = 6))
    }

    @Test
    fun `a slow release settles on the nearest slot`() {
        assertEquals(3f, settleTarget(position = 2.6f, velocity = 0f, started = 2f))
        assertEquals(2f, settleTarget(position = 2.3f, velocity = 0f, started = 2f))
    }

    @Test
    fun `a flick carries on to the next slot`() {
        assertEquals(3f, settleTarget(position = 2.2f, velocity = 4f, started = 2f))
        assertEquals(1f, settleTarget(position = 1.8f, velocity = -4f, started = 2f))
    }

    @Test
    fun `a hard throw never skips more than one card`() {
        assertEquals(3f, settleTarget(position = 2.9f, velocity = 40f, started = 2f))
        assertEquals(1f, settleTarget(position = 1.1f, velocity = -40f, started = 2f))
    }
}
