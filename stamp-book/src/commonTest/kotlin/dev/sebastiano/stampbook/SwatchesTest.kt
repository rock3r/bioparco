package dev.sebastiano.stampbook

import kotlin.test.Test
import kotlin.test.assertEquals

class SwatchesTest {
    @Test
    fun `stepping right past the last swatch wraps to the first`() {
        assertEquals(1, cycled(index = 0, step = 1, count = 3))
        assertEquals(0, cycled(index = 2, step = 1, count = 3))
    }

    @Test
    fun `stepping left past the first swatch wraps to the last`() {
        assertEquals(2, cycled(index = 0, step = -1, count = 3))
        assertEquals(1, cycled(index = 2, step = -1, count = 3))
    }
}
