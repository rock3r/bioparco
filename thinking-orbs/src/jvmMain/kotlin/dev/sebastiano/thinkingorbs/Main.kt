package dev.sebastiano.thinkingorbs

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.sebastiano.bioparco.tracing.TracedFrames
import dev.sebastiano.bioparco.tracing.startTracingFromEnvironment

fun main() {
    startTracingFromEnvironment("thinking-orbs")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Thinking Orbs",
            state = rememberWindowState(size = DpSize(860.dp, 780.dp)),
        ) {
            TracedFrames()
            App()
        }
    }
}
