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
    // Traveling core on the 1px stroke ring (Metal kind=0 / CSS ::after).
    drawMaskedBlobs(
        alpha = layerOpacity(look.stroke, variant, strength, fade),
        matrix = matrix,
        blurPx = 0f,
        degrees = degrees,
        mask = maskStopsMd,
    ) {
        clipRing(radius, BORDER_WIDTH) {
            drawBlobList(border)
            drawConicWash(degrees, highlight, ink, BlendMode.SrcOver)
        }
    }
    drawOuterBloom(
        look,
        variant,
        strength,
        fade,
        degrees,
        bloom,
        ink,
        radius,
    )
}

/**
 * Soft rim wash. Metal kind=2 / CSS `[data-beam-bloom]`: mono white/black conic on a 1px ring, then
 * blur(~8) + brightness/saturate with **no** hue. No color blobs, no DstIn beam mask, no outside
 * aura — those painted disconnected hue islands past the card.
 */
private fun DrawScope.drawOuterBloom(
    look: BeamLook,
    variant: BeamColorVariant,
    strength: Float,
    fade: Float,
    degrees: Float,
    bloom: List<Pair<Float, Float>>,
    ink: Color,
    radius: Float,
) {
    // Web bloom filter is static brightness/saturate only (no hue-rotate).
    val bloomMatrix = colorMatrix4x5(0f, look.brightness, look.saturation)
    // overflow:hidden parity — keep the softened wash on the card face.
    clipRounded(radius) {
        withFilteredLayer(
            alpha = layerOpacity(look.bloom, variant, strength, fade),
            colorMatrix = bloomMatrix,
            blurPx = BLOOM_BLUR,
            outset = BLOOM_BLUR * 2.5f,
            blendMode = BlendMode.SrcOver,
        ) {
            clipRing(radius, BORDER_WIDTH) { drawConicWash(degrees, bloom, ink, BlendMode.SrcOver) }
        }
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
/** Spec / CSS `borderWidth` for stroke + bloom rings. */
private const val BORDER_WIDTH = 1f
/** CSS / Metal `bloomBlurPx` (Gaussian std-dev). */
private const val BLOOM_BLUR = 8f
private const val DEFAULT_HUE = 30f
private const val HUE_PERIOD = 12f
