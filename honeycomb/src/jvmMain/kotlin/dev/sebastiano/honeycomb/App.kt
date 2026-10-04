package dev.sebastiano.honeycomb

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

/** A black field of honeycomb circles. Drag to pan; there is no chrome. */
@Composable
fun App(modifier: Modifier = Modifier) {
    IntUiTheme(isDark = true) { HoneycombStage(modifier.fillMaxSize()) }
}
