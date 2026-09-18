package dev.sebastiano.grabbystepper

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertTrue

class ElasticTrackTest {
    private val rest = RestLayout(width = 200f, height = 64f, thumbDiameter = 56f, inset = 4f)

    @Test
    fun stretchedPathIsWiderThanRest() {
        val stretch = stretchTrack(rest, offsetX = 48f, offsetY = 0f, follow = 1f)
        val path =
            elasticTrackPath(
                stretch = stretch,
                thumbCenter = Offset(stretch.right - 28f, stretch.centerY),
                thumbRadius = 28f,
                gooey = 0f,
                maxNeckDistance = 120f,
            )
        val bounds = path.getBounds()
        assertTrue(bounds.width > rest.width)
        assertTrue(bounds.height >= rest.thumbDiameter)
    }

    @Test
    fun verticalGooeyExtendsBelowThePill() {
        val stretch = stretchTrack(rest, offsetX = 0f, offsetY = 50f, follow = 1f)
        val thumb = Offset(stretch.centerX, stretch.centerY + 50f)
        val path =
            elasticTrackPath(
                stretch = stretch,
                thumbCenter = thumb,
                thumbRadius = 28f,
                gooey = 1f,
                maxNeckDistance = 160f,
            )
        val bounds = path.getBounds()
        assertTrue(bounds.bottom > rest.restBottom)
        assertTrue(bounds.height > rest.height)
    }
}
