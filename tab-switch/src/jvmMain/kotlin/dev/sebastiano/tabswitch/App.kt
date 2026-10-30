package dev.sebastiano.tabswitch

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

/**
 * A tab bar with a light pill behind the selected tab. Click another tab, or use the arrow keys,
 * and the pill inches across to it: its back end leaves first, its front end follows, and the icons
 * it passes over turn dark and solid where it covers them.
 */
@Composable
fun App(modifier: Modifier = Modifier) {
    IntUiTheme(isDark = true) { TabSwitchStage(modifier.fillMaxSize()) }
}
