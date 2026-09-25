package chatbubble

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.Text

internal val BubbleFill = Color(0xFFE5E5EA)
internal val ChatBackground = Color(0xFFFFFFFF)
internal val MessageInk = Color(0xFF111111)
internal val PlaceholderInk = Color(0xFF8E8E93)
internal val SendIdleFill = Color(0xFFD1D1D6)
internal val SendIdleArrow = Color(0xFF6E6E73)
internal val SendActiveFill = Color(0xFF007AFF)
internal val ComposerFieldFill = Color(0xFFFFFFFF)
internal val ComposerFieldBorder = Color(0xFFC7C7CC)

internal val BubbleRadius = 20.dp
internal val BubbleTailRadius = 6.dp
internal val ComposerPillRadius = 22.dp
internal val MessageGap = 8.dp

internal val MessageTextStyle = TextStyle(color = MessageInk, fontSize = 16.sp, lineHeight = 21.sp)

private const val FLIGHT_DURATION_MS = 340
/** Softer than FastOutSlowIn — less of a launch punch at t≈0. */
private val FLIGHT_EASING = CubicBezierEasing(0.33f, 0.00f, 0.20f, 1.00f)

data class ChatMessage(val id: Long, val text: String)

data class Flight(val id: Long, val text: String, val start: Rect)

/**
 * Kavsoft-style send: measure composer chrome as start, the actual clipped bubble as destination,
 * then morph an overlay along a quadratic Bezier arc. No SharedTransitionLayout — overlay-only
 * keeps Desktop reliable.
 */
@Composable
fun ChatApp(modifier: Modifier = Modifier) {
    // Jewel supplies the text defaults; the chat keeps its own explicit colours and styles.
    IntUiTheme(isDark = false) { ChatScene(modifier) }
}

@Composable
private fun ChatScene(modifier: Modifier = Modifier) {
    val messages = remember {
        mutableStateListOf(
            ChatMessage(1, "Hey, I'm Justine"),
            ChatMessage(2, "This is Chat Bubble Transition Using SwiftUI!"),
            ChatMessage(3, "Hello world"),
        )
    }
    var draft by remember { mutableStateOf("") }
    var nextId by remember { mutableLongStateOf(4L) }
    var flight by remember { mutableStateOf<Flight?>(null) }
    var destination by remember { mutableStateOf<Rect?>(null) }
    var composerBounds by remember { mutableStateOf(Rect.Zero) }
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    fun send() {
        val text = draft.trim()
        if (text.isEmpty()) return
        if (flight != null) return
        val id = nextId
        nextId = id + 1
        val start = composerBounds
        draft = ""
        destination = null
        messages.add(0, ChatMessage(id, text))
        flight = Flight(id, text, start)
        scope.launch {
            progress.snapTo(0f)
            // Full list: reverseLayout can leave the new item off-screen until a
            // manual scroll — pin to index 0 so it composes and the flight can arm.
            listState.scrollToItem(0)
        }
    }

    Box(
        modifier =
            modifier.fillMaxSize().background(ChatBackground).onGloballyPositioned {
                rootCoordinates = it
            }
    ) {
        Column(Modifier.fillMaxSize().imePadding()) {
            MessageList(
                messages = messages,
                listState = listState,
                pendingFlightId = flight?.id,
                rootCoordinates = rootCoordinates,
                onDestination = { id, rect ->
                    if (flight?.id == id) {
                        val firstFix = destination == null
                        destination = rect
                        if (firstFix) {
                            scope.launch {
                                progress.animateTo(
                                    targetValue = 1f,
                                    animationSpec =
                                        tween(
                                            durationMillis = FLIGHT_DURATION_MS,
                                            easing = FLIGHT_EASING,
                                        ),
                                )
                                flight = null
                                destination = null
                                progress.snapTo(0f)
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            ComposerBar(
                draft = draft,
                onDraftChange = { draft = it },
                onSend = ::send,
                onComposerBounds = { composerBounds = it },
                rootCoordinates = rootCoordinates,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // animateItem temporarily raises list-item zIndex — keep the morph above it.
        Box(Modifier.fillMaxSize().zIndex(10f)) {
            FlightOverlay(flight = flight, destination = destination, progress = progress.value)
        }
    }
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    listState: LazyListState,
    pendingFlightId: Long?,
    rootCoordinates: LayoutCoordinates?,
    onDestination: (Long, Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    // reverseLayout packs to the bottom; animateItem slides older bubbles up in sync
    // with the flight (same duration/easing) — Kavsoft "make room" feel.
    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(MessageGap),
    ) {
        items(messages, key = { it.id }) { message ->
            val flying = pendingFlightId == message.id
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .animateItem(
                            fadeInSpec = null,
                            fadeOutSpec = null,
                            placementSpec =
                                tween(durationMillis = FLIGHT_DURATION_MS, easing = FLIGHT_EASING),
                        ),
                contentAlignment = Alignment.CenterEnd,
            ) {
                ChatBubble(
                    text = message.text,
                    hidden = flying,
                    modifier =
                        Modifier.widthIn(max = 280.dp).onGloballyPositioned { coordinates ->
                            if (flying) {
                                val root = rootCoordinates ?: return@onGloballyPositioned
                                onDestination(message.id, coordinates.boundsIn(root))
                            }
                        },
                )
            }
        }
    }
}

@Composable
private fun ComposerBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onComposerBounds: (Rect) -> Unit,
    rootCoordinates: LayoutCoordinates?,
    modifier: Modifier = Modifier,
) {
    val canSend = draft.isNotBlank()

    Row(
        modifier = modifier.padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ComposerField(
            draft = draft,
            onDraftChange = onDraftChange,
            onSend = onSend,
            onBounds = onComposerBounds,
            rootCoordinates = rootCoordinates,
            modifier = Modifier.weight(1f),
        )
        SendButton(enabled = canSend, onClick = onSend)
    }
}

@Composable
private fun ComposerField(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onBounds: (Rect) -> Unit,
    rootCoordinates: LayoutCoordinates?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(ComposerPillRadius)
    BasicTextField(
        value = draft,
        onValueChange = onDraftChange,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .testTag(ChatTags.COMPOSER)
                .onGloballyPositioned { coordinates ->
                    val root = rootCoordinates ?: return@onGloballyPositioned
                    onBounds(coordinates.boundsIn(root))
                }
                .clip(shape)
                .background(ComposerFieldFill, shape)
                .border(1.dp, ComposerFieldBorder, shape)
                .padding(horizontal = 16.dp, vertical = 11.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                        if (event.isShiftPressed) {
                            false
                        } else {
                            onSend()
                            true
                        }
                    } else {
                        false
                    }
                },
        textStyle = MessageTextStyle,
        cursorBrush = SolidColor(SendActiveFill),
        singleLine = false,
        maxLines = 6,
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Send,
            ),
        keyboardActions = KeyboardActions(onSend = { onSend() }),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (draft.isEmpty()) {
                    Text(
                        text = "Type a message",
                        style = MessageTextStyle.copy(color = PlaceholderInk),
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun ChatBubble(text: String, modifier: Modifier = Modifier, hidden: Boolean = false) {
    val shape = SentBubbleShape
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(if (hidden) Color.Transparent else BubbleFill, shape)
                .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            text = text,
            style = MessageTextStyle,
            color = if (hidden) Color.Transparent else MessageInk,
        )
    }
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val fill = if (enabled) SendActiveFill else SendIdleFill
    val arrow = if (enabled) Color.White else SendIdleArrow
    Box(
        modifier =
            Modifier.size(34.dp)
                .clip(CircleShape)
                .background(fill)
                .testTag(ChatTags.SEND)
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        UpArrow(color = arrow)
    }
}

@Composable
private fun UpArrow(color: Color) {
    Canvas(Modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        val path =
            Path().apply {
                moveTo(w * 0.50f, h * 0.12f)
                lineTo(w * 0.86f, h * 0.58f)
                lineTo(w * 0.64f, h * 0.58f)
                lineTo(w * 0.64f, h * 0.88f)
                lineTo(w * 0.36f, h * 0.88f)
                lineTo(w * 0.36f, h * 0.58f)
                lineTo(w * 0.14f, h * 0.58f)
                close()
            }
        drawPath(path, color)
    }
}

@Composable
private fun FlightOverlay(flight: Flight?, destination: Rect?, progress: Float) {
    // Draw immediately at the composer bounds so text never blinks out while we wait
    // for the list to measure the real destination bubble.
    if (flight == null) return
    val start = flight.start
    val end = destination ?: start
    val t = if (destination == null) 0f else progress.coerceIn(0f, 1f)

    // Right-anchored flight: Bezier tracks the trailing (right) edge so a still-wide
    // morph never overshoots the message margin the way center-anchoring did.
    val startRight = Offset(start.right, start.top + start.height / 2f)
    val endRight = Offset(end.right, end.top + end.height / 2f)
    val mid = Offset((startRight.x + endRight.x) / 2f, (startRight.y + endRight.y) / 2f)
    val maxRight = maxOf(startRight.x, endRight.x)
    val control =
        Offset(
            x =
                (mid.x + (kotlin.math.abs(endRight.x - startRight.x) * 0.35f).coerceAtLeast(56f))
                    .coerceAtMost(maxRight),
            y = mid.y + (kotlin.math.abs(endRight.y - startRight.y) * 0.28f).coerceAtLeast(40f),
        )
    val edge = quadraticBezier(startRight, control, endRight, t)
    // Hard clamp: never past the destination's right margin once we know it.
    val right = if (destination != null) minOf(edge.x, end.right) else edge.x

    val width = lerp(start.width, end.width, t)
    val height = lerp(start.height, end.height, t)
    val left = right - width
    val top = edge.y - height / 2f

    val density = LocalDensity.current
    val startRadius = start.height / 2f
    val endLarge = with(density) { BubbleRadius.toPx() }
    val endTail = with(density) { BubbleTailRadius.toPx() }
    val topStart = lerp(startRadius, endLarge, t)
    val topEnd = lerp(startRadius, endLarge, t)
    val bottomStart = lerp(startRadius, endLarge, t)
    val bottomEnd = lerp(startRadius, endTail, t)

    // White at launch (matches composer), fades to bubble grey over the first third.
    val fillT = (t / (1f / 3f)).coerceIn(0f, 1f)
    val flightFill = androidx.compose.ui.graphics.lerp(ComposerFieldFill, BubbleFill, fillT)

    Box(
        modifier =
            Modifier.graphicsLayer {
                    // Sub-pixel translation — IntOffset rounding was adding micro-hitch each frame.
                    translationX = left
                    translationY = top
                }
                .size(with(density) { width.toDp() }, with(density) { height.toDp() })
                .clip(
                    LerpBubbleShape(
                        topStart = topStart,
                        topEnd = topEnd,
                        bottomStart = bottomStart,
                        bottomEnd = bottomEnd,
                    )
                )
                .background(flightFill)
                .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        // Text size stays constant — no scale on the typography.
        Text(text = flight.text, style = MessageTextStyle)
    }
}

private fun LayoutCoordinates.boundsIn(root: LayoutCoordinates): Rect {
    val topLeft = root.localPositionOf(this, Offset.Zero)
    return Rect(topLeft, size.toSize())
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

private fun quadraticBezier(p0: Offset, p1: Offset, p2: Offset, t: Float): Offset {
    val u = 1f - t
    return Offset(
        x = u * u * p0.x + 2f * u * t * p1.x + t * t * p2.x,
        y = u * u * p0.y + 2f * u * t * p1.y + t * t * p2.y,
    )
}

private val SentBubbleShape =
    RoundedCornerShape(
        topStart = BubbleRadius,
        topEnd = BubbleRadius,
        bottomStart = BubbleRadius,
        bottomEnd = BubbleTailRadius,
    )

private class LerpBubbleShape(
    private val topStart: Float,
    private val topEnd: Float,
    private val bottomStart: Float,
    private val bottomEnd: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        return Outline.Rounded(
            RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                topLeftCornerRadius = CornerRadius(topStart),
                topRightCornerRadius = CornerRadius(topEnd),
                bottomLeftCornerRadius = CornerRadius(bottomStart),
                bottomRightCornerRadius = CornerRadius(bottomEnd),
            )
        )
    }
}
