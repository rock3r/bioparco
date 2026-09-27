package dev.sebastiano.scrolleffects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.SegmentedControl
import org.jetbrains.jewel.ui.component.SegmentedControlButtonData
import org.jetbrains.jewel.ui.component.Text

/**
 * The page: a header naming the current effect, the carousel stage, and a tab per effect. Drag the
 * cards, scroll, or use the arrow keys.
 */
@Composable
fun App(modifier: Modifier = Modifier) {
    var effect by remember { mutableStateOf(CarouselEffect.Stretch) }
    ScrollEffectsPage(effect, { effect = it }, rememberCarouselState(), modifier)
}

@Composable
internal fun ScrollEffectsPage(
    effect: CarouselEffect,
    onEffectChange: (CarouselEffect) -> Unit,
    carousel: CarouselState,
    modifier: Modifier = Modifier,
) {
    IntUiTheme(isDark = false) {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(PageColors.page)
                    .drawBehind { dotGrid() }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionLeft -> carousel.step(-1)
                            Key.DirectionRight -> carousel.step(1)
                            else -> return@onPreviewKeyEvent false
                        }
                        true
                    }
        ) {
            Header(effect)
            Box(modifier = Modifier.fillMaxWidth().weight(1f).background(PageColors.stage)) {
                ScrollCarousel(effect, carousel, Modifier.fillMaxSize())
                Text(
                    "Drag, scroll or use ← →",
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                    style = TextStyle(fontSize = 11.sp),
                    color = PageColors.hint,
                )
            }
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                EffectTabs(effect, onEffectChange)
            }
        }
    }
}

@Composable
private fun Header(effect: CarouselEffect) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "CAROUSEL",
                style =
                    TextStyle(
                        fontSize = 10.sp,
                        letterSpacing = 1.6.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                color = PageColors.muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${effect.number}",
                    style = TextStyle(fontSize = 24.sp),
                    color = PageColors.faint,
                )
                Text(
                    effect.label,
                    style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium),
                    color = PageColors.ink,
                )
            }
        }
        Text(
            "6 sample cards · Compose",
            style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
            color = PageColors.faint,
        )
    }
}

@Composable
private fun EffectTabs(selected: CarouselEffect, onSelect: (CarouselEffect) -> Unit) {
    SegmentedControl(
        buttons =
            CarouselEffect.entries.map { effect ->
                SegmentedControlButtonData(
                    selected = effect == selected,
                    content = {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("${effect.number}", color = PageColors.faint)
                            Text(effect.label)
                        }
                    },
                    onSelect = { onSelect(effect) },
                )
            }
    )
}

/** The dotted paper behind the header and the tabs. The stage band covers the middle. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.dotGrid() {
    val step = 14.dp.toPx()
    val radius = 0.9.dp.toPx()
    var y = step / 2f
    while (y < size.height) {
        var x = step / 2f
        while (x < size.width) {
            drawCircle(PageColors.dot, radius, Offset(x, y))
            x += step
        }
        y += step
    }
}

private object PageColors {
    val page = Color(0xFFEDEDED)
    val stage = Color(0xFFF3F3F3)
    val dot = Color(0xFFD6D6D6)
    val ink = Color(0xFF151515)
    val muted = Color(0xFF7A7A7A)
    val faint = Color(0xFFA3A3A3)
    val hint = Color(0xFF9A9A9A)
}
