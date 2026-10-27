package dev.sebastiano.squeezeprogress

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.sebastiano.bioparco.tracing.Tracing
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Sizes in px, measured off the source video and scaled to an 800×600 window. */
internal class SqueezeMetrics(density: Density) {
    val stroke = with(density) { 8.dp.toPx() }
    /** Half the stretch the settled squiggle is made of. */
    val tightHalfWidth = with(density) { 36.dp.toPx() }
    /** Half the stretch a barely started (or released) crumple spreads over. */
    val wideHalfWidth = with(density) { 110.dp.toPx() }
    /** The tallest lobe of the settled squiggle. */
    val height = with(density) { 37.dp.toPx() }
    /** How far behind the tip the crumple sits. */
    val tipGap = with(density) { 90.dp.toPx() }
    /** How far back along the line a released crumple slides. */
    val driftDistance = with(density) { 110.dp.toPx() }
    /** The line starts off the left edge, far enough that sliding right never shows its end. */
    val lineStart = -(wideHalfWidth * 2f + stroke)
    val lineStyle = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
}

/** The line's path, rebuilt in place each frame rather than allocated. */
internal class CrumpledLine {
    val path = Path()

    fun trace(start: Float, tip: Float, y: Float, crumple: Crumple?) {
        path.rewind()
        if (crumple == null) {
            path.moveTo(start, y)
            path.lineTo(tip, y)
            return
        }
        path.moveTo(crumpleX(start, crumple), y)
        val left = maxOf(start, crumple.centre - crumple.halfWidth)
        val right = minOf(tip, crumple.centre + crumple.halfWidth)
        if (right > left) {
            for (i in 0..CRUMPLE_SAMPLES) {
                val s = left + (right - left) * i / CRUMPLE_SAMPLES
                path.lineTo(crumpleX(s, crumple), y + crumpleY(s, crumple))
            }
        }
        path.lineTo(crumpleX(tip, crumple), y + crumpleY(tip, crumple))
    }
}

/**
 * How visible the ink is during a swap of looks: the video fades text, icon and line most of the
 * way out halfway through, so the two inks never sit on top of each other at full strength.
 */
internal fun inkVisibility(look: Float): Float = 1f - INK_DIP * sin(PI.toFloat() * look)

internal fun DrawScope.drawField(
    motion: SqueezeMotion,
    metrics: SqueezeMetrics,
    line: CrumpledLine,
) {
    val look = motion.look.value
    drawRect(SqueezeProgressPalette.field(look))
    val head = motion.progress * size.width
    drawRect(SqueezeProgressPalette.fill(look), size = Size(head, size.height))

    val crumple = crumpleNow(motion, metrics)
    // The round cap ends exactly on the fill's edge, as in the video.
    val tip = head - metrics.stroke / 2f
    Tracing.section("trace line") { line.trace(metrics.lineStart, tip, center.y, crumple) }
    drawPath(
        line.path,
        SqueezeProgressPalette.ink(look),
        alpha = inkVisibility(look),
        style = metrics.lineStyle,
    )
}

/** The crumple as of this frame, or null when the line is flat. */
private fun DrawScope.crumpleNow(motion: SqueezeMotion, metrics: SqueezeMetrics): Crumple? {
    val squeeze = motion.crumple.value
    if (squeeze == 0f) return null
    val drift = motion.drift.value
    // As it squeezes, the stretch it takes narrows; a released one spreads out as it slides back.
    val halfWidth =
        lerp(metrics.wideHalfWidth, metrics.tightHalfWidth, squeeze.coerceIn(0f, 1f)) *
            (1f + drift * DRIFT_SPREAD)
    return Crumple(
        centre = motion.anchor * size.width - metrics.tipGap - drift * metrics.driftDistance,
        halfWidth = halfWidth,
        height = squeeze * metrics.height,
        squeeze = squeeze,
    )
}

/**
 * The button's icon: a pause sign in a ring while playing, a pair of turning arrows while paused.
 * They cross-fade with the look, through the same dip as the rest of the ink.
 */
internal fun DrawScope.drawToggleIcon(look: Float) {
    val ink = SqueezeProgressPalette.ink(look)
    val visibility = inkVisibility(look)
    val pause = (1f - look) * visibility
    val resume = look * visibility
    if (pause > INVISIBLE) drawPauseIcon(ink, pause)
    if (resume > INVISIBLE) drawResumeIcon(ink, resume)
}

private fun DrawScope.drawPauseIcon(ink: Color, alpha: Float) {
    val d = size.minDimension
    val ring = d * RING_STROKE
    drawCircle(ink, radius = d / 2f - ring / 2f, alpha = alpha, style = Stroke(ring))
    val half = d * BAR_LENGTH / 2f
    for (side in BarSides) {
        val x = center.x + side * d * BAR_SPACING / 2f
        drawLine(
            ink,
            start = Offset(x, center.y - half),
            end = Offset(x, center.y + half),
            strokeWidth = d * RING_STROKE,
            cap = StrokeCap.Round,
            alpha = alpha,
        )
    }
}

/**
 * Two arcs chasing each other round, each ending in an L-shaped arrowhead whose corner is the tip.
 * The heads' far legs reach past the button's circle, so the button must not clip to it.
 */
private fun DrawScope.drawResumeIcon(ink: Color, alpha: Float) {
    val d = size.minDimension
    val radius = d * ARROW_RADIUS
    val width = d * ARROW_STROKE
    val leg = d * ARROW_LEG
    val style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    for (arrow in Arrows) {
        drawArc(
            ink,
            startAngle = arrow.arcStart,
            sweepAngle = ARROW_SWEEP,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            alpha = alpha,
            style = style,
        )
        val angle = arrow.corner * PI.toFloat() / 180f
        val corner = Offset(center.x + radius * cos(angle), center.y + radius * sin(angle))
        val legs = arrow.legs
        for (end in listOf(corner + Offset(legs.x * leg, 0f), corner + Offset(0f, legs.y * leg))) {
            drawLine(ink, corner, end, strokeWidth = width, cap = StrokeCap.Round, alpha = alpha)
        }
    }
}

/** Points along the crumpled stretch. Enough for a smooth curve through the tightest loop. */
private const val CRUMPLE_SAMPLES = 120

/** A released crumple ends up this much wider than it started. */
private const val DRIFT_SPREAD = 0.8f

private const val INK_DIP = 0.8f

private const val INVISIBLE = 0.004f

// Icon proportions, as fractions of the button's diameter, from the video.
private const val RING_STROKE = 0.07f
private const val BAR_LENGTH = 0.34f
private const val BAR_SPACING = 0.24f
private const val ARROW_RADIUS = 0.43f
private const val ARROW_STROKE = 0.09f
private const val ARROW_LEG = 0.28f
private const val ARROW_SWEEP = 153f

/** The two pause bars sit either side of the centre. */
private val BarSides = floatArrayOf(-1f, 1f)

/**
 * One arc of the resume icon: where it starts (degrees clockwise from 3 o'clock), where its
 * arrowhead's corner sits on the circle, and which way the head's legs point from that corner.
 */
private class Arrow(val arcStart: Float, val corner: Float, val legs: Offset)

/** The top arc runs over the top to its head near 9 o'clock; the bottom one is it turned round. */
private val Arrows = listOf(Arrow(192f, 192f, Offset(1f, -1f)), Arrow(12f, 12f, Offset(-1f, 1f)))
