package dev.sebastiano.borderbeam

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.SegmentedControl
import org.jetbrains.jewel.ui.component.SegmentedControlButtonData
import org.jetbrains.jewel.ui.component.Slider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.typography

/**
 * The playground stage: an `md` prompt card, an `sm` stop button and a `line` search bar, sized
 * like the libraries.dev demo, plus the two breathing sizes.
 */
@Composable
fun App(modifier: Modifier = Modifier) {
    var theme by remember { mutableStateOf(BeamTheme.Dark) }
    var variant by remember { mutableStateOf(BeamColorVariant.Colorful) }
    var strength by remember { mutableFloatStateOf(1f) }
    var active by remember { mutableStateOf(true) }
    val colors = StageColors.of(theme)

    IntUiTheme(isDark = theme == BeamTheme.Dark) {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(colors.page)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Border beam", style = JewelTheme.typography.h1TextStyle)
                Text(
                    "A glow that rides the edge of whatever it wraps.",
                    color = JewelTheme.globalColors.text.info,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Choice(
                    options = BeamTheme.entries,
                    selected = theme,
                    label = { it.name },
                    onSelect = { theme = it },
                )
                Choice(
                    options = BeamColorVariant.entries,
                    selected = variant,
                    label = { it.playgroundLabel },
                    onSelect = { variant = it },
                )
                OutlinedButton(onClick = { active = !active }) {
                    Text(if (active) "Pause" else "Play")
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Strength ${strengthLabel(strength)}")
                Slider(
                    value = strength,
                    onValueChange = { strength = it },
                    valueRange = 0f..1f,
                    modifier = Modifier.width(220.dp).height(24.dp),
                )
            }
            Stage(BeamSettings(variant, theme, strength, active), colors)
            Text(
                "Ported from Jakub Antalik’s border-beam · libraries.dev",
                style = JewelTheme.typography.small,
                color = JewelTheme.globalColors.text.info,
            )
        }
    }
}

private data class BeamSettings(
    val variant: BeamColorVariant,
    val theme: BeamTheme,
    val strength: Float,
    val active: Boolean,
)

private data class StageColors(
    val page: Color,
    val surface: Color,
    val raised: Color,
    val hairline: Color,
    val ink: Color,
    val muted: Color,
) {
    companion object {
        // Dark values are sampled from the libraries.dev playground.
        private val dark =
            StageColors(
                page = Color(0xFF141414),
                surface = Color(0xFF1A1A1A),
                raised = Color(0xFF242424),
                hairline = Color(0xFF232325),
                ink = Color(0xFFD4D4D8),
                muted = Color(0xFF5E5E63),
            )
        private val light =
            StageColors(
                page = Color(0xFFF4F4F5),
                surface = Color.White,
                raised = Color(0xFFF1F1F3),
                hairline = Color(0xFFE4E4E7),
                ink = Color(0xFF27272A),
                muted = Color(0xFFA1A1AA),
            )

        fun of(theme: BeamTheme): StageColors = if (theme == BeamTheme.Dark) dark else light
    }
}

@Composable
private fun Stage(settings: BeamSettings, colors: StageColors) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(36.dp),
    ) {
        PromptCard(settings, colors)
        Row(
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StopButton(settings, colors)
            SearchBar(settings, colors)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            PulseCard(BeamSize.PulseInner, settings, colors)
            PulseCard(BeamSize.PulseOutside, settings, colors)
        }
    }
}

@Composable
private fun Beam(
    size: BeamSize,
    settings: BeamSettings,
    dimensions: Pair<Dp, Dp>,
    radius: Dp,
    colors: StageColors,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    BorderBeam(
        modifier = Modifier.size(dimensions.first, dimensions.second),
        size = size,
        colorVariant = settings.variant,
        theme = settings.theme,
        strength = settings.strength,
        active = settings.active,
        borderRadius = radius,
    ) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(colors.surface, shape)
                    .border(1.dp, colors.hairline, shape)
        ) {
            content()
        }
    }
}

@Composable
private fun PromptCard(settings: BeamSettings, colors: StageColors) {
    Beam(BeamSize.Md, settings, 348.dp to 122.dp, 16.dp, colors) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            Box(
                modifier =
                    Modifier.size(24.dp)
                        .background(colors.raised, CircleShape)
                        .border(1.dp, colors.hairline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("@", color = colors.muted)
            }
            Text(
                "Build anything…",
                color = colors.muted,
                modifier = Modifier.padding(start = 4.dp, top = 10.dp),
            )
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip("Agent", colors)
                Spacer(Modifier.width(8.dp))
                Chip("Auto", colors)
                Spacer(Modifier.weight(1f))
                Glyph(colors, Modifier.size(28.dp)) { drawArrowUp(it) }
            }
        }
    }
}

@Composable
private fun StopButton(settings: BeamSettings, colors: StageColors) {
    Beam(BeamSize.Sm, settings, 36.dp to 36.dp, 18.dp, colors) {
        Canvas(Modifier.fillMaxSize()) {
            val side = 11.dp.toPx()
            drawRoundRect(
                color = colors.ink,
                topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f),
                size = Size(side, side),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
    }
}

@Composable
private fun SearchBar(settings: BeamSettings, colors: StageColors) {
    Beam(BeamSize.Line, settings, 366.dp to 42.dp, 21.dp, colors) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Canvas(Modifier.size(18.dp)) { drawMagnifier(colors.muted) }
            Text("Search", color = colors.muted)
        }
    }
}

@Composable
private fun PulseCard(size: BeamSize, settings: BeamSettings, colors: StageColors) {
    Beam(size, settings, 200.dp to 96.dp, 16.dp, colors) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(size.playgroundLabel, color = colors.muted)
        }
    }
}

@Composable
private fun Chip(label: String, colors: StageColors) {
    Row(
        modifier =
            Modifier.height(24.dp)
                .border(1.dp, colors.hairline, CircleShape)
                .background(colors.raised.copy(alpha = 0.5f), CircleShape)
                .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, color = colors.ink, style = JewelTheme.typography.small)
        Canvas(Modifier.size(width = 7.dp, height = 4.dp)) { drawChevron(colors.muted) }
    }
}

/** A round raised button face with a small line icon drawn by [icon]. */
@Composable
private fun Glyph(
    colors: StageColors,
    modifier: Modifier = Modifier,
    icon: DrawScope.(Color) -> Unit,
) {
    Canvas(modifier) {
        drawCircle(colors.raised)
        drawCircle(colors.hairline, style = Stroke(1.dp.toPx()))
        icon(colors.muted)
    }
}

private fun DrawScope.drawArrowUp(ink: Color) {
    val stroke = 1.4.dp.toPx()
    val half = size.minDimension * 0.2f
    val top = Offset(center.x, center.y - half)
    drawLine(ink, Offset(center.x, center.y + half), top, stroke, StrokeCap.Round)
    drawLine(ink, top, Offset(center.x - half * 0.8f, center.y), stroke, StrokeCap.Round)
    drawLine(ink, top, Offset(center.x + half * 0.8f, center.y), stroke, StrokeCap.Round)
}

private fun DrawScope.drawMagnifier(ink: Color) {
    val stroke = 1.6.dp.toPx()
    val r = size.width * 0.36f
    drawCircle(ink, r, Offset(r + 1f, r + 1f), style = Stroke(stroke))
    drawLine(
        ink,
        Offset(r * 1.75f, r * 1.75f),
        Offset(size.width - 1f, size.height - 1f),
        stroke,
        StrokeCap.Round,
    )
}

private fun DrawScope.drawChevron(ink: Color) {
    val stroke = 1.2.dp.toPx()
    val tip = Offset(size.width / 2f, size.height)
    drawLine(ink, Offset.Zero, tip, stroke, StrokeCap.Round)
    drawLine(ink, tip, Offset(size.width, 0f), stroke, StrokeCap.Round)
}

@Composable
private fun <T> Choice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    SegmentedControl(
        buttons =
            options.map { option ->
                SegmentedControlButtonData(
                    selected = option == selected,
                    content = { Text(label(option)) },
                    onSelect = { onSelect(option) },
                )
            }
    )
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
