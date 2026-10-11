package dev.sebastiano.anatomy.figures

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.sebastiano.anatomy.kit.IsoCanvas
import dev.sebastiano.anatomy.kit.KeyMark
import dev.sebastiano.anatomy.kit.LegendKey
import dev.sebastiano.anatomy.kit.Palette
import dev.sebastiano.anatomy.kit.Plate
import dev.sebastiano.anatomy.kit.follow
import dev.sebastiano.anatomy.kit.rememberFrameLoop
import kotlin.math.floor

/** The idle tour: shut, half, open, half, 3.2 s each. */
private val TOUR = listOf(0.0, 0.5, 1.0, 0.5)
private const val TOUR_STEP = 3.2
private const val IDLE_S = 3.6

private fun tourAt(t: Double): Double = TOUR[floor(t / TOUR_STEP).toInt() % TOUR.size]

@Composable
fun BunsenBurnerFigure(palette: Palette, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val state = remember {
        object {
            var opening = 0.0
            var target = 0.0
            var touring = true
            var tour = 0.0
            var clock = 0.0
            var idle = 0.0
            val flame = BunsenFlame.layer()
        }
    }
    // What the canvas reads: written by the loop, read in the draw phase only.
    var opening by remember { mutableDoubleStateOf(state.opening) }
    var clock by remember { mutableDoubleStateOf(0.0) }
    var readout by remember { mutableStateOf(Bunsen.readoutOf(state.opening)) }
    if (reducedMotion) state.touring = false
    val kick =
        rememberFrameLoop(reducedMotion) { dt, calm ->
            val s = state
            // Back to the tour after a pause, timed on the frame clock like the flame.
            s.idle += dt
            if (!calm && !s.touring && s.idle > IDLE_S) {
                s.touring = true
                s.tour = 0.0
            }
            if (s.touring) {
                s.tour += dt
                s.target = tourAt(s.tour)
            }
            if (!calm) s.clock += dt
            s.opening = follow(s.opening, s.target, dt, 0.45, calm)
            opening = s.opening
            clock = if (calm) STILL_TIME else s.clock
            readout = Bunsen.readoutOf(s.opening)
            // A flame flickers, so it keeps drawing; under reduced motion it rests once set.
            !calm || s.opening != s.target
        }
    fun steer(next: Double) {
        state.touring = false
        state.target = next.coerceIn(0.0, 1.0)
        state.idle = 0.0
        kick()
    }
    Plate(
        fig = "Fig 3",
        title = "Bunsen burner",
        hint = "Hover or use the arrows to turn the collar",
        readout = { readout },
        palette = palette,
        modifier = modifier,
        keys =
            listOf(
                LegendKey(KeyMark.Lit, "Air collar: its slots open the ports that let in air"),
                LegendKey(KeyMark.Raised, "Ruler zeroed at the rim, its cursor at the flame's tip"),
            ),
        caption =
            "A Bunsen burner on 20 L/h of methane. Shut, the collar lets no air in: the gas " +
                "cracks to soot that glows yellow in a tall, lazy flame of about 1,000 °C. Open, " +
                "the jet draws in up to three quarters of the air it needs, and the mixture burns " +
                "as a short roaring flame with an inner blue cone, about 1,500 °C just above its " +
                "tip. Heights are Roper's flame length and Gouy's cone.",
    ) {
        IsoCanvas(
            BunsenBench.WIDTH,
            BunsenBench.HEIGHT,
            palette,
            Modifier.fillMaxWidth()
                .semantics {
                    contentDescription = BunsenBench.LABEL
                    stateDescription = readout
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val position = event.changes.firstOrNull()?.position ?: continue
                            when (event.type) {
                                PointerEventType.Move,
                                PointerEventType.Enter,
                                PointerEventType.Press -> {
                                    steer((position.x / size.width - 0.25) / 0.5)
                                    event.changes.forEach { it.consume() }
                                }
                                PointerEventType.Exit -> steer(state.target)
                            }
                        }
                    }
                }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val here = state.target
                    val to =
                        when (event.key) {
                            Key.DirectionRight,
                            Key.DirectionUp -> here + 0.1
                            Key.DirectionLeft,
                            Key.DirectionDown -> here - 0.1
                            Key.MoveHome -> 0.0
                            Key.MoveEnd -> 1.0
                            else -> return@onKeyEvent false
                        }
                    steer(Math.round(to * 10) / 10.0)
                    true
                }
                .focusable(),
        ) {
            with(BunsenCollar) { paint(opening, clock, state.flame) }
        }
    }
}

/** The moment a reduced-motion flame is frozen at. */
private const val STILL_TIME = 0.8
