package dev.sebastiano.holocard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.bioparco.tracing.Tracing
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Surface

/**
 * The table: one card in the middle, its shadow, and the dock. Hover tilts the card toward the
 * pointer, a drag turns it further and lifts it, and picking another card in the dock drops that
 * one in from above while the old one sinks away.
 */
@Composable
internal fun HoloCardStage(modifier: Modifier = Modifier) {
    val reliefs by
        produceState<Reliefs?>(null) { value = withContext(Dispatchers.Default) { Reliefs.bake() } }
    val motion = remember { HoloMotion() }
    val deck = remember { Deck() }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(motion) { motion.run() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun select(index: Int) {
        if (index == deck.current) return
        deck.outgoing = deck.current
        deck.current = index
        scope.launch {
            deck.swap.snapTo(0f)
            deck.swap.animateTo(1f, spring(dampingRatio = SWAP_DAMPING, stiffness = SWAP_STIFFNESS))
            deck.outgoing = null
        }
    }

    Box(
        modifier
            .testTag(HoloCardTags.STAGE)
            .drawWithCache {
                val cardWidth = CARD_WIDTH.toPx()
                val cardHeight = CARD_HEIGHT.toPx()
                val shadow =
                    shadowImage(cardWidth, cardHeight, CardCorner.toPx(), SHADOW_BLUR.toPx())
                val centre = Offset(size.width / 2f, (size.height - DOCK_ROOM.toPx()) / 2f)
                onDrawBehind {
                    drawRect(Table)
                    Tracing.section("card shadow") {
                        deck.outgoing?.let {
                            val p = deck.swap.value
                            drawCardShadow(
                                shadow,
                                centre,
                                Tilt.Flat,
                                0f,
                                (1f - p).coerceIn(0f, 1f),
                                lowered = p * cardHeight * OUTGOING_DROP,
                                scale = 1f - OUTGOING_SHRINK * p,
                            )
                        }
                        val p = deck.swap.value
                        drawCardShadow(
                            shadow,
                            centre,
                            Tilt(motion.tiltX, motion.tiltY),
                            motion.lift,
                            visibility = p.coerceIn(0f, 1f),
                            lowered = -(1f - p) * cardHeight * INCOMING_RISE,
                            scale = 1f,
                        )
                    }
                }
            }
            .pointerInput(motion) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        val centre = Offset(size.width / 2f, (size.height - DOCK_ROOM.toPx()) / 2f)
                        val half = Size(CARD_WIDTH.toPx() / 2f, CARD_HEIGHT.toPx() / 2f)
                        val offset = change.position - centre
                        when (event.type) {
                            PointerEventType.Press ->
                                if (!change.isConsumed && offset.isInside(half)) motion.grab(offset)
                            PointerEventType.Move ->
                                if (event.changes.any { it.pressed }) motion.drag(offset, half)
                                else motion.hover(offset, half)
                            PointerEventType.Release -> motion.release(offset, half)
                            PointerEventType.Exit -> motion.leave()
                        }
                    }
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val count = Friend.entries.size
                when (event.key) {
                    Key.DirectionRight -> select((deck.current + 1) % count)
                    Key.DirectionLeft -> select((deck.current + count - 1) % count)
                    Key.Spacebar -> motion.toggleAutopilot()
                    else -> return@onPreviewKeyEvent false
                }
                true
            }
            .focusRequester(focus)
            .focusable()
    ) {
        val cardModifier =
            Modifier.align(Alignment.Center)
                .offset(y = -DOCK_ROOM / 2)
                .size(CARD_WIDTH, CARD_HEIGHT)
        deck.outgoing?.let { index ->
            CardFace(
                friend = Friend.entries[index],
                reliefs = reliefs,
                tilt = { Tilt.Flat },
                modifier =
                    cardModifier.graphicsLayer {
                        val p = deck.swap.value
                        translationY = p * size.height * OUTGOING_DROP
                        scaleX = 1f - OUTGOING_SHRINK * p
                        scaleY = scaleX
                        rotationX = -OUTGOING_TIP * p
                        alpha = (1f - p).coerceIn(0f, 1f)
                        cameraDistance = CAMERA_DISTANCE * density
                    },
            )
        }
        CardFace(
            friend = Friend.entries[deck.current],
            reliefs = reliefs,
            tilt = { Tilt(motion.tiltX, motion.tiltY) },
            modifier =
                cardModifier.testTag(HoloCardTags.CARD).graphicsLayer {
                    val rest = 1f - deck.swap.value
                    translationY = -rest * size.height * INCOMING_RISE
                    rotationX = motion.tiltX + rest * INCOMING_TIP
                    rotationY = motion.tiltY
                    rotationZ = -rest * INCOMING_TWIST
                    scaleX = 1f + LIFT_SCALE * motion.lift
                    scaleY = scaleX
                    cameraDistance = CAMERA_DISTANCE * density
                },
        )
        Dock(
            current = deck.current,
            autopilot = motion.autopilot,
            reliefs = reliefs,
            onSelect = ::select,
            onToggleAutopilot = motion::toggleAutopilot,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
        )
    }
}

/** Which card is up, which one is on its way out, and how far the swap has got. */
private class Deck {
    var current by mutableIntStateOf(0)
    var outgoing by mutableStateOf<Int?>(null)
    val swap = Animatable(1f)
}

@Composable
private fun Dock(
    current: Int,
    autopilot: Boolean,
    reliefs: Reliefs?,
    onSelect: (Int) -> Unit,
    onToggleAutopilot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .background(Color.White, shape)
            .border(1.dp, Color(0x12000000), shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Friend.entries.forEachIndexed { index, friend ->
            Thumbnail(
                friend = friend,
                selected = index == current,
                reliefs = reliefs,
                onClick = { onSelect(index) },
                modifier = Modifier.testTag(HoloCardTags.thumbnail(index)),
            )
        }
        AutopilotButton(
            playing = autopilot,
            onClick = onToggleAutopilot,
            modifier = Modifier.testTag(HoloCardTags.AUTOPILOT),
        )
    }
}

@Composable
private fun Thumbnail(
    friend: Friend,
    selected: Boolean,
    reliefs: Reliefs?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(30.dp, 42.dp)
                .graphicsLayer {
                    val grown = if (selected) 1.08f else 1f
                    scaleX = grown
                    scaleY = grown
                    alpha = if (selected) 1f else 0.7f
                }
                .background(CardBlack, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            MascotFoil(friend, reliefs, tilt = { Tilt.Flat }, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.size(4.dp))
        Box(
            Modifier.size(4.dp)
                .background(if (selected) Color(0xFF1C1C20) else Color.Transparent, CircleShape)
        )
    }
}

@Composable
private fun AutopilotButton(playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(30.dp, 42.dp)
            .background(Color(0xFFF2F2F4), RoundedCornerShape(6.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(bottom = 8.dp)
            .drawBehind { if (playing) drawPause() else drawPlay() }
    )
}

private fun DrawScope.drawPlay() {
    val unit = size.minDimension / 10f
    val c = Offset(size.width / 2f + unit * 0.4f, size.height / 2f + unit * 2f)
    val triangle =
        Path().apply {
            moveTo(c.x - unit * 1.6f, c.y - unit * 2f)
            lineTo(c.x + unit * 1.8f, c.y)
            lineTo(c.x - unit * 1.6f, c.y + unit * 2f)
            close()
        }
    drawPath(triangle, Ink)
}

private fun DrawScope.drawPause() {
    val unit = size.minDimension / 10f
    val c = Offset(size.width / 2f, size.height / 2f + unit * 2f)
    for (x in floatArrayOf(-1.4f, 0.6f)) {
        drawRect(
            Ink,
            topLeft = Offset(c.x + x * unit, c.y - unit * 2f),
            size = Size(unit * 0.8f, unit * 4f),
        )
    }
}

/**
 * The card's soft shadow on the table, from a light above and to the left. It is one pre-blurred
 * image drawn on whole pixels with nearest sampling, so it costs a copy rather than a blur. It
 * narrows as the card turns, and slides further out and fades as the card lifts.
 */
private fun DrawScope.drawCardShadow(
    shadow: ImageBitmap,
    centre: Offset,
    tilt: Tilt,
    lift: Float,
    visibility: Float,
    lowered: Float,
    scale: Float,
) {
    if (visibility <= 0f) return
    val reach = 1f + SHADOW_LIFT_REACH * lift
    val dx = (SHADOW_X.toPx() + tilt.y * SHADOW_TURN.toPx()) * reach
    val dy = (SHADOW_Y.toPx() - tilt.x * SHADOW_TURN.toPx()) * reach + lowered
    val width = shadow.width * scale * cos(tilt.y * RADIANS)
    val height = shadow.height * scale * cos(tilt.x * RADIANS)
    drawImage(
        image = shadow,
        dstOffset =
            IntOffset(
                (centre.x + dx - width / 2f).roundToInt(),
                (centre.y + dy - height / 2f).roundToInt(),
            ),
        dstSize = IntSize(width.roundToInt(), height.roundToInt()),
        alpha = visibility * (1f - SHADOW_LIFT_FADE * lift).coerceIn(0f, 1f),
        filterQuality = FilterQuality.None,
    )
}

private fun shadowImage(width: Float, height: Float, corner: Float, blur: Float): ImageBitmap =
    Tracing.section("shadow image") {
        val pad = (blur * 3f).roundToInt()
        val surface =
            Surface.makeRasterN32Premul(width.roundToInt() + pad * 2, height.roundToInt() + pad * 2)
        val paint =
            Paint().apply {
                color = SHADOW_COLOR
                maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, blur)
            }
        surface.canvas.drawRRect(
            RRect.makeXYWH(pad.toFloat(), pad.toFloat(), width, height, corner),
            paint,
        )
        val image = surface.makeImageSnapshot().toComposeImageBitmap()
        paint.close()
        surface.close()
        image
    }

private fun Offset.isInside(half: Size) =
    x in -half.width..half.width && y in -half.height..half.height

private val Table = Color(0xFFF6F6F4)
private val Ink = Color(0xFF1C1C20)
private val CARD_WIDTH = 300.dp
private val CARD_HEIGHT = 430.dp
private val DOCK_ROOM = 96.dp
private val SHADOW_X = 26.dp
private val SHADOW_Y = 30.dp
private val SHADOW_TURN = 0.9.dp
private val SHADOW_BLUR = 16.dp
private const val SHADOW_COLOR = 0x47000000
private const val SHADOW_LIFT_REACH = 0.6f
private const val SHADOW_LIFT_FADE = 0.3f
private const val RADIANS = (PI / 180.0).toFloat()
private const val CAMERA_DISTANCE = 14f
private const val LIFT_SCALE = 0.035f
private const val SWAP_DAMPING = 0.66f
private const val SWAP_STIFFNESS = 160f

/** How far above its spot a new card starts, in card heights, and how it is tipped. */
private const val INCOMING_RISE = 1.9f
private const val INCOMING_TIP = 26f
private const val INCOMING_TWIST = 7f

/** How far the old card sinks, in card heights, and how much it shrinks and tips. */
private const val OUTGOING_DROP = 0.42f
private const val OUTGOING_SHRINK = 0.14f
private const val OUTGOING_TIP = 12f
