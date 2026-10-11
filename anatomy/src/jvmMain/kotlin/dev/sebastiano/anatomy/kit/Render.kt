// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.skiaPaint
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter

/*
 * Paints art the way the original's `ISO_CSS` styles its SVG: hairlines are 0.6 screen px at any
 * size (the SVG's non-scaling strokes), dash patterns are in screen px, and geometry is in viewBox
 * units scaled to the canvas.
 */

/** The hairline weight in screen px. */
const val WEIGHT: Float = 0.6f

/** What a figure draws with: the canvas in viewBox units, the theme, and the clock. */
class IsoScope(
    val draw: DrawScope,
    val palette: Palette,
    /** Canvas px per viewBox unit. */
    val unit: Float,
    val textMeasurer: TextMeasurer,
    /** Seconds, for marching dashes. */
    val clock: Double,
) {
    /** A screen-px length in viewBox units. */
    fun px(value: Float): Float = value * draw.density / unit

    /** Paints [art], moving each group by the offset [offsetOf] gives its part, if any. */
    fun drawArt(art: Art, offsetOf: (String) -> Flat? = { null }) {
        for (mark in art.marks) drawMark(mark, offsetOf)
    }

    /** Paints a solid now: for a part that is rebuilt each frame. */
    fun solid(
        paths: SolidPaths,
        tone: Tone = Tone.Hi,
        crease: Tone? = Tone.Faint,
        lit: Boolean = false,
        flat: Boolean = false,
    ) = drawSolid(SolidMark(paths, tone, crease, lit, flat, null))

    /** Paints a line now: a needle, a pointer, a spring that compresses. */
    fun line(
        path: IsoPath,
        tone: Tone = Tone.Lo,
        stroke: Stroke = Stroke.Solid,
        width: Double? = null,
    ) {
        if (!path.isEmpty) drawLine(LineMark(Shape(path), tone, stroke, Free.None, width, 1.0))
    }

    /** Paints dots now: rivets on a moving part, a counter's lamps. */
    fun dots(points: List<Flat>, size: Double = 0.5, tone: Tone = Tone.Mid) {
        if (points.isNotEmpty()) drawDots(DotsMark(points, null, size, tone))
    }

    /** Paints face text now: digits a machine displays, which change as it runs. */
    fun faceText(
        matrix: FaceMatrix,
        text: String,
        size: Double = 3.0,
        tone: Tone = Tone.Mid,
        anchor: Anchor = Anchor.Start,
    ) = drawText(TextMark(matrix, text, size, tone, anchor))

    /** Paints [art] moved by a screen [offset]: one copy of a part that appears several times. */
    fun drawArt(art: Art, offset: Flat) {
        draw.translate(offset.x.toFloat(), offset.y.toFloat()) { drawArt(art) }
    }

    private fun drawMark(mark: Mark, offsetOf: (String) -> Flat?) {
        when (mark) {
            is SolidMark -> drawSolid(mark)
            is LineMark -> drawLine(mark)
            is DotsMark -> drawDots(mark)
            is WireMark -> drawWire(mark)
            is TextMark -> drawText(mark)
            is HaloMark -> drawHalo(mark)
            is FadeLineMark -> drawFadeLine(mark)
            is GroupMark -> {
                val clip = mark.clip
                if (clip == null) drawGroup(mark, offsetOf)
                else draw.clipPath(clip.path) { drawGroup(mark, offsetOf) }
            }
        }
    }

    /** A group moved by its offset; its clip, applied outside the move, holds still. */
    private fun drawGroup(mark: GroupMark, offsetOf: (String) -> Flat?) {
        val offset = offsetOf(mark.part)
        if (offset == null) {
            drawArt(mark.art, offsetOf)
        } else {
            draw.translate(offset.x.toFloat(), offset.y.toFloat()) { drawArt(mark.art, offsetOf) }
        }
    }

    private fun hairline(width: Float = WEIGHT, effect: PathEffect? = null) =
        DrawStroke(
            width = px(width),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = effect,
        )

    private fun drawSolid(mark: SolidMark) {
        val p = palette
        draw.drawPath(mark.fill.path, if (mark.lit) p.face else p.paper, style = Fill)
        if (!mark.flat) {
            mark.shades.forEachIndexed { shade, shape ->
                if (shape.iso.isEmpty) return@forEachIndexed
                val color = if (mark.lit) p.litShade else p.shades[shade]
                draw.drawPath(shape.path, color, style = Fill)
                if (mark.paths.seamless) draw.drawPath(shape.path, color, style = hairline(0.4f))
            }
            val top = if (mark.lit) p.litTop else p.top
            draw.drawPath(mark.top.path, top, style = Fill)
            if (mark.paths.seamless) draw.drawPath(mark.top.path, top, style = hairline(0.4f))
        }
        if (mark.crease != null) {
            val color = p.ink(mark.crease)
            if (!mark.creaseLine.iso.isEmpty) {
                draw.drawPath(mark.creaseLine.path, color, style = hairline())
            }
            mark.bevel?.let { draw.drawPath(it.path, color, style = hairline()) }
        }
        mark.inner?.let { drawArt(it) }
        val edge = if (mark.lit) p.lit else p.ink(mark.tone)
        draw.drawPath(mark.outline.path, edge, style = hairline())
    }

    private fun drawLine(mark: LineMark) {
        val stroke =
            when (mark.stroke) {
                Stroke.Solid -> hairline(mark.width?.toFloat() ?: WEIGHT)
                Stroke.Dotted -> hairline(1f, dashes(0.01f, 3.5f))
                Stroke.Dashed -> hairline(mark.width?.toFloat() ?: WEIGHT, dashes(3f, 3f))
                Stroke.Flow -> {
                    // The original's dashes march 32 px every 1.6 s.
                    val phase = (clock % 1.6 / 1.6 * 32).toFloat()
                    hairline(mark.width?.toFloat() ?: WEIGHT, dashes(2f, 6f, -phase))
                }
            }
        draw.drawPath(
            mark.shape.path,
            palette.ink(mark.tone),
            alpha = mark.alpha.toFloat(),
            style = stroke,
        )
    }

    private fun dashes(on: Float, off: Float, phase: Float = 0f) =
        PathEffect.dashPathEffect(floatArrayOf(px(on), px(off)), px(phase))

    private fun drawDots(mark: DotsMark) {
        val alpha = palette.dotAlpha(mark.tone)
        mark.points.forEachIndexed { index, point ->
            val each = mark.alphas?.get(index)?.coerceIn(0.0, 1.0)?.toFloat() ?: 1f
            draw.drawCircle(
                palette.dot,
                radius = mark.size.toFloat(),
                center = Offset(point.x.toFloat(), point.y.toFloat()),
                alpha = alpha * each,
            )
        }
    }

    private fun drawWire(mark: WireMark) {
        drawLine(mark.line)
        for (end in mark.ends) {
            val center = Offset(end.x.toFloat(), end.y.toFloat())
            draw.drawCircle(palette.paper, mark.size.toFloat(), center)
            draw.drawCircle(palette.ink(mark.tone), mark.size.toFloat(), center, style = hairline())
        }
    }

    private fun drawText(mark: TextMark) {
        val layout =
            textMeasurer.measure(
                mark.text,
                TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = (TEXT_REFERENCE / draw.density).sp,
                    letterSpacing = 0.04.em,
                ),
            )
        val anchorX =
            when (mark.anchor) {
                Anchor.Start -> 0f
                Anchor.Middle -> layout.size.width / 2f
                Anchor.End -> layout.size.width.toFloat()
            }
        val shrink = (mark.size / TEXT_REFERENCE).toFloat()
        val m = mark.matrix
        val matrix =
            Matrix().apply {
                this[0, 0] = m.u.x.toFloat()
                this[0, 1] = m.u.y.toFloat()
                this[1, 0] = m.v.x.toFloat()
                this[1, 1] = m.v.y.toFloat()
                this[3, 0] = m.origin.x.toFloat()
                this[3, 1] = m.origin.y.toFloat()
            }
        draw.withTransform({
            transform(matrix)
            scale(shrink, shrink, Offset.Zero)
            translate(-anchorX, -layout.firstBaseline)
        }) {
            drawText(layout, color = palette.ink(mark.tone))
        }
    }

    private fun drawHalo(mark: HaloMark) {
        draw.drawIntoCanvas { canvas ->
            val paint = Paint().apply { color = palette.halo.copy(alpha = palette.haloOpacity) }
            paint.skiaPaint.maskFilter =
                MaskFilter.makeBlur(FilterBlurMode.NORMAL, mark.blur.toFloat())
            canvas.save()
            canvas.translate(0f, HALO_DROP)
            canvas.drawPath(mark.shape.path, paint)
            canvas.restore()
        }
    }

    private fun drawFadeLine(mark: FadeLineMark) {
        val points = mark.points
        val lengths = DoubleArray(points.size)
        for (index in 1 until points.size) {
            val a = points[index - 1]
            val b = points[index]
            lengths[index] = lengths[index - 1] + hypot(b.x - a.x, b.y - a.y)
        }
        val total = lengths.last()
        if (total < 1e-6) return
        var start = mark.fadeStart
        var end = mark.fadeEnd
        if (start + end > total) {
            val share = total / (start + end)
            start *= share
            end *= share
        }
        fun at(length: Double): Flat {
            var index = 1
            while (index < points.size - 1 && lengths[index] < length) index++
            val span = (lengths[index] - lengths[index - 1]).takeIf { it != 0.0 } ?: 1.0
            val t = ((length - lengths[index - 1]) / span).coerceIn(0.0, 1.0)
            return points[index - 1] + (points[index] - points[index - 1]) * t
        }
        fun piece(from: Double, to: Double): List<Flat> =
            listOf(at(from)) +
                points.filterIndexed { i, _ ->
                    lengths[i] > from + 1e-6 && lengths[i] < to - 1e-6
                } +
                at(to)
        val color = palette.ink(mark.tone)
        fun ramp(piece: List<Flat>, rising: Boolean) {
            val stops =
                Array(FADE_STOPS + 1) { index ->
                    val t = index.toFloat() / FADE_STOPS
                    val alpha = smooth(0.0, 1.0, if (rising) t.toDouble() else 1.0 - t).toFloat()
                    t to color.copy(alpha = alpha)
                }
            val first = piece.first()
            val last = piece.last()
            val brush =
                Brush.linearGradient(
                    *stops,
                    start = Offset(first.x.toFloat(), first.y.toFloat()),
                    end = Offset(last.x.toFloat(), last.y.toFloat()),
                )
            draw.drawPath(pathOf(piece).toComposePath(), brush, style = hairline())
        }
        if (start > 0.05) ramp(piece(0.0, start), rising = true)
        if (total - end - start > 0.05) {
            draw.drawPath(
                pathOf(piece(start, total - end)).toComposePath(),
                color,
                style = hairline(),
            )
        }
        if (end > 0.05) ramp(piece(total - end, total), rising = false)
    }

    private companion object {
        const val TEXT_REFERENCE = 24.0
        const val HALO_DROP = 3f
        const val FADE_STOPS = 6
    }
}

/**
 * A figure's drawing surface: a [width] by [height] viewBox, scaled to the available width. Read
 * any per-frame state inside [figure], so motion only redraws and never recomposes.
 */
@Composable
fun IsoCanvas(
    width: Double,
    height: Double,
    palette: Palette,
    modifier: Modifier = Modifier,
    clock: () -> Double = { 0.0 },
    figure: IsoScope.() -> Unit,
) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier.aspectRatio((width / height).toFloat())) {
        val unit = size.width / width.toFloat()
        scale(unit, unit, pivot = Offset.Zero) {
            IsoScope(this, palette, unit, measurer, clock()).figure()
        }
    }
}
