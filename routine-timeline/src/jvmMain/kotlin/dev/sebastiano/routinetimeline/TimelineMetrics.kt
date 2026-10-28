package dev.sebastiano.routinetimeline

import androidx.compose.ui.unit.dp

/**
 * The card's layout in dp, measured off the source video (its card is about 990 px across; this one
 * is 510dp, so about half).
 */
internal object TimelineMetrics {
    val cardWidth = 510.dp
    val cardHeight = 470.dp
    val cardCorner = 30.dp

    /** The dial's centre, which is also where the sun/moon and the clock sit. */
    val dialX = 86.dp
    val dialY = 262.dp

    val arcRadius = 160.dp
    val arcWidth = 3.5.dp
    val outerLaneRadius = 120.dp
    val outerLaneWidth = 24.dp
    val innerLaneRadius = 94.dp
    val innerLaneWidth = 20.dp
    val markRadius = 143.dp
    val starInner = 48.dp
    val starOuter = 76.dp

    /** The lanes fade into the card over this much width from the dial's centre line. */
    val veil = 64.dp

    val markerRadius = 8.5.dp
    val markerStroke = 3.dp
    val badgeRadius = 8.dp
    val laneIcon = 12.dp
    val badgeIcon = 10.dp
    val dashFrom = 58.dp
    val dashGap = 4.dp
    val dashWidth = 1.2.dp
    val starSize = 1.3.dp
    val hairline = 1.dp

    val skyIcon = 38.dp
    val skyIconY = (-30).dp
    val skyGlow = 54.dp
    val skyTravel = 10.dp
    val clockY = 16.dp
    val meridiemY = 42.dp

    val listLeft = 262.dp
    val listWidth = 222.dp
    val rowPitch = 112.dp
    val highlightHeight = 104.dp
    val highlightCorner = 20.dp

    val headerTop = 26.dp
    val headerSide = 28.dp
}
