package dev.sebastiano.thinkingbloom

import androidx.compose.ui.graphics.Color

/** The flat colours of the reference, picked from its frames. */
internal object BloomPalette {
    val paper = Color(0xFFFFFFFF)
    val ink = Color(0xFF0A0A0A)

    private val orange = Color(0xFFEE7A4A)
    private val pink = Color(0xFFF5BFD3)
    private val teal = Color(0xFF2FAA8C)
    private val blue = Color(0xFF3592E0)
    private val cream = Color(0xFFF4ECE4)
    private val blush = Color(0xFFF7D9DF)
    private val yellow = Color(0xFFF8D247)

    /**
     * A blossom's petal and centre colours. Repeats weight the mix: the reference is mostly pink,
     * teal, orange and blue, with a few pale ones.
     */
    val looks: List<Look> =
        listOf(
            Look(orange, yellow),
            Look(orange, pink),
            Look(pink, yellow),
            Look(pink, orange),
            Look(pink, yellow),
            Look(teal, yellow),
            Look(teal, yellow),
            Look(teal, cream),
            Look(blue, yellow),
            Look(blue, yellow),
            Look(blue, pink),
            Look(cream, yellow),
            Look(blush, orange),
        )

    class Look(val petals: Color, val centre: Color)
}
