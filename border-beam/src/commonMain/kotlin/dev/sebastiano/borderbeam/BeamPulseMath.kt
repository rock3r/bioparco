package dev.sebastiano.borderbeam

/** Breathing periods from `pulseParams` in the upstream driver. */
internal fun pulseParams(size: BeamSize, theme: BeamTheme, duration: Float): PulseParams {
    val dark = theme == BeamTheme.Dark
    val scale = duration / PULSE_REFERENCE
    return if (size == BeamSize.PulseInner) {
        PulseParams(
            sp = 0.28f,
            dr = if (dark) 33f else 40f,
            op = if (dark) 0.48f else 0.45f,
            gh = if (dark) 0.34f else 0.22f,
            bs = (if (dark) 1.9f else 2.6f) * scale,
            ss = (if (dark) 2.6f else 4.6f) * scale,
            ghs = (if (dark) 2.4f else 5.5f) * scale,
            huePeriod = 16f,
        )
    } else {
        PulseParams(
            sp = if (dark) 0.28f else 0.36f,
            dr = if (dark) 14f else 19f,
            op = if (dark) 0.46f else 0f,
            gh = if (dark) 0.16f else 0.58f,
            bs = (if (dark) 2.3f else 3.7f) * scale,
            ss = (if (dark) 6.4f else 4.6f) * scale,
            ghs = (if (dark) 2.4f else 3.8f) * scale,
            huePeriod = 14f,
        )
    }
}

internal fun pulseCornerAlpha(params: PulseParams, corner: BeamCorner, timeSeconds: Float): Float {
    val (period, delay) =
        when (corner) {
            BeamCorner.Tl -> params.bs to 0f
            BeamCorner.Tr -> params.bs * 1.32f to params.bs * 0.28f
            BeamCorner.Bl -> params.bs * 0.84f to params.bs * 0.55f
            BeamCorner.Br -> params.bs * 1.58f to params.bs * 0.83f
        }
    return oscillatorValue(1f - params.op, 1f, period, delay, timeSeconds)
}

internal fun pulseBreathe(params: PulseParams, timeSeconds: Float): Float =
    oscillatorValue(1f - params.gh, 1f + params.gh, params.ghs, 0f, timeSeconds)

internal fun pulseWidthScale(params: PulseParams, region: Int, timeSeconds: Float): Float {
    val (a, b, period) =
        when (region) {
            1 -> Triple(1f - params.sp, 1f + params.sp * 1.1f, params.ss * 0.9f)
            2 -> Triple(1f + params.sp, 1f - params.sp * 0.85f, params.ss * 1.1f)
            else -> Triple(1f - params.sp * 0.6f, 1f + params.sp * 1.15f, params.ss * 0.98f)
        }
    return oscillatorValue(a, b, period, 0f, timeSeconds)
}

internal fun pulseHeightScale(params: PulseParams, region: Int, timeSeconds: Float): Float {
    val (a, b, period) =
        when (region) {
            1 -> Triple(1f + params.sp * 0.9f, 1f - params.sp * 0.85f, params.ss * 1.26f)
            2 -> Triple(1f - params.sp * 0.8f, 1f + params.sp * 1.05f, params.ss * 0.81f)
            else -> Triple(1f + params.sp * 0.75f, 1f - params.sp, params.ss * 1.4f)
        }
    return oscillatorValue(a, b, period, 0f, timeSeconds)
}

internal fun pulseDrift(params: PulseParams, region: Int, timeSeconds: Float): Pair<Float, Float> {
    val period =
        when (region) {
            1 -> params.bs * 1.6f
            2 -> params.bs * 1.88f
            else -> params.bs * 1.45f
        }
    val driftX =
        when (region) {
            1 -> oscillatorValue(-params.dr, params.dr * 0.9f, period, 0f, timeSeconds)
            2 -> oscillatorValue(params.dr * 0.8f, -params.dr * 0.9f, period, 0f, timeSeconds)
            else -> oscillatorValue(-params.dr * 0.6f, params.dr, period, 0f, timeSeconds)
        }
    val driftY =
        when (region) {
            1 -> oscillatorValue(params.dr * 0.55f, -params.dr * 0.7f, period, 0f, timeSeconds)
            2 -> oscillatorValue(-params.dr, params.dr * 0.65f, period, 0f, timeSeconds)
            else -> oscillatorValue(-params.dr * 0.85f, params.dr * 0.45f, period, 0f, timeSeconds)
        }
    return driftX to driftY
}

private const val PULSE_REFERENCE = 2.3f
