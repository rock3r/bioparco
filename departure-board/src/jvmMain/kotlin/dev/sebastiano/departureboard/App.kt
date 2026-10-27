package dev.sebastiano.departureboard

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

/**
 * A dot-matrix departure board of world clocks. Click the board to send every row through a
 * scramble; type in the search field to put another city on it.
 */
@Composable
fun App(modifier: Modifier = Modifier) {
    IntUiTheme(isDark = true) { DepartureBoardStage(modifier.fillMaxSize()) }
}
