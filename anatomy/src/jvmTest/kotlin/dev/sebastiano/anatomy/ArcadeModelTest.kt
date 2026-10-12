package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.figures.Arcade
import dev.sebastiano.anatomy.figures.ArcadeCoinMotion
import dev.sebastiano.anatomy.figures.ArcadeGame
import dev.sebastiano.anatomy.figures.ArcadeMachine
import dev.sebastiano.anatomy.figures.ArcadeParts
import dev.sebastiano.anatomy.figures.ArcadePulse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArcadeModelTest {
    @Test
    fun quarterRollsOnThirtyDegreeChuteThenFallsAndLiesFlat() {
        val coin = ArcadeCoinMotion.newCoin(7, 0)
        coin.t = 1.86
        val roll = ArcadeCoinMotion.pose(coin)
        assertEquals("roll", roll.stage)
        assertEquals(-42.0, roll.p.x)
        assertEquals(
            Arcade.chuteZ(roll.p.y + Arcade.COIN_R * 0.5) +
                Arcade.COIN_R * kotlin.math.sqrt(3.0) / 2,
            roll.p.z,
            1e-12,
        )
        assertEquals(128.45938771849955, roll.p.y, 1e-10)
        coin.t = 2.1
        assertEquals("drop", ArcadeCoinMotion.pose(coin).stage)
        coin.t = 3.0
        val landed = ArcadeCoinMotion.pose(coin)
        assertTrue(landed.settled)
        assertEquals(coin.landAt, landed.p)
        assertEquals(Arcade.Z, landed.axis)
    }

    @Test
    fun switchMetersOnceAndRelayPrecedesCreditByThirtyMilliseconds() {
        val m = ArcadeMachine()
        m.touring = false
        m.insertCoin()
        while (m.meter == 4127) m.tick(0.001, false)
        assertEquals(4128, m.meter)
        assertEquals(0, m.credits)
        assertFalse(m.power)
        repeat(419) { m.tick(0.001, false) }
        assertFalse(m.power)
        m.tick(0.002, false)
        assertTrue(m.power)
        assertEquals(0, m.credits)
        repeat(30) { m.tick(0.001, false) }
        assertEquals(1, m.credits)
        repeat(600) { m.tick(0.001, false) }
        assertEquals(4128, m.meter)
        assertEquals(1, m.pile)
        assertTrue(m.coins.isEmpty())
    }

    @Test
    fun heaterSelfTestBlankingAndAttractKeepTheirOriginalBoundaries() {
        val m = ArcadeMachine()
        m.touring = false
        m.setPower(true)
        repeat(89) { m.tick(0.01, false) }
        assertEquals("none", m.picture().mode)
        m.tick(0.02, false)
        assertEquals("test", m.picture().mode)
        repeat(268) { m.tick(0.01, false) }
        assertEquals("test", m.picture().mode)
        repeat(2) { m.tick(0.01, false) }
        assertEquals("none", m.picture().mode)
        repeat(8) { m.tick(0.01, false) }
        assertEquals("attract", m.picture().mode)
        m.setPower(false)
        repeat(15) { m.tick(0.01, false) }
        assertEquals(0.01, m.picture().sx, 1e-12)
        assertTrue(m.picture().sy in 0.0..1.0)
        repeat(105) { m.tick(0.01, false) }
        assertEquals(0.0, m.picture().dot)
    }

    @Test
    fun reducedMotionAcceptsCoinsWithoutTourOrTransientEffects() {
        val m = ArcadeMachine()
        m.insertCoin()
        m.tick(1.0 / 60, true)
        assertEquals(1, m.pile)
        assertEquals(4128, m.meter)
        assertEquals(1, m.credits)
        assertTrue(m.power)
        assertTrue(m.coins.isEmpty())
        assertTrue(m.pulses.isEmpty())
        assertFalse(m.touring)
    }

    @Test
    fun joystickClosesOppositeSwitchOnlyPastItsThreshold() {
        assertEquals(emptyList(), ArcadeParts.closed(0.12, -0.12))
        assertEquals(listOf("left", "down"), ArcadeParts.closed(0.121, -0.121))
        assertEquals(listOf("right", "up"), ArcadeParts.closed(-0.21, 0.21))
    }

    @Test
    fun cablePulseWaitsThenTravelsFromFirstChunkToTheMiddleAndLeavesTheEnd() {
        val pulse = ArcadePulse("cable.video", -0.5, 0.5)
        fun hot() = (0..10).filter { pulse.hot(it, 11) }
        assertEquals(emptyList(), hot())
        pulse.t = 0.0
        assertEquals(listOf(0), hot())
        pulse.t = 0.25
        assertEquals(listOf(5, 6), hot())
        pulse.t = 0.5
        assertEquals(emptyList(), hot())
    }

    @Test
    fun attractGameHonoursUserInputAndScoresHitsRatherThanAdvancingDuringSelfTest() {
        val m = ArcadeMachine()
        val g = ArcadeGame { 0.5 }
        g.tick(m, 0.1, "test")
        assertEquals(112.0, g.shipX)
        assertEquals(0.0, g.offset)
        g.userAt = m.clock
        m.stick = -1.0
        g.tick(m, 1.0, "attract")
        assertEquals(14.0, g.shipX)
        m.stick = 1.0
        g.tick(m, 1.0, "attract")
        assertEquals(210.0, g.shipX)
        m.stick = 0.0
        g.shipX = 43.0
        m.fire = 1.0
        repeat(60) { g.tick(m, 0.01, "attract") }
        assertEquals(17, g.alive.count { it })
        assertEquals(10, g.score)
    }

    @Test
    fun aSecondQuarterAddsCreditWithoutRestartingThePoweredBoard() {
        val m = ArcadeMachine()
        m.touring = false
        m.insertCoin()
        repeat(800) { m.tick(0.01, false) }
        val since = m.powerAt
        m.insertCoin()
        repeat(300) { m.tick(0.01, false) }
        assertEquals(2, m.credits)
        assertEquals(2, m.pile)
        assertEquals(4129, m.meter)
        assertEquals(since, m.powerAt)
        assertEquals("attract", m.picture().mode)
        assertEquals("Attract mode, 1 credit.", m.status)
        m.setPower(false)
        assertEquals(0, m.credits)
    }
}
