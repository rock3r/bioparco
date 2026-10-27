package dev.sebastiano.squeezeprogress

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

/**
 * A progress line across a blue field. Pause it (click the button, or Space) and the line crumples
 * into a springy squiggle as the field turns orange; resume and it shakes out flat again.
 */
@Composable
fun App(modifier: Modifier = Modifier) {
    IntUiTheme(isDark = false) { SqueezeProgressStage(modifier.fillMaxSize()) }
}
