package dev.sebastiano.wheelpicker

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

/** A duration ruler on a dark field. Drag, flick, scroll, click or use the arrow keys. */
@Composable
fun App(modifier: Modifier = Modifier) {
    IntUiTheme(isDark = true) { WheelPickerStage(modifier.fillMaxSize()) }
}
