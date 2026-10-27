package dev.sebastiano.departureboard

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.sebastiano.bioparco.tracing.TracedFrames
import dev.sebastiano.bioparco.tracing.startTracingFromEnvironment

fun main() {
    startTracingFromEnvironment("departure-board")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Departure board",
            state = rememberWindowState(size = DpSize(1100.dp, 700.dp)),
        ) {
            TracedFrames()
            App()
        }
    }
}
