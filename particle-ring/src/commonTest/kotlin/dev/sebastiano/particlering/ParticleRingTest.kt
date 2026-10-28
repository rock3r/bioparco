package dev.sebastiano.particlering

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParticleRingTest {
    private val count = 900
    private val frame = 1f / 60f

    @Test
    fun everyParticleWaitsInTheIconUntilTheBurst() {
        val ring = ParticleRing(count)
        ring.step(0.5f)
        assertEquals(RingPhase.Icon, ring.phase)
        assertTrue(
            (0 until count).all { ring.depth[it] < 0f },
            "a particle showed before the burst",
        )
        assertEquals(0f, ring.launchedShare(0.5f))
    }

    @Test
    fun theBurstLaunchesParticlesOverTimeFromTheIcon() {
        val ring = ParticleRing(count)
        ring.step(0f)
        assertTrue(ring.advance(1f))
        assertEquals(RingPhase.Burst, ring.phase)
        val early = ring.launchedShare(1.2f)
        val later = ring.launchedShare(1.8f)
        assertTrue(early in 0.01f..0.6f, "launched $early after 0.2 s")
        assertTrue(later > early)
        assertEquals(1f, ring.launchedShare(1f + ParticleRing.LAST_LAUNCH + 0.01f))

        // A particle first shows by the icon, a frame's flight from where it waited.
        val shown = BooleanArray(count)
        var now = 1f
        while (now < 1f + ParticleRing.BURST_SECONDS) {
            now += frame
            ring.step(now)
            for (i in 0 until count) {
                if (shown[i] || ring.depth[i] < 0f) continue
                shown[i] = true
                assertTrue(
                    abs(ring.x[i]) < ParticleRing.ICON_HALF * 1.5f &&
                        abs(ring.y[i] - ParticleRing.ICON_Y) < ParticleRing.ICON_HALF * 1.5f,
                    "particle $i first showed at (${ring.x[i]}, ${ring.y[i]})",
                )
            }
        }
        assertTrue(shown.all { it }, "some particles never launched")
    }

    @Test
    fun theBurstLandsEveryParticleOnItsPlaceInTheRing() {
        val ring = ParticleRing(count)
        ring.step(0f)
        ring.advance(0f)
        val now = run(ring, from = 0f, until = ParticleRing.BURST_SECONDS + 0.05f)
        assertEquals(RingPhase.Ring, ring.phase)
        for (i in 0 until count) {
            assertEquals(ring.ringX[i], ring.x[i], 1e-5f, "particle $i is off the ring at $now")
            assertEquals(ring.ringY[i], ring.y[i], 1e-5f, "particle $i is off the ring at $now")
        }
    }

    @Test
    fun noParticleEverJumps() {
        val ring = ParticleRing(count)
        ring.step(0f)
        ring.advance(0f)
        val lastX = FloatArray(count)
        val lastY = FloatArray(count)
        val wasShown = BooleanArray(count)
        var now = 0f
        var collapsed = false
        while (ring.phase != RingPhase.Logo || now < 20f) {
            now += frame
            if (!collapsed && ring.phase == RingPhase.Ring && now > 6f) {
                // Mid-swirl, with the ring spun and tilted by a drag.
                collapsed = ring.advance(now)
            }
            // A drag from 4 s to 5 s: the spin winds up and the tilt leans in and back.
            val spin = (now - 4f).coerceIn(0f, 1f) * 3f
            val tilt = if (now in 4f..5f) 0.3f * sin(PI.toFloat() * (now - 4f)) else 0f
            ring.step(now, spin = spin, tilt = tilt)
            for (i in 0 until count) {
                if (ring.depth[i] < 0f) continue
                if (wasShown[i]) {
                    val moved = hypot(ring.x[i] - lastX[i], ring.y[i] - lastY[i])
                    assertTrue(moved < 0.2f, "particle $i jumped $moved at $now in ${ring.phase}")
                }
                wasShown[i] = true
                lastX[i] = ring.x[i]
                lastY[i] = ring.y[i]
            }
            if (now > 30f) break
        }
        assertTrue(collapsed)
        assertEquals(RingPhase.Logo, ring.phase)
    }

    @Test
    fun theRingIsASlantedBandWithAHole() {
        val ring = swirling()
        for (i in 0 until count) {
            val x = ring.x[i]
            val y = ring.y[i]
            assertTrue(abs(x) < 1.75f && abs(y) < 1.1f, "particle $i is out at ($x, $y)")
            // The band's inner edge hangs low, so the hole shows a little below the centre.
            assertTrue(hypot(x, y - 0.15f) > 0.15f, "particle $i is in the hole at ($x, $y)")
        }
        val xs = (0 until count).map { ring.x[it] }
        val ys = (0 until count).map { ring.y[it] }
        val wide = xs.max() - xs.min()
        val tall = ys.max() - ys.min()
        assertTrue(wide > tall * 1.6f, "seen from above at a slant: $wide wide, $tall tall")
    }

    @Test
    fun theFrontOfTheRingIsNearerAndBrighterThanTheBack() {
        val ring = swirling()
        // The ring is rolled a little, so compare along its own vertical axis.
        val front = (0 until count).filter { ring.ringFront[it] > 0.3f }
        val back = (0 until count).filter { ring.ringFront[it] < -0.3f }
        assertTrue(front.size > count / 10 && back.size > count / 10)
        val frontDepth = front.map { ring.depth[it] }.average()
        val backDepth = back.map { ring.depth[it] }.average()
        assertTrue(frontDepth > backDepth + 0.3, "front $frontDepth vs back $backDepth")
        assertTrue(ring.depth.all { it in 0f..1f })
    }

    @Test
    fun aWholeTurnOfSpinLooksTheSame() {
        val a = swirling()
        val b = ParticleRing(count)
        b.step(0f)
        b.advance(0f)
        run(b, from = 0f, until = ParticleRing.BURST_SECONDS + 1f, spin = 2f * PI.toFloat())
        for (i in 0 until count) {
            assertEquals(a.x[i], b.x[i], 1e-3f)
            assertEquals(a.y[i], b.y[i], 1e-3f)
        }
    }

    @Test
    fun theCollapseFoldsTheRingIntoAHeartInsideAnArc() {
        val ring = swirling()
        val start = ParticleRing.BURST_SECONDS + 1f
        assertTrue(ring.advance(start))
        assertEquals(RingPhase.Collapse, ring.phase)
        assertEquals(0f, ring.logoShare(start))
        run(ring, from = start, until = start + ParticleRing.COLLAPSE_SECONDS + 0.05f)
        assertEquals(RingPhase.Logo, ring.phase)
        assertEquals(1f, ring.logoShare(start + ParticleRing.COLLAPSE_SECONDS + 1f))

        var inHeart = 0
        for (i in 0 until count) {
            val x = ring.x[i]
            val y = ring.y[i] - ParticleRing.HEART_Y
            val heartX = x / ParticleRing.HEART_WIDTH
            val heartY = y / ParticleRing.HEART_WIDTH
            if (HeartShape.contains(heartX, heartY)) {
                inHeart++
                continue
            }
            val radius = hypot(x, ring.y[i])
            assertEquals(ParticleRing.ARC_RADIUS, radius, ParticleRing.ARC_BAND, "particle $i")
            // The arc leaves a gap at the bottom, a quarter turn wide.
            val fromBottom = abs(atan2(x, ring.y[i]))
            assertTrue(fromBottom > PI / 4 - 0.02, "particle $i sits in the gap")
        }
        val share = inHeart / count.toFloat()
        assertEquals(ParticleRing.HEART_SHARE, share, 0.04f)
    }

    @Test
    fun spaceStepsThroughThePhasesAndReplays() {
        val ring = ParticleRing(count)
        ring.step(0f)
        assertTrue(ring.advance(0f))
        assertFalse(ring.advance(0.5f), "the burst plays out")
        var now = run(ring, from = 0f, until = ParticleRing.BURST_SECONDS + 0.1f)
        assertTrue(ring.advance(now))
        assertFalse(ring.advance(now + 0.1f), "the collapse plays out")
        now = run(ring, from = now, until = now + ParticleRing.COLLAPSE_SECONDS + 0.1f)
        assertEquals(RingPhase.Logo, ring.phase)
        assertTrue(ring.advance(now))
        assertEquals(RingPhase.Icon, ring.phase)
        ring.step(now + frame)
        assertTrue((0 until count).all { ring.depth[it] < 0f }, "the replay starts hidden")
        assertEquals(0f, ring.iconShare(now), "the icon fades back in")
        assertEquals(1f, ring.iconShare(now + 1f))
    }

    @Test
    fun theIconFadesOnceTheBurstIsUnderWay() {
        val ring = ParticleRing(count)
        assertEquals(1f, ring.iconShare(0f))
        ring.advance(1f)
        assertEquals(1f, ring.iconShare(1.1f))
        assertEquals(0f, ring.iconShare(1f + ParticleRing.BURST_SECONDS))
    }

    @Test
    fun hitTestsFindTheIconAndTheLogo() {
        assertTrue(ParticleRing.isOnIcon(0f, ParticleRing.ICON_Y))
        assertTrue(ParticleRing.isOnIcon(ParticleRing.ICON_HALF - 0.01f, ParticleRing.ICON_Y))
        assertFalse(ParticleRing.isOnIcon(0f, 0f))
        assertFalse(ParticleRing.isOnIcon(ParticleRing.ICON_HALF + 0.05f, ParticleRing.ICON_Y))
        assertTrue(ParticleRing.isOnLogo(0f, 0f))
        assertTrue(ParticleRing.isOnLogo(ParticleRing.ARC_RADIUS, 0f))
        assertFalse(ParticleRing.isOnLogo(1.2f, 0.8f))
    }

    private fun swirling(): ParticleRing {
        val ring = ParticleRing(count)
        ring.step(0f)
        ring.advance(0f)
        run(ring, from = 0f, until = ParticleRing.BURST_SECONDS + 1f)
        return ring
    }

    /** Steps [ring] a frame at a time from [from] to [until], ending exactly at [until]. */
    private fun run(ring: ParticleRing, from: Float, until: Float, spin: Float = 0f): Float {
        var now = from
        while (now < until) {
            now = minOf(now + frame, until)
            ring.step(now, spin = spin)
        }
        return now
    }
}
