package dev.sebastiano.processingfield

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() {
    System.setProperty("skiko.renderApi", "METAL")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Processing field",
            state = rememberWindowState(size = DpSize(520.dp, 760.dp)),
        ) {
            App()
        }
    }
}
