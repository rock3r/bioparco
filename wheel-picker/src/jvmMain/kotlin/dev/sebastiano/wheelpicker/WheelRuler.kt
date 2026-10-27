package dev.sebastiano.wheelpicker

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import dev.sebastiano.bioparco.tracing.Tracing
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/**
 * Draws the ticks around the fixed indicator, with the ruler at [position] stops and moving at
 * [motion] stops a second. Every tick goes through [lens]: it squeezes toward the rim, fades, goes
 * soft (wider and dimmer, which is what a blurred line looks like), and splits into its red, green
 * and blue channels, outward, inward and in place. The split grows toward the rim and with speed.
 * The channels add back to white wherever they overlap.
 */
internal fun DrawScope.drawRuler(
    position: Float,
    motion: Float,
    metrics: RulerMetrics,
    labels: List<ImageBitmap?>,
) {
    val centreX = size.width / 2f
    val baseline = size.height * BASELINE_FRACTION
    val reach = metrics.radius * RIM_STOPS_FACTOR / metrics.pitch
    val first = max(0, floor(position - reach).toInt())
    val last = min(WheelStops.lastIndex, ceil(position + reach).toInt())
    val speed = (abs(motion) / FULL_FRINGE_SPEED).coerceAtMost(1f)

    Tracing.section("ticks") {
        for (index in first..last) {
            val stop = WheelStops.all[index]
            val distance = (index - position) * metrics.pitch
            val point = lens(distance, metrics.radius)
            if (point.visibility <= 0f) continue
            val lift = centreBump(distance, metrics.bumpWidth)
            val height =
                (if (stop.isMajor) metrics.majorHeight else metrics.minorHeight) *
                    (1f + CENTRE_LIFT * lift)
            val softness = 1f + EDGE_SOFTNESS * point.edge
            val alpha =
                (if (stop.isMajor) MAJOR_ALPHA else MINOR_ALPHA) +
                    (1f - MINOR_ALPHA) * lift * CENTRE_GLOW
            val width = metrics.tickWidth * softness
            // The channels always overlap a little, so a tick gets coloured edges, never turns
            // into three ticks of one colour each.
            val split =
                sign(distance) *
                    min(
                        metrics.edgeFringe * point.edge * point.edge +
                            metrics.motionFringe * speed * (0.35f + point.edge),
                        width * MAX_SPLIT_OF_WIDTH,
                    )
            drawTick(
                x = centreX + point.x,
                baseline = baseline,
                width = width,
                height = height,
                alpha = (alpha * point.visibility / softness).coerceIn(0f, 1f),
                split = split,
            )
        }
    }

    Tracing.section("labels") {
        for (index in first..last) {
            val label = labels[index] ?: continue
            val distance = (index - position) * metrics.pitch
            val point = lens(distance, metrics.radius)
            if (point.visibility > 0f) {
                drawLabel(
                    label = label,
                    centre = Offset(centreX + point.x, baseline + metrics.labelGap),
                    // The drum turns the label away from us, so it narrows with the tick spacing.
                    squeeze = 1f - point.edge,
                    alpha = point.visibility * LABEL_ALPHA,
                    split = sign(distance) * metrics.labelFringe * point.edge,
                )
            }
        }
    }

    Tracing.section("indicator") {
        drawRoundRect(
            color = WheelPalette.tick,
            topLeft =
                Offset(
                    centreX - metrics.indicatorWidth / 2f,
                    baseline - metrics.indicatorHeight,
                ),
            size = Size(metrics.indicatorWidth, metrics.indicatorHeight),
            cornerRadius = CornerRadius(metrics.indicatorWidth / 2f),
        )
    }
}

private fun DrawScope.drawTick(
    x: Float,
    baseline: Float,
    width: Float,
    height: Float,
    alpha: Float,
    split: Float,
) {
    val corner = CornerRadius(width / 2f)
    val size = Size(width, height)
    fun at(dx: Float) = Offset(x + dx - width / 2f, baseline - height)
    if (abs(split) < MIN_SPLIT_PX) {
        drawRoundRect(WheelPalette.tick, at(0f), size, corner, alpha = alpha)
        return
    }
    drawRoundRect(
        WheelPalette.red,
        at(split),
        size,
        corner,
        alpha = alpha,
        blendMode = BlendMode.Plus,
    )
    drawRoundRect(
        WheelPalette.green,
        at(0f),
        size,
        corner,
        alpha = alpha,
        blendMode = BlendMode.Plus,
    )
    drawRoundRect(
        WheelPalette.blue,
        at(-split),
        size,
        corner,
        alpha = alpha,
        blendMode = BlendMode.Plus,
    )
}

private fun DrawScope.drawLabel(
    label: ImageBitmap,
    centre: Offset,
    squeeze: Float,
    alpha: Float,
    split: Float,
) {
    val topLeft = Offset(centre.x - label.width / 2f, centre.y)
    scale(scaleX = squeeze.coerceAtLeast(MIN_LABEL_SQUEEZE), scaleY = 1f, pivot = centre) {
        drawImage(label, topLeft, alpha = alpha)
        if (abs(split) < MIN_SPLIT_PX) return@scale
        // Thin strokes split into three channels read as three labels, green brightest. A
        // label keeps its grey and only gains faint red and blue halos, outward and inward.
        val halo = alpha * LABEL_HALO_ALPHA
        drawImage(
            label,
            topLeft + Offset(split, 0f),
            alpha = halo,
            colorFilter = RedOnly,
            blendMode = BlendMode.Plus,
        )
        drawImage(
            label,
            topLeft - Offset(split, 0f),
            alpha = halo,
            colorFilter = BlueOnly,
            blendMode = BlendMode.Plus,
        )
    }
}

/** Keeps one channel of an image: a grey label becomes its red, or its blue, part. */
private val RedOnly = ColorFilter.tint(WheelPalette.red, BlendMode.Modulate)
private val BlueOnly = ColorFilter.tint(WheelPalette.blue, BlendMode.Modulate)

/** Where the ticks stand, as a fraction of the ruler's height; labels hang below. */
private const val BASELINE_FRACTION = 0.56f

/** Ticks a little past the rim are skipped; this covers the rim with room to spare. */
private const val RIM_STOPS_FACTOR = 1.6f

private const val MINOR_ALPHA = 0.5f
private const val MAJOR_ALPHA = 0.78f
private const val LABEL_ALPHA = 0.72f
private const val LABEL_HALO_ALPHA = 0.4f

/** How much taller, and brighter, the ticks right next to the indicator get. */
private const val CENTRE_LIFT = 0.32f
private const val CENTRE_GLOW = 0.6f

/** At the rim a tick is this much wider, and as much dimmer. */
private const val EDGE_SOFTNESS = 2.2f

/** Speed, in stops a second, at which the motion fringe is at its widest. */
private const val FULL_FRINGE_SPEED = 30f

private const val MIN_SPLIT_PX = 0.4f
private const val MAX_SPLIT_OF_WIDTH = 0.6f
private const val MIN_LABEL_SQUEEZE = 0.2f
