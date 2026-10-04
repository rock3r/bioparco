package dev.sebastiano.honeycomb

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HoneycombMathTest {
    @Test
    fun `opacity stays full inside the focus radius and is gone at the fade radius`() {
        val inside =
            cellFocus(
                distance = 40f,
                fullOpacityRadius = 55f,
                fadeRadius = 300f,
                minScale = 0.25f,
                scaleFalloff = 400f,
            )
        assertEquals(1f, inside.alpha, 0.001f)
        val halfway =
            cellFocus(
                distance = (55f + 300f) / 2f,
                fullOpacityRadius = 55f,
                fadeRadius = 300f,
                minScale = 0.25f,
                scaleFalloff = 400f,
            )
        assertEquals(0.5f, halfway.alpha, 0.001f)
        val edge =
            cellFocus(
                distance = 300f,
                fullOpacityRadius = 55f,
                fadeRadius = 300f,
                minScale = 0.25f,
                scaleFalloff = 400f,
            )
        assertEquals(0f, edge.alpha, 0.001f)
        val beyond =
            cellFocus(
                distance = 480f,
                fullOpacityRadius = 55f,
                fadeRadius = 300f,
                minScale = 0.25f,
                scaleFalloff = 400f,
            )
        assertEquals(0f, beyond.alpha, 0.001f)
    }

    @Test
    fun `scale is full at the centre and falls linearly to the minimum across the falloff`() {
        val centre =
            cellFocus(
                distance = 0f,
                fullOpacityRadius = 55f,
                fadeRadius = 300f,
                minScale = 0.25f,
                scaleFalloff = 400f,
            )
        assertEquals(1f, centre.scale, 0.001f)
        val mid =
            cellFocus(
                distance = 200f,
                fullOpacityRadius = 55f,
                fadeRadius = 300f,
                minScale = 0.25f,
                scaleFalloff = 400f,
            )
        assertEquals(0.5f, mid.scale, 0.001f)
        val floor =
            cellFocus(
                distance = 400f,
                fullOpacityRadius = 55f,
                fadeRadius = 300f,
                minScale = 0.25f,
                scaleFalloff = 400f,
            )
        assertEquals(0.25f, floor.scale, 0.001f)
        val past =
            cellFocus(
                distance = 900f,
                fullOpacityRadius = 55f,
                fadeRadius = 300f,
                minScale = 0.25f,
                scaleFalloff = 1000f,
            )
        assertEquals(0.25f, past.scale, 0.001f)
    }

    @Test
    fun `rows nestle by half a pitch and every neighbour sits one pitch away`() {
        val tile = 120f
        val gap = 12f
        val pitch = tile + gap
        val origin = cellCenter(row = 0, column = 0, tile = tile, gap = gap)
        val right = cellCenter(row = 0, column = 1, tile = tile, gap = gap)
        val below = cellCenter(row = 1, column = 0, tile = tile, gap = gap)
        val nestled = cellCenter(row = 1, column = 1, tile = tile, gap = gap)
        assertEquals(pitch, right.x - origin.x, 0.01f)
        assertEquals(0f, right.y - origin.y, 0.01f)
        assertEquals(pitch / 2f, abs(origin.x - below.x), 0.01f)
        assertEquals(pitch, hypot(origin.x - below.x, origin.y - below.y), 0.05f)
        assertEquals(pitch, hypot(right.x - nestled.x, right.y - nestled.y), 0.05f)
    }

    @Test
    fun `pan stays inside the cell field`() {
        val min = Offset(-100f, -40f)
        val max = Offset(80f, 60f)
        assertEquals(Offset(12f, -8f), clampPan(Offset(12f, -8f), min, max))
        assertEquals(Offset(100f, 40f), clampPan(Offset(400f, 400f), min, max))
        assertEquals(Offset(-80f, -60f), clampPan(Offset(-400f, -400f), min, max))
    }

    @Test
    fun `a flick coasts with its velocity and a resting pan stays put`() {
        val stayed =
            coastTarget(
                position = Offset(10f, -4f),
                velocity = Offset.Zero,
                responseSeconds = 0.4f,
                damping = 0.85f,
            )
        assertEquals(10f, stayed.x, 0.001f)
        assertEquals(-4f, stayed.y, 0.001f)
        val coast =
            coastTarget(
                position = Offset(3f, 5f),
                velocity = Offset(1000f, -500f),
                responseSeconds = 0.4f,
                damping = 0.85f,
            )
        assertTrue(coast.x > 3f)
        assertTrue(coast.y < 5f)
        // Not a snap onto a cell pitch: the landing is a fraction of the velocity.
        assertTrue(coast.x - 3f < 1000f)
        assertTrue(5f - coast.y < 500f)
    }

    @Test
    fun `the centred layout puts the middle of the field on the focus`() {
        val layout = honeycombLayout(rows = 15, columns = 20, tile = 120f, gap = 12f)
        assertEquals(0.0, layout.cells.map { it.center.x }.average(), 0.02)
        assertEquals(0.0, layout.cells.map { it.center.y }.average(), 0.02)
        val corner = layout.cells.first()
        val brought = clampPan(Offset(-corner.center.x, -corner.center.y), layout.min, layout.max)
        assertEquals(-corner.center.x, brought.x, 0.01f)
        assertEquals(-corner.center.y, brought.y, 0.01f)
    }
}
