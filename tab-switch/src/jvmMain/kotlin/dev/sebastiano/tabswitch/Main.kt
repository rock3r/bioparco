package dev.sebastiano.tabswitch

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.sebastiano.bioparco.tracing.TracedFrames
import dev.sebastiano.bioparco.tracing.startTracingFromEnvironment

fun main() {
    startTracingFromEnvironment("tab-switch")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Tab switch",
            state = rememberWindowState(size = DpSize(560.dp, 320.dp)),
        ) {
            TracedFrames()
            App()
        }
    }
}
