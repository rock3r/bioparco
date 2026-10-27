package dev.sebastiano.holocard

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

/**
 * A holographic trading card on a white table. It tilts towards the pointer, drags around in 3D,
 * and its foil shifts colour as it turns. The dock swaps cards and starts a hands-off demo.
 */
@Composable
fun App(modifier: Modifier = Modifier) {
    IntUiTheme(isDark = false) { HoloCardStage(modifier.fillMaxSize()) }
}
