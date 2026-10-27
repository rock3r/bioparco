package dev.sebastiano.wheelpicker

import androidx.compose.ui.graphics.Color

internal object WheelPalette {
    val background = Color(0xFF0E0E0F)
    val tick = Color.White
    val label = Color(0xFFB4B4B8)
    val hint = Color(0xFF5C5C62)

    // The three channels of a white tick. Drawn added together they make white again; pulled
    // apart they make the coloured fringe at the edges of the lens.
    val red = Color(0xFFFF0000)
    val green = Color(0xFF00FF00)
    val blue = Color(0xFF0000FF)
}
