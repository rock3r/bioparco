package dev.sebastiano.currencyswarm

import androidx.compose.ui.graphics.Color

internal object SwarmPalette {
    val background = Color(0xFF0D0D11)
    val title = Color(0xFFF4F4F6)
    val subtitle = Color(0xFF8C8C96)
    val hint = Color(0xFF55555E)
    val chipText = Color(0xFFB9B9C2)
    val chipSelectedText = Color(0xFFF4F4F6)
    val chipBorder = Color(0x26FFFFFF)
    val chipSelected = Color(0x1FFFFFFF)

    /** The dots, by tier: dim and small, mid, bright and big. ARGB, for Skia's paints. */
    val dotArgb = intArrayOf(0x70E6E6EE, 0xB8EDEDF2.toInt(), 0xFFF7F7FA.toInt())

    /** Each tier's dot diameter, in dp. */
    val dotDiameterDp = floatArrayOf(1.3f, 1.8f, 2.3f)
}
