package dev.sebastiano.expandingsearch

import kotlin.math.floor

/**
 * Where the rows sit inside the results panel: [padding] above the first, then one [rowHeight] per
 * row. Rows are measured in fractional row units so a row sliding between slots has a position.
 */
class RowSlots(val padding: Float, val rowHeight: Float) {
    /** The row under a point [y] from the panel's top, among [count] rows, or `null` for none. */
    fun slotAt(y: Float, count: Int): Int? {
        val slot = floor((y - padding) / rowHeight).toInt()
        return if (y >= padding && slot < count) slot else null
    }

    /** The top of a row at [row] row units down. */
    fun top(row: Float): Float = padding + row * rowHeight

    /** A panel holding [rows] rows, with padding above and below. */
    fun panelHeight(rows: Float): Float = padding * 2 + rows * rowHeight
}
