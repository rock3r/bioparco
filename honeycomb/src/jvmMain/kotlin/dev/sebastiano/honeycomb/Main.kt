package dev.sebastiano.honeycomb

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.sebastiano.bioparco.tracing.TracedFrames
import dev.sebastiano.bioparco.tracing.startTracingFromEnvironment

fun main() {
    startTracingFromEnvironment("honeycomb")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Honeycomb",
            state = rememberWindowState(size = DpSize(980.dp, 740.dp)),
        ) {
            TracedFrames()
            App()
        }
    }
}
