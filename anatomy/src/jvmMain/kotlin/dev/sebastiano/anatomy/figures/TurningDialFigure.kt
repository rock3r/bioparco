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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.sebastiano.anatomy.figures.TurningDial.BREAK_END
import dev.sebastiano.anatomy.figures.TurningDial.INDEX_AT
import dev.sebastiano.anatomy.kit.IsoCanvas
import dev.sebastiano.anatomy.kit.IsoScope
import dev.sebastiano.anatomy.kit.KeyMark
import dev.sebastiano.anatomy.kit.LegendKey
import dev.sebastiano.anatomy.kit.Palette
import dev.sebastiano.anatomy.kit.Plate
import dev.sebastiano.anatomy.kit.Settling
import dev.sebastiano.anatomy.kit.rememberFrameLoop
import dev.sebastiano.anatomy.kit.settle
import dev.sebastiano.anatomy.kit.turn.FreePose
import dev.sebastiano.anatomy.kit.turn.Material
import dev.sebastiano.anatomy.kit.turn.TurnController
import dev.sebastiano.anatomy.kit.turn.TurnHook
import dev.sebastiano.anatomy.kit.turn.TurnValues
import dev.sebastiano.anatomy.kit.turn.drawTurnLayer
import dev.sebastiano.anatomy.kit.turn.materialPalette
import dev.sebastiano.anatomy.oneDecimal
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlinx.coroutines.delay

/*
 * The live figure, `live.js` of the original: an idle tour turns the dial at 18°/s while the finger
 * taps; drag round the dial to turn it, arrow keys step one number (Shift: ten), Home brings 0 to
 * the index, and B breaks the pod off (and back). The figure owns the loop and calls the turn
 * controller's `set`; the canvas redraws from the controller's frames and nothing recomposes.
 */

private const val SPEED = 18.0
private const val IDLE_MS = 3_600L
private const val DRAG_Z = 17.2

/** The index's angle in degrees. */
private val INDEX = (INDEX_AT * 180) / PI

/** The number under the index when the dial is at [turn] degrees. */
internal fun readingOf(turn: Double): Double = ((((INDEX + 90 - turn) / 3.6) % 100) + 100) % 100

internal const val TURNING_DIAL_LABEL =
    "A combination dial on a raised door plate. A small robot hand lies on the dial's hub and turns " +
        "with it: a palm with a cover plate, a connector block, a piston on two saddles with a ball " +
        "joint, three jointed fingers whose tips rest on the dial face, and a thumb on a ball knuckle."

/** A break in flight: how far along, and whether it runs backwards (the pod going back on). */
private class Breaking(var time: Double, val reverse: Boolean)

/** Everything the loop changes, outside composition. */
private class DialState(val scene: TurningDial.Scene, reduced: Boolean) {
    val controller = TurnController(scene.built.data, TurningDial::follow)
    val light = TurningDialLight(scene)
    var angle = 0.0
    var target = 0.0
    var touring = !reduced
    var clock = 0.0
    var lift = 0.0
    var broken = false
    var breaking: Breaking? = null
    val on = Settling(0.0)
    var glClock = 0.0
    var drawn: Double? = null
    var dragFrom: Double? = null
    var dragBase = 0.0
    var dragging = false

    /** A review tool's pod time, while it holds the figure. */
    var podHook: Double? = null

    fun podAt(): FreePose? {
        podHook?.let {
            return TurningDial.breakAt(it)
        }
        val b = breaking ?: return if (broken) TurningDial.breakAt(BREAK_END) else null
        val t = min(BREAK_END, b.time)
        return TurningDial.breakAt(if (b.reverse) BREAK_END - t else t)
    }

    fun draw() {
        controller.set(TurnValues(mapOf("dial" to angle, "lift" to lift), mapOf("pod" to podAt())))
        drawn = angle
    }

    /** One frame of the tour, the follow, the light, the sparks and the break; true to go on. */
    fun tick(dt: Double, calm: Boolean): Boolean {
        takeHook()
        turn(dt, calm)
        val nextLift = liftAt(dt, calm)
        var moved = abs(nextLift - lift) > 1e-5
        lift = nextLift
        glClock += dt
        val before = on.x
        val lighting = light.available && settle(on, 1.0, dt, 0.35, calm)
        if (on.x != before) moved = true
        val sparking = light.available && light.step(if (calm) 1.0 else dt)
        if (lighting || sparking) moved = true
        if (stepBreak(dt, calm)) moved = true
        val last = drawn
        if (moved || last == null || abs(angle - last) > 1e-4) draw()
        return touring || angle != target || breaking != null || lighting || sparking
    }

    /** The tour's target moves on and the dial eases toward it (at once when calm). */
    private fun turn(dt: Double, calm: Boolean) {
        if (calm) touring = false
        if (touring) {
            target += SPEED * dt
            clock += dt
        }
        angle = if (calm) target else angle + (target - angle) * (1 - exp(-dt / 0.12))
        if (abs(target - angle) < 1e-3) angle = target
    }

    /** A review tool's hold (`TurnHook`): stop the tour and take its values. */
    private fun takeHook() {
        val values = TurnHook.values ?: return
        touring = false
        values["dial"]?.let {
            angle = it
            target = it
        }
        values["lift"]?.let { lift = it }
        podHook = values["pod"]
    }

    /** The finger's lift this frame: down when calm, held, following the tap while touring. */
    private fun liftAt(dt: Double, calm: Boolean): Double =
        when {
            calm -> 0.0
            TurnHook.values?.get("lift") != null -> lift
            touring -> lift + (TurningDial.tapAt(clock) - lift) * (1 - exp(-dt / 0.15))
            else -> lift
        }

    /** Advances a break in flight, lighting the sparks as it starts; true while one runs. */
    private fun stepBreak(dt: Double, calm: Boolean): Boolean {
        val b = breaking ?: return false
        if (!b.reverse && b.time == 0.0 && !calm) light.ignite(controller)
        b.time += if (calm) BREAK_END else dt
        if (b.time >= BREAK_END) {
            broken = !b.reverse
            breaking = null
        }
        return true
    }

    /** A press on the dial at viewBox [x], [y]: where a drag starts. */
    fun press(x: Double, y: Double) {
        dragFrom = controller.angleAt(x, y, DRAG_Z, "dial")
        dragBase = target
        dragging = false
    }

    /** The pointer moved to [x], [y]: turns the dial once it has moved 1.5°; true while it does. */
    fun drag(x: Double, y: Double): Boolean {
        val from = dragFrom ?: return false
        val delta = controller.angleAt(x, y, DRAG_Z, "dial") - from
        if (!dragging && abs(delta) > 1.5) dragging = true
        if (dragging) target = dragBase + delta
        return dragging
    }

    fun toggleBreak() {
        val b = breaking
        breaking =
            if (b != null) Breaking(BREAK_END - b.time, !b.reverse) else Breaking(0.0, broken)
    }

    fun readout(): String {
        val value = drawn ?: angle
        val reading = readingOf(value).roundToLong() % 100
        return "dial $reading · ${oneDecimal(((value % 360) + 360) % 360)}°${if (broken) " · pod off" else ""}"
    }

    fun describe(): String {
        val reading = readingOf(target).roundToLong() % 100
        return "dial at $reading${if (broken) ", the pod has broken off" else ""}"
    }
}

/** The figure's drawing, back to front: static and live layers, with the light between. */
private fun IsoScope.drawDial(state: DialState) {
    val scene = state.scene
    val c = state.controller
    for ((_, art) in scene.art.getValue("back")) drawArt(art)
    drawTurnLayer(c, "lock")
    for ((name, art) in scene.art.getValue("mid")) {
        if (name == "stand.index")
            withPalette(materialPalette(palette, Material.Brass)) { drawArt(art) }
        else drawArt(art)
    }
    drawTurnLayer(c, "dial")
    val on = state.on.x
    with(state.light) { drawPool(c, on, state.glClock) }
    val fallback =
        if (state.light.available) max(0.0, (1 - on) / (1 - on * 0.92 + 1e-6)).toFloat() else 1f
    drawTurnLayer(c, "hand", fallback)
    with(state.light) { drawLed(c, on, state.glClock) }
    for ((_, art) in scene.art.getValue("front")) drawArt(art)
}

@Composable
fun TurningDialFigure(
    palette: Palette,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    isOnScreen: () -> Boolean = { true },
) {
    val state = remember { DialState(TurningDial.scene, reducedMotion) }
    var frame by remember { mutableLongStateOf(0L) }
    var readout by remember { mutableStateOf(state.readout()) }
    var described by remember { mutableStateOf(state.describe()) }
    var touches by remember { mutableIntStateOf(0) }
    if (reducedMotion) state.touring = false
    val kick =
        rememberFrameLoop(reducedMotion, isOnScreen) { dt, calm ->
            val more = state.tick(dt, calm)
            frame = state.controller.generation
            readout = state.readout()
            described = state.describe()
            more
        }
    LaunchedEffect(touches, reducedMotion) {
        if (touches == 0) return@LaunchedEffect
        delay(IDLE_MS)
        if (!reducedMotion) {
            state.touring = true
            kick()
        }
    }
    fun hold() {
        state.touring = false
        touches++
        kick()
    }
    Plate(
        fig = "Fig 5",
        title = "Turning dial",
        hint = "Drag the dial · arrow keys · B breaks the pod",
        readout = { readout },
        palette = palette,
        modifier = modifier,
        keys =
            listOf(
                LegendKey(KeyMark.Raised, "The dial and the hand on it: one rigid group"),
                LegendKey(KeyMark.Lit, "The index"),
            ),
        caption =
            "The optional 3D mode: every part on the dial is re-projected and re-shaded each frame by " +
                "the kit's own rules, and painted in an order solved from separating planes.",
    ) {
        IsoCanvas(
            TurningDial.WIDTH,
            TurningDial.HEIGHT,
            palette,
            Modifier.fillMaxWidth()
                .semantics {
                    contentDescription = TURNING_DIAL_LABEL
                    stateDescription = described
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            val unit = size.width / TurningDial.WIDTH
                            val x = change.position.x / unit
                            val y = change.position.y / unit
                            when (event.type) {
                                PointerEventType.Press -> {
                                    hold()
                                    state.press(x, y)
                                }
                                PointerEventType.Move -> if (state.drag(x, y)) hold()
                                PointerEventType.Release -> state.dragFrom = null
                            }
                        }
                    }
                }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val step = if (event.isShiftPressed) 36.0 else 3.6
                    val snapped = Math.round(state.target / 3.6) * 3.6
                    val next =
                        when (event.key) {
                            Key.B -> {
                                state.toggleBreak()
                                kick()
                                return@onKeyEvent true
                            }
                            Key.DirectionRight,
                            Key.DirectionUp -> snapped - step
                            Key.DirectionLeft,
                            Key.DirectionDown -> snapped + step
                            Key.MoveHome ->
                                INDEX + 90 -
                                    360 * Math.round((INDEX + 90 - state.target) / 360).toDouble()
                            else -> return@onKeyEvent false
                        }
                    hold()
                    state.target = next
                    true
                }
                .focusable(),
        ) {
            // Reading the frame counter redraws the canvas on every render, and nothing else.
            if (frame >= 0) drawDial(state)
        }
    }
}
