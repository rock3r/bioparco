package dev.sebastiano.stampbook

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.sebastiano.bioparco.tracing.TracedFrames
import dev.sebastiano.bioparco.tracing.startTracingFromEnvironment

fun main() {
    startTracingFromEnvironment("stamp-book")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Stamp book",
            state = rememberWindowState(size = DpSize(800.dp, 600.dp)),
        ) {
            TracedFrames()
            App()
        }
    }
}
