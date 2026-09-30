package chatbubble

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.sebastiano.bioparco.tracing.TracedFrames
import dev.sebastiano.bioparco.tracing.startTracingFromEnvironment

fun main() {
    startTracingFromEnvironment("chat-bubble-transition")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Chat Bubble Transition",
            state = rememberWindowState(width = 420.dp, height = 760.dp),
        ) {
            TracedFrames()
            ChatApp()
        }
    }
}
