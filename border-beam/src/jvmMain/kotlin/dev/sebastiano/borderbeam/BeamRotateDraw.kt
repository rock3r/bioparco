package dev.sebastiano.borderbeam

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath

/**
 * The rotate family (`md`, `sm`): the three border-beam CSS layers, bottom to top.
 * 1. Inner glow (`::before`): color blobs and a white inset shadow on the card face, shown only
 *    near the edge and inside the rotating beam window.
 * 2. Stroke (`::after`): color blobs under a white conic highlight, on the 1px border ring, inside
 *    the beam window. This is where the color of the rim lives.
 * 3. Bloom (`[data-beam-bloom]`): a mono conic, blurred, then cut to the same 1px ring.
 *
 * Each layer follows the CSS paint order: filter, then clip, then mask, then opacity. The bloom
 * blur therefore softens the conic along the ring before the ring cuts it; it never paints a halo.
 * The host clips everything to its rounded shape (`overflow: hidden`). Lengths are CSS px, which
 * map to dp, so they scale with [DrawScope.density].
 */
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
    val beam =
        RotateBeam(
            compact = sizeKind == BeamSize.Sm,
            variant = variant,
            angle = fractionalTurn(seconds, defaultDurationSeconds(sizeKind)) * 360f,
            ink = if (look.darkInk) Color.Black else Color.White,
            filter = layerFilter(sizeKind, variant, look, seconds),
            radius = radius.coerceAtMost(size.minDimension / 2f),
            borderWidth = BORDER_WIDTH * density,
        )
    clipPath(roundedPath(0f, beam.radius)) {
        drawInnerGlow(beam, layerOpacity(look.inner, variant, strength, fade), theme)
        drawStroke(beam, layerOpacity(look.stroke, variant, strength, fade))
        drawBloom(beam, layerOpacity(look.bloom, variant, strength, fade), look)
    }
}

private class RotateBeam(
    val compact: Boolean,
    val variant: BeamColorVariant,
    val angle: Float,
    val ink: Color,
    val filter: FloatArray?,
    val radius: Float,
    val borderWidth: Float,
)

/** `::before`: blobs plus inset shadow, masked to the beam window and the edge fade. */
private fun DrawScope.drawInnerGlow(beam: RotateBeam, alpha: Float, theme: BeamTheme) {
    val blobs = if (beam.compact) smallInnerBlobs(beam.variant) else derivedInnerBlobs(beam.variant)
    val shadowBlur = (if (beam.compact) SM_SHADOW_BLUR else MD_SHADOW_BLUR) * density
    withLayer(alpha = alpha) {
        withLayer(colorMatrix = beam.filter) {
            drawBlobList(blobs, density)
            drawInsetShadow(beam.radius, innerShadow(beam.compact, theme), shadowBlur, density)
        }
        if (beam.compact) {
            drawConicWash(beam.angle, maskStopsSm, Color.White, BlendMode.DstIn)
        } else {
            withLayer(blendMode = BlendMode.DstIn) {
                drawEdgeFade(INNER_EDGE * density)
                drawConicWash(beam.angle, maskStopsMd, Color.White, BlendMode.DstIn)
            }
        }
    }
}

/** `::after`: the colored rim. Blobs under the white conic, cut to the ring and the window. */
private fun DrawScope.drawStroke(beam: RotateBeam, alpha: Float) {
    val blobs = if (beam.compact) smallBorderBlobs(beam.variant) else borderBlobs(beam.variant)
    val highlight = if (beam.ink == Color.Black) highlightStopsLight else highlightStopsDark
    withLayer(alpha = alpha) {
        clipPath(ringPath(beam)) {
            withLayer(colorMatrix = beam.filter) {
                drawBlobList(blobs, density)
                drawConicWash(beam.angle, highlight, beam.ink, BlendMode.SrcOver)
            }
            drawConicWash(beam.angle, maskStopsMd, Color.White, BlendMode.DstIn)
        }
    }
}

/**
 * `[data-beam-bloom]`: a mono conic filtered with `blur(8px) brightness() saturate()`, no hue and
 * no beam window, then cut to the ring. The blur only softens the hot spot along the rim.
 */
private fun DrawScope.drawBloom(beam: RotateBeam, alpha: Float, look: BeamLook) {
    val stops = if (beam.ink == Color.Black) bloomStopsLight else bloomStopsDark
    withLayer(alpha = alpha) {
        withLayer(
            colorMatrix = colorMatrix4x5(0f, look.brightness, look.saturation),
            blurSigma = BLOOM_BLUR * density,
        ) {
            clipPath(roundedPath(0f, elementRadius(beam))) {
                drawConicWash(beam.angle, stops, beam.ink, BlendMode.SrcOver)
            }
        }
        maskTo(ringPath(beam))
    }
}

/**
 * Hue, brightness and saturation for the inner and stroke layers. With static colors (mono) the web
 * layers carry no filter at all, so neither do these.
 */
private fun layerFilter(
    sizeKind: BeamSize,
    variant: BeamColorVariant,
    look: BeamLook,
    seconds: Float,
): FloatArray? {
    if (forcesStaticColors(variant, requested = false)) return null
    val hue =
        hueShiftDegrees(
            timeSeconds = seconds,
            range = effectiveHueRange(sizeKind, DEFAULT_HUE),
            period = HUE_PERIOD,
            staticColors = false,
            continuous = false,
        )
    return colorMatrix4x5(hue, look.brightness, look.saturation)
}

/** Theme `innerShadow` for the rotate sizes. */
private fun innerShadow(compact: Boolean, theme: BeamTheme): Color =
    when {
        theme == BeamTheme.Light -> Color.Black.copy(alpha = 0.14f)
        compact -> Color.White.copy(alpha = 0.30f)
        else -> Color.White.copy(alpha = 0.27f)
    }

/** The `::after` box keeps `border-radius: radius - borderWidth`. */
private fun elementRadius(beam: RotateBeam): Float =
    (beam.radius - beam.borderWidth).coerceAtLeast(0f)

/** Border box minus content box of the `::after` element: one border width, all the way round. */
private fun DrawScope.ringPath(beam: RotateBeam): Path =
    ringPath(elementRadius(beam), beam.borderWidth)

/** Spec `borderWidth`, shared by the stroke and bloom rings. */
private const val BORDER_WIDTH = 1f
/** CSS `blur(8px)` on the bloom: a Gaussian standard deviation. */
private const val BLOOM_BLUR = 8f
/** `::before` inset shadow blur radius. */
private const val MD_SHADOW_BLUR = 9f
private const val SM_SHADOW_BLUR = 5f
/** `md` inner edge fade: color shows within 28px of each side. */
private const val INNER_EDGE = 28f
private const val DEFAULT_HUE = 30f
private const val HUE_PERIOD = 12f
