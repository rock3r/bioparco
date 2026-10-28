package dev.sebastiano.layoutmorph

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.sebastiano.bioparco.tracing.TracedFrames
import dev.sebastiano.bioparco.tracing.startTracingFromEnvironment

fun main() {
    startTracingFromEnvironment("layout-morph")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Layout morph",
            state = rememberWindowState(size = DpSize(960.dp, 660.dp)),
        ) {
            TracedFrames()
            App()
        }
    }
}
