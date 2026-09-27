package dev.sebastiano.scrolleffects

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * Stretch: a card flips over as it leaves the centre, so the neighbours show their mirrored backs.
 * Their outer half then smears out to the edge of the stage, flaring taller like a trumpet bell,
 * with an iridescent rim along the flare.
 */
internal class StretchCard {
    private val grid = GridMesh(cols = COLS, rows = ROWS)
    private val rim = ColorMesh(COLS * 2 * VERTICES_PER_QUAD)

    /** How far along the smear each grid column is, 0 at its start and 1 at the stage edge. */
    private val columnSmear = FloatArray(COLS + 1)

    fun update(offset: Float, stage: CarouselStage, layer: CardLayer) {
        val pitch = stage.cardWidth * PITCH
        val depth = stage.cardHeight * CAMERA_DEPTH
        val angle = offset.coerceIn(-1f, 1f) * PI.toFloat()
        val cosA = cos(angle)
        val sinA = sin(angle)
        val centerX = stage.centerX + offset * pitch

        for (row in 0..ROWS) {
            val localY = (grid.v(row) - 0.5f) * stage.cardHeight
            for (col in 0..COLS) {
                val localX = (grid.u(col) - 0.5f) * stage.cardWidth
                // Turn the card about its own vertical axis, then project it from a camera in
                // front of the stage centre.
                val x3 = offset * pitch + localX * cosA
                val z3 = localX * sinA
                val scale = depth / (depth - z3)
                grid.setPoint(col, row, stage.centerX + x3 * scale, stage.centerY + localY * scale)
            }
        }

        val strength = smoothstep(STRETCH_START, 1f, abs(offset))
        val smeared = strength > 0f && smear(offset, strength, centerX, stage)
        grid.commit()
        layer.mesh = grid.mesh
        layer.rim = if (smeared) rim else null
    }

    private fun smear(
        offset: Float,
        strength: Float,
        centerX: Float,
        stage: CarouselStage,
    ): Boolean {
        val side = signOf(offset)
        var halfWidth = 0f
        for (row in 0..ROWS) {
            for (col in 0..COLS) {
                halfWidth = max(halfWidth, side * (grid.x(col, row) - centerX))
            }
        }
        if (halfWidth < 1f) return false
        val margin = stage.cardWidth * EDGE_MARGIN
        val target = if (side > 0f) stage.width + margin else -margin
        val reach = max(0f, side * (target - (centerX + side * halfWidth)))

        for (col in 0..COLS) {
            // A turned card still has straight columns: every point in one shares its x.
            val along = side * (grid.x(col, 0) - centerX) / halfWidth
            columnSmear[col] = ((along - SMEAR_START) / (1f - SMEAR_START)).coerceIn(0f, 1f)
        }
        for (row in 0..ROWS) {
            for (col in 0..COLS) {
                val x = grid.x(col, row)
                val y = grid.y(col, row)
                val t = columnSmear[col]
                if (t <= 0f) continue
                val pull = strength * reach * t.pow(PULL_POWER)
                val flare = 1f + strength * FLARE * t.pow(FLARE_POWER)
                grid.setPoint(
                    col,
                    row,
                    x + side * pull,
                    stage.centerY + (y - stage.centerY) * flare,
                )
            }
        }
        buildRim(strength, stage)
        return true
    }

    /** A ribbon above the top edge and below the bottom edge, fading outwards. */
    private fun buildRim(strength: Float, stage: CarouselStage) {
        var vertex = 0
        for (edge in 0..1) {
            val row = if (edge == 0) 0 else ROWS
            val outward = if (edge == 0) -1f else 1f
            for (col in 0 until COLS) {
                val x0 = grid.x(col, row)
                val y0 = grid.y(col, row)
                val x1 = grid.x(col + 1, row)
                val y1 = grid.y(col + 1, row)
                val t0 = columnSmear[col]
                val t1 = columnSmear[col + 1]
                val w0 = strength * stage.unit * (RIM_BASE + RIM_GROWTH * t0)
                val w1 = strength * stage.unit * (RIM_BASE + RIM_GROWTH * t1)
                val c0 = rimColor(t0, strength * RIM_OPACITY * t0.pow(RIM_FADE_IN))
                val c1 = rimColor(t1, strength * RIM_OPACITY * t1.pow(RIM_FADE_IN))
                val c0Out = c0 and 0x00FFFFFF
                val c1Out = c1 and 0x00FFFFFF
                // The rim overlaps the card edge slightly so no seam shows between them.
                val inset = RIM_INSET * stage.unit * outward
                vertex =
                    rim.quad(
                        vertex,
                        x0,
                        y0 - inset,
                        c0,
                        x1,
                        y1 - inset,
                        c1,
                        x0,
                        y0 + outward * w0,
                        c0Out,
                        x1,
                        y1 + outward * w1,
                        c1Out,
                    )
            }
        }
    }

    private companion object {
        const val COLS = 56
        const val ROWS = 14
        const val PITCH = 1.2f
        const val CAMERA_DEPTH = 3.2f
        const val STRETCH_START = 0.3f
        const val EDGE_MARGIN = 0.12f
        const val SMEAR_START = -0.05f
        const val PULL_POWER = 2.2f
        const val FLARE = 1.35f
        const val FLARE_POWER = 2.4f
        const val RIM_BASE = 5f
        const val RIM_GROWTH = 34f
        const val RIM_OPACITY = 0.9f
        const val RIM_FADE_IN = 0.9f
        const val RIM_INSET = 1.5f
    }
}

private fun ColorMesh.quad(
    start: Int,
    ax: Float,
    ay: Float,
    ac: Int,
    bx: Float,
    by: Float,
    bc: Int,
    cx: Float,
    cy: Float,
    cc: Int,
    dx: Float,
    dy: Float,
    dc: Int,
): Int {
    var v = start
    v = vertex(v, ax, ay, ac)
    v = vertex(v, bx, by, bc)
    v = vertex(v, cx, cy, cc)
    v = vertex(v, bx, by, bc)
    v = vertex(v, dx, dy, dc)
    return vertex(v, cx, cy, cc)
}

private fun ColorMesh.vertex(index: Int, x: Float, y: Float, color: Int): Int {
    positions[index * 2] = x
    positions[index * 2 + 1] = y
    colors[index] = color
    return index + 1
}

/** Pink by the card, through violet and cyan, to lime and gold out at the stage edge. */
private val rimStops =
    intArrayOf(
        0xFFFF5FD2.toInt(),
        0xFFA07BFF.toInt(),
        0xFF5FE3FF.toInt(),
        0xFFB8FF6A.toInt(),
        0xFFFFE27A.toInt(),
    )

private fun rimColor(t: Float, alpha: Float): Int {
    val scaled = t.coerceIn(0f, 1f) * (rimStops.size - 1)
    val i = scaled.toInt().coerceAtMost(rimStops.size - 2)
    val f = scaled - i
    val a = rimStops[i]
    val b = rimStops[i + 1]
    fun channel(shift: Int): Int =
        lerp(((a shr shift) and 0xFF).toFloat(), ((b shr shift) and 0xFF).toFloat(), f).toInt()
    val alphaByte = (alpha.coerceIn(0f, 1f) * 255f).toInt()
    return (alphaByte shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}
