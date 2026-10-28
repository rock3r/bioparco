package dev.sebastiano.expandingsearch

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A Gaussian-blurred round rect, drawn as the ring where it fades: four straight ramps along the
 * edges and four quarter rings at the corners, each a gradient following the blur's falloff.
 *
 * A blur mask filter looks the same but paints the whole shadow, interior included, and in software
 * that cost about 2 ms of a 3 ms frame. The interior sits under an opaque surface, so the ring
 * alone is enough; it is drawn [fillInside] only while the surface is see-through.
 *
 * Gradients are built once in unit space and placed with canvas transforms, so drawing allocates
 * nothing. Only the corner gradient depends on the corner radius, and it is rebuilt when that
 * changes.
 */
internal class SoftShadow(private val color: Color, private val sigma: Float) {
    /** How far the fade reaches either side of the edge: three sigmas, past which it is nil. */
    private val reach = 3f * sigma
    private val paint = Paint()

    /** Across an edge: 0 is the outer end, transparent; 1 the inner, full shadow. */
    private val rampColors = rampIntensities.map { color.copy(alpha = color.alpha * it) }
    private val acrossX = LinearGradientShader(Offset.Zero, Offset(1f, 0f), rampColors, RAMP_STOPS)
    private val acrossY = LinearGradientShader(Offset.Zero, Offset(0f, 1f), rampColors, RAMP_STOPS)
    private var corner: Shader? = null
    private var cornerRadius = Float.NaN

    fun draw(canvas: Canvas, rect: Rect, radius: Float, alpha: Float, fillInside: Boolean) {
        if (alpha <= 0f || rect.isEmpty) return
        val r = min(radius, rect.minDimension / 2f)
        paint.alpha = alpha
        val centre = rect.center
        val span = 2f * reach
        // A shape thinner than the fade: each ramp stops at the middle, so opposite ramps never
        // overlap. The middle then gets less than full shadow, as a real blur of a thin shape does.
        val depthY = min(span, centre.y - (rect.top - reach)) / span
        val depthX = min(span, centre.x - (rect.left - reach)) / span
        val length = rect.width - 2f * r
        val height = rect.height - 2f * r

        paint.shader = acrossY
        if (length > 0f) {
            edge(canvas, rect.left + r, rect.top - reach, length, span, depthY)
            edge(canvas, rect.left + r, rect.bottom + reach, length, -span, depthY)
        }
        paint.shader = acrossX
        if (height > 0f) {
            edge(canvas, rect.left - reach, rect.top + r, span, height, depthX, vertical = true)
            edge(canvas, rect.right + reach, rect.top + r, -span, height, depthX, vertical = true)
        }

        paint.shader = cornerShader(r)
        val extent = r + reach
        quarter(canvas, rect.left + r, rect.top + r, -extent, -extent)
        quarter(canvas, rect.right - r, rect.top + r, extent, -extent)
        quarter(canvas, rect.left + r, rect.bottom - r, -extent, extent)
        quarter(canvas, rect.right - r, rect.bottom - r, extent, extent)

        if (fillInside) {
            paint.shader = null
            paint.color = color
            paint.alpha = color.alpha * alpha
            val inside = rect.deflate(max(reach, r))
            if (!inside.isEmpty) canvas.drawRect(inside, paint)
        }
    }

    /** One straight ramp, from [x], [y] scaled by [sx], [sy]; [depth] of it is drawn, from 0. */
    private fun edge(
        canvas: Canvas,
        x: Float,
        y: Float,
        sx: Float,
        sy: Float,
        depth: Float,
        vertical: Boolean = false,
    ) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(sx, sy)
        if (vertical) canvas.drawRect(0f, 0f, depth, 1f, paint)
        else canvas.drawRect(0f, 0f, 1f, depth, paint)
        canvas.restore()
    }

    /**
     * One corner's quarter ring, about the corner's centre, mirrored by the signs of [sx], [sy].
     */
    private fun quarter(canvas: Canvas, cx: Float, cy: Float, sx: Float, sy: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(sx, sy)
        canvas.drawRect(0f, 0f, 1f, 1f, paint)
        canvas.restore()
    }

    /** A radial fade about a corner's centre, in units of the corner's [radius] plus the reach. */
    private fun cornerShader(radius: Float): Shader {
        corner?.let { if (radius == cornerRadius) return it }
        val extent = radius + reach
        val stops = mutableListOf<Float>()
        val colors = mutableListOf<Color>()
        // At the centre: full, unless the corner is tighter than the fade.
        stops += 0f
        colors += color.copy(alpha = color.alpha * blurred(-radius / sigma))
        for (k in -3..3) {
            val position = (radius + k * sigma) / extent
            if (position <= 0f) continue
            stops += position
            colors += color.copy(alpha = color.alpha * blurred(k.toFloat()))
        }
        return RadialGradientShader(Offset.Zero, 1f, colors, stops).also {
            corner = it
            cornerRadius = radius
        }
    }

    private companion object {
        val RAMP_STOPS = List(7) { it / 6f }

        /** Shadow at each ramp stop: from three sigmas outside the edge to three inside. */
        val rampIntensities = RAMP_STOPS.map { blurred(3f - 6f * it) }

        /** How much shadow falls [sigmas] outside an edge: the Gaussian's upper tail. */
        fun blurred(sigmas: Float): Float = 1f - normalCdf(sigmas)

        /** The standard normal CDF, from Abramowitz and Stegun 26.2.17 (error under 1e-7). */
        fun normalCdf(x: Float): Float {
            val t = 1.0 / (1.0 + 0.2316419 * abs(x))
            val poly =
                t *
                    (0.319381530 +
                        t *
                            (-0.356563782 +
                                t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))))
            val tail = exp(-0.5 * x * x) / sqrt(2 * PI) * poly
            return (if (x >= 0f) 1.0 - tail else tail).toFloat()
        }
    }
}
