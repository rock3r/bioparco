package dev.sebastiano.achievementbadge

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() {
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Achievement badge",
            state = rememberWindowState(size = DpSize(760.dp, 820.dp)),
        ) {
            App()
        }
    }
}
