package dev.sebastiano.layoutmorph

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.jetbrains.jewel.ui.component.Text

/**
 * The six views as a segmented control. The white pill slides to the picked view; it is drawn
 * behind the labels from two springs read while drawing.
 */
@Composable
internal fun Switcher(
    selected: Arrangement,
    onSelect: (Arrangement) -> Unit,
    modifier: Modifier = Modifier,
) {
    val padding = with(LocalDensity.current) { 3.dp.toPx() }
    val spans = remember { mutableStateListOf(*Array(Arrangement.entries.size) { Offset.Zero }) }
    val left = remember { Animatable(0f) }
    val width = remember { Animatable(0f) }
    LaunchedEffect(selected, spans[selected.ordinal]) {
        val (x, w) = spans[selected.ordinal]
        if (width.value == 0f) {
            left.snapTo(x)
            width.snapTo(w)
        } else {
            val motion = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
            launch { left.animateTo(x, motion) }
            launch { width.animateTo(w, motion) }
        }
    }
    Row(
        modifier
            .drawBehind {
                val r = CornerRadius(8.dp.toPx())
                drawRoundRect(MorphPalette.switcher, cornerRadius = r)
                if (width.value > 0f) {
                    val inset = 3.dp.toPx()
                    val pill = CornerRadius(6.dp.toPx())
                    drawRoundRect(
                        MorphPalette.shadow.copy(alpha = 0.07f),
                        Offset(left.value, inset + 1.dp.toPx()),
                        Size(width.value, size.height - 2 * inset),
                        pill,
                    )
                    drawRoundRect(
                        MorphPalette.switcherSelected,
                        Offset(left.value, inset),
                        Size(width.value, size.height - 2 * inset),
                        pill,
                    )
                }
            }
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (arrangement in Arrangement.entries) {
            val picked = arrangement == selected
            val ink = if (picked) MorphPalette.ink else MorphPalette.muted
            Row(
                Modifier.testTag(LayoutMorphTags.arrangement(arrangement))
                    .onPlaced {
                        // x and width of the segment, in the switcher's pixels (outside its
                        // padding).
                        spans[arrangement.ordinal] =
                            Offset(it.positionInParent().x + padding, it.size.width.toFloat())
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        onSelect(arrangement)
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(Modifier.size(11.dp)) { drawGlyph(arrangement, ink) }
                Spacer(Modifier.width(5.dp))
                Text(arrangement.label, color = ink, fontSize = 11.5.sp)
            }
        }
    }
}

/** A tiny line drawing of each view. */
private fun DrawScope.drawGlyph(arrangement: Arrangement, ink: Color) {
    val s = size.width
    val line = Stroke(width = 1.1.dp.toPx(), cap = StrokeCap.Round)
    val r = CornerRadius(1.5.dp.toPx())
    fun box(x: Float, y: Float, w: Float, h: Float) =
        drawRoundRect(ink, Offset(x * s, y * s), Size(w * s, h * s), r, style = line)
    when (arrangement) {
        Arrangement.Grid -> {
            box(0.05f, 0.05f, 0.38f, 0.38f)
            box(0.57f, 0.05f, 0.38f, 0.38f)
            box(0.05f, 0.57f, 0.38f, 0.38f)
            box(0.57f, 0.57f, 0.38f, 0.38f)
        }
        Arrangement.Masonry -> {
            box(0.05f, 0.05f, 0.38f, 0.55f)
            box(0.05f, 0.74f, 0.38f, 0.21f)
            box(0.57f, 0.05f, 0.38f, 0.3f)
            box(0.57f, 0.49f, 0.38f, 0.46f)
        }
        Arrangement.List ->
            for (k in 0..2) {
                val y = (0.15f + k * 0.35f) * s
                drawLine(
                    ink,
                    Offset(0.05f * s, y),
                    Offset(0.15f * s, y),
                    line.width,
                    StrokeCap.Round,
                )
                drawLine(
                    ink,
                    Offset(0.32f * s, y),
                    Offset(0.95f * s, y),
                    line.width,
                    StrokeCap.Round,
                )
            }
        Arrangement.Stack ->
            for (k in -1..1) {
                rotate(k * 18f, Offset(0.5f * s, 1.1f * s)) { box(0.3f, 0.1f, 0.4f, 0.75f) }
            }
        Arrangement.Carousel -> {
            box(0.27f, 0.08f, 0.46f, 0.84f)
            drawLine(
                ink,
                Offset(0.05f * s, 0.25f * s),
                Offset(0.05f * s, 0.75f * s),
                line.width,
                StrokeCap.Round,
            )
            drawLine(
                ink,
                Offset(0.95f * s, 0.25f * s),
                Offset(0.95f * s, 0.75f * s),
                line.width,
                StrokeCap.Round,
            )
        }
        Arrangement.Helix -> {
            // A four-pointed sparkle.
            val c = Offset(0.5f * s, 0.5f * s)
            for (k in 0..3) {
                rotate(k * 90f, c) {
                    drawLine(ink, c, Offset(0.5f * s, 0.02f * s), line.width, StrokeCap.Round)
                }
            }
            drawCircle(ink, 0.14f * s, c)
        }
    }
}
