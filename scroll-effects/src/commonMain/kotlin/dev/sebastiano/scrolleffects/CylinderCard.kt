package dev.sebastiano.scrolleffects

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Bulge and Drum wrap the whole strip of cards around a vertical cylinder.
 * - **Bulge** puts the cylinder in front of the camera, so the strip curves away: side cards shrink
 *   towards the edges, and a lens swells the centre card into a pillow.
 * - **Drum** puts the camera inside the cylinder, so the strip curves towards it: side cards grow
 *   taller towards the edges and the centre card's corners lift.
 */
internal class CylinderCard(private val convex: Boolean) {
    private val grid = GridMesh(cols = COLS, rows = ROWS)

    fun update(offset: Float, stage: CarouselStage, layer: CardLayer) {
        val pitch = stage.cardWidth * PITCH
        val radius = pitch * (if (convex) BULGE_RADIUS else DRUM_RADIUS)
        val depth = stage.cardHeight * (if (convex) BULGE_DEPTH else DRUM_DEPTH)
        val limit = if (convex) BULGE_ANGLE_LIMIT else DRUM_ANGLE_LIMIT
        var visible = false

        for (row in 0..ROWS) {
            val localY = (grid.v(row) - 0.5f) * stage.cardHeight
            for (col in 0..COLS) {
                val flatX = offset * pitch + (grid.u(col) - 0.5f) * stage.cardWidth
                val angle = (flatX / radius).coerceIn(-limit, limit)
                if (abs(flatX / radius) < limit) visible = true
                val x3 = radius * sin(angle)
                val bend = radius * (1f - cos(angle))
                // Convex: the strip bends away from the camera. Concave: towards it.
                val z3 = if (convex) -bend else bend
                val scale = depth / max(depth - z3, 1f)
                var x = x3 * scale
                var y = localY * scale
                if (convex) {
                    val lens = pillow(x, y, stage)
                    x *= lens
                    y *= lens
                }
                grid.setPoint(col, row, stage.centerX + x, stage.centerY + y)
            }
        }
        grid.commit()
        layer.mesh = if (visible) grid.mesh else null
    }

    /** A soft lens over the stage centre: 1 at its rim, [PILLOW] + 1 in the middle. */
    private fun pillow(x: Float, y: Float, stage: CarouselStage): Float {
        val nx = x / (stage.cardWidth * PILLOW_WIDTH)
        val ny = y / (stage.cardHeight * PILLOW_HEIGHT)
        return 1f + PILLOW * max(0f, 1f - (nx * nx + ny * ny))
    }

    private companion object {
        const val COLS = 28
        const val ROWS = 28
        const val PITCH = 1.045f
        const val BULGE_RADIUS = 1.3f
        const val DRUM_RADIUS = 1.6f
        const val BULGE_DEPTH = 2.6f
        const val DRUM_DEPTH = 3.4f
        const val BULGE_ANGLE_LIMIT = 1.52f
        const val DRUM_ANGLE_LIMIT = 1.3f
        const val PILLOW = 0.16f
        const val PILLOW_WIDTH = 0.95f
        const val PILLOW_HEIGHT = 0.64f
    }
}
