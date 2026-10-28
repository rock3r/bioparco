package dev.sebastiano.stampbook

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

/**
 * A frosted stamp book on a grey table. Hover it and the cover swings open while two stamps slide
 * out to the right; move away and they tuck back in. The dots underneath recolour the book.
 */
@Composable
fun App(modifier: Modifier = Modifier) {
    IntUiTheme(isDark = false) { StampBookStage(modifier.fillMaxSize()) }
}
