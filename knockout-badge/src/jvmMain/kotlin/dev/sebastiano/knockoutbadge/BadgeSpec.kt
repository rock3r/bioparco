package dev.sebastiano.knockoutbadge

import androidx.compose.ui.unit.dp

/** The badge's measurements, in its own frame. */
internal object BadgeSpec {
    val WIDTH = 250.dp
    val HEIGHT = 400.dp
    val MARGIN = 18.dp
    val CORNER = 14.dp

    /** The tear-off stub's notches, as a share of the height. */
    const val STUB_AT = 0.745f
    val NOTCH = 9.dp
    val SLOT_WIDTH = 46.dp
    val SLOT_HEIGHT = 8.dp
    val SLOT_TOP = 14.dp
    val KICKER_TOP = 34.dp
    val WORD_BASELINE = 150.dp
    val NAME_TOP = 166.dp
    val GLOBE = 22.dp

    /** The facts row, as a share of the height. */
    const val FACTS_AT = 0.6f
    val BARS_GAP = 16.dp
    val BARS_HEIGHT = 32.dp

    /** How far the hand can reach in from the corner to grab it. */
    val CORNER_GRAB = 40.dp

    /** How far the stack moves right when it comes apart, to leave room for the notes. */
    val APART_SHIFT = 100.dp

    const val WORD = "init"
    const val HOLDER = "Nina Marsh"
}
