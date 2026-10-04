package dev.sebastiano.honeycomb

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** A fixed palette. Cells cycle through it, so neighbours stay distinct without any pictures. */
internal object HoneycombPalette {
    private val colors =
        listOf(
            Color(0xFFE5484D),
            Color(0xFFF76808),
            Color(0xFFFFC53D),
            Color(0xFFBDEE63),
            Color(0xFF30A46C),
            Color(0xFF12A594),
            Color(0xFF0091FF),
            Color(0xFF3E63DD),
            Color(0xFF7C66DC),
            Color(0xFF8E4EC6),
            Color(0xFFD6409F),
            Color(0xFFE93D82),
        )

    fun color(row: Int, column: Int): Color =
        colors[(row * HONEYCOMB_COLUMNS + column) % colors.size]
}

/** A simple generated gradient: lighter in the middle, the palette colour, then a darker rim. */
internal fun honeycombBrush(color: Color): Brush {
    val lift =
        Color(
            red = color.red + (1f - color.red) * LIFT,
            green = color.green + (1f - color.green) * LIFT,
            blue = color.blue + (1f - color.blue) * LIFT,
        )
    val shade =
        Color(red = color.red * SHADE, green = color.green * SHADE, blue = color.blue * SHADE)
    return Brush.radialGradient(listOf(lift, color, shade))
}

private const val LIFT = 0.42f
private const val SHADE = 0.55f
