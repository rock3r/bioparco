// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline

import androidx.compose.ui.graphics.Color

/** The palette: the plate fill and four stroke inks. */
data class HairlinePalette(
    val plate: Color,
    val hi: Color,
    val edge: Color,
    val mid: Color,
    val lo: Color,
) {
    operator fun get(ink: Ink): Color =
        when (ink) {
            Ink.Plate -> plate
            Ink.Hi -> hi
            Ink.Edge -> edge
            Ink.Mid -> mid
            Ink.Lo -> lo
        }

    companion object {
        val Light =
            HairlinePalette(
                Color(0xFFFFFFFF),
                Color(0xFF232327),
                Color(0xFFA4A4AC),
                Color(0xFFC3C3C9),
                Color(0xFFE0E0E4),
            )
        val Dark =
            HairlinePalette(
                Color(0xFF08090A),
                Color(0xFFD0D6E0),
                Color(0xFF5B5D64),
                Color(0xFF3E3E44),
                Color(0xFF29292D),
            )
    }
}
