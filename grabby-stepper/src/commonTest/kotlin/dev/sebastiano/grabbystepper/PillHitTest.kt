package dev.sebastiano.grabbystepper

import kotlin.test.Test
import kotlin.test.assertEquals

class PillHitTest {
    private val layout = RestLayout(width = 208f, height = 64f, thumbDiameter = 56f, inset = 4f)

    @Test
    fun overflowGuttersMiss() {
        assertEquals(PillHit.None, hitTestPill(-40f, layout.centerY, layout))
        assertEquals(PillHit.None, hitTestPill(layout.width + 40f, layout.centerY, layout))
        assertEquals(PillHit.None, hitTestPill(layout.centerX, -20f, layout))
        assertEquals(PillHit.None, hitTestPill(layout.centerX, layout.height + 20f, layout))
    }

    @Test
    fun visibleButtonsAndThumbHit() {
        assertEquals(PillHit.Minus, hitTestPill(12f, layout.centerY, layout))
        assertEquals(PillHit.Plus, hitTestPill(layout.width - 12f, layout.centerY, layout))
        assertEquals(PillHit.Thumb, hitTestPill(layout.centerX, layout.centerY, layout))
    }
}
