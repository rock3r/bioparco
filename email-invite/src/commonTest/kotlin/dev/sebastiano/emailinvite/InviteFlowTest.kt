package dev.sebastiano.emailinvite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InviteFlowTest {
    @Test
    fun `it starts collapsed`() {
        assertEquals(InvitePhase.Collapsed, InviteFlow().phase)
    }

    @Test
    fun `opening a collapsed pill starts editing`() {
        val flow = InviteFlow()
        assertTrue(flow.open())
        assertEquals(InvitePhase.Editing, flow.phase)
    }

    @Test
    fun `opening twice does nothing the second time`() {
        val flow = InviteFlow()
        flow.open()
        assertFalse(flow.open())
        assertEquals(InvitePhase.Editing, flow.phase)
    }

    @Test
    fun `an unfinished address is not sent`() {
        val flow = InviteFlow()
        flow.open()
        assertFalse(flow.submit("new@gmail.c"))
        assertEquals(InvitePhase.Editing, flow.phase)
    }

    @Test
    fun `a whole address is sent`() {
        val flow = InviteFlow()
        flow.open()
        assertTrue(flow.submit("new@gmail.com"))
        assertEquals(InvitePhase.Sent, flow.phase)
    }

    @Test
    fun `a held Enter sends once`() {
        val flow = InviteFlow()
        flow.open()
        assertTrue(flow.submit("new@gmail.com"))
        assertFalse(flow.submit("new@gmail.com"))
        assertEquals(InvitePhase.Sent, flow.phase)
    }

    @Test
    fun `nothing is sent while collapsed`() {
        val flow = InviteFlow()
        assertFalse(flow.submit("new@gmail.com"))
        assertEquals(InvitePhase.Collapsed, flow.phase)
    }

    @Test
    fun `cancelling while editing collapses`() {
        val flow = InviteFlow()
        flow.open()
        assertTrue(flow.cancel())
        assertEquals(InvitePhase.Collapsed, flow.phase)
    }

    @Test
    fun `a sent invite cannot be cancelled or reopened, only settled`() {
        val flow = InviteFlow()
        flow.open()
        flow.submit("new@gmail.com")
        assertFalse(flow.cancel())
        assertFalse(flow.open())
        assertEquals(InvitePhase.Sent, flow.phase)
        assertTrue(flow.settle())
        assertEquals(InvitePhase.Collapsed, flow.phase)
    }

    @Test
    fun `settling only follows a send`() {
        val flow = InviteFlow()
        assertFalse(flow.settle())
        flow.open()
        assertFalse(flow.settle())
        assertEquals(InvitePhase.Editing, flow.phase)
    }

    @Test
    fun `the pill opens again after a send settles`() {
        val flow = InviteFlow()
        flow.open()
        flow.submit("new@gmail.com")
        flow.settle()
        assertTrue(flow.open())
        assertEquals(InvitePhase.Editing, flow.phase)
    }
}
