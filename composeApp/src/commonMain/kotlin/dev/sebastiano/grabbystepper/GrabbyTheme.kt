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
)

val DarkGrabbyColors =
    GrabbyColors(
        canvas = Color(0xFF000000),
        canvasGlow = Color(0xFF1A1010),
        track = Color(0xFF1C1C1E),
        thumb = Color(0xFF3A3A3C),
        number = Color(0xFFFFFFFF),
        chrome = Color(0xFF8E8E93),
        hint = Color(0xFF636366),
    )

val LightGrabbyColors =
    GrabbyColors(
        canvas = Color(0xFFF2F2F7),
        canvasGlow = Color(0xFFE8E8EE),
        track = Color(0xFFE5E5EA),
        thumb = Color(0xFFFFFFFF),
        number = Color(0xFF1C1C1E),
        chrome = Color(0xFF8E8E93),
        hint = Color(0xFF6C6C70),
    )
