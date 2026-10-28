package dev.sebastiano.expandingsearch

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

/**
 * A round search button. Click it and it springs open into a field, with a panel of suggestions
 * unfolding underneath; type to filter them, and the cross clears the text, then folds it all away.
 */
@Composable
fun App(modifier: Modifier = Modifier) {
    IntUiTheme(isDark = false) { ExpandingSearchStage(modifier.fillMaxSize()) }
}
