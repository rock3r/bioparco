package dev.sebastiano.componentanatomy

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.ButtonStyle
import org.jetbrains.jewel.ui.theme.defaultButtonStyle
import org.jetbrains.jewel.ui.theme.outlinedButtonStyle

/**
 * The anatomy is only honest if its layers, stacked flat, are the real Jewel button. This test
 * draws both, for every style, state and theme, and asks for the same pixels.
 */
class ButtonParityTest {
    @Test
    fun theCollapsedStackIsTheRealButton() {
        val mismatches = mutableListOf<String>()
        for (dark in listOf(true, false)) {
            for (outlined in listOf(false, true)) {
                for (state in AnatomyState.entries) {
                    val real = render(dark) { RealButton(outlined, state) }
                    val forked = render(dark) { ForkedButton(outlined, state) }
                    val diff = countDifferentPixels(real, forked)
                    if (diff > 0) {
                        mismatches += "dark=$dark outlined=$outlined $state: $diff pixels differ"
                    }
                }
            }
        }
        assertEquals(emptyList<String>(), mismatches)
    }

    @Composable
    private fun RealButton(outlined: Boolean, state: AnatomyState) {
        val source = remember { MutableInteractionSource() }
        LaunchedEffect(source) {
            // Wait a frame, so the button's own collector is listening before we emit.
            withFrameNanos {}
            state.interactions().forEach { source.emit(it) }
        }
        val enabled = state != AnatomyState.Disabled
        if (outlined) {
            OutlinedButton(onClick = {}, enabled = enabled, interactionSource = source) {
                Text(LABEL)
            }
        } else {
            DefaultButton(onClick = {}, enabled = enabled, interactionSource = source) {
                Text(LABEL)
            }
        }
    }

    @Composable
    private fun ForkedButton(outlined: Boolean, state: AnatomyState) {
        val style: ButtonStyle =
            if (outlined) JewelTheme.outlinedButtonStyle else JewelTheme.defaultButtonStyle
        val paint = style.colors.lookFor(state.toButtonState()).toPaint()
        CollapsedButton(paint = paint, style = style) { Text(LABEL) }
    }

    private fun AnatomyState.interactions(): List<Interaction> =
        when (this) {
            AnatomyState.Normal,
            AnatomyState.Disabled -> emptyList()
            AnatomyState.Hovered -> listOf(HoverInteraction.Enter())
            AnatomyState.Pressed ->
                listOf(HoverInteraction.Enter(), PressInteraction.Press(Offset.Zero))
            AnatomyState.Focused -> listOf(FocusInteraction.Focus())
        }

    private fun render(dark: Boolean, content: @Composable () -> Unit): IntArray {
        val scene =
            ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(2f)) {
                IntUiTheme(isDark = dark) {
                    Box(Modifier.fillMaxSize().background(STAGE).padding(12.dp)) { content() }
                }
            }
        try {
            repeat(FRAMES) { scene.render(it * FRAME_NANOS) }
            val pixels = scene.render(FRAMES * FRAME_NANOS).toComposeImageBitmap().toPixelMap()
            return IntArray(WIDTH * HEIGHT) { pixels[it % WIDTH, it / WIDTH].hashCode() }
        } finally {
            scene.close()
        }
    }

    private fun countDifferentPixels(a: IntArray, b: IntArray) = a.indices.count { a[it] != b[it] }

    private companion object {
        const val LABEL = "Anatomy"
        const val WIDTH = 320
        const val HEIGHT = 120
        const val FRAMES = 8
        const val FRAME_NANOS = 16_000_000L
        val STAGE = Color(0xFF15161A)
    }
}
