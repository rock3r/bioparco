package dev.sebastiano.knockoutbadge

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BadgeLayoutTest {
    @Test
    fun `assembled, every layer sits flat where the badge is`() {
        for (layer in 0 until Layers.COUNT) {
            assertEquals(LayerPose.Flat, layerPose(layer, explode = 0f))
        }
    }

    @Test
    fun `apart, the layers share a turn and stack upwards in order`() {
        val poses = (0 until Layers.COUNT).map { layerPose(it, explode = 1f) }
        assertEquals(1, poses.map { it.degrees }.distinct().size)
        assertEquals(1, poses.map { it.squash }.distinct().size)
        assertTrue(poses.first().squash < 1f)
        for (i in 1 until poses.size) {
            assertTrue(poses[i].lift < poses[i - 1].lift, "layer $i should sit above ${i - 1}")
        }
    }

    @Test
    fun `the sticker multiplies when assembled and lies over the video when apart`() {
        assertEquals(1f, multiplyShare(0f))
        assertEquals(1f, multiplyShare(-0.05f))
        assertEquals(0f, multiplyShare(1f))
        assertEquals(0f, multiplyShare(1.04f))
        var last = 1f
        for (i in 0..100) {
            val share = multiplyShare(i / 100f)
            assertTrue(share <= last, "share should only fall, at ${i / 100f}")
            last = share
        }
    }

    @Test
    fun `a barcode is the same every time, in order, without overlaps, inside its box`() {
        val bars = barcode(seed = 427, count = 46)
        assertContentEquals(bars, barcode(seed = 427, count = 46))
        assertEquals(46 * 2, bars.size)
        var end = 0f
        for (i in 0 until 46) {
            val start = bars[i * 2]
            val width = bars[i * 2 + 1]
            assertTrue(width > 0f)
            assertTrue(start >= end, "bar $i overlaps the one before")
            end = start + width
        }
        assertTrue(bars[0] >= 0f)
        assertTrue(end <= 1f + 1e-5f)
        assertEquals(1f, end, 1e-4f)
    }

    @Test
    fun `the pointer tips the badge towards it, no further than the most`() {
        val tilt = tiltToward(dx = 50f, dy = -100f, halfWidth = 100f, halfHeight = 100f, most = 10f)
        // Right of centre: the right edge goes away. Above centre: the top edge goes away.
        assertEquals(5f, tilt.y)
        assertEquals(10f, tilt.x)
        val far = tiltToward(dx = 900f, dy = 900f, halfWidth = 100f, halfHeight = 100f, most = 10f)
        assertEquals(Tilt(-10f, 10f), far)
    }
}
