package dev.sebastiano.thinkingorbs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun App(modifier: Modifier = Modifier, initialDark: Boolean = true) {
    var dark by remember(initialDark) { mutableStateOf(initialDark) }
    var paused by remember { mutableStateOf(false) }
    var orbSize by remember { mutableStateOf(OrbSize.Regular) }
    val background = if (dark) Color(0xFF111113) else Color(0xFFF7F7F8)

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(background)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Thinking Orbs",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    "Nine dotted signals for what an AI is doing.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleButton("Dark", dark) { dark = true }
                ToggleButton("Light", !dark) { dark = false }
                ToggleButton("Regular", orbSize == OrbSize.Regular) { orbSize = OrbSize.Regular }
                ToggleButton("Small", orbSize == OrbSize.Small) { orbSize = OrbSize.Small }
                ToggleButton(if (paused) "Play" else "Pause", paused) { paused = !paused }
            }
            OrbGallery(size = orbSize, paused = paused, dark = dark)
            Text(
                "Ported from Haplo LLC’s ThinkingOrbs · Original engine by Jakub Antalik",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToggleButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color =
            if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
    ) {
        TextButton(onClick = onClick) {
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun OrbGallery(size: OrbSize, paused: Boolean, dark: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OrbDesign.entries.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { design ->
                    OrbCard(
                        design = design,
                        size = size,
                        paused = paused,
                        dark = dark,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun OrbCard(
    design: OrbDesign,
    size: OrbSize,
    paused: Boolean,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(180.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ThinkingOrb(
                    design = design,
                    size = size,
                    diameter = 72.dp,
                    isPaused = paused,
                    isDark = dark,
                )
            }
            Text(
                design.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                design.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
