package dev.sebastiano.processingfield

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * A field of marks on a fixed square grid whose sizes describe one soft mass.
 *
 * The mark centres never move. Only each mark's size, its ink, and (for
 * [ProcessingFieldStyle.Lines]) its angle change. It draws no background, so it drops in as an
 * overlay. It says nothing about how far along the work is.
 *
 * Port of Haplo LLC's SwiftUI `ProcessingField`.
 */
@Composable
fun ProcessingField(
    modifier: Modifier = Modifier,
    style: ProcessingFieldStyle = ProcessingFieldStyle.Dots,
    isActive: Boolean = true,
    pitch: Dp = 14.dp,
    ink: Color = Color.Unspecified,
    reach: Double = 0.40,
    softness: Double = 0.34,
    minRadiusRatio: Double = 0.085,
    maxRadiusRatio: Double = 0.19,
    minInk: Double = 0.30,
    maxInk: Double = 0.85,
    driftSpeed: Double = 1.0,
    foldSpeed: Double = 1.0,
    lineLengthScale: Double = 2.4,
    lineWeightScale: Double = 0.9,
) {
    var timeSeconds by remember { mutableDoubleStateOf(0.0) }

    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        val originNs = withInfiniteAnimationFrameNanos { it }
        var lastDrawnNs = 0L
        val minIntervalNs = 1_000_000_000L / 30L
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                if (lastDrawnNs == 0L || now - lastDrawnNs >= minIntervalNs) {
                    lastDrawnNs = now
                    timeSeconds = (now - originNs) / 1_000_000_000.0
                }
            }
        }
    }

    val resolvedInk = if (ink == Color.Unspecified) LocalContentColor.current else ink

    Canvas(
        modifier = modifier.fillMaxSize().testTag(ProcessingFieldTags.FIELD).clearAndSetSemantics {}
    ) {
        if (size.width <= 1f || size.height <= 1f) return@Canvas

        val time = if (isActive) timeSeconds else null
        val frame =
            FieldFrame(
                width = size.width.toDouble(),
                height = size.height.toDouble(),
                time = time,
                pitch = pitch.toPx().toDouble(),
                reach = reach,
                softness = softness,
                minRadiusRatio = minRadiusRatio,
                maxRadiusRatio = maxRadiusRatio,
                minInk = minInk,
                maxInk = maxInk,
                driftSpeed = driftSpeed,
                foldSpeed = foldSpeed,
            )

        val drawsLines = style == ProcessingFieldStyle.Lines
        val halfScale = max(0.0, lineLengthScale)
        val weightScale = max(0.0, lineWeightScale)

        for (row in 0 until frame.rows) {
            val y = frame.yOfRow(row)
            val ny = frame.normalisedY(y)
            for (column in 0 until frame.columns) {
                val x = frame.xOfColumn(column)
                val nx = frame.normalisedX(x)
                val level = frame.level(nx, ny)
                val radius = frame.radiusAtLevel(level)
                val shade = resolvedInk.copy(alpha = frame.inkAtLevel(level).toFloat())

                if (drawsLines) {
                    val (tx, ty) = frame.tangent(nx, ny)
                    val half = radius * halfScale
                    drawLine(
                        color = shade,
                        start = Offset((x - tx * half).toFloat(), (y - ty * half).toFloat()),
                        end = Offset((x + tx * half).toFloat(), (y + ty * half).toFloat()),
                        strokeWidth = max(0.2, radius * weightScale).toFloat(),
                        cap = StrokeCap.Round,
                    )
                } else if (radius > 0.0) {
                    drawCircle(
                        color = shade,
                        radius = radius.toFloat(),
                        center = Offset(x.toFloat(), y.toFloat()),
                    )
                }
            }
        }
    }
}
