package dev.sebastiano.expandingsearch

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/**
 * Every size in the widget, in pixels for one [density]. Measured off the video, where the pill is
 * 146 px tall: 54dp here, so 2.7 px to the dp.
 *
 * The widget's own coordinates put the full pill's top-left at the origin. The pill grows from its
 * centre, so its left edge moves right as it narrows.
 */
internal class SearchGeometry(density: Density) {
    val pillHeight: Float
    val fullWidth: Float
    val rim: Float
    val hairline: Float
    val panelGap: Float
    val panelRadius: Float

    /** The panel's height as it first appears: a thin bar under the pill. */
    val barHeight: Float
    val rows: RowSlots

    /**
     * The magnifier's centre, from the pill's left edge: half the height, so it sits mid-circle.
     */
    val iconCentre: Float
    val iconRadius: Float
    val iconStroke: Float
    val textStart: Float

    /** From the pill's right edge to the cross's centre. */
    val crossInset: Float
    val crossSize: Float
    val rowTextStart: Float
    val shadowBlur: Float
    val shadowDrop: Float
    val blurMax: Float

    init {
        with(density) {
            pillHeight = 54.dp.toPx()
            fullWidth = 263.dp.toPx()
            rim = 5.dp.toPx()
            hairline = 1f
            panelGap = 8.dp.toPx()
            panelRadius = 20.dp.toPx()
            barHeight = 4.dp.toPx()
            rows = RowSlots(padding = 5.dp.toPx(), rowHeight = 39.dp.toPx())
            iconCentre = 27.dp.toPx()
            iconRadius = 5.6.dp.toPx()
            iconStroke = 1.7.dp.toPx()
            textStart = 44.dp.toPx()
            crossInset = 25.dp.toPx()
            crossSize = 26.dp.toPx()
            rowTextStart = 18.dp.toPx()
            shadowBlur = 14.dp.toPx()
            shadowDrop = 10.dp.toPx()
            blurMax = 8.dp.toPx()
        }
    }

    val panelTop: Float
        get() = pillHeight + panelGap

    /** Tall enough for the most rows the panel ever lists. */
    val panelMaxHeight: Float
        get() = rows.panelHeight(MAX_RESULTS.toFloat())

    val totalHeight: Float
        get() = panelTop + panelMaxHeight

    /** How wide the pill is at an [expansion] from 0 (a circle) to 1 (the full field). */
    fun pillWidth(expansion: Float): Float = lerp(pillHeight, fullWidth, expansion)

    fun pillLeft(expansion: Float): Float = (fullWidth - pillWidth(expansion)) / 2f

    fun pill(expansion: Float): Rect =
        Rect(pillLeft(expansion), 0f, pillLeft(expansion) + pillWidth(expansion), pillHeight)

    /** How much of the field's text shows between the magnifier and the cross. */
    fun textWidth(expansion: Float): Float =
        (pillWidth(expansion) - textStart - crossInset - crossSize / 2f).coerceAtLeast(0f)

    /** The panel, as wide as the pill, between a thin bar and its rows' height. */
    fun panel(expansion: Float, reveal: Float, panelRows: Float): Rect {
        val height = lerp(barHeight, rows.panelHeight(panelRows), reveal)
        return Rect(
            pillLeft(expansion),
            panelTop,
            pillLeft(expansion) + pillWidth(expansion),
            panelTop + height,
        )
    }

    /** Whether a point in widget coordinates lands on the pill or, while it shows, the panel. */
    fun hits(point: Offset, expansion: Float, reveal: Float, panelRows: Float): Boolean =
        pill(expansion).contains(point) ||
            (reveal > 0f && panel(expansion, reveal, panelRows).contains(point))
}
