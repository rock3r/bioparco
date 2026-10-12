// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.figures.DeskModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeskModelTest {
    @Test
    fun typingMapsShiftedKeysAndCapsTheBufferButStillPressesKeys() {
        val pc = DeskModel()
        "Az!? ".forEach(pc::type)
        assertEquals("Az!? ", pc.line)
        assertTrue(pc.pressed.containsAll(listOf("Shift", "a", "z", "1", "/", " ")))
        pc.type('é')
        assertEquals("Az!? ", pc.line)
        repeat(30) { pc.type('x') }
        assertEquals("Az!? " + "x".repeat(15), pc.line)
        pc.type('7')
        assertEquals(20, pc.line.length)
        assertTrue("7" in pc.pressed)
        assertEquals("on · 20 chars · key 7", pc.readout())
    }

    @Test
    fun PowerPreservesTheLineAndRejectsInputWhileOff() {
        val pc = DeskModel()
        "stay".forEach(pc::type)
        pc.setPower(false)
        pc.type('x')
        pc.erase()
        pc.enter()
        assertEquals("stay", pc.line)
        assertEquals("off", pc.readout())
        pc.setPower(true)
        pc.erase()
        assertEquals("sta", pc.line)
        assertEquals("on · 3 chars · key backspace", pc.readout())
        pc.enter()
        assertEquals("", pc.line)
        assertEquals("on · 0 chars · key return", pc.readout())
    }

    @Test
    fun TakingOverClearsTheTourOnceAndSwitchingDoesNotTurnItOn() {
        val pc = DeskModel()
        "tour".forEach(pc::type)
        pc.setPower(false)
        pc.takeOver(switching = true)
        assertFalse(pc.power)
        assertFalse(pc.touring)
        assertEquals("", pc.line)
        pc.setPower(true)
        pc.type('a')
        pc.takeOver()
        assertEquals("a", pc.line)
        pc.resume()
        assertTrue(pc.touring)
        assertTrue(pc.power)
        assertEquals("", pc.line)
    }
}
