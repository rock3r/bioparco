package dev.sebastiano.componentanatomy

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() {
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Component anatomy",
            state = rememberWindowState(size = DpSize(1000.dp, 720.dp)),
        ) {
            App()
        }
    }
}
