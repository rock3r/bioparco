package dev.sebastiano.bioparco.showcase

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

fun main() {
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "bioparco",
            state = rememberWindowState(size = DpSize(720.dp, 820.dp)),
        ) {
            IntUiTheme(isDark = true) { ShowcaseApp() }
        }
    }
}
