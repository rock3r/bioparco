package dev.sebastiano.thinkingorbs

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() {
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Thinking Orbs",
            state = rememberWindowState(size = DpSize(860.dp, 780.dp)),
        ) {
            App()
        }
    }
}
