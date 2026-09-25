package dev.sebastiano.borderbeam

internal val maskStopsMd: List<Pair<Float, Float>> =
    listOf(
        0f to 0f,
        0.30f to 0f,
        0.36f to 0.10f,
        0.44f to 0.35f,
        0.52f to 1f,
        0.80f to 1f,
        0.86f to 0.35f,
        0.92f to 0.10f,
        0.95f to 0f,
        1f to 0f,
    )

internal val maskStopsSm: List<Pair<Float, Float>> =
    listOf(
        0f to 0f,
        0.22f to 0f,
        0.28f to 0.12f,
        0.36f to 0.40f,
        0.46f to 1f,
        0.82f to 1f,
        0.88f to 0.40f,
        0.94f to 0.12f,
        0.97f to 0f,
        1f to 0f,
    )

internal val highlightStopsDark: List<Pair<Float, Float>> =
    listOf(
        0f to 0f,
        0.54f to 0f,
        0.57f to 0.10f,
        0.60f to 0.30f,
        0.63f to 0.60f,
        0.66f to 0.75f,
        0.69f to 0.60f,
        0.72f to 0.30f,
        0.75f to 0.10f,
        0.78f to 0f,
        1f to 0f,
    )

internal val highlightStopsLight: List<Pair<Float, Float>> =
    listOf(
        0f to 0f,
        0.54f to 0f,
        0.57f to 0.08f,
        0.60f to 0.20f,
        0.63f to 0.40f,
        0.66f to 0.55f,
        0.69f to 0.40f,
        0.72f to 0.20f,
        0.75f to 0.08f,
        0.78f to 0f,
        1f to 0f,
    )

internal val bloomStopsDark: List<Pair<Float, Float>> =
    listOf(
        0f to 0f,
        0.58f to 0f,
        0.62f to 0.03f,
        0.65f to 0.08f,
        0.67f to 0.20f,
        0.69f to 0.45f,
        0.70f to 0.85f,
        0.705f to 0.85f,
        0.715f to 0.45f,
        0.73f to 0.20f,
        0.75f to 0.08f,
        0.78f to 0.03f,
        0.82f to 0f,
        1f to 0f,
    )

internal val bloomStopsLight: List<Pair<Float, Float>> =
    listOf(
        0f to 0f,
        0.58f to 0f,
        0.62f to 0.02f,
        0.65f to 0.08f,
        0.67f to 0.20f,
        0.69f to 0.40f,
        0.70f to 0.60f,
        0.705f to 0.60f,
        0.715f to 0.40f,
        0.73f to 0.20f,
        0.75f to 0.08f,
        0.78f to 0.02f,
        0.82f to 0f,
        1f to 0f,
    )

internal val lineTravelX: List<Pair<Float, Float>> =
    listOf(
        0f to 0.06f,
        0.10f to 0.15f,
        0.20f to 0.25f,
        0.30f to 0.35f,
        0.40f to 0.44f,
        0.50f to 0.50f,
        0.60f to 0.56f,
        0.70f to 0.65f,
        0.80f to 0.75f,
        0.90f to 0.85f,
        1f to 0.94f,
    )

internal val lineTravelWidth: List<Pair<Float, Float>> =
    listOf(
        0f to 0.50f,
        0.10f to 0.80f,
        0.20f to 1.10f,
        0.30f to 1.30f,
        0.40f to 1.45f,
        0.50f to 1.50f,
        0.60f to 1.45f,
        0.70f to 1.30f,
        0.80f to 1.10f,
        0.90f to 0.80f,
        1f to 0.50f,
    )

internal val lineEdgeFade: List<Pair<Float, Float>> =
    listOf(
        0f to 0f,
        0.125f to 0f,
        0.325f to 1f,
        0.675f to 1f,
        0.875f to 0f,
        1f to 0f,
    )

internal val lineBreathe: List<Pair<Float, Float>> =
    listOf(
        0f to 0.80f,
        0.25f to 1.25f,
        0.55f to 0.85f,
        0.80f to 1.30f,
        1f to 0.80f,
    )
