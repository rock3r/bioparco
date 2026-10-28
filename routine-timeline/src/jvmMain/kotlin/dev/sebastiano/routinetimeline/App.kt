package dev.sebastiano.routinetimeline

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

/**
 * A day's routine on a half dial. The dial turns under a fixed marker as you drag it, scroll or
 * press ↑ ↓; the list of windows beside it follows, and Now spins back to the present.
 *
 * [fixedNow] freezes the present at that minute of the day (recordings use 9:52 AM, as the source
 * video starts); by default it follows the system clock.
 */
@Composable
fun App(modifier: Modifier = Modifier, fixedNow: Int? = null) {
    IntUiTheme(isDark = true) { RoutineTimelineStage(fixedNow, modifier.fillMaxSize()) }
}
