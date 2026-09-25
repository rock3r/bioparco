package dev.sebastiano.borderbeam

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() {
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Border beam",
            state = rememberWindowState(size = DpSize(980.dp, 760.dp)),
        ) {
            App()
        }
    }
}
