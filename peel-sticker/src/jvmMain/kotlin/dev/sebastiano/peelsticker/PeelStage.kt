package dev.sebastiano.peelsticker

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.skia.Rect

/**
 * The sticker on its table. Drag it from an edge to peel it; let go and it lays itself back down.
 * Move over it and its face shines the [mode] way.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PeelStage(
    picture: StickerPicture,
    mode: ShineMode,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val fontFamily = JewelTheme.defaultTextStyle.fontFamily ?: FontFamily.Default
    // Printing takes a few hundred milliseconds, so it happens off the UI thread; the old sticker
    // stays until the new one is ready. A print nobody wants any more is closed.
    var printed by remember { mutableStateOf<StickerTexture?>(null) }
    LaunchedEffect(picture, fontFamily) {
        val texture =
            withContext(Dispatchers.Default + NonCancellable) {
                printSticker(picture, measurer, fontFamily)
            }
        if (isActive) printed = texture else texture.close()
    }
    // One stable node carries the tag, so whoever finds the stage early keeps the right bounds.
    Box(modifier.testTag(PeelStickerTags.STAGE)) {
        printed?.let { StickerStage(it, mode, focus, Modifier.fillMaxSize()) }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun StickerStage(
    texture: StickerTexture,
    mode: ShineMode,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val renderer = remember(texture) { StickerRenderer(texture) }
    DisposableEffect(renderer) {
        onDispose {
            renderer.close()
            texture.close()
        }
    }
    val peel = remember { PeelState() }
    val light = remember { LightState() }
    val scope = rememberCoroutineScope()

    val lit by remember { derivedStateOf { light.shine.value > 0f } }
    LaunchedEffect(lit, mode) {
        if (!lit || (mode != ShineMode.Sparkle && mode != ShineMode.Ripple)) return@LaunchedEffect
        val start = withFrameNanos { it } - (light.time * NANOS_PER_SECOND).toLong()
        while (true) withFrameNanos { light.time = (it - start) / NANOS_PER_SECOND }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val stageSize = remember { mutableStateOf(IntSize.Zero) }
    fun hover(position: Offset) {
        light.position = position
        val size = stageSize.value
        val layout = StageLayout.of(size.width.toFloat(), size.height.toFloat())
        val over = peel.grab != null || layout.hits(texture, position)
        scope.launch { light.follow(over) }
    }

    Spacer(
        modifier
            .onSizeChanged { stageSize.value = it }
            .focusRequester(focus)
            .focusable()
            .clipToBounds()
            .onPointerEvent(PointerEventType.Enter) { hover(it.changes.first().position) }
            .onPointerEvent(PointerEventType.Move) { hover(it.changes.first().position) }
            .onPointerEvent(PointerEventType.Exit) {
                if (peel.grab == null) scope.launch { light.follow(false) }
            }
            .pointerInput(texture) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    focus.requestFocus()
                    val layout = StageLayout.of(size.width.toFloat(), size.height.toFloat())
                    if (!layout.hits(texture, down.position, GrabSlop.toPx()))
                        return@awaitEachGesture
                    down.consume()
                    scope.launch { peel.hold(down.position) }
                    var change = down
                    while (change.pressed) {
                        change =
                            awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (change.pressed) {
                            peel.hand = change.position
                            light.position = change.position
                        }
                    }
                    scope.launch { peel.letGo() }
                }
            }
            .drawBehind {
                val layout = StageLayout.of(size.width, size.height)
                val fold =
                    peel.grab?.let { grab ->
                        val hand = peel.hand
                        val hold = peel.holding.value
                        peelFold(
                            grabX = grab.x,
                            grabY = grab.y,
                            pointerX = grab.x + (hand.x - grab.x) * hold,
                            pointerY = grab.y + (hand.y - grab.y) * hold,
                            extent = { dx, dy -> layout.extent(texture, dx, dy) },
                            size = layout.side,
                        )
                    }
                val frame =
                    StickerFrame(
                        originX = layout.originX,
                        originY = layout.originY,
                        texScale = layout.texScale,
                        fold = fold,
                        mode = mode,
                        pointerX = (light.position.x - layout.originX) * layout.texScale,
                        pointerY = (light.position.y - layout.originY) * layout.texScale,
                        shine = light.shine.value,
                        time = light.time,
                        density = density,
                    )
                drawIntoCanvas {
                    renderer.draw(it.skiaCanvas, Rect.makeWH(size.width, size.height), frame)
                }
            }
    )
}

/** A peel in hand: where the sticker was grabbed, where the hand is, and how firmly it holds. */
@Stable
private class PeelState {
    var grab by mutableStateOf<Offset?>(null)
    var hand by mutableStateOf(Offset.Zero)

    /** 1 while held; on release it springs to 0 and the sticker lays itself back down. */
    val holding = Animatable(0f)

    suspend fun hold(at: Offset) {
        grab = at
        hand = at
        holding.snapTo(1f)
    }

    suspend fun letGo() {
        holding.animateTo(0f, spring(dampingRatio = 1f, stiffness = RELEASE_STIFFNESS))
        grab = null
    }
}

/** Where the light is, and how much of it shows. The shine fades in over the sticker. */
@Stable
private class LightState {
    var position by mutableStateOf(Offset(-1e4f, -1e4f))
    var time by mutableFloatStateOf(0f)
    val shine = Animatable(0f)
    private var target = 0f

    suspend fun follow(over: Boolean) {
        val next = if (over) 1f else 0f
        if (next == target) return
        target = next
        shine.animateTo(
            next,
            if (over) tween(SHINE_IN_MILLIS, easing = LinearOutSlowInEasing)
            else tween(SHINE_OUT_MILLIS, easing = FastOutSlowInEasing),
        )
    }
}

/**
 * The sticker's place on a stage of this size: its die-cut is [side] stage pixels across, centred,
 * as big as in the original's window.
 */
private class StageLayout(
    val originX: Float,
    val originY: Float,
    val texScale: Float,
    val side: Float,
) {
    /** Whether [at] is on the sticker, or within [slop] stage pixels of its edge. */
    fun hits(texture: StickerTexture, at: Offset, slop: Float = 0f): Boolean {
        val cell = StickerTexture.CELL
        return texture.silhouette.near(
            (at.x - originX) * texScale / cell,
            (at.y - originY) * texScale / cell,
            slop * texScale / cell,
        )
    }

    fun extent(texture: StickerTexture, dx: Float, dy: Float): Float =
        originX * dx +
            originY * dy +
            texture.silhouette.extent(dx, dy) * StickerTexture.CELL / texScale

    companion object {
        /**
         * The texture lands on whole pixels, a whole number of them across, so the renderer can
         * copy the plain sticker instead of resampling it: on the CPU that is 60 times cheaper.
         */
        fun of(width: Float, height: Float): StageLayout {
            val fit = min(width * FIT_WIDTH, height * FIT_HEIGHT)
            val texturePixels =
                max(1f, (StickerTexture.SIZE * fit / StickerTexture.CUT).roundToInt().toFloat())
            val texScale = StickerTexture.SIZE / texturePixels
            val side = StickerTexture.CUT / texScale
            val originX = ((width - texturePixels) / 2f).roundToInt().toFloat()
            val originY = ((height - texturePixels) / 2f).roundToInt().toFloat()
            return StageLayout(originX, originY, texScale, side)
        }
    }
}

/** How far off the die-cut a press still grabs its edge. */
private val GrabSlop = 8.dp

private const val FIT_WIDTH = 0.484f
private const val FIT_HEIGHT = 0.708f
private const val RELEASE_STIFFNESS = 60f
private const val SHINE_IN_MILLIS = 260
private const val SHINE_OUT_MILLIS = 520
private const val NANOS_PER_SECOND = 1_000_000_000f
