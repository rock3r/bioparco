package dev.sebastiano.tabswitch

import androidx.compose.ui.graphics.Color

/** Sampled from the source video: a near-black page, a charcoal bar, and one light pill. */
internal object TabSwitchPalette {
    val page = Color(0xFF131313)
    val bar = Color(0xFF252525)
    val pill = Color(0xFFF2F2F2)

    /** Icons outside the pill, at rest and under the pointer. */
    val icon = Color(0xFFB4B4B4)
    val iconHover = Color(0xFFF2F2F2)

    /** Icons, labels and the badge inside the pill. */
    val ink = Color(0xFF121212)
    val hint = Color(0xFF5E5E5E)
}
