package dev.sebastiano.borderbeam

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

internal fun DrawScope.drawRotateBeam(
    sizeKind: BeamSize,
    variant: BeamColorVariant,
    theme: BeamTheme,
    strength: Float,
    seconds: Float,
    fade: Float,
    radius: Float,
) {
    val look = beamLook(sizeKind, theme)
    val degrees = fractionalTurn(seconds, defaultDurationSeconds(sizeKind)) * 360f
    val matrix = rotateMatrix(sizeKind, variant, look, seconds)
    val border = if (sizeKind == BeamSize.Sm) smallBorderBlobs(variant) else borderBlobs(variant)
    val inner =
        if (sizeKind == BeamSize.Sm) smallInnerBlobs(variant) else derivedInnerBlobs(variant)
    val innerMask = if (sizeKind == BeamSize.Sm) maskStopsSm else maskStopsMd
    val highlight = if (look.darkInk) highlightStopsLight else highlightStopsDark
    val bloom = if (look.darkInk) bloomStopsLight else bloomStopsDark
    val ink = if (look.darkInk) Color.Black else Color.White
    drawMaskedBlobs(
        alpha = layerOpacity(look.inner, variant, strength, fade),
        matrix = matrix,
        blurPx = 0f,
        degrees = degrees,
        mask = innerMask,
    ) {
        clipRounded(radius) {
            drawBlobList(inner)
            if (sizeKind != BeamSize.Sm) drawEdgeBand(INNER_BAND)
        }
    }
    drawMaskedBlobs(
        alpha = layerOpacity(look.stroke, variant, strength, fade),
        matrix = matrix,
        blurPx = 0f,
        degrees = degrees,
        mask = maskStopsMd,
    ) {
        clipRing(radius, STROKE_BAND) {
            drawBlobList(border)
            drawConicWash(degrees, highlight, ink, BlendMode.SrcOver)
        }
    }
    withFilteredLayer(layerOpacity(look.bloom, variant, strength, fade), matrix, BLOOM_BLUR) {
        clipRing(radius, STROKE_BAND) { drawConicWash(degrees, bloom, ink, BlendMode.SrcOver) }
    }
}

private fun rotateMatrix(
    sizeKind: BeamSize,
    variant: BeamColorVariant,
    look: BeamLook,
    seconds: Float,
): FloatArray {
    val hue =
        hueShiftDegrees(
            timeSeconds = seconds,
            range = effectiveHueRange(sizeKind, DEFAULT_HUE),
            period = HUE_PERIOD,
            staticColors = forcesStaticColors(variant, requested = false),
            continuous = false,
        )
    return colorMatrix4x5(hue, look.brightness, look.saturation)
}

private fun DrawScope.drawMaskedBlobs(
    alpha: Float,
    matrix: FloatArray,
    blurPx: Float,
    degrees: Float,
    mask: List<Pair<Float, Float>>,
    block: DrawScope.() -> Unit,
) {
    withFilteredLayer(alpha, matrix, blurPx) {
        block()
        drawConicWash(degrees, mask, Color.White, BlendMode.DstIn)
    }
}

private const val INNER_BAND = 28f
private const val STROKE_BAND = 2f
private const val BLOOM_BLUR = 8f
private const val DEFAULT_HUE = 30f
private const val HUE_PERIOD = 12f
