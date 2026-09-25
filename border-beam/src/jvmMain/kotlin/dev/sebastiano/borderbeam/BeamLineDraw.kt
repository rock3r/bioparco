package dev.sebastiano.borderbeam

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath

/**
 * The `line` size: a glow that slides along the bottom edge. Same three CSS layers as the rotate
 * family, but every mask is an ellipse riding at the beam position instead of a conic window.
 * 1. Inner glow: color blobs and inset shadow above the edge, inside the ellipse and edge fade.
 * 2. Stroke: color blobs under a white highlight, on the 1px ring, inside the ellipse.
 * 3. Bloom: thin color spikes and a white hot spot, blurred, then cut to a taller ellipse.
 *
 * Paint order per layer is filter, clip, mask, opacity; the host clips everything to its rounded
 * shape. Lengths are CSS px, scaled by [DrawScope.density].
 */
internal fun DrawScope.drawLineBeam(
    variant: BeamColorVariant,
    theme: BeamTheme,
    strength: Float,
    seconds: Float,
    fade: Float,
    radius: Float,
) {
    val travel = fractionalTurn(seconds, defaultDurationSeconds(BeamSize.Line))
    // The line keyframes fade the whole beam in and out at the ends of the track.
    val visibility = fade.coerceIn(0f, 1f) * sampleKeyframes(lineEdgeFade, travel)
    if (visibility <= MIN_LAYER_ALPHA) return
    val look = beamLook(BeamSize.Line, theme)
    val static = forcesStaticColors(variant, requested = false)
    val beam =
        LineBeam(
            variant = variant,
            dark = theme == BeamTheme.Dark,
            mono = variant == BeamColorVariant.Mono,
            x = sampleKeyframes(lineTravelX, travel) * size.width,
            w = sampleKeyframes(lineTravelWidth, travel),
            h =
                sampleKeyframes(
                    lineBreathe,
                    fractionalTurn(seconds, BREATHE_SECONDS),
                    eased = true,
                ),
            spike =
                sampleKeyframes(lineSpike, fractionalTurn(seconds, SPIKE_SECONDS), eased = true),
            spike2 =
                sampleKeyframes(lineSpike2, fractionalTurn(seconds, SPIKE2_SECONDS), eased = true),
            filter = if (static) null else lineFilter(look, seconds, HUE_RANGE, HUE_PERIOD),
            radius = radius.coerceAtMost(size.minDimension / 2f),
            px = density,
        )
    // Unlike the rotate sizes, the web line never halves its opacities for mono.
    fun opacity(preset: Float) = cssOpacity(preset * clampUnit(strength) * visibility)
    clipPath(roundedPath(0f, beam.radius)) {
        drawLineInner(beam, opacity(look.inner), theme)
        drawLineStroke(beam, opacity(look.stroke), theme)
        drawLineBloom(beam, opacity(look.bloom), look, seconds, static)
    }
}

private class LineBeam(
    val variant: BeamColorVariant,
    val dark: Boolean,
    val mono: Boolean,
    /** Beam center along the bottom edge, in pixels. */
    val x: Float,
    /** `--beam-w`: width scale from the travel keyframes. */
    val w: Float,
    /** `--beam-h`: height scale from the breathe keyframes. */
    val h: Float,
    val spike: Float,
    val spike2: Float,
    val filter: FloatArray?,
    val radius: Float,
    /** One CSS px. */
    val px: Float,
)

private fun DrawScope.drawLineInner(beam: LineBeam, alpha: Float, theme: BeamTheme) {
    val shadow = if (theme == BeamTheme.Dark) Color.White.copy(alpha = 0.10f) else INK_SHADOW
    withLayer(alpha = alpha) {
        withLayer(colorMatrix = beam.filter) {
            val glows = lineInnerGlows(beam.variant)
            for (index in glows.lastIndex downTo 0) {
                val glow = glows[index]
                drawEllipseGradient(
                    center = Offset(beam.x + glow.dx * beam.px, size.height + glow.dy * beam.px),
                    radiusX = glow.rx * beam.w * beam.px,
                    radiusY = glow.ry * beam.h * beam.px,
                    stops = fadeOut(Color(glow.r, glow.g, glow.b).copy(alpha = glow.a)),
                )
            }
            drawInsetShadow(beam.radius, shadow, SHADOW_BLUR * beam.px, beam.px)
        }
        withLayer(blendMode = BlendMode.DstIn) {
            drawEdgeFade(INNER_EDGE * beam.px)
            drawBeamEllipse(beam, STROKE_MASK_W, STROKE_MASK_H, 0.45f, BlendMode.DstIn)
        }
    }
}

private fun DrawScope.drawLineStroke(beam: LineBeam, alpha: Float, theme: BeamTheme) {
    val borderWidth = BORDER_WIDTH * beam.px
    withLayer(alpha = alpha) {
        clipPath(ringPath((beam.radius - borderWidth).coerceAtLeast(0f), borderWidth)) {
            withLayer(colorMatrix = beam.filter) {
                val spots = lineSpots(beam.variant, theme)
                for (index in spots.lastIndex downTo 0) {
                    val spot = spots[index]
                    drawEllipseGradient(
                        center =
                            Offset(beam.x + spot.dx * beam.px, size.height + spot.dy * beam.px),
                        radiusX = spot.rx * beam.w * beam.px,
                        radiusY = spot.ry * beam.h * beam.px,
                        stops = fadeOut(Color(spot.r, spot.g, spot.b)),
                    )
                }
                drawLineHighlight(beam)
            }
            drawBeamEllipse(beam, STROKE_MASK_W, STROKE_MASK_H, 0.45f, BlendMode.DstIn)
        }
    }
}

/** The white (dark theme) or black (light theme) hot spot that sits on the edge. */
private fun DrawScope.drawLineHighlight(beam: LineBeam) {
    val center = Offset(beam.x, size.height + 2f * beam.px)
    if (beam.dark) {
        drawEllipseGradient(
            center,
            24f * beam.w * beam.px,
            28f * beam.h * beam.px,
            arrayOf(
                0f to Color.White.copy(alpha = 0.38f),
                0.30f to Color.White.copy(alpha = 0.12f),
                0.65f to Color.White.copy(alpha = 0f),
            ),
        )
    } else {
        drawEllipseGradient(
            center,
            35f * beam.w * beam.px,
            28f * beam.h * beam.px,
            arrayOf(
                0f to Color.Black.copy(alpha = 0.60f),
                0.35f to Color.Black.copy(alpha = 0.25f),
                0.70f to Color.Black.copy(alpha = 0f),
            ),
        )
    }
}

/**
 * `[data-beam-bloom]`: filtered first (blur, and for color palettes a wider hue swing), then masked
 * by a tall ellipse at the beam. It has no ring, so the spikes rise into the bar.
 */
private fun DrawScope.drawLineBloom(
    beam: LineBeam,
    alpha: Float,
    look: BeamLook,
    seconds: Float,
    static: Boolean,
) {
    // Mono only blurs; the color palettes animate blur + hue + brightness + saturation.
    val filter =
        if (static) null else lineFilter(look, seconds, HUE_RANGE + BLOOM_HUE_EXTRA, BLOOM_PERIOD)
    val blur = (if (static) MONO_BLOOM_BLUR else BLOOM_BLUR) * beam.px
    withLayer(alpha = alpha) {
        withLayer(colorMatrix = filter, blurSigma = blur) {
            clipPath(roundedPath(0f, (beam.radius - BORDER_WIDTH * beam.px).coerceAtLeast(0f))) {
                val spots = bloomSpots(beam)
                for (index in spots.lastIndex downTo 0) {
                    val spot = spots[index]
                    drawEllipseGradient(spot.center, spot.radiusX, spot.radiusY, spot.stops)
                }
            }
        }
        drawBeamEllipse(beam, BLOOM_MASK_W, BLOOM_MASK_H, 0.35f, BlendMode.DstIn)
    }
}

private class BloomSpot(
    val center: Offset,
    val radiusX: Float,
    val radiusY: Float,
    val stops: Array<Pair<Float, Color>>,
)

/** `getLineBloomGradients`, top gradient first: seven fixed spikes, then the glow at the beam. */
private fun DrawScope.bloomSpots(beam: LineBeam): List<BloomSpot> =
    fixedSpikes(beam) + beamGlow(beam)

/**
 * Thin vertical spikes at fixed spots along the edge; the bloom mask reveals them near the beam.
 */
private fun DrawScope.fixedSpikes(beam: LineBeam): List<BloomSpot> {
    val theme = if (beam.dark) BeamTheme.Dark else BeamTheme.Light
    val edge = edgeSpikeStops(beam, theme)
    val spikes = lineSpikes(beam.variant, theme)
    fun spikeStops(index: Int, mid: Float, end: Float): Array<Pair<Float, Color>> {
        val spike = spikes[index]
        return if (beam.mono) {
            stops(
                spike.center.attenuated(MONO_SPIKE),
                mid to spike.mid.attenuated(MONO_SPIKE * 0.7f),
                end,
            )
        } else {
            stops(spike.center.toColor(), mid to spike.mid.toColor(), end)
        }
    }
    fun spot(at: Float, lift: Float, rx: Float, ry: Float, stops: Array<Pair<Float, Color>>) =
        BloomSpot(
            Offset(at * size.width, size.height - lift * beam.px),
            rx * beam.px,
            ry * beam.px,
            stops,
        )
    val thin = if (beam.mono) MONO_THIN else COLOR_THIN
    val w4 = if (beam.dark) thin.w4 else thin.lightW4
    return listOf(
        spot(0.08f, 2f, thin.w1 * beam.spike, thin.h1 * beam.h, edge.first),
        spot(0.22f, 4f, 10f * beam.spike2, 35f * beam.h, edge.second),
        spot(0.36f, 3f, thin.w2 * (2f - beam.spike), thin.h2 * beam.h, spikeStops(0, 0.40f, 0.90f)),
        spot(0.50f, 2f, 14f * beam.spike2, 28f * beam.h, spikeStops(1, 0.55f, 0.96f)),
        spot(
            0.64f,
            4f,
            thin.w3 * (2f - beam.spike2),
            thin.h3 * beam.h,
            spikeStops(2, 0.35f, 0.89f),
        ),
        spot(0.78f, 2f, 7f * beam.spike, 45f * beam.h, spikeStops(3, 0.48f, 0.94f)),
        spot(0.92f, 3f, w4 * (2f - beam.spike), thin.h4 * beam.h, spikeStops(4, 0.42f, 0.91f)),
    )
}

/** Stops for the two outer spikes, drawn in the palette `spike` / `spikeLt` colors. */
private fun edgeSpikeStops(
    beam: LineBeam,
    theme: BeamTheme,
): Pair<Array<Pair<Float, Color>>, Array<Pair<Float, Color>>> {
    val (primary, secondary) = lineEdgeSpikes(beam.variant, theme)
    val center1 = if (beam.mono) primary.attenuated(0.14f) else primary.toColor()
    val center2 = if (beam.mono) secondary.attenuated(0.12f) else secondary.toColor()
    val mid1 =
        when {
            beam.mono -> primary.attenuated(if (beam.dark) 0.09f else 0.11f)
            beam.dark -> primary.toColor()
            else -> primary.toColor().copy(alpha = 0.85f)
        }
    val mid2 =
        when {
            beam.mono && beam.dark -> secondary.toColor().copy(alpha = 0.06f)
            beam.mono -> secondary.attenuated(0.09f)
            beam.dark -> secondary.toColor().copy(alpha = 0.49f)
            else -> secondary.toColor().copy(alpha = 0.7f)
        }
    return stops(center1, 0.30f to mid1, 0.88f) to stops(center2, 0.50f to mid2, 0.95f)
}

/** The glow that travels with the beam: a white dot and ambient wash, or a dark smudge in light. */
private fun DrawScope.beamGlow(beam: LineBeam): List<BloomSpot> {
    val px = beam.px
    if (!beam.dark) {
        return listOf(
            BloomSpot(
                Offset(beam.x, size.height),
                50f * beam.w * px,
                32f * beam.h * px,
                arrayOf(
                    0f to Color.Black.copy(alpha = 0.5f),
                    0.30f to Color.Black.copy(alpha = 0.18f),
                    0.60f to Color.Black.copy(alpha = 0.03f),
                    0.85f to Color.Black.copy(alpha = 0f),
                ),
            )
        )
    }
    val glow = if (beam.mono) 0.5f else 1f
    return listOf(
        BloomSpot(
            Offset(beam.x, size.height + px),
            21f * beam.spike * px,
            15f * beam.spike2 * px,
            arrayOf(
                0f to Color.White.copy(alpha = glow),
                0.20f to Color.White.copy(alpha = 0.9f * glow),
                0.50f to Color.White.copy(alpha = 0.5f * glow),
                1f to Color.White.copy(alpha = 0f),
            ),
        ),
        BloomSpot(
            Offset(beam.x, size.height),
            42f * beam.w * px,
            40f * beam.h * px,
            arrayOf(
                0f to Color.White.copy(alpha = 0.3f * glow),
                0.25f to Color.White.copy(alpha = 0.12f * glow),
                0.55f to Color.White.copy(alpha = 0.03f * glow),
                0.80f to Color.White.copy(alpha = 0f),
            ),
        ),
    )
}

/** Spike widths and heights. Mono widens and shortens them so they read as soft glows. */
private class ThinSpikes(
    val w1: Float,
    val w2: Float,
    val w3: Float,
    val w4: Float,
    val lightW4: Float,
    val h1: Float,
    val h2: Float,
    val h3: Float,
    val h4: Float,
)

private val COLOR_THIN = ThinSpikes(0.8f, 2f, 1.2f, 0.6f, 1f, 92f, 72f, 85f, 60f)
private val MONO_THIN = ThinSpikes(12f, 14f, 12f, 10f, 12f, 42f, 38f, 40f, 32f)

/** A white ellipse mask at the beam, `white 0%, rgba(255,255,255,.5) <mid>, transparent 100%`. */
private fun DrawScope.drawBeamEllipse(
    beam: LineBeam,
    radiusX: Float,
    radiusY: Float,
    mid: Float,
    blendMode: BlendMode,
) {
    drawEllipseGradient(
        center = Offset(beam.x, size.height),
        radiusX = radiusX * beam.w * beam.px,
        radiusY = radiusY * beam.h * beam.px,
        stops =
            arrayOf(
                0f to Color.White,
                mid to Color.White.copy(alpha = 0.5f),
                1f to Color.White.copy(alpha = 0f),
            ),
        blendMode = blendMode,
        cover = true,
    )
}

private fun lineFilter(look: BeamLook, seconds: Float, range: Float, period: Float): FloatArray {
    val hue =
        hueShiftDegrees(
            timeSeconds = seconds,
            range = range,
            period = period,
            staticColors = false,
            continuous = false,
        )
    return colorMatrix4x5(hue, look.brightness, look.saturation)
}

/** `color, transparent`, fading to the same color so the edge does not darken. */
private fun fadeOut(color: Color): Array<Pair<Float, Color>> =
    arrayOf(0f to color, 1f to color.copy(alpha = 0f))

private fun stops(
    center: Color,
    mid: Pair<Float, Color>,
    end: Float,
): Array<Pair<Float, Color>> = arrayOf(0f to center, mid, end to mid.second.copy(alpha = 0f))

private fun LineColor.toColor(): Color = Color(r, g, b).copy(alpha = a)

/** `attenuateSpike`: an rgba keeps its alpha times [factor]; an rgb gets [factor] as alpha. */
private fun LineColor.attenuated(factor: Float): Color = Color(r, g, b).copy(alpha = a * factor)

private val INK_SHADOW = Color.Black.copy(alpha = 0.14f)
private const val BORDER_WIDTH = 1f
private const val SHADOW_BLUR = 9f
private const val INNER_EDGE = 28f
private const val STROKE_MASK_W = 78f
private const val STROKE_MASK_H = 60f
private const val BLOOM_MASK_W = 84f
private const val BLOOM_MASK_H = 110f
private const val BLOOM_BLUR = 8f
private const val MONO_BLOOM_BLUR = 6f
private const val MONO_SPIKE = 0.14f
private const val HUE_RANGE = 13f
private const val HUE_PERIOD = 12f
private const val BLOOM_HUE_EXTRA = 10f
private const val BLOOM_PERIOD = 8f
/** CSS rounds `duration * 1.3`, `* 1.33` and `* 1.7` to one decimal for the 3.1s line. */
private const val BREATHE_SECONDS = 4.0f
private const val SPIKE_SECONDS = 4.1f
private const val SPIKE2_SECONDS = 5.3f
