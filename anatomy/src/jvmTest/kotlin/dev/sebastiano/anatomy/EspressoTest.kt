package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.figures.Espresso
import dev.sebastiano.anatomy.figures.EspressoState
import dev.sebastiano.anatomy.figures.Pressurestat
import dev.sebastiano.anatomy.figures.Shot
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EspressoTest {
    @Test
    fun `boiler water boils at the steam-table temperature of its pressure`() {
        // Home-Barista's table: 1.0 bar is 253 °F (122.8 °C), 1.2 bar is 257 °F (125.0 °C).
        assertEquals(122.8, Espresso.saturationC(1.0), 0.6)
        assertEquals(125.0, Espresso.saturationC(1.2), 0.6)
        assertTrue(Espresso.saturationC(1.2) > Espresso.saturationC(1.0))
    }

    @Test
    fun `the vibration pump delivers about 260 ml a minute against 9 bar and none dead-headed`() {
        assertEquals(260.0, Espresso.pumpFlow(9.0) * 60, 13.0)
        assertEquals(600.0, Espresso.pumpFlow(0.0) * 60, 1e-9)
        assertEquals(0.0, Espresso.pumpFlow(Espresso.PUMP_DEAD_BAR), 1e-9)
    }

    @Test
    fun `brew pressure builds to the OPV setting and never past it`() {
        assertEquals(0.0, Espresso.brewPressure(0.0), 1e-9)
        var last = 0.0
        for (step in 1..400) {
            val p = Espresso.brewPressure(step * 0.1)
            assertTrue(p >= last && p <= Espresso.BREW_BAR, "at ${step * 0.1} s: $p")
            last = p
        }
        assertTrue(Espresso.brewPressure(15.0) > 8.9)
    }

    @Test
    fun `the cup stays dry until the puck is soaked, then fills to 36 g in 25 to 30 s`() {
        assertEquals(0.0, Espresso.yieldAt(Espresso.FIRST_DROPS_S - 0.5), 1e-9)
        val time = Espresso.shotTime()
        assertTrue(time in 25.0..30.0, "shot time $time")
        assertEquals(Espresso.TARGET_G, Espresso.yieldAt(time), 0.05)
    }

    @Test
    fun `whatever the pump delivers beyond the puck goes back to the tank through the OPV`() {
        assertEquals(0.0, Espresso.bypassFlow(1.0), 1e-9)
        val t = 20.0
        val p = Espresso.brewPressure(t)
        assertEquals(
            Espresso.pumpFlow(p) - Espresso.puckFlow(t),
            Espresso.bypassFlow(t),
            1e-9,
        )
        assertTrue(Espresso.bypassFlow(t) > 0)
    }

    @Test
    fun `a shot runs from the lever and stops itself at the target yield`() {
        val shot = Shot()
        assertFalse(shot.running)
        shot.pull()
        assertTrue(shot.running)
        repeat(3_000) { shot.advance(0.01) }
        assertFalse(shot.running)
        assertEquals(Espresso.TARGET_G, shot.grams, 0.1)
        assertEquals(Espresso.shotTime(), shot.time, 0.02)
    }

    @Test
    fun `pulling the lever down early stops the shot where it is`() {
        val shot = Shot()
        shot.pull()
        repeat(1_000) { shot.advance(0.01) }
        shot.pull()
        assertFalse(shot.running)
        val grams = shot.grams
        repeat(100) { shot.advance(0.01) }
        assertEquals(grams, shot.grams, 1e-9)
        assertEquals(Espresso.yieldAt(10.0), grams, 0.05)
    }

    @Test
    fun `the pressurestat heats below cut-in, stops above cut-out and holds in between`() {
        val stat = Pressurestat()
        assertTrue(stat.update(0.9))
        assertTrue(stat.update(1.1))
        assertFalse(stat.update(1.21))
        assertFalse(stat.update(1.1))
        assertTrue(stat.update(0.99))
    }

    @Test
    fun `the readout reads the shot from the model`() {
        val shot = Shot()
        assertTrue(Espresso.readoutOf(shot, 1.1).startsWith("ready · boiler 1.1 bar · "))
        shot.pull()
        repeat(1_500) { shot.advance(0.01) }
        val grams = Espresso.yieldAt(15.0)
        assertEquals("shot 15 s · 9.0 bar · ${grams.toInt()} g", Espresso.readoutOf(shot, 1.1))
        repeat(2_000) { shot.advance(0.01) }
        val seconds = Espresso.shotTime().toInt()
        assertEquals("36 g in $seconds s · boiler 1.1 bar", Espresso.readoutOf(shot, 1.1))
        assertTrue(abs(shot.grams - 36.0) < 0.1)
    }

    @Test
    fun `under reduced motion the gauge drops to zero when a shot ends by itself`() {
        val state = EspressoState().apply { touring = false }
        state.shot.pull()
        var frames = 0
        while (state.tick(DT, calm = true)) {
            frames++
            check(frames < 10_000) { "the shot never ended" }
        }
        assertFalse(state.shot.running)
        assertTrue(state.view.pressure < 0.05, "gauge left at ${state.view.pressure} bar")
    }

    @Test
    fun `under reduced motion the gauge drops to zero when a shot is stopped`() {
        val state = EspressoState().apply { touring = false }
        state.shot.pull()
        repeat(300) { state.tick(DT, calm = true) }
        state.shot.pull()
        assertFalse(state.tick(DT, calm = true), "the loop should settle at once")
        assertTrue(state.view.pressure < 0.05, "gauge left at ${state.view.pressure} bar")
    }

    private companion object {
        const val DT = 1.0 / 60
    }
}
