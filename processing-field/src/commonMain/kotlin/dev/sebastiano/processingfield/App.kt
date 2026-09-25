package dev.sebastiano.processingfield

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.RadioButtonChip
import org.jetbrains.jewel.ui.component.Slider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.typography

@Composable
fun App(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) DarkFieldColors else LightFieldColors

    var style by remember { mutableStateOf(ProcessingFieldStyle.Dots) }
    var isRunning by remember { mutableStateOf(true) }
    var forceStill by remember { mutableStateOf(false) }

    var pitch by remember { mutableDoubleStateOf(Defaults.pitch) }
    var reach by remember { mutableDoubleStateOf(Defaults.reach) }
    var softness by remember { mutableDoubleStateOf(Defaults.softness) }
    var radiusFloor by remember { mutableDoubleStateOf(Defaults.radiusFloor) }
    var radiusCeiling by remember { mutableDoubleStateOf(Defaults.radiusCeiling) }
    var inkFloor by remember { mutableDoubleStateOf(Defaults.inkFloor) }
    var inkCeiling by remember { mutableDoubleStateOf(Defaults.inkCeiling) }
    var driftSpeed by remember { mutableDoubleStateOf(Defaults.driftSpeed) }
    var foldSpeed by remember { mutableDoubleStateOf(Defaults.foldSpeed) }

    val showsStill = forceStill

    IntUiTheme(isDark = dark) {
        // The field and plain text take the specimen's own ink, not Jewel's content colour.
        CompositionLocalProvider(LocalContentColor provides colors.ink) {
            Box(modifier = modifier.fillMaxSize().background(colors.background)) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    ProcessingField(
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                        style = style,
                        isActive = isRunning && !showsStill,
                        pitch = pitch.dp,
                        reach = reach,
                        softness = softness,
                        minRadiusRatio = radiusFloor,
                        maxRadiusRatio = radiusCeiling,
                        minInk = inkFloor,
                        maxInk = inkCeiling,
                        driftSpeed = driftSpeed,
                        foldSpeed = foldSpeed,
                    )
                    Text(
                        text =
                            when {
                                showsStill -> "Still frame. The same field, simply not moving."
                                !isRunning -> "Parked. The timeline leaves the tree entirely."
                                else ->
                                    "The mark centres never move. Only their size and their ink do."
                            },
                        style = JewelTheme.typography.small,
                        color = colors.muted,
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(top = 8.dp, bottom = 2.dp),
                    )
                    Column(
                        modifier =
                            Modifier.weight(1f)
                                .fillMaxWidth()
                                .testTag(ProcessingFieldTags.CONTROLS)
                                .verticalScroll(rememberScrollState())
                    ) {
                        SectionLabel("Mark", colors)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProcessingFieldStyle.entries.forEach { option ->
                                RadioButtonChip(
                                    selected = option == style,
                                    onClick = { style = option },
                                    modifier =
                                        Modifier.testTag(
                                            if (option == ProcessingFieldStyle.Dots) {
                                                ProcessingFieldTags.STYLE_DOTS
                                            } else {
                                                ProcessingFieldTags.STYLE_LINES
                                            }
                                        ),
                                ) {
                                    Text(option.label)
                                }
                            }
                        }

                        SectionLabel("State", colors)
                        ToggleRow(
                            label = "Running",
                            checked = isRunning,
                            tag = ProcessingFieldTags.RUNNING,
                        ) {
                            isRunning = it
                        }
                        ToggleRow(
                            label = "Still frame",
                            checked = showsStill,
                            tag = ProcessingFieldTags.STILL,
                        ) {
                            forceStill = it
                        }

                        SectionLabel("Grid", colors)
                        Knob(
                            colors = colors,
                            label = "Pitch",
                            value = pitch,
                            range = 6.0..32.0,
                            tag = ProcessingFieldTags.PITCH,
                            decimals = 0,
                            suffix = " dp",
                        ) {
                            pitch = it
                        }
                        Knob(
                            colors = colors,
                            label = "Reach",
                            value = reach,
                            range = 0.12..0.70,
                            tag = ProcessingFieldTags.REACH,
                        ) {
                            reach = it
                        }
                        Knob(
                            colors = colors,
                            label = "Softness",
                            value = softness,
                            range = 0.04..0.80,
                            tag = ProcessingFieldTags.SOFTNESS,
                        ) {
                            softness = it
                        }
                        Knob(
                            colors = colors,
                            label = "Drift speed",
                            value = driftSpeed,
                            range = 0.0..3.0,
                            tag = ProcessingFieldTags.DRIFT_SPEED,
                            suffix = "x",
                        ) {
                            driftSpeed = it
                        }
                        Knob(
                            colors = colors,
                            label = "Fold speed",
                            value = foldSpeed,
                            range = 0.0..3.0,
                            tag = ProcessingFieldTags.FOLD_SPEED,
                            suffix = "x",
                        ) {
                            foldSpeed = it
                        }

                        SectionLabel("Weight of a mark", colors)
                        Knob(
                            colors = colors,
                            label = "Radius floor",
                            value = radiusFloor,
                            range = 0.0..0.20,
                            tag = ProcessingFieldTags.RADIUS_FLOOR,
                            decimals = 3,
                        ) {
                            radiusFloor = min(it, radiusCeiling - 0.005)
                        }
                        Knob(
                            colors = colors,
                            label = "Radius ceiling",
                            value = radiusCeiling,
                            range = 0.02..0.45,
                            tag = ProcessingFieldTags.RADIUS_CEILING,
                            decimals = 3,
                        ) {
                            radiusCeiling = max(it, radiusFloor + 0.005)
                        }
                        Knob(
                            colors = colors,
                            label = "Ink floor",
                            value = inkFloor,
                            range = 0.0..0.70,
                            tag = ProcessingFieldTags.INK_FLOOR,
                        ) {
                            inkFloor = min(it, inkCeiling - 0.005)
                        }
                        Knob(
                            colors = colors,
                            label = "Ink ceiling",
                            value = inkCeiling,
                            range = 0.05..1.0,
                            tag = ProcessingFieldTags.INK_CEILING,
                        ) {
                            inkCeiling = max(it, inkFloor + 0.005)
                        }

                        OutlinedButton(
                            onClick = {
                                style = ProcessingFieldStyle.Dots
                                isRunning = true
                                forceStill = false
                                pitch = Defaults.pitch
                                reach = Defaults.reach
                                softness = Defaults.softness
                                radiusFloor = Defaults.radiusFloor
                                radiusCeiling = Defaults.radiusCeiling
                                inkFloor = Defaults.inkFloor
                                inkCeiling = Defaults.inkCeiling
                                driftSpeed = Defaults.driftSpeed
                                foldSpeed = Defaults.foldSpeed
                            },
                            modifier =
                                Modifier.padding(vertical = 8.dp)
                                    .testTag(ProcessingFieldTags.RESET),
                        ) {
                            Text("Reset")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, colors: FieldColors) {
    Column {
        Divider(
            orientation = Orientation.Horizontal,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        )
        Text(
            text = text,
            style = JewelTheme.typography.h4TextStyle,
            color = colors.ink,
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    tag: String,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.testTag(tag),
        )
    }
}

@Composable
private fun Knob(
    colors: FieldColors,
    label: String,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    tag: String,
    decimals: Int = 2,
    suffix: String = "",
    onValueChange: (Double) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f))
            Text(
                text = formatFixed(value, decimals) + suffix,
                fontFamily = FontFamily.Monospace,
                color = colors.muted,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toDouble()) },
            valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
            modifier = Modifier.height(24.dp).testTag(tag),
        )
    }
}

private fun formatFixed(value: Double, decimals: Int): String {
    var factor = 1.0
    repeat(decimals) { factor *= 10.0 }
    val rounded = round(value * factor) / factor
    if (decimals == 0) return rounded.toInt().toString()
    val raw = rounded.toString()
    val dot = raw.indexOf('.')
    if (dot < 0) return raw + "." + "0".repeat(decimals)
    val present = raw.length - dot - 1
    return if (present >= decimals) {
        raw.substring(0, dot + 1 + decimals)
    } else {
        raw + "0".repeat(decimals - present)
    }
}

private class FieldColors(val background: Color, val ink: Color, val muted: Color)

private val DarkFieldColors =
    FieldColors(background = Color.Black, ink = Color(0xFFF2F2F2), muted = Color(0xFFA3A3A3))

private val LightFieldColors =
    FieldColors(background = Color.White, ink = Color(0xFF1C1C1C), muted = Color(0xFF6B6B6B))

private object Defaults {
    const val pitch = 14.0
    const val reach = 0.22
    const val softness = 0.34
    const val radiusFloor = 0.085
    const val radiusCeiling = 0.19
    const val inkFloor = 0.30
    const val inkCeiling = 0.85
    const val driftSpeed = 1.0
    const val foldSpeed = 1.0
}
