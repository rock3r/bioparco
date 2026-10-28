package dev.sebastiano.particlering

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.sebastiano.bioparco.tracing.TracedFrames
import dev.sebastiano.bioparco.tracing.startTracingFromEnvironment

fun main() {
    startTracingFromEnvironment("particle-ring")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Particle ring",
            state = rememberWindowState(size = DpSize(560.dp, 680.dp)),
        ) {
            TracedFrames()
            App()
        }
    }
}
