package dev.sebastiano.stampbook

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HoverLatchTest {
    // The closed book, and the wider spread it makes once the stamps have fanned out to its right.
    private val folder = Area(left = 100f, top = 100f, right = 300f, bottom = 350f)
    private val spread = Area(left = 100f, top = 90f, right = 520f, bottom = 360f)

    private fun latch() = HoverLatch(opener = folder, keeper = spread)

    @Test
    fun `starts closed`() {
        assertFalse(latch().isOpen)
    }

    @Test
    fun `hovering where the stamps would fan out does not open a closed book`() {
        val latch = latch()

        latch.move(450f, 200f)

        assertFalse(latch.isOpen)
    }

    @Test
    fun `hovering the book opens it, and it stays open over the stamps`() {
        val latch = latch()

        latch.move(200f, 200f)
        assertTrue(latch.isOpen)

        latch.move(450f, 200f)
        assertTrue(latch.isOpen)
    }

    @Test
    fun `moving off the spread closes it`() {
        val latch = latch()
        latch.move(200f, 200f)

        latch.move(600f, 200f)

        assertFalse(latch.isOpen)
    }

    @Test
    fun `the pointer leaving the window closes it`() {
        val latch = latch()
        latch.move(200f, 200f)

        latch.leave()

        assertFalse(latch.isOpen)
    }

    @Test
    fun `a click pins it open until the next click`() {
        val latch = latch()

        latch.toggle()
        assertTrue(latch.isOpen)

        latch.move(700f, 500f)
        latch.leave()
        assertTrue(latch.isOpen)

        latch.toggle()
        assertFalse(latch.isOpen)
    }

    @Test
    fun `clicking a hovered-open book closes it until the pointer goes away and comes back`() {
        val latch = latch()
        latch.move(200f, 200f)

        latch.toggle()
        assertFalse(latch.isOpen)

        latch.move(210f, 210f)
        assertFalse(latch.isOpen)

        latch.move(600f, 200f)
        latch.move(200f, 200f)
        assertTrue(latch.isOpen)
    }

    @Test
    fun `a pinned book closed by a click opens again on the next hover`() {
        val latch = latch()
        latch.toggle()
        latch.toggle()

        latch.move(200f, 200f)

        assertTrue(latch.isOpen)
    }
}
