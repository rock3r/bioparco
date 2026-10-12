// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.sebastiano.anatomy.kit.IsoCanvas
import dev.sebastiano.anatomy.kit.KeyMark
import dev.sebastiano.anatomy.kit.LegendKey
import dev.sebastiano.anatomy.kit.Palette
import dev.sebastiano.anatomy.kit.Plate
import kotlinx.coroutines.delay

@Composable
fun ArcadeCabinetFigure(palette: Palette, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val live = remember { ArcadeMachine() }
    val paint = remember(palette) { ArcadePaint(palette) }
    var frame by remember { mutableLongStateOf(0L) }
    var touches by remember { mutableIntStateOf(0) }
    var readout by remember { mutableStateOf("off · insert coin") }
    var status by remember { mutableStateOf("Off. Insert a coin.") }
    val focus = remember { FocusRequester() }
    val kick =
        dev.sebastiano.anatomy.kit.rememberFrameLoop(reducedMotion) { dt, calm ->
            val busy = live.tick(dt, calm)
            frame++
            readout = arcadeSay(live, live.picture())
            status = live.status
            busy
        }
    LaunchedEffect(touches) {
        if (touches == 0) return@LaunchedEffect
        delay(12_000)
        if (!reducedMotion) {
            live.tour.resume()
            kick()
        }
    }
    val input =
        ArcadeInput(live) {
            live.touring = false
            touches++
            kick()
        }
    Plate(
        "Fig 8",
        "Upright cabinet, side off",
        "C or click the quarters · ← → · space",
        { readout },
        palette,
        modifier,
        keys =
            listOf(
                LegendKey(
                    KeyMark.Lit,
                    "The picture: drawn in the plane of the CRT behind the tilted glass",
                ),
                LegendKey(KeyMark.Raised, "Coin path: slot, mech, switch wire, meter, cash box"),
                LegendKey(
                    KeyMark.Edge,
                    "Harness from the joystick, buttons and coin switch to the board's edge connector",
                ),
            ),
        caption = ARCADE_CAPTION,
    ) {
        IsoCanvas(
            Arcade.W,
            Arcade.H,
            palette,
            Modifier.fillMaxWidth()
                .focusRequester(focus)
                .onKeyEvent(input::key)
                .semantics {
                    contentDescription = ARCADE_LABEL
                    stateDescription = status
                    liveRegion = LiveRegionMode.Polite
                    onClick("Insert a quarter") {
                        input.coin()
                        true
                    }
                }
                .focusable()
                .pointerInput(input) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Release) {
                                val p = event.changes.first().position
                                if (
                                    arcadeCoinHit(
                                        p.x / size.width * Arcade.W,
                                        p.y / size.width * Arcade.W,
                                    )
                                ) {
                                    input.coin()
                                    focus.requestFocus()
                                }
                            }
                        }
                    }
                },
            clock = { live.clock },
        ) {
            frame
            paint.paint(this, live)
        }
    }
}

internal class ArcadeInput(private val m: ArcadeMachine, private val wake: () -> Unit) {
    fun coin() {
        m.insertCoin()
        wake()
    }

    fun key(event: KeyEvent): Boolean {
        if (event.isMetaPressed || event.isCtrlPressed || event.isAltPressed) return false
        if (event.type == KeyEventType.KeyUp) {
            when (event.key) {
                Key.DirectionLeft,
                Key.DirectionRight -> m.target = 0.0
                Key.Spacebar,
                Key.Enter -> m.fireHeld = minOf(m.fireHeld, 0.06)
                else -> return false
            }
        } else
            when (event.key) {
                Key.C,
                Key.Five -> m.insertCoin()
                Key.DirectionLeft -> m.steer(-1.0)
                Key.DirectionRight -> m.steer(1.0)
                Key.Spacebar,
                Key.Enter -> m.shoot(0.5)
                Key.Escape -> m.setPower(false)
                else -> return false
            }
        wake()
        return true
    }
}

private const val ARCADE_LABEL =
    "Arcade cabinet. Press C or click the quarters to insert a coin; left and right arrows move the joystick; space presses the fire button; Escape switches the cabinet off. An upright cabinet with its left side panel off, a tilted CRT, marquee lamp, speaker, joystick microswitches, coin mechs, switch wire, meter, cash box, game board and power supply. A stack of quarters sits on a tray."
private const val ARCADE_CAPTION =
    "An upright cabinet in the style of 1981, 178 cm tall, with the left side panel off. Drop a quarter (24.26 mm) in the left slot: the mech checks it and lets it fall 27 cm past the coin-switch wire into the open cash box. The switch pulses the meter and the board's coin line, and on this machine it also latches the power relay, so the first coin wakes the cabinet: the marquee strikes, the CRT's heater warms for about two seconds, the board runs its RAM and ROM self-test and the attract mode starts. The monitor lies on its side for a vertical game, 224 × 288 pixels on a 15.734 kHz raster, so its scan lines run up and down the glass and it collapses to a vertical line when switched off. Move the stick to see the actuator close a microswitch under the panel."
