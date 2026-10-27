package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.figures.Bunsen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BunsenTest {
    @Test
    fun `the collar meters primary air in proportion to its open area`() {
        assertEquals(0.0, Bunsen.airToGas(0.0), 1e-12)
        assertEquals(Bunsen.airToGas(1.0) / 2, Bunsen.airToGas(0.5), 1e-12)
        // Fully open draws three quarters of the 9.52 volumes of air methane needs.
        assertEquals(7.14, Bunsen.airToGas(1.0), 1e-9)
        assertEquals(1.333, Bunsen.equivalenceRatio(1.0), 1e-3)
        assertTrue(Bunsen.equivalenceRatio(0.0).isInfinite())
    }

    @Test
    fun `a closed collar burns a tall luminous yellow diffusion flame at about 1000 C`() {
        assertEquals(1.0, Bunsen.sootiness(0.0), 1e-9)
        assertEquals(0.0, Bunsen.coneStrength(0.0), 1e-9)
        assertEquals(1_000.0, Bunsen.hottest(0.0), 1e-9)
        assertEquals("yellow flame", Bunsen.phaseOf(0.0))
        // Roper's laminar jet flame length for 20 L/h of methane: about 74 mm.
        assertEquals(74.0, Bunsen.flameHeight(0.0), 0.5)
    }

    @Test
    fun `an open collar burns a short soot-free flame with an inner blue cone at about 1500 C`() {
        assertEquals(0.0, Bunsen.sootiness(1.0), 1e-9)
        assertEquals(1.0, Bunsen.coneStrength(1.0), 1e-9)
        assertEquals(1_500.0, Bunsen.hottest(1.0), 1e-9)
        assertEquals("blue cone", Bunsen.phaseOf(1.0))
        assertEquals(40.0, Bunsen.flameHeight(1.0), 1.0)
        // Gouy's cone: a 10 mm bore, the mixture at 0.58 m/s and burning at 0.21 m/s.
        assertEquals(12.8, Bunsen.coneHeight(1.0), 0.3)
    }

    @Test
    fun `opening the collar shortens the flame, clears the soot and pulls the cone down`() {
        val steps = (0..20).map { it / 20.0 }
        for ((a, b) in steps.zipWithNext()) {
            assertTrue(Bunsen.flameHeight(b) < Bunsen.flameHeight(a), "height at $b")
            assertTrue(Bunsen.sootiness(b) <= Bunsen.sootiness(a), "soot at $b")
            assertTrue(Bunsen.coneStrength(b) >= Bunsen.coneStrength(a), "cone at $b")
            assertTrue(Bunsen.hottest(b) > Bunsen.hottest(a), "temperature at $b")
            assertTrue(Bunsen.coneHeight(b) <= Bunsen.coneHeight(a), "cone height at $b")
        }
        for (opening in steps) {
            assertTrue(
                Bunsen.coneHeight(opening) < Bunsen.flameHeight(opening),
                "inside at $opening",
            )
        }
    }

    @Test
    fun `the readout says how open the collar is, what the flame is and how hot`() {
        assertEquals("collar 0% open · yellow flame · 1,000 °C", Bunsen.readoutOf(0.0))
        assertEquals("collar 100% open · blue cone · 1,500 °C", Bunsen.readoutOf(1.0))
        assertEquals("collar 50% open · yellow tips · 1,250 °C", Bunsen.readoutOf(0.5))
    }
}
