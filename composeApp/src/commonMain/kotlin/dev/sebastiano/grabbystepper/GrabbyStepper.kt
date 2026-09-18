package dev.sebastiano.grabbystepper

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
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

private val SnapSpring =
    spring<Float>(dampingRatio = 0.42f, stiffness = 210f, visibilityThreshold = 0.1f)

private val PunchSpring =
    spring<Float>(dampingRatio = 0.36f, stiffness = Spring.StiffnessMediumLow)

private enum class DragAxis {
    Undecided,
    Horizontal,
    Vertical,
}

@Composable
fun GrabbyStepper(
    colors: GrabbyColors,
    modifier: Modifier = Modifier,
    initialValue: Int = 0,
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

    var value by remember { mutableIntStateOf(initialValue) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val thumbScale = remember { Animatable(1f) }
    val numberScale = remember { Animatable(1f) }
    val trackPulse = remember { Animatable(1f) }

    var dragging by remember { mutableStateOf(false) }
    var axis by remember { mutableStateOf(DragAxis.Undecided) }
    var liveX by remember { mutableStateOf(0f) }
    var liveY by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    val displayX = if (dragging) liveX else offsetX.value
    val displayY = if (dragging) liveY else offsetY.value

    val maxStretchX = layout.width * 0.72f
    val resetThreshold = layout.thumbDiameter * 1.35f
    val detachStart = layout.thumbDiameter * 0.85f
    val detachEnd = resetThreshold
    val maxNeck = layout.thumbDiameter * 2.1f

    fun punchNumber() {
        scope.launch {
            numberScale.snapTo(1.22f)
            numberScale.animateTo(1f, PunchSpring)
        }
    }

    fun jiggleTrack() {
        scope.launch {
            trackPulse.snapTo(1.055f)
            trackPulse.animateTo(1f, SnapSpring)
        }
    }

    fun commitStep(delta: Int) {
        val next = applyStep(value, delta)
        if (next == value) return
        value = next
        punchNumber()
        jiggleTrack()
    }

    suspend fun springHome(fromX: Float, fromY: Float) {
        offsetX.snapTo(fromX)
        offsetY.snapTo(fromY)
        coroutineScope {
            launch { offsetX.animateTo(0f, SnapSpring) }
            launch { offsetY.animateTo(0f, SnapSpring) }
            launch { thumbScale.animateTo(1f, PunchSpring) }
        }
    }

    LaunchedEffect(dragging, axis) {
        if (!dragging || axis != DragAxis.Horizontal) return@LaunchedEffect
        while (isActive) {
            val normalized = (liveX / maxStretchX).coerceIn(-1f, 1f)
            val interval = tickIntervalMs(normalized)
            if (interval == Long.MAX_VALUE) {
                delay(16)
                continue
            }
            delay(interval)
            if (!isActive || !dragging || axis != DragAxis.Horizontal) break
            val delta = sign(liveX).roundToInt()
            if (delta != 0) commitStep(delta)
        }
    }

    val overflowX = with(density) { 120.dp.toPx() }
    val overflowY = with(density) { 160.dp.toPx() }
    val boxWidth = layout.width + overflowX * 2f
    val boxHeight = layout.height + overflowY

    val chromeStyle =
        remember(colors) {
            TextStyle(
                color = colors.chrome,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    val numberStyle =
        remember(colors) {
            TextStyle(
                color = colors.number,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    val xStyle =
        remember(colors) {
            TextStyle(
                color = colors.number,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
            )
        }

    Box(
        modifier =
            modifier
                .requiredSize(
                    width = with(density) { boxWidth.toDp() },
                    height = with(density) { boxHeight.toDp() },
                )
                .pointerInput(layout, colors) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val origin = down.position
                        val local = origin - Offset(overflowX, overflowY / 2f)
                        val thumbAtRest = Offset(layout.centerX, layout.centerY)
                        val onThumb =
                            hypot(local.x - thumbAtRest.x, local.y - thumbAtRest.y) <=
                                layout.thumbRadius + layout.inset * 2f
                        val onMinus = local.x < layout.width * 0.28f && local.y in 0f..layout.height
                        val onPlus = local.x > layout.width * 0.72f && local.y in 0f..layout.height

                        if (!onThumb && (onMinus || onPlus)) {
                            val pointer = down.id
                            var dragged = false
                            val slop = viewConfiguration.touchSlop
                            drag(pointer) { change ->
                                if (hypot(change.position.x - origin.x, change.position.y - origin.y) >
                                    slop
                                ) {
                                    dragged = true
                                }
                                change.consume()
                            }
                            if (!dragged) {
                                commitStep(if (onPlus) 1 else -1)
                            }
                            return@awaitEachGesture
                        }

                        if (!onThumb) return@awaitEachGesture

                        dragging = true
                        axis = DragAxis.Undecided
                        liveX = 0f
                        liveY = 0f
                        var didInitialTick = false
                        var accX = 0f
                        var accY = 0f
                        scope.launch { thumbScale.animateTo(1.08f, PunchSpring) }

                        try {
                            drag(down.id) { change ->
                                val delta = change.positionChange()
                                change.consume()
                                accX += delta.x
                                accY += delta.y
                                if (axis == DragAxis.Undecided) {
                                    val slop = viewConfiguration.touchSlop
                                    val ax = abs(accX)
                                    val ay = abs(accY)
                                    when {
                                        ax > slop && ax > ay * 1.15f -> axis = DragAxis.Horizontal
                                        ay > slop && accY > 0f && ay > ax * 1.05f ->
                                            axis = DragAxis.Vertical
                                    }
                                }
                                when (axis) {
                                    DragAxis.Horizontal -> {
                                        liveX = rubberband(accX, maxStretchX)
                                        liveY *= 0.35f
                                    }
                                    DragAxis.Vertical -> {
                                        liveY = rubberband(accY.coerceAtLeast(0f), resetThreshold * 1.15f)
                                        liveX *= 0.35f
                                    }
                                    DragAxis.Undecided -> {
                                        liveX = accX * 0.35f
                                        liveY = accY.coerceAtLeast(-8f) * 0.35f
                                    }
                                }
                                if (
                                    axis == DragAxis.Horizontal &&
                                        !didInitialTick &&
                                        abs(liveX) >= maxStretchX * INITIAL_TICK_FRACTION
                                ) {
                                    didInitialTick = true
                                    commitStep(sign(liveX).roundToInt())
                                }
                            }
                        } finally {
                            val reset =
                                axis == DragAxis.Vertical &&
                                    shouldResetOnRelease(liveY, resetThreshold)
                            if (reset) {
                                value = 0
                                punchNumber()
                            }
                            val homeX = liveX
                            val homeY = liveY
                            dragging = false
                            axis = DragAxis.Undecided
                            scope.launch { springHome(homeX, homeY) }
                        }
                    }
                },
    ) {
        val ox = displayX
        val oy = displayY
        val follow = trackFollow(oy, detachStart, detachEnd)
        val stretch = stretchTrack(layout, ox, oy, follow)
        val pullProgress = (oy / resetThreshold).coerceIn(0f, 1f)
        val chromeAlpha = 1f - pullProgress
        val xAlpha = pullProgress
        val gooey = follow * (0.25f + 0.75f * pullProgress).coerceIn(0f, 1f)

        val minusLayout = measurer.measure("−", chromeStyle)
        val plusLayout = measurer.measure("+", chromeStyle)
        val xLayout = measurer.measure("✕", xStyle)
        val numberLayout = measurer.measure(value.toString(), numberStyle)

        Canvas(Modifier.matchParentSize()) {
            val origin = Offset(overflowX, overflowY / 2f)
            scale(trackPulse.value, pivot = Offset(origin.x + layout.centerX, origin.y + layout.centerY)) {
                drawGrabby(
                    origin = origin,
                    layout = layout,
                    stretch = stretch,
                    offsetX = ox,
                    offsetY = oy,
                    thumbScale = thumbScale.value,
                    numberScale = numberScale.value,
                    colors = colors,
                    chromeAlpha = chromeAlpha,
                    xAlpha = xAlpha,
                    gooey = gooey,
                    maxNeck = maxNeck,
                    minus = minusLayout,
                    plus = plusLayout,
                    xMark = xLayout,
                    number = numberLayout,
                )
            }
        }
    }
}

private fun DrawScope.drawGrabby(
    origin: Offset,
    layout: RestLayout,
    stretch: StretchRect,
    offsetX: Float,
    offsetY: Float,
    thumbScale: Float,
    numberScale: Float,
    colors: GrabbyColors,
    chromeAlpha: Float,
    xAlpha: Float,
    gooey: Float,
    maxNeck: Float,
    minus: TextLayoutResult,
    plus: TextLayoutResult,
    xMark: TextLayoutResult,
    number: TextLayoutResult,
) {
    val thumbCenter = Offset(origin.x + layout.centerX + offsetX, origin.y + layout.centerY + offsetY)
    val shiftedStretch =
        StretchRect(
            left = origin.x + stretch.left,
            top = origin.y + stretch.top,
            right = origin.x + stretch.right,
            bottom = origin.y + stretch.bottom,
        )
    val trackPath =
        elasticTrackPath(
            stretch = shiftedStretch,
            thumbCenter = thumbCenter,
            thumbRadius = layout.thumbRadius * thumbScale,
            gooey = gooey,
            maxNeckDistance = maxNeck,
        )
    drawPath(trackPath, colors.track)

    val restCenter = Offset(origin.x + layout.centerX, origin.y + layout.centerY)
    if (xAlpha > 0.02f) {
        drawText(
            textLayoutResult = xMark,
            color = colors.number.copy(alpha = xAlpha),
            topLeft =
                Offset(
                    restCenter.x - xMark.size.width / 2f,
                    restCenter.y - xMark.size.height / 2f,
                ),
        )
    }
    if (chromeAlpha > 0.02f) {
        val minusLeft = origin.x + layout.inset * 2.4f
        val plusLeft = origin.x + layout.width - layout.inset * 2.4f - plus.size.width
        drawText(
            textLayoutResult = minus,
            color = colors.chrome.copy(alpha = chromeAlpha),
            topLeft = Offset(minusLeft, restCenter.y - minus.size.height / 2f),
        )
        drawText(
            textLayoutResult = plus,
            color = colors.chrome.copy(alpha = chromeAlpha),
            topLeft = Offset(plusLeft, restCenter.y - plus.size.height / 2f),
        )
    }

    drawCircle(
        color = colors.thumb,
        radius = layout.thumbRadius * thumbScale,
        center = thumbCenter,
    )
    scale(numberScale, pivot = thumbCenter) {
        drawText(
            textLayoutResult = number,
            color = colors.number,
            topLeft =
                Offset(
                    thumbCenter.x - number.size.width / 2f,
                    thumbCenter.y - number.size.height / 2f,
                ),
        )
    }
}
