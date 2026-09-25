package dev.sebastiano.borderbeam

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive

/**
 * Wraps [content] in a border-beam: a traveling glow for [BeamSize.Md], [BeamSize.Sm] and
 * [BeamSize.Line], or a breathing halo for the pulse sizes.
 *
 * [strength] is 0–1 and only scales the glow. [active] fades the animation out when false. Motion
 * is read in the draw phase.
 */
@Composable
fun BorderBeam(
    modifier: Modifier = Modifier,
    size: BeamSize = BeamSize.Md,
    colorVariant: BeamColorVariant = BeamColorVariant.Colorful,
    theme: BeamTheme = BeamTheme.Dark,
    strength: Float = 1f,
    active: Boolean = true,
    borderRadius: Dp = if (size == BeamSize.Sm) 32.dp else 16.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val playback = remember { BeamPlaybackClock() }
    val seconds = remember { mutableFloatStateOf(0f) }
    val fade = remember { mutableFloatStateOf(0f) }
    val activeNow = rememberUpdatedState(active)
    LaunchedEffect(playback) {
        while (isActive) {
            withFrameNanos { frameNanos ->
                playback.onFrame(frameNanos, activeNow.value)
                seconds.floatValue = playback.seconds
                fade.floatValue = playback.opacity
            }
        }
    }
    Box(
        modifier.drawWithContent {
            val time = seconds.floatValue
            val opacity = fade.floatValue
            drawBeamBehind(size, colorVariant, theme, strength, time, opacity)
            drawContent()
            drawBeamFront(size, colorVariant, theme, strength, time, opacity, borderRadius.toPx())
        },
        content = content,
    )
}

private fun DrawScope.drawBeamBehind(
    size: BeamSize,
    variant: BeamColorVariant,
    theme: BeamTheme,
    strength: Float,
    seconds: Float,
    fade: Float,
) {
    if (size != BeamSize.PulseOutside || fade <= 0.002f || strength <= 0f) return
    drawPulseHalo(variant, theme, strength, seconds, fade)
}

private fun DrawScope.drawBeamFront(
    size: BeamSize,
    variant: BeamColorVariant,
    theme: BeamTheme,
    strength: Float,
    seconds: Float,
    fade: Float,
    radius: Float,
) {
    if (fade <= 0.002f || strength <= 0f) return
    when (size) {
        BeamSize.Md,
        BeamSize.Sm -> drawRotateBeam(size, variant, theme, strength, seconds, fade, radius)
        BeamSize.Line -> drawLineBeam(variant, theme, strength, seconds, fade, radius)
        BeamSize.PulseInner,
        BeamSize.PulseOutside ->
            drawPulseFront(size, variant, theme, strength, seconds, fade, radius)
    }
}
