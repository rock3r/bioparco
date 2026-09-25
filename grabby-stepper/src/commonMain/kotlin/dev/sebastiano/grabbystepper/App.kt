package dev.sebastiano.grabbystepper

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.SegmentedControl
import org.jetbrains.jewel.ui.component.SegmentedControlButtonData
import org.jetbrains.jewel.ui.component.Text

@Composable
fun App(modifier: Modifier = Modifier) {
    val systemDark = isSystemInDarkTheme()
    var dark by remember { mutableStateOf(systemDark) }
    val colors = if (dark) DarkGrabbyColors else LightGrabbyColors

    IntUiTheme(isDark = dark) { GrabbyScene(dark, colors, { dark = it }, modifier) }
}

@Composable
private fun GrabbyScene(
    dark: Boolean,
    colors: GrabbyColors,
    onDarkChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier.fillMaxSize().drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        colors = listOf(colors.canvas, colors.canvasGlow, colors.canvas)
                    )
                )
                drawCircle(
                    brush =
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    colors.canvasGlow.copy(alpha = 0.9f),
                                    colors.canvas.copy(alpha = 0f),
                                ),
                            center = Offset(size.width * 0.72f, size.height * 0.22f),
                            radius = size.minDimension * 0.55f,
                        )
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            GrabbyStepper(colors = colors)
            Text(
                text = "Drag the number · pull down to reset",
                color = colors.hint,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Keyboard: Tab to the pill · arrows or −/+ step · 0 resets",
                color = colors.hint,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            SegmentedControl(
                buttons =
                    listOf(
                        SegmentedControlButtonData(
                            selected = dark,
                            content = { Text("Dark") },
                            onSelect = { onDarkChange(true) },
                        ),
                        SegmentedControlButtonData(
                            selected = !dark,
                            content = { Text("Light") },
                            onSelect = { onDarkChange(false) },
                        ),
                    ),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
