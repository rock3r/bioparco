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
import dev.sebastiano.anatomy.figures.EspressoPaint.paint
import dev.sebastiano.anatomy.kit.IsoCanvas
import dev.sebastiano.anatomy.kit.KeyMark
import dev.sebastiano.anatomy.kit.LegendKey
import dev.sebastiano.anatomy.kit.Palette
import dev.sebastiano.anatomy.kit.Plate
import dev.sebastiano.anatomy.kit.follow
import dev.sebastiano.anatomy.kit.rememberFrameLoop
import kotlin.math.exp
import kotlinx.coroutines.delay

/*
 * The live figure: one loop runs the shot, the pressurestat and the boiler, eases the lever, and
 * writes one view for the canvas. The tour pulls a shot, lets it stop itself at 36 g, rests and
 * pulls the next.
 */

private const val IDLE_MS = 3_600L
private const val TOUR_PULL_S = 2.0
private const val TOUR_REST_S = 7.0

/** The thermosyphon's slow circulation, shown at rest; not a measured rate. */
private const val THERMOSYPHON_ML_S = 1.5

/** How the boiler's pressure drifts, in bar a second: the element, the losses, a shot's draw. */
private const val HEAT_BAR_S = 0.012
private const val LOSS_BAR_S = 0.003
private const val DRAW_BAR_S = 0.004

internal class EspressoState {
    val shot = Shot()
    val stat = Pressurestat()
    var boiler = 1.12
    var lever = LEVER_DOWN
    var clock = 0.0
    var touring = true
    var tour = 0.0
    val view = EspressoPaint.View()

    /** Advances everything by [dt]; returns true while anything still moves. */
    fun tick(dt: Double, calm: Boolean): Boolean {
        if (touring) {
            val before = tour
            tour += dt
            val period = TOUR_PULL_S + Espresso.shotTime() + TOUR_REST_S
            if (before % period < TOUR_PULL_S && tour % period >= TOUR_PULL_S && !shot.running)
                shot.pull()
        }
        shot.advance(dt)
        val heating = stat.update(boiler)
        boiler +=
            dt *
                ((if (heating) HEAT_BAR_S else 0.0) -
                    LOSS_BAR_S -
                    (if (shot.running) DRAW_BAR_S else 0.0))
        lever = follow(lever, if (shot.running) LEVER_UP else LEVER_DOWN, dt, 0.12, calm)
        if (!calm) clock += dt
        val v = view
        v.lever = lever
        v.pressure = shot.pressure
        v.clock = clock
        v.grams = shot.grams
        v.pour = if (shot.running) Espresso.puckFlow(shot.time) else 0.0
        v.flows = if (calm) emptyMap() else flowsNow()
        return !calm ||
            touring ||
            shot.running ||
            lever != (if (shot.running) LEVER_UP else LEVER_DOWN)
    }

    private fun flowsNow(): Map<String, Double> {
        val t = shot.time
        val running = shot.running
        val drain = if (!running && t > 0) DRAIN_ML_S * exp(-shot.vented / 0.4) else 0.0
        return mapOf(
            Water.FEED to if (running) Espresso.pumpFlow(shot.pressure) else 0.0,
            Water.BREW to if (running) Espresso.brewFlow(t) else 0.0,
            Water.RETURN to if (running) Espresso.bypassFlow(t) else 0.0,
            Water.UP to THERMOSYPHON_ML_S + if (running) Espresso.brewFlow(t) else 0.0,
            Water.DOWN to THERMOSYPHON_ML_S,
            Water.DRAIN to if (drain > 0.05) drain else 0.0,
        )
    }

    private companion object {
        const val DRAIN_ML_S = 6.0
    }
}

@Composable
fun EspressoMachineFigure(palette: Palette, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val state = remember { EspressoState() }
    var readout by remember { mutableStateOf(Espresso.readoutOf(state.shot, state.boiler)) }
    var touches by remember { mutableIntStateOf(0) }
    if (reducedMotion) state.touring = false
    val kick =
        rememberFrameLoop(reducedMotion) { dt, calm ->
            val more = state.tick(dt, calm)
            readout = Espresso.readoutOf(state.shot, state.boiler)
            more
        }
    LaunchedEffect(touches) {
        if (touches == 0) return@LaunchedEffect
        delay(IDLE_MS)
        if (!reducedMotion) {
            state.touring = true
            state.tour = 0.0
            kick()
        }
    }
    fun pull() {
        state.touring = false
        state.shot.pull()
        touches++
        kick()
    }
    Plate(
        fig = "Fig 4",
        title = "Heat-exchanger espresso machine",
        hint = "Click or press Space to pull a shot",
        readout = { readout },
        palette = palette,
        modifier = modifier,
        keys =
            listOf(
                LegendKey(
                    KeyMark.Lit,
                    "Heat exchanger: fresh water crosses the boiler in its own tube",
                ),
                LegendKey(
                    KeyMark.Dotted,
                    "Water: a dot every quarter millilitre, at its true speed",
                ),
                LegendKey(
                    KeyMark.Raised,
                    "Thermosyphon: hot water rises to the group and sinks back",
                ),
            ),
        caption =
            "A heat-exchanger machine with an E61 group, side panel off and boiler cut in half. " +
                "The 2.2 L boiler is held at 1.0 to 1.2 bar, so its water boils at 123 to 125 °C, " +
                "and steam from the top feeds the wand. Brew water never mixes with it: on a " +
                "shot the vibration pump pushes about 260 ml a minute from the tank into the back " +
                "of the heat exchanger, a 34 mm tube that crosses the boiler, and it reaches the " +
                "group hot through the upper thermosyphon pipe. The OPV holds 9 bar and returns " +
                "what the puck doesn't take. 18 g of coffee gives 36 g in about 27 s.",
    ) {
        IsoCanvas(
            EspressoMachine.WIDTH,
            EspressoMachine.HEIGHT,
            palette,
            Modifier.fillMaxWidth()
                .semantics {
                    contentDescription = EspressoMachine.LABEL
                    stateDescription = readout
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Press) pull()
                        }
                    }
                }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.Spacebar,
                        Key.Enter -> {
                            pull()
                            true
                        }
                        else -> false
                    }
                }
                .focusable(),
        ) {
            paint(state.view)
        }
    }
}
