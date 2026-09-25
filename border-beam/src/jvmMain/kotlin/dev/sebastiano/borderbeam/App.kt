package dev.sebastiano.borderbeam

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun App(modifier: Modifier = Modifier) {
    var theme by remember { mutableStateOf(BeamTheme.Dark) }
    var size by remember { mutableStateOf(BeamSize.Md) }
    var variant by remember { mutableStateOf(BeamColorVariant.Colorful) }
    var strength by remember { mutableFloatStateOf(0.7f) }
    var active by remember { mutableStateOf(true) }
    val dark = theme == BeamTheme.Dark
    val background = if (dark) Color(0xFF111113) else Color(0xFFF4F4F5)

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(background)
                    .padding(horizontal = 28.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Border beam",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    "A glow that rides the edge of whatever it wraps.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToggleButton("Dark", theme == BeamTheme.Dark) { theme = BeamTheme.Dark }
                ToggleButton("Light", theme == BeamTheme.Light) { theme = BeamTheme.Light }
                BeamSize.entries.forEach { option ->
                    ToggleButton(option.playgroundLabel, size == option) { size = option }
                }
                BeamColorVariant.entries.forEach { option ->
                    ToggleButton(option.playgroundLabel, variant == option) { variant = option }
                }
                ToggleButton(if (active) "Pause" else "Play", !active) { active = !active }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Strength ${strengthLabel(strength)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(value = strength, onValueChange = { strength = it }, valueRange = 0f..1f)
            }
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                DemoCard(
                    size = size,
                    variant = variant,
                    theme = theme,
                    strength = strength,
                    active = active,
                )
            }
            Text(
                "Ported from Jakub Antalik’s border-beam · libraries.dev",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DemoCard(
    size: BeamSize,
    variant: BeamColorVariant,
    theme: BeamTheme,
    strength: Float,
    active: Boolean,
) {
    val dark = theme == BeamTheme.Dark
    val card = if (dark) Color(0xFF1C1C1F) else Color.White
    val hairline = if (dark) Color(0xFF3A3A3E) else Color(0xFFE4E4E7)
    val radius = if (size == BeamSize.Sm) 32.dp else 16.dp
    BorderBeam(
        modifier = Modifier.size(size.cardWidth, size.cardHeight),
        size = size,
        colorVariant = variant,
        theme = theme,
        strength = strength,
        active = active,
        borderRadius = radius,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = card,
            shape = RoundedCornerShape(radius),
            border = BorderStroke(1.dp, hairline),
        ) {
            Column(
                modifier = Modifier.padding(if (size == BeamSize.Sm) 0.dp else 22.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment =
                    if (size == BeamSize.Sm || size == BeamSize.Line) Alignment.CenterHorizontally
                    else Alignment.Start,
            ) {
                Text(
                    size.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (
                    size == BeamSize.Md ||
                        size == BeamSize.PulseInner ||
                        size == BeamSize.PulseOutside
                ) {
                    Text(
                        "The beam stays on the border. The card underneath does not move.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
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

private fun strengthLabel(strength: Float): String {
    val tenths = (strength * 10f).roundToInt().coerceIn(0, 10)
    return "${tenths / 10}.${tenths % 10}"
}

private val BeamSize.playgroundLabel: String
    get() =
        when (this) {
            BeamSize.Sm -> "sm"
            BeamSize.Md -> "md"
            BeamSize.Line -> "line"
            BeamSize.PulseInner -> "pulse-inner"
            BeamSize.PulseOutside -> "pulse-outside"
        }

private val BeamColorVariant.playgroundLabel: String
    get() =
        when (this) {
            BeamColorVariant.Colorful -> "colorful"
            BeamColorVariant.Mono -> "mono"
            BeamColorVariant.Ocean -> "ocean"
            BeamColorVariant.Sunset -> "sunset"
        }

private val BeamSize.cardWidth: Dp
    get() =
        when (this) {
            BeamSize.Sm -> 132.dp
            BeamSize.Line -> 440.dp
            else -> 380.dp
        }

private val BeamSize.cardHeight: Dp
    get() =
        when (this) {
            BeamSize.Sm -> 44.dp
            BeamSize.Line -> 56.dp
            else -> 196.dp
        }

private val BeamSize.cardTitle: String
    get() =
        when (this) {
            BeamSize.Sm -> "Open"
            BeamSize.Line -> "Search the park"
            else -> "Border beam"
        }
