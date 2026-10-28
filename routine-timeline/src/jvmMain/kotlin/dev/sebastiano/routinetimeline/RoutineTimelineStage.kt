package dev.sebastiano.routinetimeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sebastiano.bioparco.tracing.Tracing
import kotlin.math.atan2
import kotlin.math.hypot
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

@Composable
internal fun RoutineTimelineStage(fixedNow: Int?, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val motion = remember(scope, fixedNow) { TimelineMotion(scope, fixedNow) }
    LaunchedEffect(motion) { motion.run() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }

    Box(
        modifier
            .fillMaxSize()
            .background(RoutinePalette.window)
            .testTag(RoutineTimelineTags.STAGE)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> motion.step(1)
                    Key.DirectionUp -> motion.step(-1)
                    Key.MoveHome,
                    Key.N -> motion.backToNow()
                    else -> return@onPreviewKeyEvent false
                }
                true
            }
            .focusRequester(focus)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        TimelineCard(motion)
    }
}

@Composable
private fun TimelineCard(motion: TimelineMotion) {
    val measurer = rememberTextMeasurer()
    val baseStyle = JewelTheme.defaultTextStyle
    Box(
        Modifier.size(TimelineMetrics.cardWidth, TimelineMetrics.cardHeight)
            .testTag(RoutineTimelineTags.DIAL)
            .pointerInput(motion) { turnDial(motion) }
            .pointerInput(motion) { scrollDial(motion) }
            .drawWithCache {
                val painter = DialPainter(this, measurer, baseStyle, motion.routine)
                // The time is read here, in the draw phase: turning the dial only redraws.
                onDrawBehind {
                    Tracing.section("routine dial") {
                        painter.draw(this, motion.time.value.toDouble())
                    }
                }
            }
    ) {
        Header(motion, Modifier.fillMaxWidth().padding(top = TimelineMetrics.headerTop))
        EventList(
            motion,
            Modifier.offset(x = TimelineMetrics.listLeft, y = TimelineMetrics.dialY - ListBox / 2)
                .size(TimelineMetrics.listWidth, ListBox),
        )
    }
}

private val ListBox = TimelineMetrics.rowPitch * 3

@Composable
private fun Header(motion: TimelineMotion, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(horizontal = TimelineMetrics.headerSide).height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "ROUTINE TIMELINE",
            style =
                JewelTheme.defaultTextStyle.copy(
                    color = RoutinePalette.header,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.4.sp,
                ),
        )
        Spacer(Modifier.weight(1f))
        NowButton(motion)
    }
}

@Composable
private fun NowButton(motion: TimelineMotion, modifier: Modifier = Modifier) {
    val interactions = remember { MutableInteractionSource() }
    Row(
        modifier
            .graphicsLayer {
                // Dim while the dial already shows now; it wakes up as soon as it doesn't.
                alpha = if (motion.awayFromNow() < 1.0) NOW_IDLE_ALPHA else 1f
            }
            .clip(RoundedCornerShape(16.dp))
            .background(RoutinePalette.nowFill)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactions,
                indication = null,
                role = Role.Button,
                onClickLabel = "Back to now",
                onClick = motion::backToNow,
            )
            .semantics { contentDescription = "Back to now" }
            .testTag(RoutineTimelineTags.NOW)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(16.dp)) {
            RoutineIcons.back.draw(this, center, size.width, RoutinePalette.nowInk)
        }
        Spacer(Modifier.width(6.dp))
        Text(
            "Now",
            style =
                JewelTheme.defaultTextStyle.copy(
                    color = RoutinePalette.nowInk,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
        )
    }
}

/**
 * Turns the dial like a wheel: whatever is under the pointer stays under it, and a flick coasts.
 * Near the centre a small move swings the angle a long way, so the centre is a dead zone.
 */
private suspend fun PointerInputScope.turnDial(motion: TimelineMotion) {
    val centre = Offset(TimelineMetrics.dialX.toPx(), TimelineMetrics.dialY.toPx())
    val deadZone = DEAD_ZONE.toPx()
    val tracker = VelocityTracker1D(isDataDifferential = false)
    var angle = 0.0
    var turned = 0.0
    fun angleOf(position: Offset): Double =
        Math.toDegrees(
            atan2((position.y - centre.y).toDouble(), (position.x - centre.x).toDouble())
        )

    detectDragGestures(
        onDragStart = { position ->
            motion.grab()
            angle = angleOf(position)
            turned = 0.0
            tracker.resetTracking()
        },
        onDragEnd = { motion.fling(tracker.calculateVelocity()) },
        onDragCancel = { motion.fling(0f) },
    ) { change, _ ->
        val position = change.position
        val next = angleOf(position)
        if (hypot(position.x - centre.x, position.y - centre.y) > deadZone) {
            val minutes = minutesForTurn(turnDegrees(angle, next))
            turned += minutes
            motion.turnBy(minutes)
        }
        angle = next
        tracker.addDataPoint(change.uptimeMillis, turned.toFloat())
        change.consume()
    }
}

private suspend fun PointerInputScope.scrollDial(motion: TimelineMotion) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Scroll) {
                val delta = event.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat()
                motion.wheel(delta)
                event.changes.forEach { it.consume() }
            }
        }
    }
}

private val DEAD_ZONE = 24.dp
private const val NOW_IDLE_ALPHA = 0.55f
