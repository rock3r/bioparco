package dev.sebastiano.componentanatomy

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toSize
import kotlin.math.exp
import kotlin.math.max
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.ButtonStyle
import org.jetbrains.jewel.ui.theme.defaultButtonStyle
import org.jetbrains.jewel.ui.theme.outlinedButtonStyle

/** The stage: the exploded button, its ghosts, its labels and the leader lines between them. */
@Composable
fun ComponentAnatomy(stage: AnatomyStage, modifier: Modifier = Modifier) {
    val style =
        if (stage.outlined) JewelTheme.outlinedButtonStyle else JewelTheme.defaultButtonStyle
    val look = style.colors.lookFor(stage.state.toButtonState())
    val paint = animatedPaint(look.toPaint())
    val density = LocalDensity.current
    val geometry = remember(stage, density) { StackGeometry(stage, density) }
    val focusColor = JewelTheme.globalColors.outlines.focused
    val lineColor = AnatomyColors.leader

    val tracker = remember { ChangeTracker() }
    val outlined = stage.outlined
    SideEffect { tracker.update(look, outlined, stage.beats) }

    Box(
        modifier =
            modifier
                .background(AnatomyColors.stage)
                .drawBehind { drawGrid() }
                .pointerInput(stage) {
                    detectDragGestures(
                        onDragEnd = stage::endOrbit,
                        onDragCancel = stage::endOrbit,
                    ) { change, drag ->
                        change.consume()
                        stage.orbit(drag.x * ORBIT_PER_PX, drag.y * ORBIT_PER_PX)
                    }
                }
                .onSizeChanged { geometry.stageSize = it.toSize() }
                .testTag(AnatomyTags.STAGE)
                .drawWithContent {
                    drawContent()
                    drawLeaders(geometry, tracker, stage, lineColor)
                }
    ) {
        Box(
            Modifier.offset { geometry.stackOrigin.round() }
                .onSizeChanged { geometry.layerSize = it.toSize() }
        ) {
            ButtonPart.entries.forEachIndexed { index, part ->
                ButtonLayer(
                    part = part,
                    paint = paint,
                    style = style,
                    modifier = Modifier.layerIn3d(geometry, index, part, paint, tracker, stage),
                ) {
                    Text(BUTTON_TEXT)
                }
            }
        }
        LabelColumn(geometry, stage) {
            ButtonPart.entries.forEachIndexed { index, part ->
                LayerLabel(index, part, look, style, focusColor, tracker, stage)
            }
        }
    }
}

private fun Modifier.layerIn3d(
    geometry: StackGeometry,
    index: Int,
    part: ButtonPart,
    paint: ButtonPaint,
    tracker: ChangeTracker,
    stage: AnatomyStage,
): Modifier = drawWithContent {
    val layer = this
    val matrix = geometry.matrix(index, size)
    withTransform({ transform(matrix) }) {
        val open = stage.explode.coerceIn(0f, 1f)
        val glow = tracker.glow(part, stage.beats)
        val ghost = lerp(AnatomyColors.ghost, AnatomyColors.accent, glow)
        val inset = GHOST_OUTSET.toPx() / SCALE
        drawRoundRect(
            color = ghost,
            alpha = open * (GHOST_ALPHA + (1 - GHOST_ALPHA) * glow),
            topLeft = Offset(-inset, -inset),
            size = Size(size.width + 2 * inset, size.height + 2 * inset),
            cornerRadius = CornerRadius(GHOST_RADIUS.toPx() / SCALE),
            style =
                Stroke(
                    width = GHOST_STROKE.toPx() / SCALE,
                    pathEffect =
                        PathEffect.dashPathEffect(
                            floatArrayOf(4f / SCALE * density, 3f / SCALE * density)
                        ),
                ),
        )
        val alpha = if (part == ButtonPart.FocusOutline) paint.focusOutlineAlpha else 1f
        if (alpha < 1f) {
            // Fade the halo as one piece. The bounds are generous because the halo draws outside.
            val bounds = Rect(Offset.Zero, size).inflate(HALO_ROOM.toPx())
            drawIntoCanvas { canvas ->
                canvas.saveLayer(bounds, Paint().apply { this.alpha = alpha })
                layer.drawContent()
                canvas.restore()
            }
        } else {
            layer.drawContent()
        }
    }
}

/** Places the labels in a column right of the stack, each level with its layer's anchor. */
@Composable
private fun LabelColumn(
    geometry: StackGeometry,
    stage: AnatomyStage,
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
        modifier =
            Modifier.fillMaxSize().graphicsLayer {
                alpha = ((stage.explode - LABELS_FADE_START) / LABELS_FADE_LENGTH).coerceIn(0f, 1f)
            },
    ) { measurables, constraints ->
        val placeables = measurables.map {
            it.measure(constraints.copy(minWidth = 0, minHeight = 0))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            geometry.labelHeights = FloatArray(placeables.size) { placeables[it].height.toFloat() }
            val slots = geometry.labelSlots()
            placeables.forEachIndexed { index, placeable ->
                val slot = slots.getOrNull(index) ?: return@forEachIndexed
                placeable.place(slot.x.toInt(), (slot.y - placeable.height / 2f).toInt())
            }
        }
    }
}

@Composable
private fun LayerLabel(
    index: Int,
    part: ButtonPart,
    look: ButtonLook,
    style: ButtonStyle,
    focusColor: Color,
    tracker: ChangeTracker,
    stage: AnatomyStage,
) {
    val mono = JewelTheme.editorTextStyle
    val info = JewelTheme.globalColors.text.info
    val (property, swatch) =
        when (part) {
            ButtonPart.Background -> "colors.${look.background.property}" to look.background.value
            ButtonPart.Label -> "colors.${look.content.property}" to SolidColor(look.content.value)
            ButtonPart.Border -> "colors.${look.border.property}" to look.border.value
            ButtonPart.FocusOutline ->
                if (look.focused) "globalColors.outlines.focused" to SolidColor(focusColor)
                else "not focused: nothing to draw" to null
        }
    Column(
        modifier =
            Modifier.width(LABEL_WIDTH)
                .drawBehind {
                    val glow = tracker.glow(part, stage.beats)
                    if (glow > 0.01f) {
                        drawRect(
                            AnatomyColors.accent,
                            alpha = glow,
                            size = Size(ACCENT_BAR.toPx(), size.height),
                        )
                    }
                }
                .padding(start = LABEL_INDENT),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("0${index + 1}", style = mono, color = info)
            Text(part.title, fontWeight = FontWeight.SemiBold)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (swatch != null) {
                Box(
                    Modifier.size(10.dp)
                        .background(swatch, RoundedCornerShape(2.dp))
                        .border(1.dp, AnatomyColors.swatchEdge, RoundedCornerShape(2.dp))
                )
            }
            Text(property, style = mono, fontSize = mono.fontSize * SMALL)
            if (swatch != null)
                Text(
                    swatch.describe(),
                    style = mono,
                    color = info,
                    fontSize = mono.fontSize * SMALL,
                )
        }
        Text(part.metrics(style), color = info)
    }
}

private fun ButtonPart.metrics(style: ButtonStyle): String {
    val metrics = style.metrics
    return when (this) {
        ButtonPart.Background -> {
            val corner = metrics.cornerSize.toPx(Size(CORNER_PROBE, CORNER_PROBE), Density(1f))
            "cornerSize ${corner.pretty()}dp · drawn before the content"
        }
        ButtonPart.Label -> {
            val padding = metrics.padding
            "padding ${padding.calculateLeftPadding(LayoutDirection.Ltr).pretty()} × " +
                "${padding.calculateTopPadding().pretty()} · min ${metrics.minSize.width.pretty()} × " +
                metrics.minSize.height.pretty()
        }
        ButtonPart.Border ->
            "borderWidth ${metrics.borderWidth.pretty()} · inside · after the content"
        ButtonPart.FocusOutline -> {
            val expand =
                if (metrics.focusOutlineExpand.isSpecified) metrics.focusOutlineExpand.pretty()
                else "0dp"
            "${style.focusOutlineAlignment.name.lowercase()} · expand $expand · on top of everything"
        }
    }
}

private fun Float.pretty() = if (this % 1f == 0f) toInt().toString() else "%.1f".format(this)

private fun Dp.pretty() = "${value.pretty()}dp"

private fun Brush.describe(): String =
    when (this) {
        is SolidColor ->
            if (value.alpha == 0f) "transparent"
            else
                "#%06X".format(value.toArgb() and RGB_MASK) +
                    if (value.alpha < 1f) " · ${(value.alpha * 100).toInt()}%" else ""
        else -> "gradient"
    }

/** Remembers when each layer last changed, in beats, so the stage can make it glow. */
private class ChangeTracker {
    private var previous: ButtonLook? = null
    private var previousOutlined: Boolean? = null
    private val changedAt = DoubleArray(ButtonPart.entries.size) { Double.NEGATIVE_INFINITY }

    fun update(look: ButtonLook, outlined: Boolean, beats: Double) {
        val before = previous
        if (before != null && previousOutlined == outlined) {
            if (before.background != look.background)
                changedAt[ButtonPart.Background.ordinal] = beats
            if (before.content != look.content) changedAt[ButtonPart.Label.ordinal] = beats
            if (before.border != look.border) changedAt[ButtonPart.Border.ordinal] = beats
            if (before.focused != look.focused) changedAt[ButtonPart.FocusOutline.ordinal] = beats
        } else if (before != null) {
            changedAt.fill(beats)
        }
        previous = look
        previousOutlined = outlined
    }

    fun glow(part: ButtonPart, beats: Double): Float =
        exp(-(beats - changedAt[part.ordinal]).coerceAtLeast(0.0) * GLOW_DECAY).toFloat()
}

/** Shared by the layers, the labels and the leader lines, so they always agree. */
private class StackGeometry(private val stage: AnatomyStage, private val density: Density) {
    var stageSize by mutableStateOf(Size.Zero)
    var layerSize by mutableStateOf(Size.Zero)

    /**
     * The stack's top-left corner. Centred when collapsed; it slides left as the stack opens, to
     * make room for the labels.
     */
    val stackOrigin: Offset
        get() {
            val shift = with(density) { STACK_SHIFT.toPx() } * stage.explode.coerceIn(0f, 1f)
            return Offset(
                (stageSize.width - layerSize.width) / 2 + shift,
                (stageSize.height - layerSize.height) / 2,
            )
        }

    var labelHeights = FloatArray(0)
    private val spacing = with(density) { LAYER_SPACING.toPx() }

    private fun view() =
        Axonometry(
            yawDegrees = stage.yawDegrees,
            pitchDegrees = stage.pitchDegrees,
            scale = SCALE,
            cameraDistance = with(density) { CAMERA.toPx() },
        )

    private fun depth(index: Int): Float {
        val middle = (ButtonPart.entries.size - 1) / 2f
        val open = stage.explode
        val beat = 1f + PULSE_GAIN * stage.pulse * open.coerceIn(0f, 1f)
        return (index - middle) * spacing * open * beat
    }

    fun matrix(index: Int, size: Size) = view().matrix(depth(index), pivot = size.center())

    /** The point on a layer's right edge that its label points at, in stage coordinates. */
    fun anchor(index: Int): Offset {
        val size = layerSize
        val edge = Offset(size.width, size.height / 2)
        return stackOrigin + view().project(edge, depth(index), pivot = size.center())
    }

    /** Where each label's left edge and vertical centre go, in stage coordinates. */
    fun labelSlots(): List<Offset> {
        val anchors = ButtonPart.entries.indices.map(::anchor)
        val column =
            with(density) {
                max(
                    stackOrigin.x +
                        layerSize.width / 2 +
                        layerSize.width * SCALE / 2 +
                        LABEL_GAP.toPx(),
                    anchors.maxOf { it.x } + LABEL_GAP.toPx() / 2,
                )
            }
        val gap = with(density) { LABEL_SPACING.toPx() }
        // Keep the anchor order, then push labels apart where they would overlap.
        val order = anchors.indices.sortedBy { anchors[it].y }
        val centres = FloatArray(anchors.size)
        var floor = Float.NEGATIVE_INFINITY
        for (index in order) {
            val half = (labelHeights.getOrNull(index) ?: 0f) / 2
            val centre = max(anchors[index].y, floor + half)
            centres[index] = centre
            floor = centre + half + gap
        }
        return anchors.indices.map { Offset(column, centres[it]) }
    }
}

private fun DrawScope.drawLeaders(
    geometry: StackGeometry,
    tracker: ChangeTracker,
    stage: AnatomyStage,
    color: Color,
) {
    val open = ((stage.explode - LABELS_FADE_START) / LABELS_FADE_LENGTH).coerceIn(0f, 1f)
    if (open <= 0f) return
    val slots = geometry.labelSlots()
    slots.forEachIndexed { index, slot ->
        val anchor = geometry.anchor(index)
        val glow = tracker.glow(ButtonPart.entries[index], stage.beats)
        val lineColor = lerp(color, AnatomyColors.accent, glow)
        val end = Offset(slot.x - LEADER_GAP.toPx(), slot.y)
        val elbow = Offset(end.x - LEADER_ELBOW.toPx(), end.y)
        val width = LEADER_STROKE.toPx()
        drawLine(lineColor, anchor, elbow, width, alpha = open)
        drawLine(lineColor, elbow, end, width, alpha = open)
        drawCircle(lineColor, radius = DOT.toPx(), center = anchor, alpha = open)
    }
}

private fun DrawScope.drawGrid() {
    val step = GRID.toPx()
    var x = size.width / 2 % step
    while (x < size.width) {
        drawLine(AnatomyColors.grid, Offset(x, 0f), Offset(x, size.height), 1f)
        x += step
    }
    var y = size.height / 2 % step
    while (y < size.height) {
        drawLine(AnatomyColors.grid, Offset(0f, y), Offset(size.width, y), 1f)
        y += step
    }
}

@Composable
private fun animatedPaint(target: ButtonPaint): ButtonPaint {
    val spec = tween<Color>(COLOR_MILLIS)
    val background by
        animateColorAsState((target.background as? SolidColor)?.value ?: Color.Unspecified, spec)
    val border by
        animateColorAsState((target.border as? SolidColor)?.value ?: Color.Unspecified, spec)
    val content by animateColorAsState(target.content, spec)
    val focus by animateFloatAsState(target.focusOutlineAlpha, tween(COLOR_MILLIS))
    return ButtonPaint(
        // Gradients snap; only solid colours animate.
        background =
            if (target.background is SolidColor) SolidColor(background) else target.background,
        border = if (target.border is SolidColor) SolidColor(border) else target.border,
        content = content,
        focusOutlineAlpha = focus,
    )
}

private fun Size.center() = Offset(width / 2, height / 2)

private object AnatomyColors {
    val stage = Color(0xFF0E0F12)
    val grid = Color(0x0CFFFFFF)
    val ghost = Color(0xFF8A8F99)
    val leader = Color(0x668A8F99)
    val accent = Color(0xFFF5B94A)
    val swatchEdge = Color(0x40FFFFFF)
}

private const val BUTTON_TEXT = "Anatomy"
private const val SCALE = 3.6f
private const val PULSE_GAIN = 0.07f
private const val GHOST_ALPHA = 0.28f
private const val GLOW_DECAY = 1.4
private const val LABELS_FADE_START = 0.35f
private const val LABELS_FADE_LENGTH = 0.45f
private const val ORBIT_PER_PX = 0.25f
private const val SMALL = 0.9f
private const val CORNER_PROBE = 100f
private const val RGB_MASK = 0xFFFFFF
private const val COLOR_MILLIS = 180
private val STACK_SHIFT = (-170).dp
private val LAYER_SPACING = 72.dp
private val CAMERA = 2400.dp
private val GHOST_OUTSET = 10.dp
private val GHOST_RADIUS = 14.dp
private val GHOST_STROKE = 1.dp
private val HALO_ROOM = 12.dp
private val LABEL_WIDTH = 300.dp
private val LABEL_GAP = 56.dp
private val LABEL_SPACING = 10.dp
private val LABEL_INDENT = 8.dp
private val ACCENT_BAR = 2.dp
private val LEADER_GAP = 6.dp
private val LEADER_ELBOW = 18.dp
private val LEADER_STROKE = 1.dp
private val DOT = 2.5.dp
private val GRID = 24.dp
