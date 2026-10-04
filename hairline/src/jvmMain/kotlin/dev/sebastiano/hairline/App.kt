package dev.sebastiano.hairline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.SegmentedControl
import org.jetbrains.jewel.ui.component.SegmentedControlButtonData
import org.jetbrains.jewel.ui.component.Slider
import org.jetbrains.jewel.ui.component.Text

/**
 * The catalogue: all nineteen figures on one shelf, each with its name and live caption, under one
 * intensity slider, a light/dark switch and a reduced-motion box.
 */
@Composable
fun App(modifier: Modifier = Modifier) {
    var intensity by remember { mutableFloatStateOf(DEFAULT_INTENSITY.toFloat()) }
    var dark by rememberSaveable { mutableStateOf(false) }
    var reduced by rememberSaveable { mutableStateOf(false) }
    val palette = if (dark) HairlinePalette.Dark else HairlinePalette.Light
    IntUiTheme(isDark = dark) {
        Column(modifier = modifier.fillMaxSize().background(palette.plate)) {
            Header(
                intensity = intensity,
                onIntensityChange = { intensity = it },
                dark = dark,
                onDarkChange = { dark = it },
                reduced = reduced,
                onReducedChange = { reduced = it },
                palette = palette,
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(250.dp),
                modifier = Modifier.fillMaxWidth().weight(1f).testTag(HairlineTags.GRID),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(Figure.entries, key = { it.id }) { figure ->
                    FigureCard(figure, intensity, dark, reduced, palette)
                }
            }
        }
    }
}

@Composable
private fun Header(
    intensity: Float,
    onIntensityChange: (Float) -> Unit,
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
    reduced: Boolean,
    onReducedChange: (Boolean) -> Unit,
    palette: HairlinePalette,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "HAIRLINE",
            style =
                TextStyle(fontSize = 10.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Medium),
            color = palette.edge,
        )
        Text(
            "Nineteen isometric figures that answer the pointer",
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium),
            color = palette.hi,
        )
        Text(
            "After Lucas Marques’s hairline · MIT",
            style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
            color = palette.edge,
        )
        Row(
            modifier = Modifier.padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Intensity", color = palette.hi)
            Slider(
                value = intensity,
                onValueChange = onIntensityChange,
                valueRange = 0f..1f,
                modifier = Modifier.width(180.dp).height(24.dp).testTag(HairlineTags.INTENSITY),
            )
            Text(
                toFixed(intensity.toDouble(), 2),
                fontFamily = FontFamily.Monospace,
                color = palette.edge,
                modifier = Modifier.padding(end = 12.dp),
            )
            SegmentedControl(
                buttons =
                    listOf(false to "Light", true to "Dark").map { (isDark, label) ->
                        SegmentedControlButtonData(
                            selected = dark == isDark,
                            content = { Text(label) },
                            onSelect = { onDarkChange(isDark) },
                        )
                    },
                modifier = Modifier.testTag(HairlineTags.THEME),
            )
            CheckboxRow(
                text = "Reduced motion",
                checked = reduced,
                onCheckedChange = onReducedChange,
            )
        }
    }
}

@Composable
private fun FigureCard(
    figure: Figure,
    intensity: Float,
    dark: Boolean,
    reduced: Boolean,
    palette: HairlinePalette,
) {
    var caption by remember(figure) { mutableStateOf(figure.rest) }
    Column(
        modifier =
            Modifier.border(1.dp, palette.lo)
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                figure.title,
                modifier = Modifier.weight(1f),
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                color = palette.hi,
            )
            Text(
                caption,
                style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                color = palette.edge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            HairlineFigure(
                figure = figure,
                modifier = Modifier.fillMaxWidth().testTag(HairlineTags.figure(figure)),
                intensity = intensity,
                dark = dark,
                reducedMotion = reduced,
                onRead = { caption = it },
            )
        }
    }
}
