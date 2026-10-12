// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import androidx.compose.ui.graphics.Color

/**
 * The original's `ISO_CSS` tokens, one instance per theme. Every fill and stroke the renderer
 * paints reads one of these, so a theme is just a palette. Light is the theme to design for first.
 */
data class Palette(
    val page: Color,
    val card: Color,
    val text: Color,
    val strong: Color,
    val muted: Color,
    /**
     * What solids are filled with. It equals the card, so fills hide without showing as patches.
     */
    val paper: Color,
    val face: Color,
    val top: Color,
    /** The four side tones, darkest (facing away from the light) first. */
    val shades: List<Color>,
    val litTop: Color,
    val litShade: Color,
    val hi: Color,
    val mid: Color,
    val lo: Color,
    val faint: Color,
    val lit: Color,
    val halo: Color,
    val haloOpacity: Float,
    val dot: Color,
    val red: Color,
    val green: Color,
    val blue: Color,
    val isDark: Boolean,
) {
    fun ink(tone: Tone): Color =
        when (tone) {
            Tone.Hi -> hi
            Tone.Mid -> mid
            Tone.Lo -> lo
            Tone.Faint -> faint
            Tone.Lit -> lit
            Tone.Red -> red
            Tone.Green -> green
            Tone.Blue -> blue
        }

    /** Dots are one colour at an opacity per tone. */
    fun dotAlpha(tone: Tone): Float =
        when (tone) {
            Tone.Hi -> 0.85f
            Tone.Mid -> 0.45f
            Tone.Lo -> 0.22f
            Tone.Faint -> 0.12f
            else -> 0.32f
        }

    companion object {
        val Dark =
            Palette(
                page = Color(0xFF0B0B0B),
                card = Color(0xFF131313),
                text = Color(0xFFA3A3A3),
                strong = Color(0xFFE6E6E6),
                muted = Color(0xFF6E6E6E),
                paper = Color(0xFF131313),
                face = Color(0xFF1B1B1B),
                top = Color(0xFF1C1C1C),
                shades =
                    listOf(
                        Color(0xFF0F0F0F),
                        Color(0xFF121212),
                        Color(0xFF151515),
                        Color(0xFF181818),
                    ),
                litTop = Color(0xFF262626),
                litShade = Color(0xFF1D1D1D),
                hi = Color(0xFFD4D4D4),
                mid = Color(0xFF6A6A6A),
                lo = Color(0xFF3E3E3E),
                faint = Color(0xFF2A2A2A),
                lit = Color(0xFFFFFFFF),
                halo = Color(0xFF050505),
                haloOpacity = 0.9f,
                dot = Color(0xFFE6E6E6),
                red = Color(0xFFFF6B6B),
                green = Color(0xFF6BE0A0),
                blue = Color(0xFF6B9BFF),
                isDark = true,
            )

        val Light =
            Palette(
                page = Color(0xFFF2F2EF),
                card = Color(0xFFFBFBFA),
                text = Color(0xFF57575A),
                strong = Color(0xFF18181A),
                muted = Color(0xFF8C8C90),
                paper = Color(0xFFFBFBFA),
                face = Color(0xFFFFFFFF),
                top = Color(0xFFFFFFFF),
                shades =
                    listOf(
                        Color(0xFFDCDCD9),
                        Color(0xFFE5E5E2),
                        Color(0xFFEDEDEA),
                        Color(0xFFF4F4F1),
                    ),
                litTop = Color(0xFFFFFFFF),
                litShade = Color(0xFFEEEEEB),
                hi = Color(0xFF26262A),
                mid = Color(0xFF8A8A8F),
                lo = Color(0xFFB4B4B8),
                faint = Color(0xFFD4D4D6),
                lit = Color(0xFF000000),
                halo = Color(0xFF8A8A80),
                haloOpacity = 0.32f,
                dot = Color(0xFF3A3A3E),
                red = Color(0xFFE5484D),
                green = Color(0xFF30A46C),
                blue = Color(0xFF3E63DD),
                isDark = false,
            )
    }
}
