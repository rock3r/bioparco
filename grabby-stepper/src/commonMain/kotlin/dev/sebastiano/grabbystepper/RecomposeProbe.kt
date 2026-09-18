package dev.sebastiano.grabbystepper

import androidx.compose.runtime.Composable

@Composable expect fun RecomposeProbe(tag: String, enabled: Boolean = true)
