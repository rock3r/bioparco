package dev.sebastiano.particlering

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RingViewTest {
    private val frame = 1f / 60f

    @Test
    fun aSidewaysDragSpinsTheRingAndTheSpinCoasts() {
        val view = RingView()
        view.press()
        repeat(6) {
            view.dragBy(0.08f, 0f)
            view.step(frame)
        }
        view.release()
        val atRelease = view.spin
        assertTrue(abs(atRelease) > 0.2f, "a drag of half a ring spun it by $atRelease")
        run(view, 0.3f)
        val coasted = view.spin - atRelease
        assertTrue(coasted * atRelease > 0f, "the spin went on the same way: $coasted")
        run(view, 6f)
        val settled = view.spin
        run(view, 1f)
        assertEquals(settled, view.spin, 1e-3f, "the spin comes to rest")
    }

    @Test
    fun anUpDownDragTiltsTheRingWithinItsLimitAndItSpringsBack() {
        val view = RingView()
        view.press()
        view.dragBy(0f, 0.3f)
        view.step(frame)
        val small = view.tilt
        assertTrue(small > 0f, "dragging down tilts the ring towards the viewer: $small")
        view.dragBy(0f, 20f)
        view.step(frame)
        assertTrue(view.tilt <= RingView.MAX_TILT, "tilt ${view.tilt} over the limit")
        assertTrue(view.tilt > small)
        view.release()
        run(view, 3f)
        assertEquals(0f, view.tilt, 1e-3f, "the tilt springs back")
    }

    @Test
    fun aLongPauseDoesNotFlingTheRing() {
        val view = RingView()
        view.press()
        view.dragBy(0.5f, 0.5f)
        view.step(frame)
        view.release()
        val spin = view.spin
        view.step(2f)
        assertTrue(abs(view.tilt) <= RingView.MAX_TILT)
        assertTrue(
            abs(view.spin - spin) < 1f,
            "a stalled frame spun the ring by ${view.spin - spin}",
        )
    }

    private fun run(view: RingView, seconds: Float) {
        var t = 0f
        while (t < seconds) {
            view.step(frame)
            t += frame
        }
    }
}
