package dev.sebastiano.expandingsearch

import androidx.compose.ui.graphics.Color

/**
 * Sampled from the source video: warm greys on an off-white page, white surfaces, one blue caret.
 */
internal object ExpandingSearchPalette {
    val page = Color(0xFFF6F4F0)

    /** The pill's inside, and the row highlight. */
    val well = Color(0xFFECEBE4)

    /** The pill's thick rim, and the results panel. */
    val surface = Color.White

    /** A one-pixel edge around both surfaces, and their soft shadow. */
    val edge = Color(0x17000000)
    val shadow = Color(0x202A2418)

    val ink = Color(0xFF1E1D1A)

    /** The rest of a name once a query has matched part of it. */
    val mutedInk = Color(0xFF8F8E88)
    val placeholder = Color(0xFFADACA5)
    val icon = Color(0xFF2F2E2A)
    val cross = Color(0xFF8E8D87)
    val crossHover = Color(0xFFE2E0DB)
    val caret = Color(0xFF4F46E5)
}
