package dev.sebastiano.anatomy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sebastiano.anatomy.figures.BunsenBurnerFigure
import dev.sebastiano.anatomy.figures.DialIndicatorFigure
import dev.sebastiano.anatomy.figures.EspressoMachineFigure
import dev.sebastiano.anatomy.figures.TokenBucketFigure
import dev.sebastiano.anatomy.kit.Palette
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.SegmentedControl
import org.jetbrains.jewel.ui.component.SegmentedControlButtonData
import org.jetbrains.jewel.ui.component.Text

/** The figures built with the kit, in the order the page shows them. */
enum class Figure(val id: String, val title: String) {
    DialIndicator("dial-indicator", "Dial indicator"),
    TokenBucket("token-bucket", "Token bucket"),
    BunsenBurner("bunsen-burner", "Bunsen burner"),
    EspressoMachine("espresso-machine", "Espresso machine");

    companion object {
        fun of(id: String): Figure? = entries.firstOrNull { it.id == id }
    }
}

/** One figure in its card. */
@Composable
fun FigureContent(
    figure: Figure,
    palette: Palette,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    when (figure) {
        Figure.DialIndicator -> DialIndicatorFigure(palette, reducedMotion, modifier)
        Figure.TokenBucket -> TokenBucketFigure(palette, reducedMotion, modifier)
        Figure.BunsenBurner -> BunsenBurnerFigure(palette, reducedMotion, modifier)
        Figure.EspressoMachine -> EspressoMachineFigure(palette, reducedMotion, modifier)
    }
}

/** The page: every figure in its card, under a light/dark switch and a reduced-motion box. */
@Composable
fun App(modifier: Modifier = Modifier) {
    var dark by rememberSaveable { mutableStateOf(false) }
    var reduced by rememberSaveable { mutableStateOf(false) }
    val palette = if (dark) Palette.Dark else Palette.Light
    IntUiTheme(isDark = dark) {
        Column(
            modifier.fillMaxSize().background(palette.page).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = PAGE_WIDTH.dp).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                Header(dark, { dark = it }, reduced, { reduced = it }, palette)
                for (figure in Figure.entries) {
                    FigureContent(
                        figure,
                        palette,
                        reduced,
                        Modifier.fillMaxWidth().testTag(AnatomyTags.figure(figure)),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
    reduced: Boolean,
    onReducedChange: (Boolean) -> Unit,
    palette: Palette,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "ANATOMY",
            style =
                TextStyle(fontSize = 10.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Medium),
            color = palette.muted,
        )
        Text(
            "Ideas explained by building them as real, working objects",
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium),
            color = palette.strong,
        )
        Text(
            "After Ryan’s Anatomy · MIT",
            style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
            color = palette.muted,
        )
        Row(
            Modifier.padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SegmentedControl(
                buttons =
                    listOf(false to "Light", true to "Dark").map { (isDark, label) ->
                        SegmentedControlButtonData(
                            selected = dark == isDark,
                            content = { Text(label) },
                            onSelect = { onDarkChange(isDark) },
                        )
                    }
            )
            CheckboxRow(
                text = "Reduced motion",
                checked = reduced,
                onCheckedChange = onReducedChange,
            )
        }
    }
}

/** Test tags for recordings and tests. */
object AnatomyTags {
    fun figure(figure: Figure): String = "anatomy-${figure.id}"
}

/** The original's page width, in dp. */
const val PAGE_WIDTH = 650
