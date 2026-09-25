package dev.sebastiano.borderbeam

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect

internal fun DrawScope.drawLineBeam(
    variant: BeamColorVariant,
    theme: BeamTheme,
    strength: Float,
    seconds: Float,
    fade: Float,
    radius: Float,
) {
    val look = beamLook(BeamSize.Line, theme)
    val duration = defaultDurationSeconds(BeamSize.Line)
    val travel = fractionalTurn(seconds, duration)
    val edge = sampleKeyframes(lineEdgeFade, travel)
    if (edge <= 0.01f) return
    val along = sampleKeyframes(lineTravelX, travel)
    val widthScale = sampleKeyframes(lineTravelWidth, travel)
    val heightScale =
        sampleKeyframes(
            lineBreathe,
            fractionalTurn(seconds, duration * BREATHE_SCALE),
            eased = true,
        )
    val matrix = lineMatrix(variant, look, seconds)
    val spots = lineSpots(variant, theme)
    val origin = Offset(along * size.width, size.height)
    val ink = if (look.darkInk) Color.Black else Color.White
    val scaledFade = fade * edge
    drawLineLayer(
        layerOpacity(look.inner, variant, strength, scaledFade),
        matrix,
        0f,
        radius,
        origin,
        spots,
        widthScale,
        heightScale,
        ink,
        0.45f,
    )
    drawLineLayer(
        layerOpacity(look.stroke, variant, strength, scaledFade),
        matrix,
        0f,
        radius,
        origin,
        spots,
        widthScale,
        heightScale,
        ink,
        1f,
    )
    drawLineLayer(
        layerOpacity(look.bloom, variant, strength, scaledFade),
        matrix,
        BLOOM_BLUR,
        radius,
        origin,
        spots,
        widthScale,
        heightScale,
        ink,
        1f,
        outset = BLOOM_OUTSET,
        blendMode = BlendMode.Screen,
        spill = true,
    )
}

private fun lineMatrix(variant: BeamColorVariant, look: BeamLook, seconds: Float): FloatArray {
    val hue =
        hueShiftDegrees(
            timeSeconds = seconds,
            range = effectiveHueRange(BeamSize.Line, DEFAULT_HUE),
            period = HUE_PERIOD,
            staticColors = forcesStaticColors(variant, requested = false),
            continuous = false,
        )
    return colorMatrix4x5(hue, look.brightness, look.saturation)
}

private fun DrawScope.drawLineLayer(
    alpha: Float,
    matrix: FloatArray,
    blurPx: Float,
    radius: Float,
    origin: Offset,
    spots: List<LineSpot>,
    widthScale: Float,
    heightScale: Float,
    ink: Color,
    spotAlpha: Float,
    outset: Float = 0f,
    blendMode: BlendMode = BlendMode.SrcOver,
    spill: Boolean = false,
) {
    withFilteredLayer(alpha, matrix, blurPx, outset, blendMode) {
        val top = size.height - if (spill) BOTTOM_BAND + 12f else BOTTOM_BAND
        val bottom = if (spill) size.height + outset else size.height
        val drawSpots: DrawScope.() -> Unit = {
            clipRect(top = top, bottom = bottom) {
                drawLineSpots(spots, origin, widthScale, heightScale, spotAlpha)
                drawSoftEllipse(
                    center = Offset(origin.x, origin.y + 2f),
                    radiusX = 24f * widthScale,
                    radiusY = 28f * heightScale,
                    color = ink.copy(alpha = 0.55f),
                )
            }
        }
        if (spill) drawSpots() else clipRounded(radius, drawSpots)
        drawEllipseMask(
            origin,
            78f * widthScale,
            if (spill) 72f * heightScale else 60f * heightScale,
        )
    }
}

private fun DrawScope.drawLineSpots(
    spots: List<LineSpot>,
    origin: Offset,
    widthScale: Float,
    heightScale: Float,
    spotAlpha: Float,
) {
    spots.forEach { spot ->
        drawSoftEllipse(
            center = Offset(origin.x + spot.dx, origin.y + spot.dy),
            radiusX = spot.rx * widthScale,
            radiusY = spot.ry * heightScale,
            color = Color(spot.r / 255f, spot.g / 255f, spot.b / 255f, spotAlpha),
        )
    }
}

private const val BREATHE_SCALE = 1.3f
private const val BLOOM_BLUR = 22f
private const val BLOOM_OUTSET = 48f
private const val BOTTOM_BAND = 42f
private const val DEFAULT_HUE = 30f
private const val HUE_PERIOD = 12f
