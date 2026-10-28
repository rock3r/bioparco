package dev.sebastiano.particlering

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The heart of the icon and the logo: the classic parametric heart, sin³ t across and 13 cos t − 5
 * cos 2t − 2 cos 3t − cos 4t up. It is scaled to one unit wide, with its bounds centred on the
 * origin. Its y grows downwards, as on screen, so the point is at the bottom.
 */
object HeartShape {
    /** How tall the heart is when it is one unit wide. */
    const val HEIGHT = 0.9039f

    private const val SEGMENTS = 96
    private val shape = outline(SEGMENTS)

    /** The outline as [segments] `x, y` pairs, going round once. */
    fun outline(segments: Int = SEGMENTS): FloatArray {
        val raw = FloatArray(segments * 2)
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (i in 0 until segments) {
            val t = 2.0 * PI * i / segments
            val s = sin(t)
            raw[i * 2] = (s * s * s).toFloat()
            raw[i * 2 + 1] =
                (-(13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t)) / 16).toFloat()
            minY = minOf(minY, raw[i * 2 + 1])
            maxY = maxOf(maxY, raw[i * 2 + 1])
        }
        // x already spans −1 to 1: halve everything, and centre the height.
        val middle = (minY + maxY) / 2f
        for (i in 0 until segments) {
            raw[i * 2] /= 2f
            raw[i * 2 + 1] = (raw[i * 2 + 1] - middle) / 2f
        }
        return raw
    }

    /** Whether (`x`, `y`) is inside the heart: an even-odd test against the outline. */
    fun contains(x: Float, y: Float): Boolean {
        var inside = false
        var j = SEGMENTS - 1
        for (i in 0 until SEGMENTS) {
            val xi = shape[i * 2]
            val yi = shape[i * 2 + 1]
            val xj = shape[j * 2]
            val yj = shape[j * 2 + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    /** [count] points spread evenly at random over the heart, as `x, y` pairs. */
    fun sample(count: Int, random: Random): FloatArray {
        val points = FloatArray(count * 2)
        var found = 0
        while (found < count) {
            val x = random.nextFloat() - 0.5f
            val y = (random.nextFloat() - 0.5f) * HEIGHT
            if (contains(x, y)) {
                points[found * 2] = x
                points[found * 2 + 1] = y
                found++
            }
        }
        return points
    }
}
