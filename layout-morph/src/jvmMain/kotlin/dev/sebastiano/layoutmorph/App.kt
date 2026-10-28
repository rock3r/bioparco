package dev.sebastiano.layoutmorph

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

/**
 * One shop collection, six ways to browse it: grid, masonry, list, a fanned stack, a carousel and a
 * turning helix. Switch the view and every card flies to its new place.
 */
@Composable
fun App(modifier: Modifier = Modifier) {
    IntUiTheme(isDark = false) { LayoutMorphStage(modifier.fillMaxSize()) }
}

/** The app around a [motion] the caller drives: for benchmarks and captures. */
@Composable
internal fun App(motion: MorphMotion, modifier: Modifier = Modifier) {
    IntUiTheme(isDark = false) { LayoutMorphStage(modifier.fillMaxSize(), motion) }
}
