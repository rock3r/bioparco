package dev.sebastiano.currencyswarm

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.sebastiano.bioparco.tracing.TracedFrames
import dev.sebastiano.bioparco.tracing.startTracingFromEnvironment

fun main() {
    startTracingFromEnvironment("currency-swarm")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Currency swarm",
            state = rememberWindowState(size = DpSize(560.dp, 680.dp)),
        ) {
            TracedFrames()
            App()
        }
    }
}
