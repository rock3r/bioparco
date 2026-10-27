package dev.sebastiano.wheelpicker

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.sebastiano.bioparco.tracing.TracedFrames
import dev.sebastiano.bioparco.tracing.startTracingFromEnvironment

fun main() {
    startTracingFromEnvironment("wheel-picker")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Wheel picker",
            state = rememberWindowState(size = DpSize(920.dp, 560.dp)),
        ) {
            TracedFrames()
            App()
        }
    }
}
