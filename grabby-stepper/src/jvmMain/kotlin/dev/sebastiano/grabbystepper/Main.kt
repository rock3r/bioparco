package dev.sebastiano.grabbystepper

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.sebastiano.bioparco.tracing.TracedFrames
import dev.sebastiano.bioparco.tracing.startTracingFromEnvironment

fun main() {
    startTracingFromEnvironment("grabby-stepper")
    // Prefer Metal; avoid software Skia fallback jank on Desktop.
    System.setProperty("skiko.renderApi", "METAL")
    System.err.println("[skiko] requested renderApi=${System.getProperty("skiko.renderApi")}")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Grabby stepper",
            state = rememberWindowState(size = DpSize(520.dp, 640.dp)),
        ) {
            FrameFpsProbe("grabby", enabled = DEBUG_MOTION_PROBES)
            TracedFrames()
            App()
        }
    }
}
