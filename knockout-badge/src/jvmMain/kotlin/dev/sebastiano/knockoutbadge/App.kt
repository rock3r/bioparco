package dev.sebastiano.knockoutbadge

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

/**
 * A conference badge that is a black sticker with white ink, multiplied over a video, so the word
 * is a window onto the footage. Hover to tilt it, drag its corner to peel the sticker back, click
 * to take it apart into its layers, and type to change the word.
 */
@Composable
fun App(modifier: Modifier = Modifier) {
    IntUiTheme(isDark = true) { KnockoutBadgeStage(modifier.fillMaxSize()) }
}
