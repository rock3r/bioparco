package dev.sebastiano.scrolleffects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/** The six sample cards, in carousel order. Solar output starts in the middle. */
internal val sampleCards: List<CardSpec> =
    listOf(
        CardSpec(
            chart = CardChart.Solar,
            title = "Solar output",
            subtitle = "Rooftop array",
            value = "6.4",
            unit = "kW",
            footer = "Peak today at 13:10",
            base = Color(0xFFF08A4B),
            blobs =
                listOf(
                    Blob(0.55f, 0.18f, 0.62f, Color(0xFFF7D35C)),
                    Blob(0.08f, 0.62f, 0.55f, Color(0xFFEE5A3A)),
                    Blob(0.95f, 0.45f, 0.42f, Color(0xFFF4A04A)),
                    Blob(0.96f, 0.98f, 0.5f, Color(0xFFF392A6)),
                    Blob(0.5f, 0.52f, 0.33f, Color(0xFF4F9A43)),
                ),
            numberTop = Color(0xFFFFFFFF),
            numberBottom = Color(0xFFFFF0C8),
            glow = Color(0xFF3F8F32),
        ),
        CardSpec(
            chart = CardChart.Surf,
            title = "Surf report",
            subtitle = "North beach",
            value = "2.8",
            unit = "m",
            footer = "Period 14 s · Low tide 16:05",
            chip = "Offshore",
            base = Color(0xFF2B8A70),
            blobs =
                listOf(
                    Blob(0.15f, 0.35f, 0.6f, Color(0xFF0E3A33)),
                    Blob(0.75f, 0.12f, 0.5f, Color(0xFF93E6C4)),
                    Blob(0.9f, 0.9f, 0.45f, Color(0xFF5ACFA4)),
                    Blob(0.1f, 0.95f, 0.45f, Color(0xFF1C6A5E)),
                ),
            numberTop = Color(0xFFE3FFF3),
            numberBottom = Color(0xFF7DE6BE),
            glow = Color(0xFF3FD19A),
        ),
        CardSpec(
            chart = CardChart.Roast,
            title = "Coffee roast",
            subtitle = "Batch 27",
            value = "212",
            unit = "°C",
            footer = "First crack in 3:40",
            base = Color(0xFF34277F),
            blobs =
                listOf(
                    Blob(0.85f, 0.22f, 0.55f, Color(0xFF5B4AE8)),
                    Blob(0.12f, 0.15f, 0.5f, Color(0xFF120C38)),
                    Blob(0.15f, 0.9f, 0.65f, Color(0xFFE88A38)),
                    Blob(0.95f, 0.95f, 0.4f, Color(0xFFB0557A)),
                ),
            numberTop = Color(0xFFFFE7C7),
            numberBottom = Color(0xFFFF9C48),
            glow = Color(0xFFFF8A3A),
        ),
        CardSpec(
            chart = CardChart.Heart,
            title = "Heart rate",
            subtitle = "Morning run",
            value = "142",
            unit = "bpm",
            footer = "Zone 4 · 38 min",
            live = true,
            base = Color(0xFFB0283F),
            blobs =
                listOf(
                    Blob(0.1f, 0.45f, 0.55f, Color(0xFFE45A2F)),
                    Blob(0.92f, 0.22f, 0.5f, Color(0xFFE63D70)),
                    Blob(0.55f, 0.55f, 0.32f, Color(0xFF6A1224)),
                    Blob(0.3f, 1f, 0.5f, Color(0xFF7E1530)),
                ),
            numberTop = Color(0xFFFFFFFF),
            numberBottom = Color(0xFFFFB3C9),
            glow = Color(0xFFFF6A8A),
        ),
        CardSpec(
            chart = CardChart.Focus,
            title = "Deep focus",
            subtitle = "This week",
            value = "18.5",
            unit = "h",
            footer = "Best day, Thursday",
            chip = "+12%",
            base = Color(0xFF1A3CA6),
            blobs =
                listOf(
                    Blob(0.3f, 0.08f, 0.6f, Color(0xFF050E36)),
                    Blob(0.55f, 0.85f, 0.6f, Color(0xFF2E6BFF)),
                    Blob(0.98f, 0.98f, 0.35f, Color(0xFFA6D6FF)),
                    Blob(0.9f, 0.35f, 0.35f, Color(0xFF14328E)),
                ),
            numberTop = Color(0xFFDDF3FF),
            numberBottom = Color(0xFF58B4FF),
            glow = Color(0xFF3D8BFF),
        ),
        CardSpec(
            chart = CardChart.Moon,
            title = "Night sky",
            subtitle = "Moon phase",
            value = "84",
            unit = "%",
            footer = "Waxing gibbous · Rises 18:42",
            base = Color(0xFF6A2ECF),
            blobs =
                listOf(
                    Blob(0.85f, 0.25f, 0.5f, Color(0xFF9E5CFF)),
                    Blob(0.08f, 0.92f, 0.55f, Color(0xFF3A1380)),
                    Blob(0.1f, 0.15f, 0.45f, Color(0xFF4A1EA6)),
                    Blob(0.8f, 0.9f, 0.4f, Color(0xFF8A47F0)),
                ),
            numberTop = Color(0xFFF5E2FF),
            numberBottom = Color(0xFFC08AFF),
            glow = Color(0xFFB57BFF),
        ),
    )

/** One sample card: its words, its gradient and the little chart under the number. */
internal data class CardSpec(
    val chart: CardChart,
    val title: String,
    val subtitle: String,
    val value: String,
    val unit: String,
    val footer: String,
    val base: Color,
    val blobs: List<Blob>,
    val numberTop: Color,
    val numberBottom: Color,
    val glow: Color,
    val chip: String? = null,
    val live: Boolean = false,
)

/** A soft radial patch of colour. Position and radius are fractions of the card width/height. */
internal data class Blob(val x: Float, val y: Float, val radius: Float, val color: Color) {
    fun center(width: Float, height: Float): Offset = Offset(x * width, y * height)
}

internal enum class CardChart {
    Solar,
    Surf,
    Roast,
    Heart,
    Focus,
    Moon,
}
