package dev.sebastiano.routinetimeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sebastiano.bioparco.tracing.Tracing
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * Three windows at a time, centred on the one the dial is at. There is one row per window, composed
 * once: each slides to wherever its window sits relative to [TimelineMotion.listScroll] and wraps
 * round out of sight, in offset and layer lambdas. Composition only runs when a label changes.
 */
@Composable
internal fun EventList(motion: TimelineMotion, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth().height(TimelineMetrics.highlightHeight).drawBehind {
                Tracing.section("event highlight") { drawHighlight(motion) }
            }
        )
        for (index in motion.routine.windows.indices) {
            key(index) { EventRow(motion, index) }
        }
    }
}

/**
 * Where window [index]'s row sits, in rows from the centre: the copy of it nearest the scroll
 * position, so a row leaving at one end comes back in at the other. The routine needs at least four
 * windows for the wrap to happen out of sight.
 */
private fun TimelineMotion.rowDistance(index: Int): Float {
    val size = routine.size
    val d = (index - listScroll.value).mod(size.toFloat())
    return if (d > size / 2f) d - size else d
}

private fun DrawScope.drawHighlight(motion: TimelineMotion) {
    // Between two rows, the tint blends from one window's colour to the next.
    val scroll = motion.listScroll.value
    val below = floor(scroll).toInt()
    val routine = motion.routine
    val tint =
        lerp(
            RoutinePalette.kind(routine.windowAt(below).kind),
            RoutinePalette.kind(routine.windowAt(below + 1).kind),
            scroll - below,
        )
    val corner = CornerRadius(TimelineMetrics.highlightCorner.toPx())
    drawRoundRect(lerp(RoutinePalette.raised, tint, HIGHLIGHT_TINT), cornerRadius = corner)
    drawRoundRect(
        lerp(RoutinePalette.cardEdge, tint, EDGE_TINT),
        cornerRadius = corner,
        style = Stroke(TimelineMetrics.hairline.toPx()),
    )
}

@Composable
private fun EventRow(motion: TimelineMotion, index: Int) {
    val routine = motion.routine
    val window = routine.windows[index]
    val status by
        remember(motion, index) {
            derivedStateOf { routine.statusOf(window, motion.time.value.toDouble()) }
        }
    val colour = RoutinePalette.kind(window.kind)
    val base = JewelTheme.defaultTextStyle
    Column(
        Modifier.fillMaxWidth()
            .rowOffset(motion, index)
            .graphicsLayer { alpha = rowAlpha(motion.rowDistance(index)) }
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(14.dp)) {
                RoutineIcons.of(window.kind).draw(this, center, size.width, colour)
            }
            Spacer(Modifier.width(7.dp))
            Text(
                statusLabel(status),
                style =
                    base.copy(
                        color = colour,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.8.sp,
                    ),
            )
            if (status == WindowStatus.InWindow) {
                Spacer(Modifier.width(7.dp))
                Canvas(Modifier.size(5.dp)) { drawCircle(colour) }
            }
        }
        Text(
            window.title,
            style =
                base.copy(
                    color = RoutinePalette.title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium,
                ),
            // Away from the centre the title greys: white at a lower alpha, in a layer.
            modifier =
                Modifier.graphicsLayer {
                    val away = abs(motion.rowDistance(index)).coerceAtMost(1f)
                    alpha = 1f - TITLE_DIM * away
                },
        )
        Text(window.subtitle, style = base.copy(color = RoutinePalette.subtitle, fontSize = 13.sp))
    }
}

private fun Modifier.rowOffset(motion: TimelineMotion, index: Int): Modifier = offset {
    val pitch = TimelineMetrics.rowPitch.toPx()
    IntOffset(0, (motion.rowDistance(index) * pitch).roundToInt())
}

private fun statusLabel(status: WindowStatus): String =
    when (status) {
        WindowStatus.Earlier -> "EARLIER"
        WindowStatus.InWindow -> "IN WINDOW"
        WindowStatus.Later -> "LATER"
    }

/** Rows one step away stay readable; past that they fade out by two steps. */
private fun rowAlpha(distance: Float): Float = (1f - (abs(distance) - 1f) * 1.25f).coerceIn(0f, 1f)

private const val HIGHLIGHT_TINT = 0.10f
private const val EDGE_TINT = 0.24f
private const val TITLE_DIM = 0.42f
