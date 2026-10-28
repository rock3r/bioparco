package dev.sebastiano.particlering

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.ui.component.Text

@Composable
internal fun ParticleRingStage(modifier: Modifier = Modifier) {
    val state = remember { RingStageState() }
    val focus = remember { FocusRequester() }

    LaunchedEffect(state) { while (true) withFrameNanos(state::step) }
    LaunchedEffect(state) { withContext(Dispatchers.Default) { state.warmUp() } }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        modifier
            .fillMaxSize()
            .background(RingPalette.background)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Spacebar,
                    Key.Enter -> state.advance()
                    else -> return@onKeyEvent false
                }
                true
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.testTag(RingTags.STAGE)
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds()
                .onSizeChanged(state::onSize)
                .pointerHoverIcon(PointerIcon.Hand)
                .pointerInput(state) { trackGestures(state) }
        ) {
            RingCanvas(state, Modifier.fillMaxSize())
            // Markers over the icon and the logo, so Spectre can find and click them.
            Box(
                Modifier.atRing(0f, ParticleRing.ICON_Y, ParticleRing.ICON_HALF * 2f)
                    .testTag(RingTags.ICON)
            )
            Box(Modifier.atRing(0f, 0f, ParticleRing.ARC_RADIUS * 2f).testTag(RingTags.LOGO))
            Caption(state, Modifier.atRing(0f, CAPTION_Y))
        }
        Text(
            "Click the icon to read. Drag the ring to stir and tilt it. Space steps through",
            color = RingPalette.hint,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 22.dp),
        )
    }
}

/**
 * A press and drag stirs and tilts the ring; a press that barely moves is a click. Nothing is
 * consumed.
 */
private suspend fun PointerInputScope.trackGestures(state: RingStageState) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        state.press()
        var last = down.position
        var travelled = 0f
        try {
            var change = nextChange(down.id)
            while (change != null && change.pressed) {
                val delta = change.position - last
                travelled += delta.getDistance()
                last = change.position
                state.dragBy(delta)
                change = nextChange(down.id)
            }
        } finally {
            state.release()
        }
        if (travelled < viewConfiguration.touchSlop) state.onTap(down.position)
    }
}

/** The next change of pointer [id], or null if it has gone. */
private suspend fun AwaitPointerEventScope.nextChange(id: PointerId): PointerInputChange? =
    awaitPointerEvent().changes.firstOrNull { it.id == id }

/**
 * Places the content centred on (`x`, `y`) in ring units, square and [side] units across if given,
 * else at its own size. It reads the stage's size at layout, so it moves only on a resize.
 */
private fun Modifier.atRing(x: Float, y: Float, side: Float? = null): Modifier =
    layout { measurable, constraints ->
        val unit = ringUnit(constraints.maxWidth, constraints.maxHeight)
        val centre = ringCentre(constraints.maxWidth, constraints.maxHeight)
        val placeable =
            measurable.measure(
                if (side != null)
                    Constraints.fixed((side * unit).roundToInt(), (side * unit).roundToInt())
                else constraints.copy(minWidth = 0, minHeight = 0)
            )
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(
                (centre.x + x * unit - placeable.width / 2f).roundToInt(),
                (centre.y + y * unit - placeable.height / 2f).roundToInt(),
            )
        }
    }

/**
 * Every caption, laid out once and stacked. A new caption only changes their layers' alphas, which
 * the stage steps each frame: no text is laid out and nothing recomposes on the frame the particles
 * set off. Fading them with `animateFloatAsState`, and moving the test tag between them, cost 14 to
 * 22 ms on the first frame of every burst.
 */
@Composable
private fun Caption(state: RingStageState, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Captions.forEachIndexed { i, text ->
            val done = i == Captions.lastIndex
            Text(
                text,
                color = if (done) RingPalette.captionDone else RingPalette.caption,
                fontSize = if (done) 17.sp else 13.sp,
                fontWeight = if (done) FontWeight.SemiBold else FontWeight.Normal,
                modifier =
                    Modifier.graphicsLayer {
                        // Read in the layer phase, so each frame updates the layer, nothing more.
                        state.frame.longValue
                        alpha = state.captionAlpha[i]
                    },
            )
        }
        CaptionSemantics(state, Modifier.matchParentSize())
    }
}

/** What Spectre reads: one small node whose text is the caption on show. */
@Composable
private fun CaptionSemantics(state: RingStageState, modifier: Modifier = Modifier) {
    val shown by state.caption
    Box(
        modifier.testTag(RingTags.CAPTION).clearAndSetSemantics {
            if (shown >= 0) text = AnnotatedString(Captions[shown])
        }
    )
}

/** Where the captions sit, in ring units: between the ring and the icon. */
private const val CAPTION_Y = 1.32f
