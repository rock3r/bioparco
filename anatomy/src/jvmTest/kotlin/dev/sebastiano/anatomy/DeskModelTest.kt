// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.figures.DeskComputer
import dev.sebastiano.anatomy.figures.DeskModel
import dev.sebastiano.anatomy.figures.click
import dev.sebastiano.anatomy.figures.deskPicture
import dev.sebastiano.anatomy.kit.Flat
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
    fun screenWhitespaceMatchesSvgWithoutChangingTheTypedBuffer() {
        val pc = DeskModel()
        assertEquals(">", pc.displayText)
        "a  b   ".forEach(pc::type)
        assertEquals("> a b", pc.displayText)
        assertEquals("a  b   ", pc.line)
        assertEquals("on · 7 chars · key space", pc.readout())
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

    @Test
    fun theSideRockerAndTheCaseTogglePowerButEmptyBenchDoesNot() {
        val pc = DeskModel()
        assertFalse(pc.click(Flat(599.0, 439.0)))
        assertTrue(pc.power)
        assertTrue(pc.click(DeskComputer.at(DeskComputer.SIDE + 3, -49.0, 15.0)))
        assertFalse(pc.power)
        assertTrue(pc.click(DeskComputer.at(0.0, DeskComputer.FRONT, 110.0)))
        assertTrue(pc.power)
    }

    @Test
    fun reducedMotionStopsTheTourAndSettlesPowerAndKeysWithoutLosingInput() {
        val pc = DeskModel()
        pc.tick(0.016, true)
        assertFalse(pc.touring)
        pc.type('A')
        pc.tick(0.016, true)
        assertEquals("A", pc.line)
        assertEquals(2.2, pc.keys[DeskKeysIndex("a")].at)
        pc.setPower(false)
        pc.tick(0.016, true)
        assertEquals(0.0, deskPicture(pc.power, pc.powerClock).sx)
        repeat(30) { pc.tick(1.0 / 60, true) }
        assertTrue(pc.keys.all { it.at == 0.0 })
        assertEquals("A", pc.line)
    }

    @Test
    fun theTourTypesErasesSwitchesOffAndRestarts() {
        val pc = DeskModel()
        var typed = false
        var erased = false
        var off = false
        var restarted = false
        repeat(2_400) {
            pc.tick(1.0 / 60, false)
            if (pc.line.isNotEmpty()) typed = true
            if (typed && pc.line.isEmpty()) erased = true
            if (!pc.power) off = true
            if (off && pc.power && pc.line.isNotEmpty()) restarted = true
        }
        assertTrue(typed && erased && off && restarted)
    }

    private fun DeskKeysIndex(code: String) =
        dev.sebastiano.anatomy.figures.DeskKeys.keys.indexOfFirst { it.code == code }
}
