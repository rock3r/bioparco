package dev.sebastiano.borderbeam

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sin

/** Cosine ease used by the pulse driver: 0 at phase 0/1, 1 at phase 0.5. */
fun pingPong(phase: Float): Float = ((1.0 - cos(TWO_PI * phase)) / 2.0).toFloat()

fun oscillatorValue(a: Float, b: Float, period: Float, delay: Float, timeSeconds: Float): Float {
    if (period <= 0f) return a
    return a + (b - a) * pingPong((timeSeconds - delay) / period)
}

fun sampleKeyframes(
    stops: List<Pair<Float, Float>>,
    progress: Float,
    eased: Boolean = false,
): Float {
    if (stops.isEmpty()) return 0f
    val t = progress.coerceIn(0f, 1f)
    if (t <= stops.first().first) return stops.first().second
    if (t >= stops.last().first) return stops.last().second
    for (index in 1 until stops.size) {
        val (end, endValue) = stops[index]
        if (t <= end) {
            val (start, startValue) = stops[index - 1]
            val span = end - start
            if (span <= 0f) return endValue
            val fraction = (t - start) / span
            val curved = if (eased) easeInOut(fraction) else fraction
            return startValue + (endValue - startValue) * curved
        }
    }
    return stops.last().second
}

fun easeInOut(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

fun clampUnit(value: Float): Float = value.coerceIn(0f, 1f)

/** CSS clamps the `opacity` property, including presets that are authored above 1. */
fun cssOpacity(value: Float): Float = value.coerceIn(0f, 1f)

fun layerOpacity(preset: Float, variant: BeamColorVariant, strength: Float, fade: Float): Float =
    cssOpacity(
        preset * monoOpacityMultiplier(variant) * clampUnit(strength) * fade.coerceIn(0f, 1f)
    )

fun defaultDurationSeconds(size: BeamSize): Float =
    when (size) {
        BeamSize.Line -> LINE_DURATION
        BeamSize.PulseInner,
        BeamSize.PulseOutside -> PULSE_DURATION
        BeamSize.Md,
        BeamSize.Sm -> ROTATE_DURATION
    }

fun effectiveHueRange(size: BeamSize, hueRange: Float): Float =
    if (size == BeamSize.Line) minOf(hueRange, LINE_HUE_CAP) else hueRange

fun forcesStaticColors(variant: BeamColorVariant, requested: Boolean): Boolean =
    variant == BeamColorVariant.Mono || requested

fun monoOpacityMultiplier(variant: BeamColorVariant): Float =
    if (variant == BeamColorVariant.Mono) MONO_OPACITY else 1f

fun hueShiftDegrees(
    timeSeconds: Float,
    range: Float,
    period: Float,
    staticColors: Boolean,
    continuous: Boolean,
): Float {
    if (staticColors || period <= 0f) return 0f
    return if (continuous) {
        fraction(timeSeconds / period) * range
    } else {
        -range + 2f * range * pingPong(timeSeconds / period)
    }
}

/** Element size / reference card, clamped the way pulse-outside measures its halo. */
fun pulseGlowScale(lengthPx: Float, referencePx: Float): Float {
    if (referencePx <= 0f) return 1f
    val clamped = (lengthPx / referencePx).coerceIn(GLOW_SCALE_MIN, GLOW_SCALE_MAX)
    return round(clamped * 1000f) / 1000f
}

fun fractionalTurn(seconds: Float, duration: Float): Float {
    if (duration <= 0f) return 0f
    return fraction(seconds / duration)
}

/** CSS `hue-rotate`, the feColorMatrix linear-RGB form, not an HSL spin. */
fun hueRotateMatrix(degrees: Float): FloatArray {
    val rad = degrees * PI.toFloat() / 180f
    val c = cos(rad)
    val s = sin(rad)
    return floatArrayOf(
        0.213f + c * 0.787f - s * 0.213f,
        0.715f - c * 0.715f - s * 0.715f,
        0.072f - c * 0.072f + s * 0.928f,
        0.213f - c * 0.213f + s * 0.143f,
        0.715f + c * 0.285f + s * 0.140f,
        0.072f - c * 0.072f - s * 0.283f,
        0.213f - c * 0.213f - s * 0.787f,
        0.715f - c * 0.715f + s * 0.715f,
        0.072f + c * 0.928f + s * 0.072f,
    )
}

fun saturateMatrix(saturation: Float): FloatArray =
    floatArrayOf(
        0.213f + 0.787f * saturation,
        0.715f - 0.715f * saturation,
        0.072f - 0.072f * saturation,
        0.213f - 0.213f * saturation,
        0.715f + 0.285f * saturation,
        0.072f - 0.072f * saturation,
        0.213f - 0.213f * saturation,
        0.715f - 0.715f * saturation,
        0.072f + 0.928f * saturation,
    )

fun multiply3x3(left: FloatArray, right: FloatArray): FloatArray {
    val out = FloatArray(9)
    for (row in 0 until 3) {
        for (col in 0 until 3) {
            var sum = 0f
            for (k in 0 until 3) {
                sum += left[row * 3 + k] * right[k * 3 + col]
            }
            out[row * 3 + col] = sum
        }
    }
    return out
}

/** `hue-rotate(hue) brightness(b) saturate(s)`, applied in that order. */
fun composedFilterMatrix(hueDegrees: Float, brightness: Float, saturation: Float): FloatArray {
    val hue = hueRotateMatrix(hueDegrees)
    for (index in hue.indices) hue[index] *= brightness
    return multiply3x3(saturateMatrix(saturation), hue)
}

private fun fraction(value: Float): Float = value - floor(value.toDouble()).toFloat()

private const val TWO_PI = (PI * 2.0).toFloat()
private const val ROTATE_DURATION = 1.96f
private const val LINE_DURATION = 3.1f
private const val PULSE_DURATION = 2.3f
private const val LINE_HUE_CAP = 13f
private const val MONO_OPACITY = 0.5f
private const val GLOW_SCALE_MIN = 0.35f
private const val GLOW_SCALE_MAX = 4f
