package dev.sebastiano.honeycomb

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI

/** How a cell looks from its distance to the viewport centre. */
data class CellFocus(val scale: Float, val alpha: Float)

data class PlacedCell(val row: Int, val column: Int, val center: Offset)

/** Cell centres relative to the middle of the field, plus the pan limits that reach them. */
data class HoneycombLayout(val cells: List<PlacedCell>, val min: Offset, val max: Offset)

/**
 * Opacity stays full inside [fullOpacityRadius] and reaches 0 at [fadeRadius]. Scale starts at 1
 * and falls linearly to [minScale] across [scaleFalloff], then stays there. Same shape as the
 * library, which uses `max(minScale, 1 - distance / scaleFalloff)`.
 */
fun cellFocus(
    distance: Float,
    fullOpacityRadius: Float,
    fadeRadius: Float,
    minScale: Float,
    scaleFalloff: Float,
): CellFocus {
    val span = (fadeRadius - fullOpacityRadius).coerceAtLeast(SMALLEST_SPAN)
    val alpha = (1f - (distance - fullOpacityRadius).coerceAtLeast(0f) / span).coerceIn(0f, 1f)
    val scale = (1f - distance / scaleFalloff.coerceAtLeast(SMALLEST_SPAN)).coerceAtLeast(minScale)
    return CellFocus(scale = scale, alpha = alpha)
}

/**
 * Centre of the circle at [row]/[column]. Even rows shift by half a pitch so the circles nestle,
 * and the next row sits `pitch × √3/2` down, which keeps the gap uniform.
 */
fun cellCenter(row: Int, column: Int, tile: Float, gap: Float): Offset {
    val pitch = tile + gap
    val x = tile / 2f + column * pitch + if (row % 2 == 0) pitch / 2f else 0f
    val y = tile / 2f + row * pitch * SQRT_3 / 2f
    return Offset(x, y)
}

fun honeycombLayout(rows: Int, columns: Int, tile: Float, gap: Float): HoneycombLayout {
    val raw = ArrayList<PlacedCell>(rows * columns)
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            raw.add(PlacedCell(row, column, cellCenter(row, column, tile, gap)))
        }
    }
    val origin = centroid(raw)
    val cells = raw.map { cell -> cell.copy(center = cell.center - origin) }
    return HoneycombLayout(cells, minCenter(cells), maxCenter(cells))
}

/** Keeps the viewport centre over a cell, so a pan cannot walk off the field. */
fun clampPan(pan: Offset, min: Offset, max: Offset): Offset =
    Offset(x = pan.x.coerceIn(-max.x, -min.x), y = pan.y.coerceIn(-max.y, -min.y))

/**
 * Where a released pan comes to rest. [velocity] is px/s. The lead is the distance a spring of
 * [responseSeconds] and [damping] covers before its acceleration would reverse, so the handoff from
 * a 1:1 drag does not hitch and nothing snaps to a cell.
 */
fun coastTarget(
    position: Offset,
    velocity: Offset,
    responseSeconds: Float,
    damping: Float,
): Offset {
    val omega = (2.0 * PI / responseSeconds).toFloat()
    return position + velocity * (2f * damping / omega)
}

private fun centroid(cells: List<PlacedCell>): Offset {
    var sumX = 0f
    var sumY = 0f
    for (cell in cells) {
        sumX += cell.center.x
        sumY += cell.center.y
    }
    val count = cells.size.coerceAtLeast(1).toFloat()
    return Offset(sumX / count, sumY / count)
}

private fun minCenter(cells: List<PlacedCell>): Offset {
    var x = Float.POSITIVE_INFINITY
    var y = Float.POSITIVE_INFINITY
    for (cell in cells) {
        x = minOf(x, cell.center.x)
        y = minOf(y, cell.center.y)
    }
    return Offset(x, y)
}

private fun maxCenter(cells: List<PlacedCell>): Offset {
    var x = Float.NEGATIVE_INFINITY
    var y = Float.NEGATIVE_INFINITY
    for (cell in cells) {
        x = maxOf(x, cell.center.x)
        y = maxOf(y, cell.center.y)
    }
    return Offset(x, y)
}

private const val SQRT_3 = 1.7320508f
private const val SMALLEST_SPAN = 0.001f
