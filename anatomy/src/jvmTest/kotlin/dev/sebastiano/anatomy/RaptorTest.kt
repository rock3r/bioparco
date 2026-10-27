package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.figures.BurnPhase
import dev.sebastiano.anatomy.figures.Raptor
import dev.sebastiano.anatomy.figures.RaptorBurn
import dev.sebastiano.anatomy.figures.RaptorControls
import dev.sebastiano.anatomy.figures.RaptorLive
import dev.sebastiano.anatomy.figures.RaptorNumbers
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Fig 6's engine numbers and its live behaviour: the explode, the selection and the burn. */
class RaptorTest {
    private val dt = 1.0 / 60

    private fun live() = RaptorLive().apply { touring = false }

    private fun RaptorLive.run(seconds: Double, onFrame: RaptorLive.() -> Unit = {}) {
        repeat((seconds / dt).toInt()) {
            tick(dt, calm = false)
            onFrame()
        }
    }

    @Test
    fun theEngineNumbersAreRaptor2s() {
        assertEquals(1630, RaptorNumbers.DRY_KG)
        assertEquals(300.0, RaptorNumbers.bar(1.0))
        // 1 atm on the 1.3 m exit: 13.7 tf more in vacuum than at sea level.
        assertEquals(13.7, RaptorNumbers.BACK_TF, 0.05)
        assertEquals(230.0, RaptorNumbers.thrust(1.0), 1e-9)
        assertEquals(0.0, RaptorNumbers.thrust(0.05))
        assertEquals("1,630", RaptorNumbers.fmt(1630.4))
    }

    @Test
    fun theNozzleLeavesFirstAndTheGimbalLast() {
        val live = live()
        live.setApart(true)
        val started = LinkedHashMap<String, Double>()
        live.run(1.0) {
            for ((id, spring) in springs) if (spring.x > 0.01 && id !in started) started[id] = clock
        }
        assertEquals(
            listOf(
                "nozzle",
                "downcomer",
                "controller",
                "plumbing",
                "lox",
                "ftp",
                "hotgas",
                "chamber",
                "orpb",
                "otp",
                "gimbal",
                "stand",
            ),
            started.keys.toList(),
        )
        live.run(2.0)
        assertTrue(live.springs.values.all { it.x == 1.0 }, "every spring settles apart")
        assertEquals("apart · 11 assemblies", live.readout())
    }

    @Test
    fun reversingMidwayReschedulesTheSpringsStillToGo() {
        val live = live()
        live.setApart(true)
        live.run(0.1)
        assertTrue(live.springs.getValue("gimbal").pending != null, "the gimbal hasn't left yet")
        live.setApart(false)
        assertEquals(
            null,
            live.springs.getValue("gimbal").pending,
            "a spring already at its goal drops its pending move",
        )
        assertEquals("closing up", live.readout())
        live.run(3.0)
        assertTrue(live.springs.values.all { it.x == 0.0 })
        assertEquals("1,630 kg · cold", live.readout())
    }

    @Test
    fun arrowKeysWalkTheLegendAndLightOneAssembly() {
        val live = live()
        live.step(1)
        assertEquals("gimbal", live.selected)
        live.step(1)
        live.step(1)
        assertEquals("orpb", live.selected)
        live.step(-1)
        assertEquals("otp", live.selected)
        live.step(null, 10)
        assertEquals("nozzle", live.selected)
        assertEquals(11, live.valueNow)
        live.select("stand")
        assertEquals(null, live.selected, "the stand can't be selected")
    }

    @Test
    fun aBurnArmsVentsLightsThePreburnersRunsMainStageAndShutsDown() {
        val live = live()
        live.setApart(true)
        live.run(2.0)
        live.ignite()
        val phases = ArrayList<BurnPhase>()
        var peak = 0.0
        live.run(14.0) {
            if (phases.lastOrNull() != burn.phase) phases += burn.phase
            peak = maxOf(peak, burn.pc)
        }
        assertEquals(
            listOf(
                BurnPhase.Arming,
                BurnPhase.ChillDown,
                BurnPhase.Preburners,
                BurnPhase.Main,
                BurnPhase.Shutdown,
                BurnPhase.Cold,
            ),
            phases,
        )
        assertTrue(abs(peak - 1.0) < 0.01, "main stage reaches full chamber pressure, $peak")
        assertFalse(live.burn.on)
        assertEquals("1,630 kg · cold", live.readout())
    }

    @Test
    fun mainStageReadsChamberPressureAndThrust() {
        val live = live()
        live.ignite()
        live.run(RaptorBurn.MAIN + 6)
        assertEquals("Pc 300 bar · 230 tf", live.readout())
        live.toggleFire()
        live.run(0.05)
        assertTrue(live.readout().startsWith("shutdown · "), live.readout())
    }

    @Test
    fun theTourTakesItApartListsEveryAssemblyAndFires() {
        assertEquals(false, RaptorLive.tourAt(0.2).apart)
        assertEquals(true, RaptorLive.tourAt(1.0).apart)
        assertEquals("gimbal", RaptorLive.tourAt(1.4).part)
        assertEquals("nozzle", RaptorLive.tourAt(1.3 + 10 * 1.5 + 0.1).part)
        assertEquals(true, RaptorLive.tourAt(1.3 + 11 * 1.5 + 1.5).fire)
    }

    @Test
    fun `turning reduced motion on mid-explode finishes the explode at once`() {
        val live = RaptorLive().apply { touring = false }
        live.setApart(true)
        live.tick(1.0 / 60, calm = false) // Only the first assemblies have started.
        assertFalse(live.tick(1.0 / 60, calm = true), "a calm frame should settle everything")
        assertTrue(live.springs.values.all { it.x == 1.0 && it.pending == null })
    }

    @Test
    fun `a tap takes the engine apart and lights the part, and it stays apart after the finger lifts`() {
        val live = RaptorLive().apply { touring = false }
        var closes = 0
        val controls =
            RaptorControls(live, quiet = {}, kick = {}, later = { closes++ }, cancel = {})
        val part = Raptor.ORDER.last()
        controls.tap(part)
        controls.lift()
        assertTrue(live.apart)
        assertEquals(part, live.selected)
        assertFalse(live.inside, "a lifted finger is not over the engine")
        assertEquals(0, closes, "lifting a finger must not close the engine")
    }
}
