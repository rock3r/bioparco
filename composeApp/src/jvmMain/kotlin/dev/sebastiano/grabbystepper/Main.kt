package dev.sebastiano.grabbystepper

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Grabby stepper",
        state = rememberWindowState(size = DpSize(520.dp, 640.dp)),
    ) {
        App()
    }
}
