package dev.sebastiano.holocard

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate

/** One card in the collection: who is on it, what it says, and how its foil is tinted. */
internal enum class Friend(
    val title: String,
    val tagline: String,
    val mascot: Mascot,
    /** Where in the rainbow the mascot's body sits at rest, 0..1: 0 red, 1/3 blue, 2/3 green. */
    val hue: Float,
    /** The foil colours the name sweeps through as the card turns. */
    val foil: List<Color>,
) {
    Bloomie(
        title = "bloomie",
        tagline = "A little glow buddy\nthat blooms whenever\nyou say hello.",
        mascot = Mascot.Bloom,
        hue = 0.3f,
        foil = listOf(Color(0xFF7CF5FF), Color(0xFFB18CFF), Color(0xFFFF8AD8), Color(0xFFC6FF8A)),
    ),
    Pixie(
        title = "pixie",
        tagline = "A little pixel friend\nwho scuttles along\nwith every tilt.",
        mascot = Mascot.Pixie,
        hue = 0.93f,
        foil = listOf(Color(0xFFFFE27A), Color(0xFFFF9A5C), Color(0xFFFF6FB5), Color(0xFF9DFFB0)),
    ),
    Botto(
        title = "botto",
        tagline = "A little coding buddy\nthat beeps happily\nat green builds.",
        mascot = Mascot.Botto,
        hue = 0.2f,
        foil = listOf(Color(0xFF6EA8FF), Color(0xFFA07CFF), Color(0xFFFF7CE0), Color(0xFF7CFFF2)),
    ),
}

/**
 * The face details that sit on top of the foil: eyes and visor. [size] is the mascot's square box;
 * the mascots are drawn in its -1..1 units, y pointing down.
 */
internal fun DrawScope.drawFeatures(friend: Friend, size: Float) {
    val unit = size / 2f
    fun at(x: Float, y: Float) = Offset(unit + x * unit, unit + y * unit)
    when (friend) {
        Friend.Bloomie -> {
            val gloss = Brush.verticalGradient(listOf(Color(0xF0FFFFFF), Color(0x99B8F6FF)))
            for (x in floatArrayOf(-0.17f, 0.15f)) {
                rotate(degrees = 12f, pivot = at(x, -0.06f)) {
                    drawRoundRect(
                        brush = gloss,
                        topLeft = at(x - 0.065f, -0.24f),
                        size = Size(0.13f * unit, 0.36f * unit),
                        cornerRadius = CornerRadius(0.065f * unit),
                    )
                }
            }
        }
        Friend.Pixie -> {
            for (x in floatArrayOf(-0.26f, 0.26f)) {
                drawRect(
                    color = Color(0xFF2A0F1E),
                    topLeft = at(x - 0.075f, -0.31f),
                    size = Size(0.15f * unit, 0.15f * unit),
                )
            }
        }
        Friend.Botto -> {
            drawRoundRect(
                color = Color(0xEE120A2A),
                topLeft = at(-0.4f, -0.5f),
                size = Size(0.8f * unit, 0.42f * unit),
                cornerRadius = CornerRadius(0.2f * unit),
            )
            val eye = Stroke(width = 0.055f * unit, cap = StrokeCap.Round)
            for (x in floatArrayOf(-0.16f, 0.16f)) {
                drawArc(
                    color = Color(0xFF8AF6FF),
                    startAngle = 20f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = at(x - 0.09f, -0.38f),
                    size = Size(0.18f * unit, 0.14f * unit),
                    style = eye,
                )
            }
        }
    }
}
