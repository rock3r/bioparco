package dev.sebastiano.thinkingbloom

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.sebastiano.bioparco.tracing.TracedFrames
import dev.sebastiano.bioparco.tracing.startTracingFromEnvironment

fun main() {
    startTracingFromEnvironment("thinking-bloom")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Thinking bloom",
            state = rememberWindowState(size = DpSize(960.dp, 600.dp)),
        ) {
            TracedFrames()
            App()
        }
    }
}
