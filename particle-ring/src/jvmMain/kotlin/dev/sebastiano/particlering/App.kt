package dev.sebastiano.particlering

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

/**
 * A Health icon that bursts into a swirling ring of particles while it reads your data, then folds
 * them into a heart inside a ring. Click the icon to start; drag the ring to stir and tilt it.
 */
@Composable
fun App(modifier: Modifier = Modifier) {
    IntUiTheme(isDark = true) { ParticleRingStage(modifier.fillMaxSize()) }
}
