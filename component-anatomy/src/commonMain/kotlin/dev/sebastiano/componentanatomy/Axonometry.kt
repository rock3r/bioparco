package dev.sebastiano.componentanatomy

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The view of the exploded stack: a turn around the vertical axis ([yawDegrees]), then a tip around
 * the horizontal axis ([pitchDegrees]), a [scale] around the pivot, and an optional perspective.
 *
 * Each layer is flat and sits at a `depth` along the stack's normal. Positive depth is towards the
 * viewer. [matrix] draws a layer; [project] places a label on it. Both come from the same numbers,
 * so a label always lands on its layer.
 */
class Axonometry(
    val yawDegrees: Float,
    val pitchDegrees: Float,
    val scale: Float = 1f,
    val cameraDistance: Float = Float.POSITIVE_INFINITY,
) {
    private val cosYaw = cos(yawDegrees * DEG).toFloat()
    private val sinYaw = sin(yawDegrees * DEG).toFloat()
    private val cosPitch = cos(pitchDegrees * DEG).toFloat()
    private val sinPitch = sin(pitchDegrees * DEG).toFloat()

    /** Where [point], in the layer's own coordinates, lands on screen for a layer at [depth]. */
    fun project(point: Offset, depth: Float, pivot: Offset): Offset {
        val (x, y, w) = forms(depth, pivot)
        return Offset(x(point) / w(point), y(point) / w(point))
    }

    /** The drawing matrix for a layer at [depth]. `matrix.map(p)` equals `project(p, …)`. */
    fun matrix(depth: Float, pivot: Offset): Matrix {
        val (x, y, w) = forms(depth, pivot)
        val values = FloatArray(MATRIX_SIZE)
        // Compose's Matrix maps a point as a row vector: X = (m00·x + m10·y + m30) / w, where
        // w = m03·x + m13·y + m33. The z row and column pass through untouched.
        values[0] = x.a
        values[4] = x.b
        values[12] = x.c
        values[1] = y.a
        values[5] = y.b
        values[13] = y.c
        values[3] = w.a
        values[7] = w.b
        values[15] = w.c
        values[10] = 1f
        return Matrix(values)
    }

    /** Screen x, screen y and the perspective divisor, as linear functions of the layer point. */
    private fun forms(depth: Float, pivot: Offset): Triple<Linear, Linear, Linear> {
        // In pivot-relative coordinates (u, v), the scaled point is (s·u, s·v, depth).
        val s = scale
        val rotatedX = Linear(cosYaw * s, 0f, sinYaw * depth)
        val turnedZ = Linear(-sinYaw * s, 0f, cosYaw * depth)
        val rotatedY = Linear(0f, cosPitch * s, 0f) - turnedZ * sinPitch
        val rotatedZ = Linear(0f, sinPitch * s, 0f) + turnedZ * cosPitch
        val w =
            if (cameraDistance.isFinite()) Linear(0f, 0f, 1f) - rotatedZ * (1f / cameraDistance)
            else Linear(0f, 0f, 1f)
        val screenX = w * pivot.x + rotatedX
        val screenY = w * pivot.y + rotatedY
        return Triple(screenX.unpivot(pivot), screenY.unpivot(pivot), w.unpivot(pivot))
    }

    /** `a·u + b·v + c`, where (u, v) is a point relative to the pivot. */
    private data class Linear(val a: Float, val b: Float, val c: Float) {
        operator fun plus(other: Linear) = Linear(a + other.a, b + other.b, c + other.c)

        operator fun minus(other: Linear) = Linear(a - other.a, b - other.b, c - other.c)

        operator fun times(k: Float) = Linear(a * k, b * k, c * k)

        operator fun invoke(point: Offset) = a * point.x + b * point.y + c

        /** The same function, written for layer coordinates instead of pivot-relative ones. */
        fun unpivot(pivot: Offset) = Linear(a, b, c - a * pivot.x - b * pivot.y)
    }

    private companion object {
        const val DEG = PI / 180.0
        const val MATRIX_SIZE = 16
    }
}
