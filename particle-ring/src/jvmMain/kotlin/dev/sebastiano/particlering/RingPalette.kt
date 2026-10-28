package dev.sebastiano.particlering

import androidx.compose.ui.graphics.Color

internal object RingPalette {
    /**
     * Plain black. The original fades to a faint plum at the bottom, but a gradient over the whole
     * window cost 7.5 ms a frame in software, five times everything else together.
     */
    val background = Color(0xFF000000)
    val caption = Color(0xFF8E8E93)
    val captionDone = Color(0xFFF2F2F7)
    val hint = Color(0xFF4E4E55)

    val iconTile = Color(0xFFFFFFFF)
    val iconTileFaded = Color(0xFF2C2C2E)
    val heartTop = Color(0xFFFF4F93)
    val heartBottom = Color(0xFFE3004F)

    /** The logo's arc runs from blush at its start, over the top, to graphite at its end. */
    val arcStart = Color(0xFFE58BAE)
    val arcMiddle = Color(0xFFF6D2DF)
    val arcEnd = Color(0xFF2E2E31)
    val glow = Color(0x4DFF2D78)

    /** The particles' three tones, deep pink to blush white. ARGB, for Skia's paints. */
    val toneArgb = intArrayOf(0xFFE83A78.toInt(), 0xFFF46F9F.toInt(), 0xFFFFE4EE.toInt())

    /** Each tone's dot diameter, in dp, at middle depth. */
    val toneDiameterDp = floatArrayOf(1.5f, 1.5f, 1.3f)

    /** Back, middle and front of the ring: how opaque and how big, relative to the tone's. */
    val depthAlpha = floatArrayOf(0.34f, 0.66f, 1f)
    val depthScale = floatArrayOf(0.8f, 1f, 1.22f)
}
