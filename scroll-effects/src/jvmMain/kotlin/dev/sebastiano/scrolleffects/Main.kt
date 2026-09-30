package dev.sebastiano.scrolleffects

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.sebastiano.bioparco.tracing.TracedFrames
import dev.sebastiano.bioparco.tracing.startTracingFromEnvironment

fun main() {
    startTracingFromEnvironment("scroll-effects")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Scroll effects",
            state = rememberWindowState(size = DpSize(900.dp, 780.dp)),
        ) {
            TracedFrames()
            App()
        }
    }
}
