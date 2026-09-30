package dev.sebastiano.dotmatrixrecorder

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.sebastiano.bioparco.tracing.TracedFrames
import dev.sebastiano.bioparco.tracing.startTracingFromEnvironment

fun main() {
    startTracingFromEnvironment("dot-matrix-recorder")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Dot-matrix recorder",
            state = rememberWindowState(size = DpSize(360.dp, 280.dp)),
        ) {
            TracedFrames()
            App()
        }
    }
}
