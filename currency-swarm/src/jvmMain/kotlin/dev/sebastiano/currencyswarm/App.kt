package dev.sebastiano.currencyswarm

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

/**
 * A cloud of dots that settles into one currency sign after another. Move the pointer through them;
 * click, pick a chip or use the arrow keys to change sign.
 */
@Composable
fun App(modifier: Modifier = Modifier) {
    IntUiTheme(isDark = true) { CurrencySwarmStage(modifier.fillMaxSize()) }
}
