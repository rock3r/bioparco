package dev.sebastiano.grabbystepper

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

@Composable
fun App(modifier: Modifier = Modifier) {
    val systemDark = isSystemInDarkTheme()
    var dark by remember { mutableStateOf(systemDark) }
    val colors = if (dark) DarkGrabbyColors else LightGrabbyColors

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
            Surface(
                color = colors.toggleSurface,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Row(modifier = Modifier.padding(horizontal = 6.dp)) {
                    TextButton(onClick = { dark = true }) {
                        Text(
                            "Dark",
                            color = if (dark) colors.number else colors.hint,
                            fontWeight = if (dark) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                    TextButton(onClick = { dark = false }) {
                        Text(
                            "Light",
                            color = if (!dark) colors.number else colors.hint,
                            fontWeight = if (!dark) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}
