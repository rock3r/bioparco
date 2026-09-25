package dev.sebastiano.achievementbadge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BadgeTimelineTest {
    @Test
    fun theStageStartsEmpty() {
        val frame = badgeFrame(0f)
        assertEquals(0f, frame.ghostAlpha)
        assertEquals(0f, frame.backScale)
        assertEquals(0f, frame.frontScale)
        assertEquals(0f, frame.trophyAlpha)
        assertEquals(0f, frame.fanReveal)
        assertEquals(0f, frame.neonTop)
        assertEquals(0f, frame.raysAlpha)
    }

    @Test
    fun everythingRestsOnceTheIntroIsOver() {
        val frame = badgeFrame(BadgeTimeline.SETTLED + 0.01f)
        assertEquals(1f, frame.backScale, TOLERANCE)
        assertEquals(0f, frame.backRotation, TOLERANCE)
        assertEquals(1f, frame.frontScale, TOLERANCE)
        assertEquals(0f, frame.frontRotation, TOLERANCE)
        assertEquals(1f, frame.trophyScale, TOLERANCE)
        assertEquals(0f, frame.trophyOffset, TOLERANCE)
        assertEquals(1f, frame.handleScale, TOLERANCE)
        assertEquals(1f, frame.fanReveal, TOLERANCE)
        assertEquals(1f, frame.neonTop, TOLERANCE)
        assertEquals(1f, frame.neonLow, TOLERANCE)
        assertEquals(0f, frame.goldRing.alpha, TOLERANCE)
        assertEquals(0f, frame.pinkRing.alpha, TOLERANCE)
        assertTrue(frame.shine < 0f, "the shine sweep is over")
    }

    @Test
    fun theStarsOvershootBeforeSettling() {
        val peak = (0..200).maxOf { badgeFrame(it / 100f).backScale }
        assertTrue(peak > 1.02f, "expected an overshoot, got $peak")
    }

    @Test
    fun theNeonOnlyEverGrows() {
        var previous = 0f
        for (step in 0..300) {
            val frame = badgeFrame(step / 100f)
            assertTrue(frame.neonTop >= previous)
            previous = frame.neonTop
        }
    }

    @Test
    fun theRaysKeepTurningAfterTheIntro() {
        val a = badgeFrame(3f).raysRotation
        val b = badgeFrame(4f).raysRotation
        assertEquals(BadgeTimeline.RAYS_DEGREES_PER_SECOND, b - a, TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 0.0005f
    }
}
