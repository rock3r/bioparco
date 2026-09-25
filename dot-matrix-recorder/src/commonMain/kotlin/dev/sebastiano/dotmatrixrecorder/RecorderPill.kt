package dev.sebastiano.dotmatrixrecorder

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import org.jetbrains.jewel.ui.component.Text

// Sampled from the reference video.
private val Red = Color(0xFFEA113F)
private val Ink = Color.White
private val Muted = Color(0xFF616161)
private val Surface = Color.Black
private val Rim = Color(0xFF3F3F3F)
private val RowHighlight = Color(0xFF0D0D0D)

/** Restart and Delete keep their unlit dots darker than the lens and the screenshot frame. */
private const val SECONDARY_DIM_ALPHA = 0.14f

private val LabelStyle = TextStyle(color = Ink, fontSize = 15.sp, fontFeatureSettings = "tnum")

// Proportions measured on the reference against the 5×5 lens.
private val PillHeight = 44.dp
private val PillRadius = PillHeight / 2
private val MenuRadius = 16.dp
private val RowHeight = 41.dp
private val MenuInset = 8.dp

/** The dock is exactly as tall as the two-row menu, so opening it only makes it wider. */
private val DockWidth = 55.dp
private val DockRadius = DockWidth / 2
private val DockIconInset = MenuInset + (RowHeight - IconSize) / 2

/** Hover grace before collapsing, so the morph cannot flicker under a still pointer. */
private const val COLLAPSE_DELAY_MS = 220L

private enum class Face {
    /** Idle and collapsed: two icons stacked in a vertical pill. */
    Dock,
    /** Idle or counting down, hovered: Record and Screenshot rows. */
    Menu,
    /** Recording, hovered: timer (stop), Restart, Delete. */
    RecordingMenu,
    /** Counting down or recording, collapsed: icon and timer in a horizontal pill. */
    Pill,
}

/** Everything one face needs to draw, frozen so an exiting face keeps its last look. */
@Immutable
private data class Look(
    val session: Int,
    val face: Face,
    val state: RecorderState,
    val seconds: Long,
    val shotAtMs: Long,
)

private class RecorderActions(
    val nowMs: () -> Long,
    val send: (RecorderEvent) -> Unit,
    val shoot: () -> Unit,
    /** True while keyboard focus is on one of the pill's controls. */
    val focusInside: () -> Boolean,
)

/**
 * A floating record/screenshot control drawn with 5×5 dot icons.
 *
 * Collapsed it is a vertical pill. Hover opens it into a menu. Record counts down 3, 2, 1 on the
 * lens, then the pill collapses to a timer. Hover again to stop (click the timer), restart, or
 * delete.
 *
 * Its labels are Jewel `Text`, so it needs a Jewel theme (for example `IntUiTheme`) around it.
 */
@Composable
fun RecorderPill(modifier: Modifier = Modifier) {
    val clock = remember { TimeSource.Monotonic.markNow() }
    val nowMs = remember(clock) { { clock.elapsedNow().inWholeMilliseconds } }
    var state by remember { mutableStateOf<RecorderState>(RecorderState.Idle) }
    var session by remember { mutableIntStateOf(0) }
    var seconds by remember { mutableLongStateOf(0L) }
    var shotAtMs by remember { mutableLongStateOf(Long.MIN_VALUE / 2) }

    LaunchedEffect(state) {
        seconds = 0
        if (state == RecorderState.Idle) return@LaunchedEffect
        while (true) {
            withFrameMillis {}
            val now = nowMs()
            val next = state.reduce(RecorderEvent.Tick(now))
            if (next != state) {
                state = next
                break
            }
            // Only whole seconds reach composition; the lens animates in the draw phase.
            val whole = state.elapsedMs(now) / 1_000
            if (whole != seconds) seconds = whole
        }
    }

    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }

    val actions =
        remember(nowMs) {
            RecorderActions(
                nowMs = nowMs,
                send = { event ->
                    val next = state.reduce(event)
                    if (next == RecorderState.Idle && state != RecorderState.Idle) session++
                    state = next
                },
                shoot = { shotAtMs = nowMs() },
                focusInside = { focused },
            )
        }

    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(hovered, focused) {
        if (hovered || focused) {
            expanded = true
        } else {
            delay(COLLAPSE_DELAY_MS)
            expanded = false
        }
    }

    val face =
        when (state) {
            RecorderState.Idle -> if (expanded) Face.Menu else Face.Dock
            is RecorderState.CountingDown -> if (expanded) Face.Menu else Face.Pill
            is RecorderState.Recording -> if (expanded) Face.RecordingMenu else Face.Pill
        }
    val look = Look(session, face, state, seconds, shotAtMs)

    AnimatedContent(
        targetState = look,
        contentKey = { it.session },
        transitionSpec = { dismissTransform() },
        contentAlignment = Alignment.Center,
        modifier =
            modifier.testTag(RecorderTags.PILL).hoverable(interaction).onFocusChanged {
                focused = it.hasFocus
            },
        label = "session",
    ) { sessionLook ->
        val blur = rememberBlurProgress()
        MorphingChrome(
            look = sessionLook,
            actions = actions,
            modifier = Modifier.blurBy(max = 10.dp) { blur.value },
        )
    }
}

@Composable
private fun MorphingChrome(look: Look, actions: RecorderActions, modifier: Modifier = Modifier) {
    val radius =
        animateDpAsState(
            targetValue =
                when (look.face) {
                    Face.Dock -> DockRadius
                    Face.Pill -> PillRadius
                    Face.Menu,
                    Face.RecordingMenu -> MenuRadius
                },
            animationSpec = MorphSpec,
            label = "radius",
        )
    Box(modifier.drawBehind { drawChrome(radius.value.toPx()) }) {
        AnimatedContent(
            targetState = look,
            contentKey = { it.face },
            transitionSpec = { morphTransform() },
            contentAlignment = Alignment.Center,
            label = "face",
        ) { faceLook ->
            val blur = rememberBlurProgress()
            // Removing a focused control clears focus from the whole window. When the face
            // changes under keyboard focus, hand it to the new face's main control instead, so
            // the pill stays open and reachable. The pill face is only shown without focus.
            val primary = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                if (faceLook.face != Face.Pill && actions.focusInside()) primary.requestFocus()
            }
            Box(Modifier.blurBy(max = 6.dp) { blur.value }) {
                when (faceLook.face) {
                    Face.Dock -> DockFace(faceLook, actions, primary)
                    Face.Menu -> MenuFace(faceLook, actions, primary)
                    Face.RecordingMenu -> RecordingMenuFace(faceLook, actions, primary)
                    Face.Pill -> PillFace(faceLook, actions)
                }
            }
        }
    }
}

@Composable
private fun DockFace(
    look: Look,
    actions: RecorderActions,
    primary: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(DockWidth).padding(vertical = DockIconInset),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RowHeight - IconSize),
    ) {
        LensIcon(
            state = look.state,
            nowMs = actions.nowMs,
            modifier =
                Modifier.size(IconSize)
                    .focusRequester(primary)
                    .clickable(onClickLabel = "Record", role = Role.Button) {
                        actions.send(RecorderEvent.RecordPressed(actions.nowMs()))
                    }
                    .testTag(RecorderTags.DOCK_RECORD),
        )
        ShutterIcon(
            shotAtMs = look.shotAtMs,
            nowMs = actions.nowMs,
            modifier =
                Modifier.size(IconSize)
                    .clickable(
                        onClickLabel = "Screenshot",
                        role = Role.Button,
                        onClick = actions.shoot,
                    ),
        )
    }
}

@Composable
private fun MenuFace(
    look: Look,
    actions: RecorderActions,
    primary: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val counting = look.state as? RecorderState.CountingDown
    MenuColumn(modifier) {
        MenuRow(
            onClick = {
                val event =
                    if (counting == null) RecorderEvent.RecordPressed(actions.nowMs())
                    else RecorderEvent.DeletePressed
                actions.send(event)
            },
            clickLabel = if (counting == null) "Start recording" else "Cancel countdown",
            tag = RecorderTags.RECORD,
            icon = { hovered -> LensIcon(look.state, actions.nowMs, hovered = hovered) },
            modifier = Modifier.focusRequester(primary),
        ) {
            Crossfade(
                targetState = counting != null,
                animationSpec = tween(200),
                label = "record",
            ) {
                if (it) Text("00:00", style = LabelStyle.copy(color = Muted))
                else Text("Record", style = LabelStyle)
            }
        }
        MenuRow(
            onClick = actions.shoot,
            clickLabel = "Take a screenshot",
            tag = RecorderTags.SCREENSHOT,
            icon = { ShutterIcon(look.shotAtMs, actions.nowMs, Modifier.size(IconSize)) },
        ) {
            Text("Screenshot", style = LabelStyle)
        }
    }
}

@Composable
private fun RecordingMenuFace(
    look: Look,
    actions: RecorderActions,
    primary: FocusRequester,
    modifier: Modifier = Modifier,
) {
    MenuColumn(modifier) {
        MenuRow(
            onClick = { actions.send(RecorderEvent.StopPressed) },
            clickLabel = "Stop recording",
            tag = RecorderTags.STOP,
            icon = { LensIcon(look.state, actions.nowMs) },
            modifier = Modifier.focusRequester(primary),
        ) {
            Text(formatTimer(look.seconds * 1_000), style = LabelStyle)
        }
        MenuRow(
            onClick = { actions.send(RecorderEvent.RestartPressed(actions.nowMs())) },
            clickLabel = "Restart recording",
            tag = RecorderTags.RESTART,
            icon = {
                DotMatrix(DotGlyphs.Restart, Ink, Modifier.size(IconSize), SECONDARY_DIM_ALPHA)
            },
        ) {
            Text("Restart", style = LabelStyle)
        }
        MenuRow(
            onClick = { actions.send(RecorderEvent.DeletePressed) },
            clickLabel = "Delete recording",
            tag = RecorderTags.DELETE,
            icon = {
                DotMatrix(DotGlyphs.Delete, Ink, Modifier.size(IconSize), SECONDARY_DIM_ALPHA)
            },
        ) {
            Text("Delete", style = LabelStyle)
        }
    }
}

@Composable
private fun PillFace(look: Look, actions: RecorderActions, modifier: Modifier = Modifier) {
    val counting = look.state is RecorderState.CountingDown
    Row(
        modifier = modifier.height(PillHeight).padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LensIcon(look.state, actions.nowMs)
        Text(
            text = formatTimer(look.seconds * 1_000),
            style = if (counting) LabelStyle.copy(color = Muted) else LabelStyle,
        )
    }
}

@Composable
private fun MenuColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier.width(IntrinsicSize.Max).padding(MenuInset)) { content() }
}

@Composable
private fun MenuRow(
    onClick: () -> Unit,
    clickLabel: String,
    tag: String,
    icon: @Composable (highlighted: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val highlight =
        animateFloatAsState(if (hovered || focused) 1f else 0f, tween(140), label = "row")
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(RowHeight)
                .drawBehind {
                    drawRoundRect(
                        color = RowHighlight,
                        alpha = highlight.value,
                        cornerRadius = CornerRadius(11.dp.toPx()),
                    )
                }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClickLabel = clickLabel,
                    role = Role.Button,
                    onClick = onClick,
                )
                .pointerHoverIcon(PointerIcon.Hand)
                .testTag(tag)
                .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(IconSize), contentAlignment = Alignment.Center) {
            icon(hovered || focused)
        }
        Spacer(Modifier.width(12.dp))
        label()
    }
}

/**
 * The red lens: a shimmering rest, one stripe sweep when Record is [hovered], 3-2-1 and a flash
 * during the countdown, and scrolling diagonal stripes while recording.
 */
@Composable
private fun LensIcon(
    state: RecorderState,
    nowMs: () -> Long,
    modifier: Modifier = Modifier,
    hovered: Boolean = false,
) {
    val sweep = state == RecorderState.Idle && hovered
    val program =
        remember(state, nowMs, sweep) {
            when (state) {
                RecorderState.Idle ->
                    if (sweep) hoverSweepProgram(nowMs(), nowMs) else restingLensProgram(nowMs)
                is RecorderState.CountingDown -> countdownProgram(state, nowMs)
                is RecorderState.Recording -> recordingStripesProgram(state.startedAtMs, nowMs)
            }
        }
    DotMatrix(program = program, tint = Red, modifier = modifier.size(IconSize))
}

@Composable
private fun ShutterIcon(shotAtMs: Long, nowMs: () -> Long, modifier: Modifier = Modifier) {
    val program = remember(shotAtMs, nowMs) { shutterProgram(shotAtMs, nowMs) }
    DotMatrix(program = program, tint = Ink, modifier = modifier)
}

private fun DrawScope.drawChrome(radiusPx: Float) {
    drawRoundRect(Surface, cornerRadius = CornerRadius(radiusPx))
    val stroke = 1.dp.toPx()
    drawRoundRect(
        color = Rim,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radiusPx - stroke / 2),
        style = Stroke(stroke),
    )
}

/** The reference morphs in about 200 ms, fast at first and settling late. */
private val MorphSpec = tween<Dp>(durationMillis = 200, easing = LinearOutSlowInEasing)

private fun AnimatedContentTransitionScope<Look>.morphTransform(): ContentTransform =
    (fadeIn(tween(durationMillis = 150, delayMillis = 40)) togetherWith
            fadeOut(tween(durationMillis = 90)))
        .using(
            SizeTransform(clip = true) { _, _ ->
                tween(durationMillis = 200, easing = LinearOutSlowInEasing)
            }
        )

/** On stop or delete the pill cuts out, the spot stays empty briefly, then the dock fades in. */
private fun AnimatedContentTransitionScope<Look>.dismissTransform(): ContentTransform =
    (fadeIn(tween(durationMillis = 140, delayMillis = 270)) +
        scaleIn(tween(durationMillis = 200, delayMillis = 270), initialScale = 0.94f)) togetherWith
        fadeOut(tween(durationMillis = 60)) using
        SizeTransform(clip = false)

/** 0 when this child is fully shown, 1 when it is fully gone. Read it in the draw phase. */
@Composable
private fun AnimatedVisibilityScope.rememberBlurProgress(): State<Float> =
    transition.animateFloat(transitionSpec = { tween(160) }, label = "blur") {
        if (it == EnterExitState.Visible) 0f else 1f
    }

/**
 * Blurs while a face fades, strongest when [progress] is 1.
 *
 * The radius only takes whole multiples of [BLUR_STEP_PX], and anything below one step is no blur.
 * Skia (m150, Ganesh) picks a GPU blur program from `ceil(3 * sigma)` and compiles it synchronously
 * the first time it is drawn. A radius that shrank smoothly to zero walked through several programs
 * and stalled frames by ~100 ms. A few fixed radii mean a few programs, all met in the first fade.
 * Steps are in pixels, not dp, so every display density gets the same programs.
 *
 * Measured with a frame probe over two or three Spectre runs per variant:
 * - fixed radii: recording start had no long frames;
 * - a continuous radius kept inside one Skia program band (6.5–14 px): 43–51 ms stalls;
 * - no blur at all: no long frames.
 */
private fun Modifier.blurBy(max: Dp, progress: () -> Float): Modifier = graphicsLayer {
    val steps = (progress() * max.toPx() / BLUR_STEP_PX).roundToInt()
    val radius = steps * BLUR_STEP_PX
    renderEffect = if (steps > 0) BlurEffect(radius, radius, TileMode.Decal) else null
}

private const val BLUR_STEP_PX = 4f
