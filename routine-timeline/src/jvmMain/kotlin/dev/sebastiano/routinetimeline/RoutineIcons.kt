package dev.sebastiano.routinetimeline

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Small line icons on a 24-unit grid, built once. Each is a filled path and a stroked one, drawn
 * scaled to the size asked for.
 */
internal class Glyph(val fill: Path?, val stroke: Path?, val strokeWidth: Float = 2f) {
    private val style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)

    /** Draws the icon [size] px across, centred on [centre]. */
    fun draw(scope: DrawScope, centre: Offset, size: Float, color: Color, alpha: Float = 1f) {
        val s = size / GRID
        scope.translate(centre.x - size / 2, centre.y - size / 2) {
            scale(s, s, pivot = Offset.Zero) {
                fill?.let { drawPath(it, color, alpha, Fill) }
                stroke?.let { drawPath(it, color, alpha, style) }
            }
        }
    }

    companion object {
        const val GRID = 24f
    }
}

internal object RoutineIcons {
    val sun: Glyph by lazy {
        val rays = Path()
        for (i in 0 until 8) {
            val a = i * PI / 4
            rays.moveTo(12 + 7.4f * cos(a).toFloat(), 12 + 7.4f * sin(a).toFloat())
            rays.lineTo(12 + 10.2f * cos(a).toFloat(), 12 + 10.2f * sin(a).toFloat())
        }
        Glyph(fill = Path().apply { addOval(Rect(Offset(12f, 12f), 4.6f)) }, stroke = rays)
    }

    val moon: Glyph by lazy {
        val disc = Path().apply { addOval(Rect(Offset(11f, 12.5f), 8.5f)) }
        val bite = Path().apply { addOval(Rect(Offset(16.5f, 8f), 7.5f)) }
        Glyph(fill = Path.combine(PathOperation.Difference, disc, bite), stroke = null)
    }

    val cup: Glyph by lazy {
        val body =
            Path().apply {
                moveTo(4.5f, 8.5f)
                lineTo(16.5f, 8.5f)
                lineTo(16f, 14f)
                cubicTo(15.6f, 17f, 13.5f, 18f, 10.5f, 18f)
                cubicTo(7.5f, 18f, 5.4f, 17f, 5f, 14f)
                close()
            }
        val handleAndSaucer =
            Path().apply {
                moveTo(16.4f, 10f)
                cubicTo(20.5f, 9.5f, 20.5f, 14.5f, 15.8f, 14.2f)
                moveTo(3.5f, 20.5f)
                lineTo(17.5f, 20.5f)
            }
        Glyph(fill = body, stroke = handleAndSaucer, strokeWidth = 1.8f)
    }

    val cutlery: Glyph by lazy {
        val knife =
            Path().apply {
                moveTo(15.2f, 21f)
                lineTo(15.2f, 3f)
                cubicTo(18.8f, 4.5f, 19.4f, 9f, 18.6f, 13.5f)
                lineTo(15.2f, 13.5f)
                close()
            }
        val fork =
            Path().apply {
                moveTo(6f, 3f)
                lineTo(6f, 8f)
                cubicTo(6f, 10.5f, 10.8f, 10.5f, 10.8f, 8f)
                lineTo(10.8f, 3f)
                moveTo(8.4f, 3f)
                lineTo(8.4f, 21f)
                moveTo(15.2f, 13f)
                lineTo(15.2f, 21f)
            }
        Glyph(fill = knife, stroke = fork, strokeWidth = 1.9f)
    }

    val bed: Glyph by lazy {
        val shapes =
            Path().apply {
                addRoundRect(RoundRect(Rect(2.5f, 12.5f, 21.5f, 17f), CornerRadius(1.2f)))
                addRoundRect(RoundRect(Rect(4.5f, 9f, 9f, 12.5f), CornerRadius(1.5f)))
                addRoundRect(RoundRect(Rect(10f, 9.5f, 21.5f, 12.5f), CornerRadius(1.2f)))
            }
        val frame =
            Path().apply {
                moveTo(2.5f, 6f)
                lineTo(2.5f, 20f)
                moveTo(21.5f, 16f)
                lineTo(21.5f, 20f)
            }
        Glyph(fill = shapes, stroke = frame, strokeWidth = 1.8f)
    }

    /** A crescent with two small stars: winding down. */
    val windDown: Glyph by lazy {
        val disc = Path().apply { addOval(Rect(Offset(10f, 14f), 7.5f)) }
        val bite = Path().apply { addOval(Rect(Offset(15f, 10.5f), 6.5f)) }
        val crescent = Path.combine(PathOperation.Difference, disc, bite)
        val sparks =
            Path().apply {
                moveTo(17f, 2.5f)
                lineTo(17f, 6.5f)
                moveTo(15f, 4.5f)
                lineTo(19f, 4.5f)
            }
        Glyph(fill = crescent, stroke = sparks, strokeWidth = 1.5f)
    }

    /** ↶, for the Now button. */
    val back: Glyph by lazy {
        val path =
            Path().apply {
                moveTo(8.5f, 5f)
                lineTo(4.5f, 9f)
                lineTo(8.5f, 13f)
                moveTo(4.5f, 9f)
                lineTo(14.5f, 9f)
                cubicTo(21f, 9f, 21f, 19f, 14.5f, 19f)
                lineTo(10.5f, 19f)
            }
        Glyph(fill = null, stroke = path, strokeWidth = 2.2f)
    }

    fun of(kind: RoutineKind): Glyph =
        when (kind) {
            RoutineKind.Light -> sun
            RoutineKind.Caffeine -> cup
            RoutineKind.Meal -> cutlery
            RoutineKind.WindDown -> windDown
            RoutineKind.Sleep -> bed
        }
}
