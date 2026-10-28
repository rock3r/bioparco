package dev.sebastiano.emailinvite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuestStackTest {
    @Test
    fun `it starts with the three people from the video`() {
        val stack = GuestStack()
        assertEquals(listOf('A', 'M', 'L'), stack.shown.map { it.initial })
        assertTrue(stack.shown.none { it.invited })
    }

    @Test
    fun `a new guest joins at the front`() {
        val stack = GuestStack()
        val change = stack.invite('N')
        assertEquals(listOf('N', 'A', 'M', 'L'), stack.shown.map { it.initial })
        assertEquals('N', change.arrived.initial)
        assertTrue(change.arrived.invited)
        assertNull(change.left)
    }

    @Test
    fun `every guest keeps its own id`() {
        val stack = GuestStack()
        stack.invite('N')
        stack.invite('N')
        val ids = stack.shown.map { it.id }
        assertEquals(ids.distinct(), ids)
    }

    @Test
    fun `the stack shows four at most, so the oldest leaves from the back`() {
        val stack = GuestStack()
        stack.invite('N')
        val change = stack.invite('B')
        assertEquals(listOf('B', 'N', 'A', 'M'), stack.shown.map { it.initial })
        assertEquals('L', change.left?.initial)
    }

    @Test
    fun `only the newest invite is marked newest`() {
        val stack = GuestStack()
        stack.invite('N')
        stack.invite('B')
        assertEquals(listOf(true, false, false, false), stack.shown.map { it == stack.newest })
    }
}
