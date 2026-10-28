package dev.sebastiano.stampbook

import kotlin.math.max
import kotlin.math.roundToInt

/** Where a stamp's edges are punched. */
object Perforation {
    /** The centre of one hole, on one of a stamp's four edges. */
    data class Hole(val x: Float, val y: Float)

    /**
     * The holes for a stamp of [width] × [height], about [pitch] apart. Each side gets a whole
     * number of holes, the pitch stretched or squeezed to fit, and its first and last holes sit
     * half a pitch in from the corners, so every corner is a whole tooth, as on a real sheet.
     */
    fun holes(width: Float, height: Float, pitch: Float): List<Hole> {
        val across = along(width, pitch)
        val down = along(height, pitch)
        return across.map { Hole(it, 0f) } +
            across.map { Hole(it, height) } +
            down.map { Hole(0f, it) } +
            down.map { Hole(width, it) }
    }

    /** Evenly spaced hole positions along a side of [length], half a step in from each end. */
    private fun along(length: Float, pitch: Float): List<Float> {
        val count = max(1, (length / pitch).roundToInt())
        val step = length / count
        return List(count) { (it + 0.5f) * step }
    }
}
