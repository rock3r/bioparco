// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sebastiano.bioparco.tracing.Tracing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One hairline figure: an isometric line drawing that answers the pointer, at a 5:4 aspect ratio.
 *
 * [intensity] is how strongly it answers, 0 (subtle) to 1 (strong); each figure maps it onto its
 * own number. [dark] picks the dark palette. [reducedMotion] lands every spring and tween at once
 * and freezes ambient motion. [onRead] gets the figure's caption each time it changes.
 */
@Composable
fun HairlineFigure(
    figure: Figure,
    modifier: Modifier = Modifier,
    intensity: Float = DEFAULT_INTENSITY.toFloat(),
    dark: Boolean = false,
    reducedMotion: Boolean = false,
    onRead: (String) -> Unit = {},
) {
    val currentOnRead by rememberUpdatedState(onRead)
    val scope = rememberCoroutineScope()
    val stage = remember(figure) { ComposeStage(scope) }
    DisposableEffect(stage) {
        stage.reduced = reducedMotion
        val mounted = stage.inside {
            MountedFigure(figure, stage, intensity.toDouble()) { currentOnRead(it) }
        }
        stage.mounted = mounted
        stage.invalidate()
        onDispose {
            mounted.destroy()
            stage.dispose()
        }
    }
    LaunchedEffect(stage, intensity) {
        stage.inside { stage.mounted?.update(intensity.toDouble()) }
    }
    LaunchedEffect(stage, reducedMotion) {
        stage.reduced = reducedMotion
        stage.wakeAll()
    }
    LaunchedEffect(stage) { stage.runFrames() }
    val palette = if (dark) HairlinePalette.Dark else HairlinePalette.Light
    val focus = remember { FocusRequester() }
    Spacer(
        modifier
            .aspectRatio(5f / 4f)
            .semantics { contentDescription = figure.label }
            .then(if (figure.focusable) Modifier.keys(stage, focus) else Modifier)
            .pointerInput(stage) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        val p =
                            Vec2(
                                change.position.x * 400.0 / size.width,
                                change.position.y * 320.0 / size.height,
                            )
                        stage.handle(event.type, change.type, p, figure.focusable, focus)
                    }
                }
            }
            .drawBehind {
                stage.redraws
                val root = stage.mounted?.svg ?: return@drawBehind
                val moving =
                    Tracing.section("hairline.draw") { drawHairline(root, palette, stage.now()) }
                if (moving) stage.kick()
                if (stage.focusVisible) {
                    // the original's :focus-visible outline: 1.5px of the bright ink, 2px out
                    val out = 2.dp.toPx()
                    drawRoundRect(
                        palette.hi,
                        topLeft = Offset(-out, -out),
                        size = Size(size.width + 2 * out, size.height + 2 * out),
                        cornerRadius = CornerRadius(out),
                        style = Stroke(1.5.dp.toPx()),
                    )
                }
            }
    )
}

/** Arrow keys and Escape for a focusable figure, and blur when focus leaves. */
private fun Modifier.keys(stage: ComposeStage, focus: FocusRequester): Modifier =
    focusRequester(focus)
        .onFocusChanged { state ->
            if (stage.focused && !state.isFocused) stage.blur()
            stage.focused = state.isFocused
        }
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            val name = DOM_KEYS[event.key] ?: return@onKeyEvent false
            stage.focusFromPointer = false
            stage.key(name)
        }
        .focusable()

private val DOM_KEYS =
    mapOf(
        Key.DirectionLeft to "ArrowLeft",
        Key.DirectionRight to "ArrowRight",
        Key.DirectionUp to "ArrowUp",
        Key.DirectionDown to "ArrowDown",
        Key.Escape to "Escape",
    )

/**
 * The host behind a figure on screen: a monotonic clock, frames from Compose, and pointer input.
 */
private class ComposeStage(private val scope: CoroutineScope) : Stage {
    private val origin = System.nanoTime()
    private val wakeups = Channel<Unit>(Channel.CONFLATED)
    private val loop = FrameLoop(::now) { kick() }
    private val pointers = ArrayList<PointerHandlers>()
    private val keyHandlers = ArrayList<(String) -> Boolean>()
    private val blurHandlers = ArrayList<() -> Unit>()
    private var leaving: Job? = null
    private var redrawCount by mutableIntStateOf(0)

    var mounted: MountedFigure? = null

    /** This figure's reduced motion. Every call into the figure runs [inside] it. */
    var reduced = false

    var focused by mutableStateOf(false)
    var focusFromPointer by mutableStateOf(false)

    /** Read in the draw phase, so a change redraws without recomposing. */
    val redraws: Int
        get() = redrawCount

    val focusVisible: Boolean
        get() = focused && !focusFromPointer

    override fun now(): Double = (System.nanoTime() - origin) / 1_000_000.0

    override fun register(tick: Tick): Loop = loop.register(tick)

    override fun pointer(handlers: PointerHandlers): () -> Unit {
        pointers.add(handlers)
        return { pointers.remove(handlers) }
    }

    override fun onKey(handler: (String) -> Boolean): () -> Unit {
        keyHandlers.add(handler)
        return { keyHandlers.remove(handler) }
    }

    override fun onBlur(handler: () -> Unit): () -> Unit {
        blurHandlers.add(handler)
        return { blurHandlers.remove(handler) }
    }

    /**
     * Runs [block], a call into the figure, with the flag the motion maths reads set to this
     * figure's.
     */
    fun <T> inside(block: () -> T): T = withReducedMotion(reduced, block)

    fun invalidate() {
        redrawCount++
    }

    /** Asks for a frame: after input, or while a colour transition runs. */
    fun kick() {
        wakeups.trySend(Unit)
    }

    fun wakeAll() {
        loop.wakeAll()
        invalidate()
    }

    fun key(name: String): Boolean {
        val claimed = inside { keyHandlers.toList().fold(false) { any, h -> h(name) || any } }
        invalidate()
        return claimed
    }

    fun blur() {
        inside { blurHandlers.toList().forEach { it() } }
        invalidate()
    }

    fun dispose() {
        leaving?.cancel()
        wakeups.close()
    }

    /** Frames while the loop or a colour transition wants them; asleep otherwise. */
    suspend fun runFrames() {
        while (true) {
            withFrameNanos {}
            if (loop.running) Tracing.section("hairline.tick") { inside { loop.frame(now()) } }
            invalidate()
            if (!loop.running) wakeups.receiveCatching().getOrNull() ?: return
        }
    }

    /**
     * The original's pointer, in viewBox units: a mouse leaving acts at once, a finger lifting
     * holds the pose for 1.4s first.
     */
    fun handle(
        type: PointerEventType,
        pointer: PointerType,
        p: Vec2,
        focusable: Boolean,
        focus: FocusRequester,
    ) {
        when (type) {
            PointerEventType.Move,
            PointerEventType.Enter -> {
                leaving?.cancel()
                inside { pointers.toList().forEach { it.move(p) } }
            }
            PointerEventType.Press -> {
                leaving?.cancel()
                if (focusable) {
                    focusFromPointer = true
                    focus.requestFocus()
                }
                inside { pointers.toList().forEach { it.down(p) } }
            }
            PointerEventType.Exit -> leave(pointer)
            PointerEventType.Release -> if (pointer != PointerType.Mouse) leave(pointer)
            else -> return
        }
        invalidate()
    }

    private fun leave(pointer: PointerType) {
        leaving?.cancel()
        leaving = scope.launch {
            if (pointer != PointerType.Mouse) delay(TOUCH_HOLD_MS)
            inside { pointers.toList().forEach { it.leave() } }
            invalidate()
        }
    }

    private companion object {
        const val TOUCH_HOLD_MS = 1400L
    }
}
