// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.IsoCanvas
import dev.sebastiano.anatomy.kit.Palette
import dev.sebastiano.anatomy.kit.Plate
import dev.sebastiano.anatomy.kit.focusOnPress
import dev.sebastiano.anatomy.kit.rememberFrameLoop
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * The live Raptor, after the original's live.js: hover (or the arrow keys, or the legend) takes
 * the engine apart into its eleven assemblies, one spring each in a staggered order, re-sorting
 * the painter's order every frame; I, Enter or the Ignite button fires it. A tour runs until the
 * viewer touches it and comes back after 3.6 s of quiet. The loop writes plain state and bumps a
 * frame counter only the canvas reads, so motion redraws and never recomposes.
 */

private const val IDLE_MS = 3_600L
private const val LEAVE_MS = 260L

/** What the figure's controls change, apart from the motion: the text it shows and says. */
internal class RaptorTexts {
    var readout by mutableStateOf("1,630 kg · cold")
    var describe by mutableStateOf("Assembled, cold")
    var phase by mutableStateOf("")
    var selected by mutableStateOf<String?>(null)
    var firing by mutableStateOf(false)
    var armed by mutableStateOf(false)
    var valueNow by mutableIntStateOf(0)

    fun update(live: RaptorLive) {
        readout = live.readout()
        describe = live.describe()
        if (live.burn.phase.say.isNotEmpty()) phase = live.burn.phase.say
        selected = live.selected
        firing = live.burn.on
        armed = live.burn.armed
        valueNow = live.valueNow
    }
}

@Composable
fun RaptorEngineFigure(
    palette: Palette,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    isOnScreen: () -> Boolean = { true },
) {
    val pieces = remember { RaptorEngine.BUILD.pieces.mapValues { it.value.say } }
    val live = remember { RaptorLive(pieces = pieces) }
    val nodes = RaptorEngine.NODES
    val order = remember { IntArray(nodes.size) { it } }
    val hits = remember { RaptorHits(nodes) }
    val layers = remember { RaptorLayers.make() }
    val palettes = remember(palette) { RaptorPalettes(palette) }
    val texts = remember { RaptorTexts() }
    var frame by remember { mutableLongStateOf(0L) }
    var touches by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val leave = remember { arrayOfNulls<Job>(1) }
    live.still = reducedMotion
    if (reducedMotion) live.touring = false
    val kick =
        rememberFrameLoop(reducedMotion, isOnScreen, trace = "raptor-engine tick") { dt, calm ->
            val more = live.tick(dt, calm)
            live.paintOrder(nodes, order)
            frame++
            texts.update(live)
            more
        }
    LaunchedEffect(touches, reducedMotion) {
        if (touches == 0) return@LaunchedEffect
        delay(IDLE_MS)
        if (live.inside || live.burn.on) {
            touches++
        } else if (!reducedMotion) {
            live.touring = true
            live.tourClock = -0.6
            kick()
        }
    }
    val controls =
        RaptorControls(
            live,
            quiet = {
                live.touring = false
                touches++
            },
            kick = kick,
            later = { block ->
                leave[0]?.cancel()
                leave[0] = scope.launch {
                    delay(LEAVE_MS)
                    block()
                    kick()
                }
            },
            cancel = { leave[0]?.cancel() },
        )
    BoxWithConstraints(modifier) {
        RaptorPlate(
            live,
            order,
            hits,
            layers,
            palettes,
            palette,
            texts,
            controls,
            maxWidth < NARROW,
        ) {
            frame
        }
    }
}

/** Under 480 dp the original assumes a finger: "Tap to take apart". */
private val NARROW = 480.dp

@Composable
private fun RaptorPlate(
    live: RaptorLive,
    order: IntArray,
    hits: RaptorHits,
    layers: RaptorLayers,
    palettes: RaptorPalettes,
    palette: Palette,
    texts: RaptorTexts,
    controls: RaptorControls,
    narrow: Boolean,
    frame: () -> Long,
) {
    Column {
        Plate(
            fig = "Fig 6",
            title = "Raptor 2 · test stand",
            hint = if (narrow) "Tap to take apart" else "Hover the engine to take it apart",
            readout = { texts.readout },
            palette = palette,
        ) {
            RaptorCanvas(live, order, hits, layers, palettes, palette, texts, controls, frame)
        }
        RaptorLegend(texts, palette, controls, Modifier.padding(top = 12.dp))
    }
}

/** What the canvas, the keys and the legend do to the live figure. */
internal class RaptorControls(
    val live: RaptorLive,
    val quiet: () -> Unit,
    val kick: () -> Unit,
    val later: (() -> Unit) -> Unit,
    val cancel: () -> Unit,
) {
    /** The pointer over [piece] (or over nothing): apart, and that assembly lit. */
    fun hover(piece: String?) {
        live.inside = true
        cancel()
        quiet()
        if (live.burn.on) return
        live.setApart(true)
        live.select(piece)
        kick()
    }

    /** The pointer leaving the figure or a legend button: back together, unless it comes back. */
    fun leave() {
        live.inside = false
        later {
            if (!live.inside) {
                live.select(null)
                live.setApart(false)
            }
        }
    }

    /** A finger or pen down on [piece] (or on nothing): what hovering does with a mouse. */
    fun tap(piece: String?) = hover(piece)

    /**
     * The finger lifted. Unlike a mouse leaving, it doesn't close the engine: it stays apart until
     * the idle tour takes over again.
     */
    fun lift() {
        live.inside = false
    }

    /** A legend button hovered, focused or clicked. */
    fun show(part: String) {
        if (live.burn.on) return
        quiet()
        cancel()
        live.setApart(true)
        live.select(part)
        kick()
    }

    fun fire() {
        quiet()
        live.toggleFire()
        kick()
    }

    /** The original's stage keys. */
    fun key(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val last = Raptor.ORDER.size - 1
        when (event.key) {
            Key.DirectionRight,
            Key.DirectionDown -> move(1, null)
            Key.DirectionLeft,
            Key.DirectionUp -> move(-1, null)
            Key.MoveHome -> move(null, 0)
            Key.MoveEnd -> move(null, last)
            Key.Escape -> {
                quiet()
                if (live.burn.on) live.burn.shutdown()
                live.select(null)
                live.setApart(false)
            }
            Key.I,
            Key.Enter -> fire()
            Key.Spacebar -> {
                quiet()
                if (!live.burn.on) live.setApart(!live.apart)
                if (!live.apart) live.select(null)
            }
            else -> return false
        }
        kick()
        return true
    }

    private fun move(delta: Int?, to: Int?) {
        if (live.burn.on) return
        quiet()
        live.step(delta, to)
    }
}

@Composable
private fun RaptorCanvas(
    live: RaptorLive,
    order: IntArray,
    hits: RaptorHits,
    layers: RaptorLayers,
    palettes: RaptorPalettes,
    palette: Palette,
    texts: RaptorTexts,
    controls: RaptorControls,
    frame: () -> Long,
) {
    Box {
        IsoCanvas(
            Raptor.W,
            Raptor.H,
            palette,
            Modifier.fillMaxWidth()
                .semantics {
                    contentDescription = "${RaptorEngine.STAGE} ${RaptorEngine.LABEL}"
                    stateDescription = texts.describe
                    progressBarRangeInfo =
                        ProgressBarRangeInfo(
                            texts.valueNow.toFloat(),
                            0f..Raptor.ORDER.size.toFloat(),
                        )
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val unit = size.width / Raptor.W
                            val change = event.changes.first()
                            val mouse = change.type == PointerType.Mouse
                            fun pieceUnder(): String? {
                                val at = Flat(change.position.x / unit, change.position.y / unit)
                                return hits.pieceAt(at, order, RaptorEngine.NODES) {
                                    live.springs.getValue(it).x
                                }
                            }
                            // A tap has no hover, so a touch or pen press does what hovering does.
                            when (event.type) {
                                PointerEventType.Enter,
                                PointerEventType.Move -> controls.hover(pieceUnder())
                                PointerEventType.Press -> if (!mouse) controls.tap(pieceUnder())
                                PointerEventType.Release -> if (!mouse) controls.lift()
                                PointerEventType.Exit -> if (mouse) controls.leave()
                            }
                        }
                    }
                }
                .onKeyEvent(controls::key)
                .focusOnPress()
                .focusable(),
            clock = { live.clock },
            trace = "raptor-engine draw",
        ) {
            if (frame() >= 0)
                drawRaptor(live, order, palettes, layers, if (live.still) 0.8 else live.clock)
        }
        Box(
            Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = texts.phase
            }
        )
    }
}
