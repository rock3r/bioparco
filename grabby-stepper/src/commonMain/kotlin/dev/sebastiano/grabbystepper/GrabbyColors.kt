package dev.sebastiano.grabbystepper

import androidx.compose.ui.graphics.Color

data class GrabbyColors(
    val canvas: Color,
    val canvasGlow: Color,
    val track: Color,
    val thumb: Color,
    val number: Color,
    val chrome: Color,
    val hint: Color,
    val focusRing: Color,
)

val DarkGrabbyColors =
    GrabbyColors(
        canvas = Color(0xFF000000),
        canvasGlow = Color(0xFF1A1010),
        track = Color(0xFF1C1C1E),
        thumb = Color(0xFF3A3A3C),
        number = Color(0xFFFFFFFF),
        // iOS secondary label on the dark pill — large ± still reads.
        chrome = Color(0xFFC7C7CC),
        hint = Color(0xFFC7C7CC),
        focusRing = Color(0xFFFFFFFF),
    )

val LightGrabbyColors =
    GrabbyColors(
        canvas = Color(0xFFF2F2F7),
        canvasGlow = Color(0xFFE8E8EE),
        track = Color(0xFFE5E5EA),
        thumb = Color(0xFFFFFFFF),
        number = Color(0xFF1C1C1E),
        chrome = Color(0xFF3A3A3C),
        hint = Color(0xFF3A3A3C),
        focusRing = Color(0xFF1C1C1E),
    )
