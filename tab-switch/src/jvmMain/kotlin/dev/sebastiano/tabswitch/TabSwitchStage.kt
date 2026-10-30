package dev.sebastiano.tabswitch

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sebastiano.bioparco.tracing.Tracing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/** The bar in the middle of a dark page, with a hint underneath. The arrow keys move the pill. */
@Composable
internal fun TabSwitchStage(modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val measurer = rememberTextMeasurer()
    val base = JewelTheme.defaultTextStyle
    val painter =
        remember(measurer, base, density) {
            TabBarPainter(
                Tabs,
                measurer,
                labelStyle =
                    base.copy(
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.2).sp,
                    ),
                badgeStyle = base.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                density = density,
            )
        }
    val state =
        remember(painter) {
            TabSwitchState(TabMotion(painter.layout, minPillWidth = TabBarPainter.PILL_HEIGHT))
        }
    LaunchedEffect(state) { state.run() }
    LaunchedEffect(state) { withContext(Dispatchers.Default) { state.warmUp() } }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        modifier
            .background(TabSwitchPalette.page)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> state.step(-1)
                    Key.DirectionRight -> state.step(1)
                    Key.MoveHome -> state.select(0)
                    Key.MoveEnd -> state.select(Tabs.lastIndex)
                    else -> {
                        val digit = NumberKeys.indexOf(event.key)
                        if (digit < 0) return@onPreviewKeyEvent false
                        state.select(digit)
                    }
                }
                true
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        TabBar(state, painter)
        Spacer(Modifier.weight(1f))
        Text(
            "Click a tab, or press ← and →",
            color = TabSwitchPalette.hint,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 18.dp),
        )
    }
}

/**
 * The bar itself. One node draws all of it, reading [TabSwitchState.tick] only while drawing, so a
 * moving frame is a redraw and nothing else. Pointer input is hit-tested against the bar as drawn.
 */
@Composable
private fun TabBar(state: TabSwitchState, painter: TabBarPainter, modifier: Modifier = Modifier) {
    val motion = state.motion
    Box(
        modifier
            .size(
                (painter.layout.width + 2 * TabBarPainter.BAR_PAD).dp,
                TabBarPainter.BAR_HEIGHT.dp,
            )
            .testTag(TabSwitchTags.BAR)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(state) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val position = event.changes.first().position
                        val tab =
                            if (event.type == PointerEventType.Exit) null
                            else painter.tabAt(position.x, motion)
                        state.hover(tab)
                        if (
                            event.type == PointerEventType.Press &&
                                event.buttons.isPrimaryPressed &&
                                tab != null
                        ) {
                            state.select(tab)
                        }
                    }
                }
            }
            .drawBehind {
                state.tick.longValue
                Tracing.section("tab bar") { painter.draw(this, motion) }
            }
    ) {
        TabNodes(state, painter, Modifier.fillMaxSize())
    }
}

/**
 * Invisible nodes for Spectre and screen readers: one per tab, where it rests with the current
 * selection, and one whose text is the selected label. They move once per switch, not per frame.
 */
@Composable
private fun TabNodes(state: TabSwitchState, painter: TabBarPainter, modifier: Modifier = Modifier) {
    val selected = state.selected
    val spans =
        remember(painter, selected) {
            FloatArray(2 * Tabs.size).also { out ->
                painter.layout.spans(FloatArray(Tabs.size) { if (it == selected) 1f else 0f }, out)
            }
        }
    Box(modifier) {
        Box(
            Modifier.fillMaxSize().testTag(TabSwitchTags.SELECTED).clearAndSetSemantics {
                text = AnnotatedString(Tabs[selected].label)
            }
        )
        for ((index, tab) in Tabs.withIndex()) {
            Box(
                Modifier.offset(x = (TabBarPainter.BAR_PAD + spans[2 * index]).dp)
                    .size((spans[2 * index + 1] - spans[2 * index]).dp, TabBarPainter.BAR_HEIGHT.dp)
                    .testTag(TabSwitchTags.tab(tab.label))
                    .semantics {
                        contentDescription = tab.label
                        role = Role.Tab
                        this.selected = index == selected
                    }
            )
        }
    }
}

private val NumberKeys = listOf(Key.One, Key.Two, Key.Three, Key.Four)
