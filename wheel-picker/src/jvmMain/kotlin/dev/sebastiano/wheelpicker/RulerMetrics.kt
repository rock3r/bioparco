package dev.sebastiano.wheelpicker

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/** The ruler's sizes in pixels, for one density and width. */
internal class RulerMetrics(
    val pitch: Float,
    val radius: Float,
    val tickWidth: Float,
    val minorHeight: Float,
    val majorHeight: Float,
    val indicatorWidth: Float,
    val indicatorHeight: Float,
    val labelGap: Float,
    val bumpWidth: Float,
    val edgeFringe: Float,
    val motionFringe: Float,
    val labelFringe: Float,
)

internal fun rulerMetrics(density: Density, widthPx: Float): RulerMetrics =
    with(density) {
        val width = if (widthPx > 0f) widthPx else 860.dp.toPx()
        RulerMetrics(
            pitch = 40.dp.toPx(),
            // A drum as wide as the ruler: ticks reach its rim right at the ruler's ends.
            radius = width / 2f,
            tickWidth = 2.5.dp.toPx(),
            minorHeight = 18.dp.toPx(),
            majorHeight = 30.dp.toPx(),
            indicatorWidth = 3.5.dp.toPx(),
            indicatorHeight = 40.dp.toPx(),
            labelGap = 12.dp.toPx(),
            bumpWidth = 52.dp.toPx(),
            edgeFringe = 7.dp.toPx(),
            motionFringe = 2.dp.toPx(),
            labelFringe = 2.dp.toPx(),
        )
    }
