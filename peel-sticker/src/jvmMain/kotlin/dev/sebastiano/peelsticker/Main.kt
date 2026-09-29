package dev.sebastiano.peelsticker

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() {
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Peel sticker",
            state = rememberWindowState(size = DpSize(760.dp, 690.dp)),
        ) {
            App()
        }
    }
}
