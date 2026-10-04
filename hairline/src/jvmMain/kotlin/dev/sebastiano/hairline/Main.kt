package dev.sebastiano.hairline

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.sebastiano.bioparco.tracing.TracedFrames
import dev.sebastiano.bioparco.tracing.startTracingFromEnvironment

fun main() {
    startTracingFromEnvironment("hairline")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Hairline",
            state = rememberWindowState(size = DpSize(1180.dp, 860.dp)),
        ) {
            TracedFrames()
            App()
        }
    }
}
