package dev.sebastiano.thinkingorbs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive

@Composable
fun ThinkingOrb(
    modifier: Modifier = Modifier,
    design: OrbDesign = OrbDesign.Working,
    size: OrbSize = OrbSize.Regular,
    diameter: Dp = size.points.dp,
    speed: Double = 1.0,
    isPaused: Boolean = false,
    isDark: Boolean = isSystemInDarkTheme(),
) {
    var clockSeconds by remember { mutableDoubleStateOf(0.6) }
    LaunchedEffect(isPaused) {
        if (isPaused) return@LaunchedEffect
        while (isActive) {
            withFrameNanos { clockSeconds = it / 1_000_000_000.0 }
        }
    }

    Canvas(
        modifier =
            modifier.size(diameter).semantics {
                contentDescription = design.accessibilityLabel
                role = Role.Image
            }
    ) {
        val frame = design.frame(size, clockSeconds * speed)
        val scale = minOf(this.size.width, this.size.height) / size.points.toFloat()
        val left = (this.size.width - size.points.toFloat() * scale) / 2
        val top = (this.size.height - size.points.toFloat() * scale) / 2
        withTransform({
            translate(left, top)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            frame.lines.forEach { line ->
                drawLine(
                    color = ink(line.white, isDark).copy(alpha = line.alpha.toFloat()),
                    start = Offset(line.x1.toFloat(), line.y1.toFloat()),
                    end = Offset(line.x2.toFloat(), line.y2.toFloat()),
                    strokeWidth = line.width.toFloat(),
                    cap = StrokeCap.Round,
                )
            }
            frame.dots.forEach { dot ->
                drawCircle(
                    color = ink(dot.white, isDark).copy(alpha = dot.alpha.toFloat()),
                    radius = dot.radius.toFloat(),
                    center = Offset(dot.x.toFloat(), dot.y.toFloat()),
                )
            }
        }
    }
}

private fun ink(white: Double, dark: Boolean): Color {
    val value = (if (dark) 1 - white else white).coerceIn(0.0, 1.0).toFloat()
    return Color(value, value, value)
}
