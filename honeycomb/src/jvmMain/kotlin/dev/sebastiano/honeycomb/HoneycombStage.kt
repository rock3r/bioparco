package dev.sebastiano.honeycomb

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Circle diameter. The gap is the clear space between neighbours. */
internal val HoneycombTile = 120.dp

internal val HoneycombGap = 12.dp

internal const val HONEYCOMB_ROWS = 15

internal const val HONEYCOMB_COLUMNS = 20

/** Full brightness inside this radius of the viewport centre. */
internal val HoneycombFullOpacityRadius = 55.dp

/** Cells are black, and gone, at this radius. */
internal val HoneycombFadeRadius = 300.dp

/**
 * The library's minimum is 0.5, which still reads as a gentle shrink. Edge cells in the video are
 * about a quarter of full size.
 */
internal const val HONEYCOMB_MIN_SCALE = 0.25f

/**
 * Distance at which scale reaches [HONEYCOMB_MIN_SCALE]. The library uses 1000pt, but a cell has
 * already faded out at 300pt while still near 0.7 scale. 400pt lands the minimum on that fade.
 */
internal val HoneycombScaleFalloff = 400.dp

internal data class HoneycombMetrics(
    val layout: HoneycombLayout,
    val fullOpacityRadiusPx: Float,
    val fadeRadiusPx: Float,
    val scaleFalloffPx: Float,
)

@Composable
internal fun HoneycombStage(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val metrics = remember(density) { metricsIn(density) }
    val scope = rememberCoroutineScope()
    val pan = remember(metrics) { HoneycombPan(scope, metrics.layout) }
    Box(
        modifier.fillMaxSize().background(Color.Black).testTag(HoneycombTags.STAGE).pointerInput(
            pan
        ) {
            trackHoneycombDrag(pan)
        }
    ) {
        // Pan is read in offset/graphicsLayer, not here, so a drag does not recompose the field.
        for (cell in metrics.layout.cells) {
            HoneycombCell(cell, pan, metrics)
        }
    }
}

@Composable
private fun BoxScope.HoneycombCell(cell: PlacedCell, pan: HoneycombPan, metrics: HoneycombMetrics) {
    Box(
        Modifier.align(Alignment.Center)
            .offset {
                val moved = pan.offset.value
                IntOffset(
                    (cell.center.x + moved.x).roundToInt(),
                    (cell.center.y + moved.y).roundToInt(),
                )
            }
            .size(HoneycombTile)
            .graphicsLayer {
                val moved = pan.offset.value
                val focus =
                    cellFocus(
                        distance = hypot(cell.center.x + moved.x, cell.center.y + moved.y),
                        fullOpacityRadius = metrics.fullOpacityRadiusPx,
                        fadeRadius = metrics.fadeRadiusPx,
                        minScale = HONEYCOMB_MIN_SCALE,
                        scaleFalloff = metrics.scaleFalloffPx,
                    )
                scaleX = focus.scale
                scaleY = focus.scale
                alpha = focus.alpha
                transformOrigin = TransformOrigin.Center
            }
            .background(honeycombBrush(HoneycombPalette.color(cell.row, cell.column)), CircleShape)
    )
}

private fun metricsIn(density: Density): HoneycombMetrics =
    with(density) {
        HoneycombMetrics(
            layout =
                honeycombLayout(
                    rows = HONEYCOMB_ROWS,
                    columns = HONEYCOMB_COLUMNS,
                    tile = HoneycombTile.toPx(),
                    gap = HoneycombGap.toPx(),
                ),
            fullOpacityRadiusPx = HoneycombFullOpacityRadius.toPx(),
            fadeRadiusPx = HoneycombFadeRadius.toPx(),
            scaleFalloffPx = HoneycombScaleFalloff.toPx(),
        )
    }

/** 1:1 from the first pixel. A flick settles after the pointer is up; the drag itself does not. */
private suspend fun PointerInputScope.trackHoneycombDrag(pan: HoneycombPan) {
    awaitEachGesture {
        val down = awaitFirstDown()
        pan.beginDrag()
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        val completed =
            drag(down.id) { change ->
                pan.dragBy(change.positionChange())
                tracker.addPosition(change.uptimeMillis, change.position)
                change.consume()
            }
        if (completed) {
            val velocity = tracker.calculateVelocity()
            pan.settle(Offset(velocity.x, velocity.y))
        }
    }
}
