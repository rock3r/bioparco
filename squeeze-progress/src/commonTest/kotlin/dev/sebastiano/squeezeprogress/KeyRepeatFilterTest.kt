package dev.sebastiano.squeezeprogress

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyRepeatFilterTest {
    private val space = 32L
    private val enter = 10L

    @Test
    fun `a press counts once, however long the key is held`() {
        val filter = KeyRepeatFilter()
        assertTrue(filter.press(space))
        repeat(20) { assertFalse(filter.press(space), "key repeat must not count again") }
    }

    @Test
    fun `releasing the key lets the next press count`() {
        val filter = KeyRepeatFilter()
        filter.press(space)
        filter.release(space)
        assertTrue(filter.press(space))
    }

    @Test
    fun `each key is tracked on its own`() {
        val filter = KeyRepeatFilter()
        assertTrue(filter.press(space))
        assertTrue(filter.press(enter))
        assertFalse(filter.press(space))
    }

    @Test
    fun `forgetting held keys lets the next press count, as after losing focus mid-press`() {
        val filter = KeyRepeatFilter()
        filter.press(space)
        filter.reset()
        assertTrue(filter.press(space))
    }
}
