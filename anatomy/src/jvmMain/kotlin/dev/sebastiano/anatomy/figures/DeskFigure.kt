// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.IsoCanvas
import dev.sebastiano.anatomy.kit.KeyMark
import dev.sebastiano.anatomy.kit.LegendKey
import dev.sebastiano.anatomy.kit.Palette
import dev.sebastiano.anatomy.kit.Plate
import dev.sebastiano.anatomy.kit.insideClips
import dev.sebastiano.anatomy.kit.rememberFrameLoop
import dev.sebastiano.anatomy.kit.slabOf
import kotlinx.coroutines.delay

@Composable
fun DeskComputerFigure(palette: Palette, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val pc = remember { DeskModel() }
    val focus = remember { FocusRequester() }
    var redraw by remember { mutableIntStateOf(0) }
    var touches by remember { mutableIntStateOf(0) }
    var readout by remember { mutableStateOf(pc.readout()) }
    val kick =
        rememberFrameLoop(reducedMotion) { dt, calm ->
            val busy = pc.tick(dt, calm)
            redraw++
            readout = pc.readout()
            busy || (pc.power && !calm) // The original's CSS cursor keeps blinking while powered.
        }
    LaunchedEffect(touches, reducedMotion) {
        if (touches == 0 || reducedMotion) return@LaunchedEffect
        delay(9_000)
        pc.resume()
        kick()
    }
    fun changed() {
        touches++
        readout = pc.readout()
        redraw++
        kick()
    }
    Plate(
        fig = "Fig 7",
        title = "Desk computer",
        hint = "Type on it · click it to switch",
        readout = { readout },
        palette = palette,
        modifier = modifier,
        keys =
            listOf(
                LegendKey(KeyMark.Lit, "The screen: the Anthropic mark and what you type"),
                LegendKey(KeyMark.Raised, "Fifty-eight keys; each one goes down as it is typed"),
                LegendKey(KeyMark.Edge, "A coiled cord from the keyboard to the side port"),
            ),
        caption =
            "An all-in-one desk computer in the spirit of 1984: a CRT set into a tall case, " +
                "a floppy slot and speaker grille under it, vents and a rocker switch on the side. " +
                "Focus it and type: each key you press goes down on the keyboard and the character " +
                "lands on the screen beneath the Anthropic mark. Backspace and Return work, Escape " +
                "or a click on the case switches it off and the picture collapses to a line and a dot, as a CRT does.",
    ) {
        IsoCanvas(
            DeskComputer.WIDTH,
            DeskComputer.HEIGHT,
            palette,
            Modifier.fillMaxWidth()
                .focusRequester(focus)
                .onKeyEvent { event ->
                    if (pc.handle(event)) {
                        changed()
                        true
                    } else false
                }
                .focusable()
                .semantics {
                    contentDescription =
                        "Desk computer. Type to write on its screen; Escape switches the power."
                    stateDescription = readout
                }
                .pointerInput(pc) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Release) {
                                val pos = event.changes.first().position
                                val point =
                                    Flat(
                                        pos.x / size.width.toDouble() * DeskComputer.WIDTH,
                                        pos.y / size.width.toDouble() * DeskComputer.WIDTH,
                                    )
                                if (pc.click(point)) {
                                    focus.requestFocus()
                                    changed()
                                }
                            }
                        }
                    }
                },
        ) {
            redraw // Snapshot subscription is draw-phase only; pc itself is a plain model.
            drawDesk(pc, reducedMotion)
        }
    }
}

private fun DeskModel.handle(event: KeyEvent): Boolean {
    if (
        event.type != KeyEventType.KeyDown ||
            event.isMetaPressed ||
            event.isCtrlPressed ||
            event.isAltPressed ||
            event.key == Key.Tab
    )
        return false
    takeOver(event.key == Key.Escape)
    when (event.key) {
        Key.Escape -> setPower(!power)
        Key.Backspace -> erase()
        Key.Enter,
        Key.NumPadEnter -> enter()
        Key.ShiftLeft -> press("Shift", 0.14)
        Key.ShiftRight -> press("ShiftRight", 0.14)
        else -> {
            if (event.utf16CodePoint !in 32..126) return false
            type(event.utf16CodePoint.toChar())
        }
    }
    return true
}

/** Click the frontmost painted cap or any part of the case, just as the SVG's hit targets do. */
fun DeskModel.click(point: Flat): Boolean {
    val key = DeskKeys.keys.asReversed().firstOrNull { insideClips(point, listOf(it.paths.fill)) }
    if (key != null) {
        takeOver()
        when (key.code) {
            "Backspace" -> erase()
            "Enter",
            "NumpadEnter" -> enter()
            else -> if (key.code.length == 1) type(key.code[0]) else press(key.code)
        }
        return true
    }
    val case = slabOf(DeskComputer.CASE, 5.0, 165.0, DeskComputer.P, 8).fill
    if (!insideClips(point, listOf(case))) return false
    takeOver(switching = true)
    setPower(!power)
    return true
}
