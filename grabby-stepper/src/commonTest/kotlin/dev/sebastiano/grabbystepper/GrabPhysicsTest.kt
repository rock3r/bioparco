package dev.sebastiano.grabbystepper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GrabPhysicsTest {
    private val rest = RestLayout(width = 208f, height = 64f, thumbDiameter = 56f, inset = 4f)

    @Test
    fun stretchGrowsRightSubtlyWithPositiveOffset() {
        val stretched = stretchTrack(rest, offsetX = 40f, offsetY = 0f, follow = 1f)
        assertEquals(rest.restLeft, stretched.left)
        assertTrue(stretched.right > rest.restRight)
        assertTrue(stretched.right < rest.restRight + 40f)
        assertEquals(rest.restTop, stretched.top)
        assertEquals(rest.restBottom, stretched.bottom)
    }

    @Test
    fun stretchGrowsLeftSubtlyWithNegativeOffset() {
        val stretched = stretchTrack(rest, offsetX = -24f, offsetY = 0f, follow = 1f)
        assertTrue(stretched.left < rest.restLeft)
        assertTrue(stretched.left > rest.restLeft - 24f)
        assertEquals(rest.restRight, stretched.right)
    }

    @Test
    fun verticalFollowCouplesThenDetaches() {
        val coupled = stretchTrack(rest, offsetX = 0f, offsetY = 30f, follow = 1f)
        assertTrue(coupled.bottom > rest.restBottom)
        val detached = stretchTrack(rest, offsetX = 0f, offsetY = 30f, follow = 0f)
        assertEquals(rest.restBottom, detached.bottom, absoluteTolerance = 0.01f)
    }
}
