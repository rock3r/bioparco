package dev.sebastiano.currencyswarm

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.jewel.ui.component.Text

/** How long the dots wait as a cloud before the first sign, in seconds. */
private const val INTRO = 0.6f

/** How long a sign stays once every dot has landed, in seconds. */
private const val HOLD = 1.5f

@Composable
internal fun CurrencySwarmStage(modifier: Modifier = Modifier) {
    val state = remember { SwarmStageState() }
    val focus = remember { FocusRequester() }

    LaunchedEffect(state) { while (true) withFrameNanos(state::step) }
    // Every change of sign, whoever made it, sends the dots off and restarts the wait for the next,
    // which also waits for the pointer to leave the dots. The waits count frame-clock time, not
    // wall time, so a scene rendered faster or slower than real time plays the same sequence. The
    // sign is read here, not in composition, so a change recomposes only the caption and chips.
    LaunchedEffect(state) {
        snapshotFlow { state.index.intValue }
            .collectLatest {
                state.awaitSeconds(INTRO)
                state.morph()
                state.awaitSeconds(state.seconds + Swarm.LONGEST_FLIGHT + HOLD)
                state.awaitPointerAway()
                state.next()
            }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        modifier
            .fillMaxSize()
            .background(SwarmPalette.background)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> state.next()
                    Key.DirectionLeft -> state.previous()
                    else -> return@onKeyEvent false
                }
                true
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SwarmCanvas(
            state,
            Modifier.testTag(SwarmTags.STAGE)
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds()
                .onSizeChanged(state::onSize)
                .pointerHoverIcon(PointerIcon.Hand)
                .pointerInput(state) { trackPointer(state) }
                .pointerInput(state) { detectTapGestures { state.next() } },
        )
        Caption(state)
        Spacer(Modifier.height(22.dp))
        Chips(state)
        Text(
            "Move the pointer through the dots. Click them, or press ← →, for the next sign",
            color = SwarmPalette.hint,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 28.dp, bottom = 22.dp),
        )
    }
}

/** Every move over the stage pushes dots; leaving lets them settle. Nothing is consumed. */
private suspend fun PointerInputScope.trackPointer(state: SwarmStageState) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: continue
            if (event.type == PointerEventType.Exit) state.onPointerExit()
            else state.onPointer(change.position)
        }
    }
}

/**
 * Every currency's caption, laid out once and stacked; a change of sign only fades their layers. A
 * `Crossfade` would compose and lay out the new caption's text on the frame the dots set off.
 */
@Composable
private fun Caption(state: SwarmStageState, modifier: Modifier = Modifier) {
    // Read here, not by the stage: a change of sign recomposes only the caption and the chips.
    val selected by state.index
    Box(modifier, contentAlignment = Alignment.Center) {
        Currencies.forEachIndexed { i, currency ->
            val alpha =
                animateFloatAsState(if (i == selected) 1f else 0f, tween(durationMillis = 360))
            Column(
                Modifier.graphicsLayer { this.alpha = alpha.value },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    currency.name,
                    color = SwarmPalette.title,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    // Only the shown caption is the stage's name, so Spectre reads the right one.
                    modifier = if (i == selected) Modifier.testTag(SwarmTags.NAME) else Modifier,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    currency.code,
                    color = SwarmPalette.subtitle,
                    fontSize = 13.sp,
                    letterSpacing = 2.sp,
                )
            }
        }
    }
}

@Composable
private fun Chips(state: SwarmStageState, modifier: Modifier = Modifier) {
    val selected by state.index
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Currencies.forEachIndexed { i, currency ->
            val isSelected = i == selected
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier.testTag(SwarmTags.chip(currency.code))
                    .size(40.dp)
                    .clip(shape)
                    .background(
                        if (isSelected) SwarmPalette.chipSelected else SwarmPalette.background
                    )
                    .border(1.dp, SwarmPalette.chipBorder, shape)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { state.select(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    currency.symbol,
                    color =
                        if (isSelected) SwarmPalette.chipSelectedText else SwarmPalette.chipText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
