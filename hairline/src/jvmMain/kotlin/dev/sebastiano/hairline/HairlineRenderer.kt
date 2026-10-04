// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import kotlin.math.min

private const val VIEW_W = 400f
private const val VIEW_H = 320f
private const val TRANSITION_MS = 260.0

/** `cubic-bezier(0.5, 0, 0.1, 1)`, the stylesheet's colour ease. */
private val colourEase = bezier(0.5, 0.0, 0.1, 1.0)

/** A colour easing from one ink to the next, as a CSS transition does when a class changes. */
private class InkTransition(var target: Color?, var from: Color?, var t0: Double) {
    fun at(now: Double): Color? {
        val f = target ?: return null
        val s = from ?: return f
        val p = colourEase(((now - t0) / TRANSITION_MS).coerceIn(0.0, 1.0)).toFloat()
        if (p >= 1f) return f
        return Color(
            s.red + (f.red - s.red) * p,
            s.green + (f.green - s.green) * p,
            s.blue + (f.blue - s.blue) * p,
        )
    }

    fun running(now: Double): Boolean = from != null && target != null && now - t0 < TRANSITION_MS

    /** Retargets from wherever it is now. `none` is not interpolable, so it switches at once. */
    fun retarget(next: Color?, now: Double) {
        if (next == target) return
        val current = at(now)
        from = if (current == null || next == null) null else current
        target = next
        t0 = now
    }
}

/**
 * Draws a figure's tree: the 400 × 320 viewBox fitted and centred in the canvas, every element in
 * tree order, strokes 0.9dp at any size as the original's non-scaling strokes are. Returns whether
 * a colour transition is still running, so the host keeps asking for frames.
 */
internal fun DrawScope.drawHairline(root: Group, palette: HairlinePalette, now: Double): Boolean {
    val scale = min(size.width / VIEW_W, size.height / VIEW_H)
    val ctx =
        DrawContext(
            palette = palette,
            now = now,
            stroke = 0.9.dp.toPx() / scale,
            dash =
                PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx() / scale, 3.dp.toPx() / scale)),
        )
    withTransform({
        translate((size.width - VIEW_W * scale) / 2, (size.height - VIEW_H * scale) / 2)
        scale(scale, scale, Offset.Zero)
    }) {
        drawGroup(root, ctx, fade = null, inGhost = false)
    }
    return ctx.transitioning
}

private class DrawContext(
    val palette: HairlinePalette,
    val now: Double,
    val stroke: Float,
    val dash: PathEffect,
) {
    var transitioning = false
}

private fun DrawScope.drawGroup(group: Group, ctx: DrawContext, fade: Fade?, inGhost: Boolean) {
    for (node in group.children) {
        if (node.hidden) continue
        when (node) {
            is Group ->
                drawGroup(node, ctx, node.fade ?: fade, inGhost || "ghost" in node.classList)
            else -> drawShape(node, ctx, fade, inGhost)
        }
    }
}

private fun DrawScope.drawShape(node: Node, ctx: DrawContext, fade: Fade?, inGhost: Boolean) {
    val paint = paintOf(node.classList, circle = node is CircleNode, inGhost = inGhost)
    val (fill, stroke) = inks(node, paint, ctx)
    val alpha = if (node.opacity.isNaN()) 1f else node.opacity.toFloat().coerceIn(0f, 1f)
    val style =
        Stroke(
            width = ctx.stroke,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = if (paint.dashed) ctx.dash else null,
        )
    when (node) {
        is PathNode -> drawPathNode(node, fill, stroke, fade, alpha, style)
        is CircleNode -> drawEllipse(node.cx, node.cy, node.r, node.r, fill, stroke, alpha, style)
        is EllipseNode ->
            drawEllipse(node.cx, node.cy, node.rx, node.ry, fill, stroke, alpha, style)
        is Group -> Unit
    }
}

/** The fill and stroke as they are now: dots ease their fill, everything else its stroke. */
private fun inks(node: Node, paint: Paint, ctx: DrawContext): Pair<Color?, Color?> {
    val fill = paint.fill?.let { ctx.palette[it] }
    val stroke = paint.stroke?.let { ctx.palette[it] }
    val eased = if (paint.fillTransitions) fill else stroke
    val transition =
        node.renderState as? InkTransition
            ?: InkTransition(eased, null, ctx.now).also { node.renderState = it }
    transition.retarget(eased, ctx.now)
    if (transition.running(ctx.now)) ctx.transitioning = true
    val now = transition.at(ctx.now)
    return if (paint.fillTransitions) now to stroke else fill to now
}

private fun DrawScope.drawPathNode(
    node: PathNode,
    fill: Color?,
    stroke: Color?,
    fade: Fade?,
    alpha: Float,
    style: Stroke,
) {
    if (node.d.isEmpty()) return
    val path = node.pathCache as? Path ?: node.d.toComposePath().also { node.pathCache = it }
    fill?.let { drawPath(path, it, alpha = alpha) }
    stroke?.let { c ->
        if (fade != null) drawPath(path, fadeBrush(c, fade), alpha = alpha, style = style)
        else drawPath(path, c, alpha = alpha, style = style)
    }
}

private fun DrawScope.drawEllipse(
    cx: Double,
    cy: Double,
    rx: Double,
    ry: Double,
    fill: Color?,
    stroke: Color?,
    alpha: Float,
    style: Stroke,
) {
    // An unplaced circle sits at the origin, as an SVG one without cx/cy does.
    val x = if (cx.isNaN()) 0f else cx.toFloat()
    val y = if (cy.isNaN()) 0f else cy.toFloat()
    val topLeft = Offset(x - rx.toFloat(), y - ry.toFloat())
    val size = Size(2 * rx.toFloat(), 2 * ry.toFloat())
    fill?.let { drawOval(it, topLeft, size, alpha) }
    stroke?.let { drawOval(it, topLeft, size, alpha, style) }
}

/** The reflection's mask: [Fade.a0] of the ink at [Fade.y0], none from [Fade.y1] down. */
private fun fadeBrush(c: Color, fade: Fade): Brush =
    Brush.verticalGradient(
        0f to c.copy(alpha = c.alpha * fade.a0.toFloat()),
        1f to c.copy(alpha = 0f),
        startY = fade.y0.toFloat(),
        endY = fade.y1.toFloat(),
    )

private fun PathData.toComposePath(): Path {
    val path = Path()
    for (part in parts) {
        part.points.forEachIndexed { i, p ->
            if (i == 0) path.moveTo(p.x.toFloat(), p.y.toFloat())
            else path.lineTo(p.x.toFloat(), p.y.toFloat())
        }
        if (part.closed) path.close()
    }
    return path
}
