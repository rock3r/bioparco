package dev.sebastiano.expandingsearch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sebastiano.bioparco.tracing.Tracing
import kotlin.math.roundToInt
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * The text field. It starts after the magnifier and is as wide as the pill leaves room for, so the
 * placeholder is uncovered as the pill grows ("Search c…") rather than squeezed.
 */
@Composable
internal fun SearchField(
    query: TextFieldState,
    motion: SearchMotion,
    geometry: SearchGeometry,
    modifier: Modifier = Modifier,
) {
    val style = searchTextStyle()
    BasicTextField(
        state = query,
        modifier =
            modifier
                .layout { measurable, _ ->
                    // Read here, the pill's width relayouts this field alone, without recomposing.
                    val expansion = motion.expansion.value
                    val width = geometry.textWidth(expansion).roundToInt()
                    val height = geometry.pillHeight.roundToInt()
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    layout(width, height) {
                        val x = geometry.pillLeft(expansion) + geometry.textStart
                        placeable.place(x.roundToInt(), 0)
                    }
                }
                .graphicsLayer { alpha = textAlpha(motion.expansion.value) }
                .testTag(ExpandingSearchTags.FIELD),
        textStyle = style.copy(color = ExpandingSearchPalette.ink),
        cursorBrush = SolidColor(ExpandingSearchPalette.caret),
        lineLimits = TextFieldLineLimits.SingleLine,
        decorator = { field ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                if (query.text.isEmpty()) {
                    Text(
                        "Search components",
                        style = style,
                        color = ExpandingSearchPalette.placeholder,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                }
                field()
            }
        },
    )
}

/** The cross at the pill's right end. It clears the text, or closes an empty field. */
@Composable
internal fun ClearButton(
    motion: SearchMotion,
    geometry: SearchGeometry,
    clears: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val size = with(LocalDensity.current) { geometry.crossSize.toDp() }
    val label = if (clears) "Clear search" else "Close search"
    val actions =
        if (motion.isOpen) {
            // The cross never takes focus: the caret stays in the field, ready for the next search,
            // as in the video. Escape does the same job from the keyboard.
            Modifier.focusProperties { canFocus = false }
                .pointerHoverIcon(PointerIcon.Hand)
                .hoverable(interactions)
                .clickable(
                    interactionSource = interactions,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = label,
                    onClick = onClick,
                )
                .semantics { contentDescription = label }
        } else {
            Modifier
        }
    Box(
        modifier
            .offset {
                val expansion = motion.expansion.value
                val pill = geometry.pill(expansion)
                IntOffset(
                    (pill.right - geometry.crossInset - geometry.crossSize / 2f).roundToInt(),
                    ((geometry.pillHeight - geometry.crossSize) / 2f).roundToInt(),
                )
            }
            .requiredSize(size)
            .graphicsLayer { alpha = crossAlpha(motion.expansion.value) }
            .then(actions)
            .testTag(ExpandingSearchTags.CLEAR)
            .drawBehind {
                if (hovered) drawCircle(ExpandingSearchPalette.crossHover)
                val arm = this.size.minDimension * 0.17f
                val stroke = 1.5.dp.toPx()
                val c = center
                drawLine(
                    ExpandingSearchPalette.cross,
                    c - Offset(arm, arm),
                    c + Offset(arm, arm),
                    stroke,
                    StrokeCap.Round,
                )
                drawLine(
                    ExpandingSearchPalette.cross,
                    c + Offset(-arm, arm),
                    c + Offset(arm, -arm),
                    stroke,
                    StrokeCap.Round,
                )
            }
    )
}

/** The round button. It covers the circle while the field is closed, and opens it. */
@Composable
internal fun OpenButton(
    motion: SearchMotion,
    geometry: SearchGeometry,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val size = with(density) { geometry.pillHeight.toDp() }
    val x = with(density) { geometry.pillLeft(0f).toDp() }
    Box(
        modifier
            .offset(x = x)
            .requiredSize(size)
            .clip(CircleShape)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                indication = null,
                interactionSource = null,
                role = Role.Button,
                onClickLabel = "Search",
                onClick = motion::open,
            )
            .semantics { contentDescription = "Search" }
            .testTag(ExpandingSearchTags.OPEN)
    )
}

/**
 * The rows, clipped to the panel's outline as it grows and shrinks. Hover and clicks are worked out
 * here from the pointer's height rather than per row, so rows sliding under a still pointer still
 * pick up the hover.
 */
@Composable
internal fun ResultRows(
    motion: SearchMotion,
    geometry: SearchGeometry,
    chrome: SearchChrome,
    current: Map<String, SearchResult>,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val pick by rememberUpdatedState(onPick)
    val pointer =
        if (motion.isOpen) {
            Modifier.pointerHoverIcon(PointerIcon.Hand).pointerInput(motion, geometry) {
                awaitPointerEventScope {
                    var pressed: Int? = null
                    while (true) {
                        val event = awaitPointerEvent()
                        val y = event.changes.first().position.y
                        val slot = geometry.rows.slotAt(y, motion.selectable)
                        when (event.type) {
                            PointerEventType.Enter,
                            PointerEventType.Move -> motion.hover(slot)
                            PointerEventType.Exit -> motion.hover(null)
                            PointerEventType.Press -> pressed = slot
                            PointerEventType.Release -> {
                                if (slot != null && slot == pressed) pick(slot)
                                pressed = null
                            }
                        }
                    }
                }
            }
        } else {
            Modifier
        }
    Box(
        modifier
            .layout { measurable, _ ->
                val width = geometry.fullWidth.roundToInt()
                val height = geometry.panelMaxHeight.roundToInt()
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(width, height) { placeable.place(0, geometry.panelTop.roundToInt()) }
            }
            .then(pointer)
            .testTag(ExpandingSearchTags.RESULTS)
            .drawWithContent {
                Tracing.section("result rows") {
                    chrome.clipToPanel(this, motion) { this@drawWithContent.drawContent() }
                }
            }
    ) {
        for (row in motion.rows) {
            // A row still on screen takes its text from this keystroke's results, not from the
            // effect that updates the rows a frame later, so a kept row restyles at once.
            ResultRow(current[row.key] ?: row.result, row, motion, geometry)
        }
    }
}

@Composable
private fun ResultRow(
    result: SearchResult,
    row: RowMotion,
    motion: SearchMotion,
    geometry: SearchGeometry,
) {
    val style = searchTextStyle()
    val label = remember(result) { rowLabel(result) }
    Box(
        Modifier.offset {
                // The layer starts a blur's reach left of the text, so the blur has room to spread.
                val x = geometry.pillLeft(motion.expansion.value) + geometry.rowTextStart
                IntOffset(
                    (x - geometry.blurMax).roundToInt(),
                    geometry.rows.top(row.y.value).roundToInt(),
                )
            }
            // As small as the text and the blur's reach around it: blurring is the most costly
            // drawing here, and a layer as wide as the panel blurred twice the pixels.
            .graphicsLayer {
                val presence = row.presence.value
                alpha = presence
                renderEffect = steppedBlur((1f - presence) * geometry.blurMax)
            }
            .testTag(ExpandingSearchTags.row(result.name))
            .layout { measurable, constraints ->
                val height = geometry.rows.rowHeight.roundToInt()
                val reach = geometry.blurMax.roundToInt()
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(placeable.width + reach * 2, height) {
                    placeable.place(reach, (height - placeable.height) / 2)
                }
            }
    ) {
        Text(label, style = style, maxLines = 1, softWrap = false)
    }
}

/**
 * A name in the ink colour, or, once a query matches part of it, the match in bold ink and the rest
 * muted, as in the video. The "nothing found" line is muted throughout.
 */
private fun rowLabel(result: SearchResult): AnnotatedString = buildAnnotatedString {
    val name = result.name
    when {
        result === NoMatches ->
            withStyle(SpanStyle(color = ExpandingSearchPalette.mutedInk)) { append(name) }
        !result.hasMatch ->
            withStyle(SpanStyle(color = ExpandingSearchPalette.ink)) { append(name) }
        else -> {
            val muted = SpanStyle(color = ExpandingSearchPalette.mutedInk)
            val match = result.match
            withStyle(muted) { append(name.substring(0, match.first)) }
            withStyle(
                SpanStyle(color = ExpandingSearchPalette.ink, fontWeight = FontWeight.SemiBold)
            ) {
                append(name.substring(match.first, match.last + 1))
            }
            withStyle(muted) { append(name.substring(match.last + 1)) }
        }
    }
}

@Composable
private fun searchTextStyle(): TextStyle = JewelTheme.defaultTextStyle.copy(fontSize = 15.sp)

/** The text shows once the pill is a quarter open, and is fully there by three quarters. */
private fun textAlpha(expansion: Float): Float = ((expansion - 0.25f) / 0.5f).coerceIn(0f, 1f)

/** The cross comes in a little after the text, as in the video. */
private fun crossAlpha(expansion: Float): Float = ((expansion - 0.45f) / 0.4f).coerceIn(0f, 1f)

/**
 * A blur whose radius only takes whole multiples of [BLUR_STEP_PX], or none below one step.
 *
 * Skia's GPU backend compiles a blur program per `ceil(3 * sigma)` the first time it meets it, and
 * a radius that shrank smoothly walked through many and stalled frames. The dot-matrix recorder
 * measured this; it uses the same steps.
 */
private fun steppedBlur(radius: Float): BlurEffect? {
    val steps = (radius / BLUR_STEP_PX).roundToInt()
    if (steps <= 0) return null
    val stepped = steps * BLUR_STEP_PX
    return BlurEffect(stepped, stepped, TileMode.Decal)
}

private const val BLUR_STEP_PX = 4f
