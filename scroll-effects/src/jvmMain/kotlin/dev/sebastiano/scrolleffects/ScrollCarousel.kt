package dev.sebastiano.scrolleffects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * The carousel stage. Cards follow [state]; [effect] decides how they bend, break or scatter away
 * from the centre. Switching effect cross-fades between the two.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ScrollCarousel(effect: CarouselEffect, state: CarouselState, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val fontFamily = JewelTheme.defaultTextStyle.fontFamily ?: FontFamily.Default
    val renderer =
        remember(density, fontFamily) {
            val grain = grainTile()
            CarouselRenderer(
                sampleCards.map { renderCardArt(it, density, measurer, fontFamily, grain) }
            )
        }
    DisposableEffect(renderer) { onDispose { renderer.close() } }
    val scene = remember { CarouselScene(sampleCards.size) }
    val outgoingScene = remember { CarouselScene(sampleCards.size) }
    val clock = remember { StageClock() }
    val focus = remember { FocusRequester() }
    var shown by remember { mutableStateOf(effect) }
    var outgoing by remember { mutableStateOf<CarouselEffect?>(null) }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(effect) {
        if (effect == shown) return@LaunchedEffect
        outgoing = shown
        shown = effect
        fade.snapTo(0f)
        fade.animateTo(1f, tween(durationMillis = CROSSFADE_MILLIS, easing = FastOutSlowInEasing))
        outgoing = null
    }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        clock.run { state.position }
    }

    Spacer(
        modifier
            .testTag(ScrollEffectsTags.STAGE)
            .focusRequester(focus)
            .focusable()
            .pointerInput(state) {
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = {
                        focus.requestFocus()
                        tracker.resetTracking()
                        state.dragStarted()
                    },
                    onDragEnd = {
                        val pitch =
                            stageFor(size.width.toFloat(), size.height.toFloat(), this).pitch
                        state.dragEnded(-tracker.calculateVelocity().x / pitch)
                    },
                    onDragCancel = { state.dragEnded(0f) },
                ) { change, dragAmount ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    val pitch = stageFor(size.width.toFloat(), size.height.toFloat(), this).pitch
                    state.dragBy(dragAmount / pitch)
                }
            }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val delta = event.changes.first().scrollDelta
                state.scroll(if (abs(delta.x) > abs(delta.y)) delta.x else delta.y)
            }
            .graphicsLayer {
                // Bulge and Drum spin fast enough to smear, like the original.
                val blurs = shown == CarouselEffect.Bulge || shown == CarouselEffect.Drum
                val radius =
                    if (blurs) (abs(clock.velocity) * MOTION_BLUR).coerceAtMost(MAX_BLUR) else 0f
                renderEffect =
                    if (radius > MIN_BLUR) BlurEffect(radius.dp.toPx(), 0f, TileMode.Decal)
                    else null
            }
            .drawBehind {
                val stage = stageFor(size, this)
                val position = state.position
                val time = clock.time
                val previous = outgoing
                val progress = fade.value
                if (previous != null && progress < 1f) {
                    drawFaded(1f - progress) {
                        renderer.draw(
                            it,
                            outgoingScene.layout(previous, stage.geometry, position, time),
                            stage.geometry,
                        )
                    }
                    drawFaded(progress) {
                        renderer.draw(
                            it,
                            scene.layout(shown, stage.geometry, position, time),
                            stage.geometry,
                        )
                    }
                } else {
                    drawIntoCanvas {
                        renderer.draw(
                            it.nativeCanvas,
                            scene.layout(shown, stage.geometry, position, time),
                            stage.geometry,
                        )
                    }
                }
            }
    )
}

private inline fun DrawScope.drawFaded(
    alpha: Float,
    crossinline block: (org.jetbrains.skia.Canvas) -> Unit,
) {
    drawIntoCanvas { canvas ->
        canvas.saveLayer(
            androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height),
            Paint().apply { this.alpha = alpha },
        )
        block(canvas.nativeCanvas)
        canvas.restore()
    }
}

/** The stage geometry for a canvas of [size], with the card scaled down to fit a small window. */
private class StageLayout(val geometry: CarouselStage) {
    /** How far a drag must travel to move one slot. */
    val pitch: Float
        get() = geometry.cardWidth * DRAG_PITCH
}

private fun stageFor(size: Size, density: Density): StageLayout =
    stageFor(size.width, size.height, density)

private fun stageFor(width: Float, height: Float, density: Density): StageLayout {
    val cardWidth = with(density) { CardWidth.toPx() } + TEXTURE_PADDING * 2
    val cardHeight = with(density) { CardHeight.toPx() } + TEXTURE_PADDING * 2
    val fit = min(1f, min(height * FIT_HEIGHT / cardHeight, width * FIT_WIDTH / cardWidth))
    return StageLayout(CarouselStage(width, height, cardWidth * fit, cardHeight * fit))
}

/** Seconds since the stage appeared, and how fast the carousel is moving in slots per second. */
private class StageClock {
    var time by mutableFloatStateOf(0f)
        private set

    var velocity by mutableFloatStateOf(0f)
        private set

    suspend fun run(position: () -> Float) {
        val start = withFrameNanos { it }
        var lastNanos = start
        var lastPosition = position()
        while (true) {
            withFrameNanos { now ->
                val dt = (now - lastNanos) / NANOS_PER_SECOND
                val current = position()
                if (dt > 0f) {
                    val instant = (current - lastPosition) / dt
                    velocity += (instant - velocity) * VELOCITY_SMOOTHING
                }
                time = (now - start) / NANOS_PER_SECOND
                lastNanos = now
                lastPosition = current
            }
        }
    }
}

private const val CROSSFADE_MILLIS = 320
private const val DRAG_PITCH = 1.15f
private const val FIT_HEIGHT = 0.66f
private const val FIT_WIDTH = 0.36f
private const val MOTION_BLUR = 2.2f
private const val MAX_BLUR = 14f
private const val MIN_BLUR = 0.4f
private const val NANOS_PER_SECOND = 1_000_000_000f
private const val VELOCITY_SMOOTHING = 0.35f
