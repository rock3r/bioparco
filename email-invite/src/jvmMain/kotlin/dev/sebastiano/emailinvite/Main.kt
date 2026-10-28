package dev.sebastiano.emailinvite

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.sebastiano.bioparco.tracing.TracedFrames
import dev.sebastiano.bioparco.tracing.startTracingFromEnvironment

fun main() {
    startTracingFromEnvironment("email-invite")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Email invite",
            state = rememberWindowState(size = DpSize(800.dp, 600.dp)),
        ) {
            TracedFrames()
            App()
        }
    }
}
