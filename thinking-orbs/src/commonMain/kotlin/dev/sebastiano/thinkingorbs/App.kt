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
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.SegmentedControl
import org.jetbrains.jewel.ui.component.SegmentedControlButtonData
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.typography

@Composable
fun App(modifier: Modifier = Modifier, initialDark: Boolean = true) {
    var dark by remember(initialDark) { mutableStateOf(initialDark) }
    var paused by remember { mutableStateOf(false) }
    var orbSize by remember { mutableStateOf(OrbSize.Regular) }
    val background = if (dark) Color(0xFF111113) else Color(0xFFF7F7F8)

    // Card colours match what the Material surfaceVariant tint used to look like here.
    val card = if (dark) Color(0xFF302E34) else Color(0xFFEEEAF1)

    IntUiTheme(isDark = dark) {
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
                Text("Thinking Orbs", style = JewelTheme.typography.h1TextStyle)
                Text(
                    "Nine dotted signals for what an AI is doing.",
                    color = JewelTheme.globalColors.text.info,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Choice(first = "Dark", second = "Light", firstSelected = dark) { dark = it }
                Choice(
                    first = "Regular",
                    second = "Small",
                    firstSelected = orbSize == OrbSize.Regular,
                ) {
                    orbSize = if (it) OrbSize.Regular else OrbSize.Small
                }
                OutlinedButton(onClick = { paused = !paused }) {
                    Text(if (paused) "Play" else "Pause")
                }
            }
            OrbGallery(size = orbSize, paused = paused, dark = dark, card = card)
            Text(
                "Ported from Haplo LLC’s ThinkingOrbs · Original engine by Jakub Antalik",
                style = JewelTheme.typography.small,
                color = JewelTheme.globalColors.text.info,
            )
        }
    }
}

/** Two mutually exclusive options. [onChange] receives `true` when [first] is picked. */
@Composable
private fun Choice(
    first: String,
    second: String,
    firstSelected: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SegmentedControl(
        buttons =
            listOf(
                SegmentedControlButtonData(
                    selected = firstSelected,
                    content = { Text(first) },
                    onSelect = { onChange(true) },
                ),
                SegmentedControlButtonData(
                    selected = !firstSelected,
                    content = { Text(second) },
                    onSelect = { onChange(false) },
                ),
            )
    )
}

@Composable
private fun OrbGallery(size: OrbSize, paused: Boolean, dark: Boolean, card: Color) {
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
                        card = card,
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
    card: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.height(180.dp).background(card, RoundedCornerShape(18.dp))) {
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
            Text(design.title, fontWeight = FontWeight.SemiBold)
            Text(
                design.summary,
                style = JewelTheme.typography.small,
                color = JewelTheme.globalColors.text.info,
            )
        }
    }
}
