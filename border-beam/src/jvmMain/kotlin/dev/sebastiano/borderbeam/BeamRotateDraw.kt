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
    val compact = sizeKind == BeamSize.Sm
    // Soft inner wash stays inside the card. SrcOver keeps hues from splitting apart.
    drawMaskedBlobs(
        alpha = layerOpacity(look.inner, variant, strength, fade),
        matrix = matrix,
        blurPx = if (compact) 1.5f else INNER_BLUR,
        degrees = degrees,
        mask = innerMask,
    ) {
        clipRounded(radius) {
            drawBlobList(inner)
            if (!compact) drawEdgeBand(INNER_BAND)
        }
    }
    // Traveling core on a modest stroke ring.
    drawMaskedBlobs(
        alpha = layerOpacity(look.stroke, variant, strength, fade),
        matrix = matrix,
        blurPx = STROKE_BLUR,
        degrees = degrees,
        mask = maskStopsMd,
        outset = STROKE_OUTSET,
    ) {
        clipRing(radius, if (compact) STROKE_BAND_SM else STROKE_BAND) {
            drawBlobList(border)
            drawConicWash(degrees, highlight, ink, BlendMode.SrcOver)
        }
    }
    drawOuterBloom(
        look,
        variant,
        strength,
        fade,
        matrix,
        degrees,
        border,
        bloom,
        ink,
        radius,
        compact,
    )
}

/**
 * Soft rim light. Source stays on a thin ring around the border; MaskFilter blur (with layer
 * outset) spills a continuous aura. Avoid Screen — with a conic + blobs it separates into discrete
 * hue islands outside the card.
 */
private fun DrawScope.drawOuterBloom(
    look: BeamLook,
    variant: BeamColorVariant,
    strength: Float,
    fade: Float,
    matrix: FloatArray,
    degrees: Float,
    border: List<BeamBlob>,
    bloom: List<Pair<Float, Float>>,
    ink: Color,
    radius: Float,
    compact: Boolean,
) {
    val blur = if (compact) BLOOM_BLUR_SM else BLOOM_BLUR
    val inside = if (compact) BLOOM_INSIDE_SM else BLOOM_INSIDE
    val outside = if (compact) BLOOM_OUTSIDE_SM else BLOOM_OUTSIDE
    withFilteredLayer(
        alpha = layerOpacity(look.bloom, variant, strength, fade),
        colorMatrix = matrix,
        blurPx = blur,
        outset = blur * 2.5f + outside,
        blendMode = BlendMode.SrcOver,
    ) {
        // Narrow aura straddling the edge — not the 30/36px band that floated blobs.
        clipAura(radius, inside, outside) {
            drawBlobList(border)
            drawConicWash(degrees, bloom, ink, BlendMode.SrcOver)
        }
        drawConicWash(
            degrees,
            if (compact) maskStopsSm else maskStopsMd,
            Color.White,
            BlendMode.DstIn,
        )
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
    outset: Float = 0f,
    blendMode: BlendMode = BlendMode.SrcOver,
    block: DrawScope.() -> Unit,
) {
    withFilteredLayer(alpha, matrix, blurPx, outset, blendMode) {
        block()
        drawConicWash(degrees, mask, Color.White, BlendMode.DstIn)
    }
}

private const val INNER_BAND = 28f
private const val INNER_BLUR = 4f
private const val STROKE_BAND = 3.5f
private const val STROKE_BAND_SM = 2.75f
private const val STROKE_BLUR = 1.25f
private const val STROKE_OUTSET = 6f
// ~10–14: soft rim without dissolving the beam into exterior hue blobs.
private const val BLOOM_BLUR = 12f
private const val BLOOM_BLUR_SM = 10f
private const val BLOOM_INSIDE = 8f
private const val BLOOM_OUTSIDE = 6f
private const val BLOOM_INSIDE_SM = 6f
private const val BLOOM_OUTSIDE_SM = 4f
private const val DEFAULT_HUE = 30f
private const val HUE_PERIOD = 12f
