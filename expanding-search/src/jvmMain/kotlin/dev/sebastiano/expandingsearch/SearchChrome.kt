package dev.sebastiano.expandingsearch

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Draws the pill and the panel behind the field and the rows: their soft shadows, one-pixel edges,
 * the pill's white rim around its grey well, the magnifier, and the row highlight.
 *
 * The shadows are [SoftShadow]s: only the ring where a blurred round rect fades is painted.
 */
internal class SearchChrome(private val geometry: SearchGeometry) {
    // Skia's blur takes a sigma; a blur reaches out about two sigmas.
    private val shadow = SoftShadow(ExpandingSearchPalette.shadow, sigma = geometry.shadowBlur / 2f)
    private val panelClip = Path()

    fun draw(scope: DrawScope, motion: SearchMotion) =
        with(scope) {
            val expansion = motion.expansion.value
            val reveal = motion.reveal.value
            val pill = geometry.pill(expansion)
            val panel = geometry.panel(expansion, reveal, motion.panelRows.value)
            val panelAlpha = panelAlpha(reveal)

            if (panelAlpha > 0f) drawShadow(panel, panelRadius(panel), panelAlpha)
            drawShadow(pill, pill.height / 2f, 1f)
            drawSurface(pill, pill.height / 2f, 1f)
            drawWell(pill)
            drawMagnifier(Offset(pill.left + geometry.iconCentre, pill.center.y))
            if (panelAlpha > 0f) {
                drawSurface(panel, panelRadius(panel), panelAlpha)
                drawHighlight(panel, motion, panelAlpha)
            }
        }

    /**
     * Draws [block] clipped to the panel's current outline. The rows' origin is the panel's top.
     */
    fun clipToPanel(scope: DrawScope, motion: SearchMotion, block: DrawScope.() -> Unit) =
        with(scope) {
            val panel =
                geometry
                    .panel(motion.expansion.value, motion.reveal.value, motion.panelRows.value)
                    .translate(0f, -geometry.panelTop)
            panelClip.reset()
            panelClip.addRoundRect(RoundRect(panel, CornerRadius(panelRadius(panel))))
            clipPath(panelClip) { block() }
        }

    private fun DrawScope.drawShadow(rect: Rect, radius: Float, alpha: Float) {
        val dropped = rect.translate(0f, geometry.shadowDrop)
        // Under a see-through surface the shadow's inside would show, so fill it then.
        drawIntoCanvas { shadow.draw(it, dropped, radius, alpha, fillInside = alpha < 1f) }
    }

    private fun DrawScope.drawSurface(rect: Rect, radius: Float, alpha: Float) {
        val edge = geometry.hairline
        drawRoundRect(
            ExpandingSearchPalette.edge,
            topLeft = rect.topLeft - Offset(edge, edge),
            size = rect.inflate(edge).size,
            cornerRadius = CornerRadius(radius + edge),
            alpha = alpha,
        )
        drawRoundRect(
            ExpandingSearchPalette.surface,
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = CornerRadius(radius),
            alpha = alpha,
        )
    }

    private fun DrawScope.drawWell(pill: Rect) {
        val well = pill.deflate(geometry.rim)
        drawRoundRect(
            ExpandingSearchPalette.well,
            topLeft = well.topLeft,
            size = well.size,
            cornerRadius = CornerRadius(well.height / 2f),
        )
    }

    private fun DrawScope.drawMagnifier(centre: Offset) {
        val radius = geometry.iconRadius
        val stroke = geometry.iconStroke
        // The lens sits up and left of centre, so lens and handle together are centred.
        val lens = centre - Offset(radius * 0.28f, radius * 0.28f)
        drawCircle(
            ExpandingSearchPalette.icon,
            radius = radius,
            center = lens,
            style = Stroke(stroke),
        )
        val diagonal = 1f / sqrt(2f)
        val start = lens + Offset(radius * diagonal, radius * diagonal)
        val end = start + Offset(radius * 0.72f, radius * 0.72f)
        drawLine(ExpandingSearchPalette.icon, start, end, stroke, cap = StrokeCap.Round)
    }

    private fun DrawScope.drawHighlight(panel: Rect, motion: SearchMotion, panelAlpha: Float) {
        val alpha = panelAlpha * motion.highlightAlpha.value
        if (alpha <= 0f) return
        val rows = geometry.rows
        val top = panel.top + rows.top(motion.highlightRow.value)
        val band =
            Rect(panel.left + rows.padding, top, panel.right - rows.padding, top + rows.rowHeight)
        panelClip.reset()
        panelClip.addRoundRect(RoundRect(panel, CornerRadius(panelRadius(panel))))
        clipPath(panelClip) {
            drawRoundRect(
                ExpandingSearchPalette.well,
                topLeft = band.topLeft,
                size = band.size,
                cornerRadius = CornerRadius(band.height / 2f),
                alpha = alpha,
            )
        }
    }

    private fun panelRadius(panel: Rect): Float = min(geometry.panelRadius, panel.height / 2f)

    private companion object {
        /** The panel fades in over the first quarter of its reveal, and out over the last. */
        fun panelAlpha(reveal: Float): Float = (reveal / 0.25f).coerceIn(0f, 1f)
    }
}
