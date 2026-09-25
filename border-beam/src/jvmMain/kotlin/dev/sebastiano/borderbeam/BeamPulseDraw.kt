package dev.sebastiano.borderbeam

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

internal fun DrawScope.drawPulseHalo(
    variant: BeamColorVariant,
    theme: BeamTheme,
    strength: Float,
    seconds: Float,
    fade: Float,
) {
    val look = beamLook(BeamSize.PulseOutside, theme)
    val params =
        pulseParams(BeamSize.PulseOutside, theme, defaultDurationSeconds(BeamSize.PulseOutside))
    val matrix = pulseMatrix(variant, look, params, seconds)
    val scaleX = pulseGlowScale(size.width, REFERENCE_WIDTH)
    val scaleY = pulseGlowScale(size.height, REFERENCE_HEIGHT)
    val blur = if (theme == BeamTheme.Dark) 22.5f else 15f
    val haloX = scaleX * HALO_SCALE_X
    val haloY = scaleY * HALO_SCALE_Y
    withFilteredLayer(
        layerOpacity(look.bloom, variant, strength, fade),
        matrix,
        blur,
        HALO_OUTSET,
    ) {
        drawPulseField(outerBloomBlobs(variant), params, seconds, haloX, haloY)
    }
    withFilteredLayer(
        layerOpacity(look.inner, variant, strength, fade),
        matrix,
        if (theme == BeamTheme.Dark) 3f else 6f,
        HALO_OUTSET,
    ) {
        drawPulseField(outerCoreBlobs(variant), params, seconds, haloX, haloY)
    }
}

internal fun DrawScope.drawPulseFront(
    sizeKind: BeamSize,
    variant: BeamColorVariant,
    theme: BeamTheme,
    strength: Float,
    seconds: Float,
    fade: Float,
    radius: Float,
) {
    val look = beamLook(sizeKind, theme)
    val params = pulseParams(sizeKind, theme, defaultDurationSeconds(sizeKind))
    val matrix = pulseMatrix(variant, look, params, seconds)
    if (sizeKind == BeamSize.PulseOutside) {
        val scaleX = pulseGlowScale(size.width, REFERENCE_WIDTH)
        val scaleY = pulseGlowScale(size.height, REFERENCE_HEIGHT)
        withFilteredLayer(layerOpacity(look.stroke, variant, strength, fade), matrix, 0f) {
            clipRing(radius, STROKE_BAND) {
                drawPulseField(outerCoreBlobs(variant), params, seconds, scaleX, scaleY)
            }
        }
        return
    }
    withFilteredLayer(layerOpacity(look.inner, variant, strength, fade), matrix, 0f) {
        clipRounded(radius) {
            clipRing(radius, INNER_BAND) {
                drawPulseField(derivedInnerBlobs(variant), params, seconds, 1f, 1f)
            }
            drawCornerAccents(params, seconds, look.darkInk)
        }
    }
    withFilteredLayer(layerOpacity(look.bloom, variant, strength, fade), matrix, BLOOM_BLUR) {
        clipRing(radius, INNER_BAND) {
            drawPulseField(borderBlobs(variant), params, seconds, 1f, 1f)
        }
    }
    withFilteredLayer(layerOpacity(look.stroke, variant, strength, fade), matrix, 0f) {
        clipRing(radius, STROKE_BAND) {
            drawPulseField(borderBlobs(variant), params, seconds, 1f, 1f)
        }
    }
}

private fun pulseMatrix(
    variant: BeamColorVariant,
    look: BeamLook,
    params: PulseParams,
    seconds: Float,
): FloatArray {
    val hue =
        hueShiftDegrees(
            timeSeconds = seconds,
            range = 360f,
            period = params.huePeriod,
            staticColors = forcesStaticColors(variant, requested = false),
            continuous = true,
        )
    return colorMatrix4x5(hue, look.brightness, look.saturation)
}

private fun DrawScope.drawPulseField(
    blobs: List<BeamBlob>,
    params: PulseParams,
    seconds: Float,
    scaleX: Float,
    scaleY: Float,
) {
    val breathe = pulseBreathe(params, seconds)
    for (index in blobs.lastIndex downTo 0) {
        val blob = blobs[index]
        val alpha = (blob.a * pulseCornerAlpha(params, blob.corner, seconds)).coerceIn(0f, 1f)
        if (alpha <= 0.01f) continue
        val (driftX, driftY) = pulseDrift(params, blob.region, seconds)
        drawSoftEllipse(
            center = Offset(blob.x * size.width + driftX, blob.y * size.height + driftY),
            radiusX = blob.rx * pulseWidthScale(params, blob.region, seconds) * scaleX,
            radiusY = blob.ry * pulseHeightScale(params, blob.region, seconds) * breathe * scaleY,
            color = Color(blob.r / 255f, blob.g / 255f, blob.b / 255f, alpha),
        )
    }
}

private fun DrawScope.drawCornerAccents(params: PulseParams, seconds: Float, darkInk: Boolean) {
    val ink = if (darkInk) Color.Black else Color.White
    val base = if (darkInk) 0.08f else 0.18f
    val corners =
        listOf(
            BeamCorner.Tl to Offset(0f, 0f),
            BeamCorner.Tr to Offset(size.width, 0f),
            BeamCorner.Bl to Offset(0f, size.height),
            BeamCorner.Br to Offset(size.width, size.height),
        )
    corners.forEach { (corner, center) ->
        drawSoftEllipse(
            center,
            60f,
            60f,
            ink.copy(alpha = base * pulseCornerAlpha(params, corner, seconds)),
        )
    }
}

private const val REFERENCE_WIDTH = 350f
private const val REFERENCE_HEIGHT = 140f
private const val STROKE_BAND = 2f
private const val INNER_BAND = 28f
private const val BLOOM_BLUR = 8f
private const val HALO_SCALE_X = 0.95f
private const val HALO_SCALE_Y = 0.9f
private const val HALO_OUTSET = 96f
