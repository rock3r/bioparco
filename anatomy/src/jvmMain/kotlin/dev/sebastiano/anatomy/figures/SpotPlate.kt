// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.sebastiano.anatomy.kit.IsoCanvas
import dev.sebastiano.anatomy.kit.KeyMark
import dev.sebastiano.anatomy.kit.LegendKey
import dev.sebastiano.anatomy.kit.Palette
import dev.sebastiano.anatomy.kit.Plate
import dev.sebastiano.anatomy.kit.rememberFrameLoop
import dev.sebastiano.anatomy.kit.turn.TurnHook
import kotlinx.coroutines.delay

private const val SPOT_LABEL =
    "A lab spot plate on a bench. The plate has six channels, each holding a pair of glass drops, R30 and R22, at gaps of 4, 10, 16, 24, 32, 44 px, drawn to scale against a ruler on the rim. On the right, beside a dropper bottle, a fan of six feeler blades, one for each gap, turns on a pivot over a protractor marked 0 to 48 px; the top blade is merge. As it opens, each blade stops at its own gap, and the pair with that gap runs together into one piece of glass."

private class SpotControl(hold: Double?) {
    val motion =
        SpotMotion().apply {
            if (hold != null) {
                aim(hold)
                tick(0.0, true)
            }
        }
    var shown by mutableDoubleStateOf(motion.shown)
    var value by mutableIntStateOf(SpotModel.roundMerge(motion.shown))
    var hovering by mutableStateOf(false)
    var focused by mutableStateOf(false)
    var touches by mutableIntStateOf(0)
    var kick: () -> Unit = {}

    fun aim(merge: Double, glide: Boolean = false) {
        motion.aim(merge, glide)
        touches++
        kick()
    }

    fun engage() {
        motion.engage(hovering || focused)
    }

    fun key(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val big = if (event.isShiftPressed) 4 else 1
        val next =
            when (event.key) {
                Key.DirectionLeft,
                Key.DirectionDown -> SpotModel.roundMerge(motion.goal - big).toDouble()
                Key.DirectionRight,
                Key.DirectionUp -> SpotModel.roundMerge(motion.goal + big).toDouble()
                Key.PageDown -> SpotModel.roundMerge(motion.goal - 8).toDouble()
                Key.PageUp -> SpotModel.roundMerge(motion.goal + 8).toDouble()
                Key.MoveHome -> 0.0
                Key.MoveEnd -> SpotRig.MERGE_MOST
                else -> return false
            }
        aim(next)
        return true
    }
}

@Composable
private fun rememberSpotControl(reducedMotion: Boolean, isOnScreen: () -> Boolean): SpotControl {
    val hold = TurnHook.values?.get("merge")
    val control = remember(hold) { SpotControl(hold) }
    control.kick =
        rememberFrameLoop(reducedMotion, isOnScreen) { dt, calm ->
            val moving = hold == null && control.motion.tick(dt, calm)
            control.shown = control.motion.shown
            control.value = SpotModel.roundMerge(SpotDrops.keyOf(control.motion.shown) / 4.0)
            moving
        }
    LaunchedEffect(control.hovering, control.focused, control.touches, reducedMotion) {
        if (
            control.touches == 0 ||
                control.hovering ||
                control.focused ||
                reducedMotion ||
                hold != null
        )
            return@LaunchedEffect
        delay(4_200)
        control.motion.resumeTour()
        control.kick()
    }
    return control
}

/** The original's Fig 7 becomes Fig 11 in bioparco; its six pairs share one merge control. */
@Composable
fun SpotPlateFigure(
    palette: Palette,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    isOnScreen: () -> Boolean = { true },
) {
    val control = rememberSpotControl(reducedMotion, isOnScreen)
    Plate(
        fig = "Fig 11",
        title = "Spot plate",
        hint = "Slide across to open the fan",
        readout = { SpotModel.readoutOf(control.value.toDouble()) },
        palette = palette,
        modifier = modifier,
        keys =
            listOf(
                LegendKey(KeyMark.Raised, "Joined: one piece of glass, R30 + R22"),
                LegendKey(KeyMark.Edge, "The neck, where a pair runs together"),
                LegendKey(KeyMark.Lit, "The merge blade"),
            ),
        caption =
            "A spot plate with six channels, each holding a pair of glass drops at gaps from 4 to 44 px. " +
                "Each pair is drawn from one distance field, so a pair closer than merge runs together " +
                "into one piece of glass. The fan sets merge: each feeler blade stops at its own gap. " +
                "At 20 px the ${SpotModel.joinedListOf(20.0)} pairs have joined, " +
                "so ${SpotModel.bladesOutOf(20.0)}.",
    ) {
        SpotCanvas(control, palette)
    }
}

@Composable
private fun SpotCanvas(control: SpotControl, palette: Palette, modifier: Modifier = Modifier) {
    val paint = remember(palette) { SpotPaint(palette) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // The CSS breakpoint is page width <= 640, while Plate has already removed its padding.
        val narrow = maxWidth <= 592.dp
        val input =
            Modifier.fillMaxWidth()
                .semantics {
                    contentDescription = "Merge, in pixels. $SPOT_LABEL"
                    stateDescription = SpotModel.spokenOf(control.value.toDouble())
                    progressBarRangeInfo =
                        ProgressBarRangeInfo(control.value.toFloat(), 0f..48f, 47)
                    setProgress {
                        control.aim(it.toDouble())
                        true
                    }
                }
                .spotPointer(control, narrow)
                .onKeyEvent(control::key)
                .onFocusChanged {
                    control.focused = it.isFocused
                    control.engage()
                    if (it.isFocused)
                        control.aim(SpotModel.roundMerge(control.motion.shown).toDouble())
                }
                .focusable()
        androidx.compose.foundation.layout.Box(
            input.then(if (narrow) Modifier.clipToBounds() else Modifier)
        ) {
            IsoCanvas(
                SpotRig.WIDTH,
                SpotRig.HEIGHT,
                palette,
                Modifier.fillMaxWidth().spotViewport(narrow),
            ) {
                paint.draw(this, control.shown)
            }
        }
    }
}

private fun Modifier.spotViewport(narrow: Boolean): Modifier =
    if (!narrow) this
    else
        layout { measurable, constraints ->
            val width = constraints.maxWidth
            val expanded = (width * 1.3).toInt()
            val placeable =
                measurable.measure(constraints.copy(minWidth = expanded, maxWidth = expanded))
            layout(width, (placeable.height - width * 0.18).toInt()) {
                placeable.place((-width * 0.15).toInt(), (-width * 0.1).toInt())
            }
        }

private fun Modifier.spotPointer(control: SpotControl, narrow: Boolean): Modifier =
    pointerInput(control, narrow) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: continue
                val mouse = change.type == PointerType.Mouse
                when (event.type) {
                    PointerEventType.Enter ->
                        if (mouse) {
                            control.hovering = true
                            control.engage()
                        }
                    PointerEventType.Exit ->
                        if (mouse) {
                            control.hovering = false
                            control.engage()
                            control.aim(SpotRig.MERGE, glide = true)
                        }
                    PointerEventType.Move,
                    PointerEventType.Press -> {
                        if (mouse || change.pressed) {
                            val share =
                                if (narrow) (change.position.x / size.width + 0.15) / 1.3
                                else change.position.x / size.width.toDouble()
                            control.aim(SpotModel.mergeAtX(share * SpotRig.WIDTH))
                            if (change.pressed) change.consume()
                        }
                    }
                }
            }
        }
    }
