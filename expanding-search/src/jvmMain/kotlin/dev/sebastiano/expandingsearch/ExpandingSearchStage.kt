package dev.sebastiano.expandingsearch

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.sebastiano.bioparco.tracing.Tracing
import kotlinx.coroutines.delay

/**
 * The page with the search widget near its top. [query] is the field's text; tests pass their own
 * to type without a keyboard.
 */
@Composable
internal fun ExpandingSearchStage(
    modifier: Modifier = Modifier,
    query: TextFieldState = rememberTextFieldState(),
) {
    val scope = rememberCoroutineScope()
    val motion = remember(scope) { SearchMotion(scope) }
    val density = LocalDensity.current
    val geometry = remember(density) { SearchGeometry(density) }
    val chrome = remember(geometry) { SearchChrome(geometry) }
    val field = remember { FocusRequester() }
    val stageFocus = remember { FocusRequester() }

    val text = query.text.toString()
    val results = remember(text) { Tracing.section("search") { searchComponents(text) } }
    val entries = remember(results) { results.ifEmpty { listOf(NoMatches) } }
    val byName = remember(entries) { entries.associateBy { it.name } }
    val shown = if (motion.isOpen) entries else emptyList()
    LaunchedEffect(shown) { motion.showRows(shown, selectable = results.size) }
    LaunchedEffect(motion) { motion.followHighlight() }
    LaunchedEffect(motion) {
        // Once the window is up, so the warm-up frames are real frames. Closed, the field is zero
        // wide and the panel's clip hides every row, so none of this shows. Each step took a long
        // frame the first time it happened for real: the field's first focus (it sets up text
        // input), its first edit, the first row changes, and the first rows with a match in bold
        // (the bold font loads).
        delay(WARM_UP_DELAY_MS)
        val untouched = query.text.isEmpty() && !motion.isOpen
        if (untouched) {
            field.requestFocus()
            query.edit { append(WARM_UP_QUERY) }
        }
        motion.warmUp(searchComponents(WARM_UP_QUERY))
        if (untouched) {
            if (query.text.toString() == WARM_UP_QUERY) query.clearText()
            if (!motion.isOpen) stageFocus.requestFocus()
        }
    }
    LaunchedEffect(motion.isOpen) {
        if (motion.isOpen) field.requestFocus() else stageFocus.requestFocus()
    }

    fun pick(slot: Int) {
        results.getOrNull(slot)?.let { query.setTextAndPlaceCursorAtEnd(it.name) }
    }

    fun dismiss() {
        when (dismissalFor(query.text)) {
            Dismissal.Clear -> {
                query.clearText()
                field.requestFocus()
            }
            Dismissal.Close -> motion.close()
        }
    }

    val topPx = with(density) { WidgetTop.toPx() }
    Box(
        modifier
            .fillMaxSize()
            .background(ExpandingSearchPalette.page)
            .testTag(ExpandingSearchTags.STAGE)
            // "/" opens the field from anywhere on the page, as search fields on the web do.
            .onPreviewKeyEvent { event ->
                val opens =
                    !motion.isOpen && event.type == KeyEventType.KeyDown && event.key == Key.Slash
                if (opens) motion.open()
                opens
            }
            .focusRequester(stageFocus)
            .focusable()
            // A click on the page, off the widget, empties and closes the field.
            .pointerInput(motion, geometry) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val origin = Offset((size.width - geometry.fullWidth) / 2f, topPx)
                    val onWidget =
                        geometry.hits(
                            down.position - origin,
                            motion.expansion.value,
                            motion.reveal.value,
                            motion.panelRows.value,
                        )
                    if (motion.isOpen && !onWidget) {
                        query.clearText()
                        motion.close()
                    }
                }
            }
    ) {
        Box(
            Modifier.align(Alignment.TopCenter)
                .offset(y = WidgetTop)
                .requiredSize(
                    with(density) { geometry.fullWidth.toDp() },
                    with(density) { geometry.totalHeight.toDp() },
                )
        ) {
            // Everything that moves is read in here and in the children's layer and offset blocks,
            // so a frame of motion redraws without recomposing.
            Box(
                Modifier.fillMaxSize().drawBehind {
                    Tracing.section("search chrome") { chrome.draw(this, motion) }
                    // Marks frames that owe a next frame, for measuring pacing from the trace.
                    if (Tracing.isEnabled && motion.isMoving) Tracing.section("moving") {}
                }
            )
            ResultRows(motion, geometry, chrome, current = byName, onPick = ::pick)
            SearchField(
                query,
                motion,
                geometry,
                Modifier.focusRequester(field).onPreviewKeyEvent { event ->
                    fieldKey(event, motion, onPick = ::pick, onDismiss = ::dismiss)
                },
            )
            ClearButton(motion, geometry, clears = text.isNotEmpty(), onClick = ::dismiss)
            if (!motion.isOpen) OpenButton(motion, geometry)
        }
    }
}

/** Arrow keys move the highlight, Enter picks it, Escape clears or closes like the cross. */
private fun fieldKey(
    event: KeyEvent,
    motion: SearchMotion,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    when (event.key) {
        Key.DirectionDown -> motion.step(1)
        Key.DirectionUp -> motion.step(-1)
        Key.Enter,
        Key.NumPadEnter -> onPick(motion.highlighted)
        Key.Escape -> onDismiss()
        else -> return false
    }
    return true
}

/** The line the panel shows when nothing matches. It cannot be highlighted or picked. */
internal val NoMatches = SearchResult("No components found", IntRange.EMPTY)

private const val WARM_UP_DELAY_MS = 400L

/** Its rows have a match in bold and the rest muted: every style a row can have. */
private const val WARM_UP_QUERY = "t"

/** The pill's top, from the page's top. */
private val WidgetTop = 120.dp
