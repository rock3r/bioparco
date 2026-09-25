package dev.sebastiano.borderbeam

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale

/**
 * The pulse sizes breathe instead of travel. Every blob belongs to a size region (1-3) that
 * stretches and drifts, and to a corner whose opacity oscillates, all on desynced periods.
 *
 * `pulse-inner` stays inside the card: an inner edge wash with corner accents, a 1px colored ring,
 * and a blurred bloom cut to that ring. `pulse-outside` puts a crisp ring on the card and paints a
 * core glow and a wide halo behind it, outside the card. Each layer follows the CSS paint order
 * (filter, clip, mask, opacity), and lengths are CSS px scaled by [DrawScope.density].
 */
internal fun DrawScope.drawPulseHalo(
    variant: BeamColorVariant,
    theme: BeamTheme,
    strength: Float,
    seconds: Float,
    fade: Float,
    radius: Float,
) {
    val pulse = pulse(BeamSize.PulseOutside, variant, theme, seconds, radius)
    val dark = theme == BeamTheme.Dark
    // Both layers sit behind the card, shrunk to 95% × 90% about the center.
    scale(OUTER_SCALE_X, OUTER_SCALE_Y) {
        withLayer(
            alpha = layerOpacity(pulse.look.inner, variant, strength, fade),
            colorMatrix = pulse.filter,
            blurSigma = (if (dark) CORE_BLUR_DARK else CORE_BLUR_LIGHT) * density,
            outset = HALO_OUTSET * density,
        ) {
            drawOuterBox(pulse, CORE_INSET, outerCoreBlobs(variant), frozen = false)
        }
        withLayer(
            alpha = layerOpacity(pulse.look.bloom, variant, strength, fade),
            colorMatrix = pulse.filter,
            blurSigma = (if (dark) HALO_BLUR_DARK else HALO_BLUR_LIGHT) * density,
            outset = HALO_OUTSET * density,
        ) {
            drawOuterBox(pulse, BLOOM_INSET, outerBloomBlobs(variant), frozen = true)
        }
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
    val pulse = pulse(sizeKind, variant, theme, seconds, radius)
    val ring = ringPath(pulse.radius, BORDER_WIDTH * density)
    val box = Rect(Offset.Zero, size)
    if (sizeKind == BeamSize.PulseOutside) {
        withLayer(alpha = layerOpacity(pulse.look.stroke, variant, strength, fade)) {
            clipPath(ring) {
                withLayer(colorMatrix = pulse.filter) {
                    drawPulseBlobs(pulse, outerCoreBlobs(variant), box, frozen = false)
                }
            }
        }
        return
    }
    clipPath(roundedPath(0f, pulse.radius)) {
        withLayer(alpha = layerOpacity(pulse.look.inner, variant, strength, fade)) {
            withLayer(colorMatrix = pulse.filter) {
                drawCornerAccents(pulse, theme)
                drawPulseBlobs(pulse, pulseInnerBlobs(variant), box, frozen = false)
            }
            withLayer(blendMode = BlendMode.DstIn) { drawEdgeFade(INNER_EDGE * density) }
        }
        withLayer(alpha = layerOpacity(pulse.look.stroke, variant, strength, fade)) {
            clipPath(ring) {
                withLayer(colorMatrix = pulse.filter) {
                    drawPulseBlobs(pulse, borderBlobs(variant), box, frozen = false)
                }
            }
        }
        withLayer(alpha = layerOpacity(pulse.look.bloom, variant, strength, fade)) {
            clipPath(ring) {
                withLayer(colorMatrix = pulse.filter, blurSigma = INNER_BLOOM_BLUR * density) {
                    drawPulseBlobs(pulse, pulseInnerBloomBlobs(variant), box, frozen = true)
                }
            }
        }
    }
}

private class Pulse(
    val look: BeamLook,
    val params: PulseParams,
    val seconds: Float,
    val filter: FloatArray,
    val radius: Float,
    /** `--pulse-glow-sx/sy`: pulse-outside fits its glow to the element; 1 otherwise. */
    val glowX: Float,
    val glowY: Float,
)

private fun DrawScope.pulse(
    sizeKind: BeamSize,
    variant: BeamColorVariant,
    theme: BeamTheme,
    seconds: Float,
    radius: Float,
): Pulse {
    val look = beamLook(sizeKind, theme)
    val params = pulseParams(sizeKind, theme, defaultDurationSeconds(sizeKind))
    val outside = sizeKind == BeamSize.PulseOutside
    // Pulse colors turn a full hue circle. Static colors keep brightness and saturation.
    val hue =
        hueShiftDegrees(
            timeSeconds = seconds,
            range = FULL_TURN,
            period = params.huePeriod,
            staticColors = forcesStaticColors(variant, requested = false),
            continuous = true,
        )
    return Pulse(
        look = look,
        params = params,
        seconds = seconds,
        filter = colorMatrix4x5(hue, look.brightness, look.saturation),
        radius = radius.coerceAtMost(size.minDimension / 2f),
        glowX = if (outside) pulseGlowScale(size.width / density, REFERENCE_WIDTH) else 1f,
        glowY = if (outside) pulseGlowScale(size.height / density, REFERENCE_HEIGHT) else 1f,
    )
}

/** A pulse-outside layer box: the element grown by [inset] CSS px, with its rounded corners. */
private fun DrawScope.drawOuterBox(
    pulse: Pulse,
    inset: Float,
    blobs: List<BeamBlob>,
    frozen: Boolean,
) {
    val grow = inset * density
    val box = Rect(Offset.Zero, size).inflate(grow)
    val shape =
        Path().apply {
            addRoundRect(
                RoundRect(
                    box,
                    CornerRadius(pulse.radius + grow),
                )
            )
        }
    clipPath(shape) { drawPulseBlobs(pulse, blobs, box, frozen) }
}

/**
 * Paints `pulseGrad` blobs, first on top, positioned as fractions of [box]. Live blobs stretch,
 * drift and fade per region and corner. [frozen] blobs hold their size and use the time-average
 * opacity, like the web's cached bloom.
 */
private fun DrawScope.drawPulseBlobs(
    pulse: Pulse,
    blobs: List<BeamBlob>,
    box: Rect,
    frozen: Boolean,
) {
    val params = pulse.params
    val time = pulse.seconds
    val breathe = if (frozen) 1f else pulseBreathe(params, time)
    for (index in blobs.lastIndex downTo 0) {
        val blob = blobs[index]
        val alpha =
            if (frozen) 1f - params.op * 0.5f else pulseCornerAlpha(params, blob.corner, time)
        val (driftX, driftY) = if (frozen) 0f to 0f else pulseDrift(params, blob.region, time)
        val stretchX = if (frozen) 1f else pulseWidthScale(params, blob.region, time)
        val stretchY = if (frozen) 1f else pulseHeightScale(params, blob.region, time) * breathe
        val color = Color(blob.r, blob.g, blob.b).copy(alpha = (blob.a * alpha).coerceIn(0f, 1f))
        drawEllipseGradient(
            center =
                Offset(
                    box.left + blob.x * box.width + driftX * density,
                    box.top + blob.y * box.height + driftY * density,
                ),
            radiusX = blob.rx * stretchX * pulse.glowX * density,
            radiusY = blob.ry * stretchY * pulse.glowY * density,
            stops = arrayOf(0f to color, 1f to color.copy(alpha = 0f)),
        )
    }
}

/** Pulse-inner corner accents: soft white (dark) or black (light) glows that breathe per corner. */
private fun DrawScope.drawCornerAccents(pulse: Pulse, theme: BeamTheme) {
    val dark = theme == BeamTheme.Dark
    val ink = if (dark) Color.White else Color.Black
    val base = if (dark) 0.18f else 0.08f
    val reach = CORNER_RADIUS * density
    listOf(
            BeamCorner.Tl to Offset(0f, 0f),
            BeamCorner.Tr to Offset(size.width, 0f),
            BeamCorner.Bl to Offset(0f, size.height),
            BeamCorner.Br to Offset(size.width, size.height),
        )
        .forEach { (corner, center) ->
            val color =
                ink.copy(alpha = base * pulseCornerAlpha(pulse.params, corner, pulse.seconds))
            drawEllipseGradient(
                center,
                reach,
                reach,
                arrayOf(0f to color, 0.7f to color.copy(alpha = 0f)),
            )
        }
}

private const val FULL_TURN = 360f
private const val BORDER_WIDTH = 1f
private const val INNER_EDGE = 28f
private const val INNER_BLOOM_BLUR = 8f
private const val CORNER_RADIUS = 60f
/** The pulse-outside core and halo boxes grow by 10px and 30px past the element. */
private const val CORE_INSET = 10f
private const val BLOOM_INSET = 30f
private const val CORE_BLUR_DARK = 3f
private const val CORE_BLUR_LIGHT = 6f
private const val HALO_BLUR_DARK = 22.5f
private const val HALO_BLUR_LIGHT = 15f
/** Room for the halo box plus three standard deviations of its blur. */
private const val HALO_OUTSET = 110f
private const val OUTER_SCALE_X = 0.95f
private const val OUTER_SCALE_Y = 0.9f
/** pulse-outside glow geometry is authored for a ~350×140 element. */
private const val REFERENCE_WIDTH = 350f
private const val REFERENCE_HEIGHT = 140f
