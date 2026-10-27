package dev.sebastiano.squeezeprogress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sebastiano.bioparco.tracing.Tracing
import kotlin.math.roundToInt
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

@Composable
internal fun SqueezeProgressStage(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val motion = remember(scope) { SqueezeMotion(scope) }
    LaunchedEffect(motion) { motion.run() }
    val density = LocalDensity.current
    val metrics = remember(density) { SqueezeMetrics(density) }
    val line = remember { CrumpledLine() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    val keys = remember { KeyRepeatFilter() }

    Box(
        modifier
            .fillMaxSize()
            .testTag(SqueezeProgressTags.STAGE)
            // Space and Enter toggle once per press: held down, the OS repeats key-downs.
            .onPreviewKeyEvent { event ->
                val toggles = event.key == Key.Spacebar || event.key == Key.Enter
                if (toggles) {
                    when (event.type) {
                        KeyEventType.KeyDown -> if (keys.press(event.key.keyCode)) motion.toggle()
                        KeyEventType.KeyUp -> keys.release(event.key.keyCode)
                    }
                }
                toggles
            }
            .onFocusChanged { if (!it.hasFocus) keys.reset() }
            .focusRequester(focus)
            .focusable()
            // Everything that moves is read in here, so a frame redraws without recomposing.
            .drawBehind { Tracing.section("squeeze field") { drawField(motion, metrics, line) } }
    ) {
        Percent(motion, Modifier.align(CentredAt(PERCENT_Y)))
        ToggleButton(motion, Modifier.align(CentredAt(BUTTON_Y)))
    }
}

/** The figure recomposes when the whole percent changes; its colour changes in a layer. */
@Composable
private fun Percent(motion: SqueezeMotion, modifier: Modifier = Modifier) {
    val percent = motion.percent
    val style = JewelTheme.defaultTextStyle.copy(fontFeatureSettings = "tnum")
    Box(modifier) {
        SqueezeProgressPalette.inks.forEachIndexed { index, ink ->
            Text(
                percentText(percent, ink),
                style = style,
                fontSize = 54.sp,
                modifier =
                    Modifier.graphicsLayer {
                        val look = motion.look.value
                        val share = if (index == 0) 1f - look else look
                        alpha = share * inkVisibility(look)
                    },
            )
        }
    }
}

private fun percentText(percent: Int, ink: Color): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = ink)) { append(percent.toString()) }
    withStyle(SpanStyle(color = ink.copy(alpha = SqueezeProgressPalette.SIGN_ALPHA))) {
        append("%")
    }
}

@Composable
private fun ToggleButton(motion: SqueezeMotion, modifier: Modifier = Modifier) {
    val interactions = remember { MutableInteractionSource() }
    val label = if (motion.playing) "Pause" else "Resume"
    Box(
        modifier
            // No circular clip: the resume arrows' heads reach past the ring's circle, as in the
            // video.
            .size(61.dp)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactions,
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onClick = motion::toggle,
            )
            .semantics { contentDescription = label }
            .testTag(SqueezeProgressTags.TOGGLE)
            .drawBehind { drawToggleIcon(motion.look.value) }
    )
}

/** Centres a child horizontally, with its middle at [fraction] of the height. */
private class CentredAt(private val fraction: Float) : Alignment {
    override fun align(
        size: IntSize,
        space: IntSize,
        layoutDirection: LayoutDirection,
    ): IntOffset =
        IntOffset(
            x = (space.width - size.width) / 2,
            y = (space.height * fraction - size.height / 2f).roundToInt(),
        )
}

/** The figure's middle sits at 30% of the height, the button's at 71%, as in the video. */
private const val PERCENT_Y = 0.3f

private const val BUTTON_Y = 0.71f
