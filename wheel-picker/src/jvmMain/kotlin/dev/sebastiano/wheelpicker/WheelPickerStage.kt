package dev.sebastiano.wheelpicker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sebastiano.bioparco.tracing.Tracing
import kotlin.math.abs
import kotlin.math.roundToInt
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/** The stop the ruler opens on: 20m, as in the original's first frame. */
private const val INITIAL_INDEX = 4

@Composable
internal fun WheelPickerStage(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val scroll = remember { WheelScroll(scope, INITIAL_INDEX) }
    val selected by scroll.selectedIndex
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(
        modifier
            .fillMaxSize()
            .background(WheelPalette.background)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> scroll.step(1)
                    Key.DirectionLeft -> scroll.step(-1)
                    Key.MoveHome -> scroll.select(0)
                    Key.MoveEnd -> scroll.select(WheelStops.lastIndex)
                    else -> return@onKeyEvent false
                }
                true
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            WheelReadout(
                text = formatReadout(WheelStops.all[selected].minutes),
                style = readoutStyle(),
                modifier = Modifier.testTag(WheelTags.READOUT).fillMaxWidth().height(84.dp),
            )
            Spacer(Modifier.height(16.dp))
            Ruler(scroll, Modifier.widthIn(max = 860.dp).fillMaxWidth().height(120.dp))
        }
        Text(
            "Drag, flick, scroll, click a tick, or use ← →",
            color = WheelPalette.hint,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
        )
    }
}

@Composable
private fun Ruler(scroll: WheelScroll, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = JewelTheme.defaultTextStyle.copy(color = WheelPalette.label, fontSize = 13.sp)
    // Each label is drawn once into an image: see textImage.
    val labels: List<ImageBitmap?> =
        remember(measurer, labelStyle, density) {
            WheelStops.all.map { stop ->
                if (!stop.isMajor) return@map null
                textImage(measurer.measure(formatReadout(stop.minutes), labelStyle), density)
            }
        }
    var widthPx by remember { mutableFloatStateOf(0f) }
    // A resize recomposes the ruler once, for the new lens; scrolling never does.
    val metrics = remember(density, widthPx) { rulerMetrics(density, widthPx) }
    val currentMetrics by rememberUpdatedState(metrics)
    Canvas(
        modifier
            .testTag(WheelTags.RULER)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(scroll) { trackDrag(scroll) { currentMetrics } }
            .pointerInput(scroll) { trackWheel(scroll) }
    ) {
        // Position and motion are read here, in the draw phase: scrolling only redraws.
        Tracing.section("wheel ruler") {
            drawRuler(scroll.position.value, scroll.motion.value, metrics, labels)
        }
    }
}

@Composable
private fun readoutStyle(): TextStyle =
    JewelTheme.defaultTextStyle.copy(
        color = WheelPalette.tick,
        fontSize = 54.sp,
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = "tnum",
    )

/**
 * A drag past the slop moves the ruler 1:1 and flicks on release. Anything shorter is a click: the
 * ruler goes to the tick under the pointer, found by undoing the lens.
 */
private suspend fun PointerInputScope.trackDrag(scroll: WheelScroll, metrics: () -> RulerMetrics) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val pitch = metrics().pitch
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var overSlop = 0f
        val start =
            awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                change.consume()
                overSlop = over
            }
        if (start == null) {
            val fromCentre = down.position.x - size.width / 2f
            val stops = unlens(fromCentre, metrics().radius) / pitch
            scroll.select((scroll.position.value + stops).roundToInt())
            return@awaitEachGesture
        }
        scroll.beginDrag()
        tracker.addPosition(start.uptimeMillis, start.position)
        scroll.dragBy(-overSlop / pitch, 0f)
        var lastMove = start.uptimeMillis
        val completed =
            horizontalDrag(start.id) { change ->
                tracker.addPosition(change.uptimeMillis, change.position)
                lastMove = change.uptimeMillis
                val speed = abs(tracker.calculateVelocity().x) / pitch
                scroll.dragBy(-change.positionChange().x / pitch, speed)
                change.consume()
            }
        // horizontalDrag keeps the release to itself; it is the scope's current event.
        val released = currentEvent.changes.firstOrNull { it.id == start.id }?.uptimeMillis
        val velocity =
            if (completed && released != null) {
                releaseVelocity(-tracker.calculateVelocity().x / pitch, lastMove, released)
            } else {
                0f
            }
        scroll.release(velocity)
    }
}

/** A wheel notch or a trackpad swipe, down or right, moves the ruler forward. */
private suspend fun PointerInputScope.trackWheel(scroll: WheelScroll) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull()
            if (event.type == PointerEventType.Scroll && change != null) {
                scroll.scrollBy(change.scrollDelta.y + change.scrollDelta.x)
                change.consume()
            }
        }
    }
}
