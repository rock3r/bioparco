package dev.sebastiano.emailinvite

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

/** The white pill with its shadow, the grey button/field, and the plus/envelope icon. */
internal fun DrawScope.drawPill(motion: InviteMotion, geometry: PillGeometry, shadow: PillShadow) {
    val open = motion.open.value
    val tuck = motion.tuck.value
    val slots = motion.slots.value
    val height = geometry.pillHeight
    val left = geometry.pillLeft(open, tuck, slots)
    val width = geometry.pillWidth(open, tuck, slots)
    val top = geometry.centreY - height / 2f

    with(shadow) { draw(left, top, width) }
    val hair = 1.dp.toPx()
    drawRoundRect(
        EmailInvitePalette.hairline,
        Offset(left - hair, top - hair),
        Size(width + 2 * hair, height + 2 * hair),
        CornerRadius(height / 2f + hair),
    )
    drawRoundRect(
        EmailInvitePalette.pill,
        Offset(left, top),
        Size(width, height),
        CornerRadius(height / 2f),
    )

    val fieldLeft = geometry.fieldLeft(open, tuck, slots)
    val fieldHeight = geometry.fieldHeight
    // Hovering darkens the button, and only the button: the open field stays put.
    val hover = motion.hover.value * (1f - open).coerceIn(0f, 1f)
    drawRoundRect(
        lerp(EmailInvitePalette.field, EmailInvitePalette.fieldHover, hover),
        Offset(fieldLeft, geometry.centreY - fieldHeight / 2f),
        Size(geometry.fieldWidth(open), fieldHeight),
        CornerRadius(fieldHeight / 2f),
    )
    drawPlusEnvelope(Offset(fieldLeft + geometry.iconCentre, geometry.centreY), motion.icon.value)
}

/**
 * The pill's soft shadow, drawn once into a bitmap and stretched like a nine-patch: whole-pixel
 * copies of its two round ends, and its middle column stretched without filtering. Drawn directly,
 * its seven translucent layers cost software Skia about 6 ns a pixel each on every frame; the copy
 * costs under one, which is what lets `SOFTWARE_COMPAT` (the CI recordings) keep more frames.
 */
internal class PillShadow(density: Density, private val height: Float) {
    private val margin = with(density) { (SHADOW_LAYERS * 1.2f + 2f).dp.toPx() }.roundToInt()
    // From the bitmap's edge to its middle column: the margin plus a round end.
    private val cap = margin + (height / 2f).roundToInt()
    private val image: ImageBitmap =
        ImageBitmap(2 * cap + 1, (height + 2 * margin).roundToInt()).also { bitmap ->
            // A pill one pixel longer than it is tall, so the bitmap has a middle column.
            CanvasDrawScope().draw(
                density,
                LayoutDirection.Ltr,
                Canvas(bitmap),
                Size(bitmap.width.toFloat(), bitmap.height.toFloat()),
            ) {
                drawSoftShadow(
                    Offset(margin.toFloat(), margin.toFloat()),
                    Size(2f * (cap - margin) + 1f, height),
                )
            }
        }

    /** Draws the shadow of a pill at [left], [top], [width] wide. */
    fun DrawScope.draw(left: Float, top: Float, width: Float) {
        val x0 = left.roundToInt() - margin
        val x1 = (left + width).roundToInt() + margin
        val y = top.roundToInt() - margin
        val size = IntSize(cap, image.height)
        drawImage(
            image,
            IntOffset.Zero,
            size,
            IntOffset(x0, y),
            size,
            filterQuality = FilterQuality.None,
        )
        drawImage(
            image,
            IntOffset(cap + 1, 0),
            size,
            IntOffset(x1 - cap, y),
            size,
            filterQuality = FilterQuality.None,
        )
        val middle = x1 - cap - (x0 + cap)
        if (middle > 0) {
            drawImage(
                image,
                IntOffset(cap, 0),
                IntSize(1, image.height),
                IntOffset(x0 + cap, y),
                IntSize(middle, image.height),
                filterQuality = FilterQuality.None,
            )
        }
    }
}

/** The video's shadow sits mostly below the pill and fades over about 8dp. */
private fun DrawScope.drawSoftShadow(topLeft: Offset, size: Size) {
    val step = 1.dp.toPx()
    for (layer in SHADOW_LAYERS downTo 1) {
        val grow = layer * step
        drawRoundRect(
            EmailInvitePalette.shadow.copy(alpha = SHADOW_ALPHA),
            Offset(topLeft.x - grow * 0.6f, topLeft.y + grow * 0.5f),
            Size(size.width + grow * 1.2f, size.height + grow),
            CornerRadius(size.height / 2f + grow),
        )
    }
}

/**
 * A plus at [morph] 0, an envelope at 1. They hand over rather than overlap: the plus shrinks away
 * in the first half and the envelope grows in over the second, as in the video.
 */
private fun DrawScope.drawPlusEnvelope(centre: Offset, morph: Float) {
    val ink = EmailInvitePalette.ink
    val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val out = (morph * 2f).coerceIn(0f, 1f)
    val into = (morph * 2f - 1f).coerceIn(0f, 1f)
    if (out < 1f) {
        val arm = 6.5.dp.toPx()
        scale(1f - 0.4f * out, centre) {
            val plus = ink.copy(alpha = 1f - out)
            drawLine(
                plus,
                centre - Offset(arm, 0f),
                centre + Offset(arm, 0f),
                stroke.width,
                StrokeCap.Round,
            )
            drawLine(
                plus,
                centre - Offset(0f, arm),
                centre + Offset(0f, arm),
                stroke.width,
                StrokeCap.Round,
            )
        }
    }
    if (into > 0f) {
        val half = Size(8.dp.toPx(), 6.3.dp.toPx())
        val body =
            Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        centre.x - half.width,
                        centre.y - half.height,
                        centre.x + half.width,
                        centre.y + half.height,
                        CornerRadius(2.5.dp.toPx()),
                    )
                )
            }
        val flap =
            Path().apply {
                val inset = 2.2.dp.toPx()
                moveTo(centre.x - half.width + inset, centre.y - half.height + inset)
                lineTo(centre.x, centre.y + 0.8.dp.toPx())
                lineTo(centre.x + half.width - inset, centre.y - half.height + inset)
            }
        scale(0.6f + 0.4f * into, centre) {
            drawPath(body, ink.copy(alpha = into), style = stroke)
            drawPath(flap, ink.copy(alpha = into), style = stroke)
        }
    }
}

/** A soft white band that sweeps once across the circle, tilted like the video's. */
internal fun DrawScope.drawShine(radius: Float, progress: Float) {
    val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, radius)) }
    val x = center.x + lerp(-1.7f * radius, 1.7f * radius, progress)
    val band = 0.55f * radius
    clipPath(circle) {
        rotate(SHINE_TILT, center) {
            drawRect(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = SHINE_ALPHA),
                    1f to Color.Transparent,
                    startX = x - band,
                    endX = x + band,
                ),
                topLeft = Offset(x - band, center.y - 2 * radius),
                size = Size(2 * band, 4 * radius),
            )
        }
    }
}

private const val SHADOW_LAYERS = 7
private const val SHADOW_ALPHA = 0.016f
private const val SHINE_TILT = 20f
private const val SHINE_ALPHA = 0.6f
