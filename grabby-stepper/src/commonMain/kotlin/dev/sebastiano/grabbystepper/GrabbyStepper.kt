package dev.sebastiano.grabbystepper

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.jewel.ui.component.Text

private val SnapSpring =
    spring<Float>(dampingRatio = 0.38f, stiffness = 180f, visibilityThreshold = 0.1f)

private val PunchSpring = spring<Float>(dampingRatio = 0.32f, stiffness = Spring.StiffnessMediumLow)

private const val IDLE_TICK_MS = 16L

private enum class DragAxis {
    Undecided,
    Horizontal,
    Vertical,
}

/**
 * Gesture-mutated fields. The composable that owns [pointerInput] must not read these during
 * composition (cancels the gesture → beachball).
 */
private class GrabbyMotion {
    var dragging by mutableStateOf(false)
    var axis by mutableStateOf(DragAxis.Undecided)
    var liveX by mutableFloatStateOf(0f)
    var liveY by mutableFloatStateOf(0f)
    val offsetX = Animatable(0f)
    val offsetY = Animatable(0f)
    val thumbScale = Animatable(1f)
    val numberScale = Animatable(1f)
    val trackPulse = Animatable(1f)

    fun displayX(): Float = if (dragging) liveX else offsetX.value

    fun displayY(): Float = if (dragging) liveY else offsetY.value
}

@Composable
fun GrabbyStepper(
    colors: GrabbyColors,
    modifier: Modifier = Modifier,
    initialCount: Int = 0,
    trackWidth: Dp = 208.dp,
    trackHeight: Dp = 64.dp,
    thumbDiameter: Dp = 56.dp,
    inset: Dp = 4.dp,
) {
    val density = LocalDensity.current
    val layout =
        remember(trackWidth, trackHeight, thumbDiameter, inset, density) {
            with(density) {
                RestLayout(
                    width = trackWidth.toPx(),
                    height = trackHeight.toPx(),
                    thumbDiameter = thumbDiameter.toPx(),
                    inset = inset.toPx(),
                )
            }
        }

    var count by remember { mutableIntStateOf(initialCount) }
    RecomposeProbe("GrabbyStepper", enabled = DEBUG_MOTION_PROBES)
    val motion = remember { GrabbyMotion() }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    // Thumb center must stay inside the pill — hard clamp, no rubber past the rim.
    val maxThumbTravel = ((layout.width / 2f) - layout.thumbRadius - layout.inset).coerceAtLeast(0f)
    val resetThreshold = layout.thumbDiameter * 1.35f
    val detachStart = layout.thumbDiameter * 0.85f
    val detachEnd = resetThreshold

    val overflowX = with(density) { 48.dp.toPx() }
    val overflowY = with(density) { 140.dp.toPx() }
    val boxWidth = layout.width + overflowX * 2f
    val boxHeight = layout.height + overflowY

    fun punchNumber() {
        scope.launch {
            motion.numberScale.snapTo(1.14f)
            motion.numberScale.animateTo(1f, PunchSpring)
        }
    }

    fun jiggleTrack() {
        scope.launch {
            motion.trackPulse.snapTo(1.03f)
            motion.trackPulse.animateTo(1f, SnapSpring)
        }
    }

    fun commitStep(delta: Int, jiggle: Boolean = true) {
        val next = applyStep(count, delta)
        if (next == count) return
        count = next
        punchNumber()
        if (jiggle) jiggleTrack()
    }

    fun resetCount() {
        if (count == 0) return
        count = 0
        punchNumber()
        jiggleTrack()
    }

    fun applyKeyAction(action: StepperKeyAction): Boolean {
        when (action) {
            StepperKeyAction.Increment -> commitStep(1)
            StepperKeyAction.Decrement -> commitStep(-1)
            StepperKeyAction.Reset -> resetCount()
        }
        return true
    }

    suspend fun springHome(fromX: Float, fromY: Float) {
        motion.offsetX.snapTo(fromX)
        motion.offsetY.snapTo(fromY)
        coroutineScope {
            launch { motion.offsetX.animateTo(0f, SnapSpring) }
            launch { motion.offsetY.animateTo(0f, SnapSpring) }
            launch { motion.thumbScale.animateTo(1f, PunchSpring) }
        }
    }

    LaunchedEffect(motion.dragging, motion.axis) {
        if (!motion.dragging || motion.axis != DragAxis.Horizontal) return@LaunchedEffect
        while (isActive) {
            val normalized =
                if (maxThumbTravel <= 0f) 0f else (motion.liveX / maxThumbTravel).coerceIn(-1f, 1f)
            val interval = tickIntervalMs(normalized)
            if (interval == Long.MAX_VALUE) {
                delay(IDLE_TICK_MS)
            } else {
                delay(interval)
                if (!isActive || !motion.dragging || motion.axis != DragAxis.Horizontal) {
                    return@LaunchedEffect
                }
                val delta = sign(motion.liveX).roundToInt()
                if (delta != 0) commitStep(delta, jiggle = false)
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier =
            modifier
                .requiredSize(
                    width = with(density) { boxWidth.toDp() },
                    height = with(density) { boxHeight.toDp() },
                )
                .testTag(GrabbyTags.STEPPER)
                .focusRequester(focusRequester)
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val action = stepperKeyAction(event.key) ?: return@onKeyEvent false
                    applyKeyAction(action)
                }
                .focusable(interactionSource = interactionSource)
                .semantics {
                    contentDescription = stepperContentDescription(count)
                    stateDescription = stepperStateDescription(count)
                    progressBarRangeInfo =
                        ProgressBarRangeInfo(current = count.toFloat(), range = 0f..9_999f)
                    setProgress {
                        count = it.toInt().coerceAtLeast(0)
                        true
                    }
                    customActions =
                        listOf(
                            CustomAccessibilityAction("Decrease") {
                                commitStep(-1)
                                true
                            },
                            CustomAccessibilityAction("Increase") {
                                commitStep(1)
                                true
                            },
                            CustomAccessibilityAction("Reset to zero") {
                                resetCount()
                                true
                            },
                        )
                }
                .pointerInput(layout, overflowX, overflowY, maxThumbTravel) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val origin = down.position
                        val local = origin - Offset(overflowX, overflowY / 2f)
                        val hit = hitTestPill(local.x, local.y, layout)
                        if (hit == PillHit.None) return@awaitEachGesture
                        focusRequester.requestFocus()
                        val onThumb = hit == PillHit.Thumb
                        val onMinus = hit == PillHit.Minus
                        val onPlus = hit == PillHit.Plus

                        if (!onThumb && (onMinus || onPlus)) {
                            var dragged = false
                            val slop = viewConfiguration.touchSlop
                            drag(down.id) { change ->
                                if (
                                    hypot(
                                        change.position.x - origin.x,
                                        change.position.y - origin.y,
                                    ) > slop
                                ) {
                                    dragged = true
                                }
                                change.consume()
                            }
                            if (!dragged) commitStep(if (onPlus) 1 else -1)
                            return@awaitEachGesture
                        }

                        if (!onThumb) return@awaitEachGesture

                        motion.dragging = true
                        motion.axis = DragAxis.Undecided
                        motion.liveX = 0f
                        motion.liveY = 0f
                        var didInitialTick = false
                        var accX = 0f
                        var accY = 0f
                        scope.launch { motion.thumbScale.animateTo(1.05f, PunchSpring) }

                        try {
                            drag(down.id) { change ->
                                val delta = change.positionChange()
                                change.consume()
                                accX += delta.x
                                accY += delta.y
                                if (motion.axis == DragAxis.Undecided) {
                                    val slop = viewConfiguration.touchSlop
                                    val ax = abs(accX)
                                    val ay = abs(accY)
                                    when {
                                        ax > slop && ax > ay * 1.15f ->
                                            motion.axis = DragAxis.Horizontal
                                        ay > slop && accY > 0f && ay > ax * 1.05f ->
                                            motion.axis = DragAxis.Vertical
                                    }
                                }
                                when (motion.axis) {
                                    DragAxis.Horizontal -> {
                                        motion.liveX =
                                            accX.coerceIn(-maxThumbTravel, maxThumbTravel)
                                        motion.liveY = 0f
                                    }
                                    DragAxis.Vertical -> {
                                        motion.liveY =
                                            rubberband(
                                                accY.coerceAtLeast(0f),
                                                resetThreshold * 1.15f,
                                            )
                                        motion.liveX = 0f
                                    }
                                    DragAxis.Undecided -> {
                                        motion.liveX =
                                            (accX * 0.25f).coerceIn(-maxThumbTravel, maxThumbTravel)
                                        motion.liveY = accY.coerceAtLeast(-8f) * 0.25f
                                    }
                                }
                                if (
                                    motion.axis == DragAxis.Horizontal &&
                                        !didInitialTick &&
                                        abs(motion.liveX) >= maxThumbTravel * INITIAL_TICK_FRACTION
                                ) {
                                    didInitialTick = true
                                    commitStep(sign(motion.liveX).roundToInt(), jiggle = false)
                                }
                            }
                        } finally {
                            val reset =
                                motion.axis == DragAxis.Vertical &&
                                    shouldResetOnRelease(motion.liveY, resetThreshold)
                            if (reset) {
                                count = 0
                                punchNumber()
                            }
                            val homeX = motion.liveX
                            val homeY = motion.liveY
                            motion.dragging = false
                            motion.axis = DragAxis.Undecided
                            scope.launch { springHome(homeX, homeY) }
                        }
                    }
                }
    ) {
        GrabbyVisuals(
            colors = colors,
            layout = layout,
            motion = motion,
            count = count,
            overflowX = overflowX,
            overflowY = overflowY,
            resetThreshold = resetThreshold,
            detachStart = detachStart,
            detachEnd = detachEnd,
            trackWidth = trackWidth,
            trackHeight = trackHeight,
            thumbDiameter = thumbDiameter,
            showFocusRing = focused,
        )
    }
}

@Composable
private fun GrabbyVisuals(
    colors: GrabbyColors,
    layout: RestLayout,
    motion: GrabbyMotion,
    count: Int,
    overflowX: Float,
    overflowY: Float,
    resetThreshold: Float,
    detachStart: Float,
    detachEnd: Float,
    trackWidth: Dp,
    trackHeight: Dp,
    thumbDiameter: Dp,
    showFocusRing: Boolean,
) {
    // Do NOT read motion.* / Animatable.value in composition — that forces a full
    // recompose every pointer move (~15fps). Read only inside graphicsLayer /
    // offset lambdas so Compose can invalidate layers instead.
    RecomposeProbe("GrabbyVisuals", enabled = DEBUG_MOTION_PROBES)

    val pillShape = RoundedCornerShape(percent = 50)
    val pillLeft = overflowX
    val pillTop = overflowY / 2f
    val thumbRestLeft = pillLeft + layout.centerX - layout.thumbRadius
    val thumbRestTop = pillTop + layout.centerY - layout.thumbRadius

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
        Box(
            modifier =
                Modifier.offset { IntOffset(pillLeft.roundToInt(), pillTop.roundToInt()) }
                    .requiredSize(trackWidth, trackHeight)
                    .graphicsLayer {
                        val ox = motion.displayX()
                        val oy = motion.displayY()
                        val follow = trackFollow(oy, detachStart, detachEnd)
                        val stretchFrac = abs(ox) / layout.width.coerceAtLeast(1f)
                        val pulse = motion.trackPulse.value
                        scaleX = (1f + stretchFrac * HORIZONTAL_STRETCH_FOLLOW * 1.4f) * pulse
                        scaleY =
                            (1f +
                                (oy / layout.height.coerceAtLeast(1f)).coerceAtLeast(0f) *
                                    VERTICAL_STRETCH_FOLLOW *
                                    follow) * pulse
                        transformOrigin = TransformOrigin(if (ox >= 0f) 0f else 1f, 0.5f)
                        translationY = oy * 0.2f * follow
                    }
                    .border(
                        width = 2.dp,
                        color = if (showFocusRing) colors.focusRing else Color.Transparent,
                        shape = pillShape,
                    )
                    .background(colors.track, pillShape),
            contentAlignment = Alignment.Center,
        ) {
            // Alpha via graphicsLayer so pullProgress does not recompose Text.
            Text(
                text = "✕",
                color = colors.number,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                modifier =
                    Modifier.clearAndSetSemantics {}
                        .graphicsLayer {
                            val oy = motion.displayY()
                            alpha = (oy / resetThreshold).coerceIn(0f, 1f)
                        },
            )
            Text(
                text = "−",
                color = colors.chrome,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                modifier =
                    Modifier.align(Alignment.CenterStart)
                        .offset(x = 18.dp)
                        .clearAndSetSemantics {}
                        .graphicsLayer {
                            val oy = motion.displayY()
                            alpha = 1f - (oy / resetThreshold).coerceIn(0f, 1f)
                        },
            )
            Text(
                text = "+",
                color = colors.chrome,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                modifier =
                    Modifier.align(Alignment.CenterEnd)
                        .offset(x = (-18).dp)
                        .clearAndSetSemantics {}
                        .graphicsLayer {
                            val oy = motion.displayY()
                            alpha = 1f - (oy / resetThreshold).coerceIn(0f, 1f)
                        },
            )
        }

        Box(
            modifier =
                Modifier.offset { IntOffset(thumbRestLeft.roundToInt(), thumbRestTop.roundToInt()) }
                    .size(thumbDiameter)
                    .graphicsLayer {
                        val ox = motion.displayX()
                        val oy = motion.displayY()
                        translationX = ox
                        translationY = oy
                        val s = motion.thumbScale.value
                        scaleX = s
                        scaleY = s
                        alpha = 1f - (oy / resetThreshold).coerceIn(0f, 1f) * 0.85f
                    }
                    .background(colors.thumb, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = count.toString(),
                color = colors.number,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier.clearAndSetSemantics {}
                        .graphicsLayer {
                            val s = motion.numberScale.value
                            scaleX = s
                            scaleY = s
                        },
            )
        }
    }
}
