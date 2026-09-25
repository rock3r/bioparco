package dev.sebastiano.borderbeam

/** Line-size data transcribed from border-beam `styles.ts`. Lengths are CSS px. */
internal data class LineGlow(
    val r: Int,
    val g: Int,
    val b: Int,
    val a: Float,
    val rx: Float,
    val ry: Float,
    val dx: Float,
    val dy: Float,
)

/** One bloom spike: the colour at the centre and at its mid stop. */
internal data class LineSpike(val center: LineColor, val mid: LineColor)

internal data class LineColor(val r: Int, val g: Int, val b: Int, val a: Float)

/** `lineInnerGradientData`. The web anchors these `dy` px above the bottom edge. */
private val lineInnerPalette: Map<BeamColorVariant, List<LineGlow>> =
    mapOf(
        BeamColorVariant.Colorful to
            listOf(
                LineGlow(255, 50, 100, 0.48f, 33.0f, 30.0f, 0.0f, 0.0f),
                LineGlow(40, 180, 220, 0.42f, 24.0f, 26.0f, 39.0f, -3.0f),
                LineGlow(50, 200, 80, 0.48f, 27.0f, 24.0f, -36.0f, 0.0f),
                LineGlow(180, 40, 240, 0.42f, 23.0f, 28.0f, -54.0f, -2.0f),
                LineGlow(255, 160, 30, 0.5f, 24.0f, 24.0f, 51.0f, -1.0f),
                LineGlow(100, 70, 255, 0.45f, 30.0f, 20.0f, 21.0f, 0.0f),
                LineGlow(40, 140, 255, 0.4f, 25.0f, 18.0f, -21.0f, -2.0f),
                LineGlow(240, 50, 180, 0.45f, 21.0f, 24.0f, 66.0f, 0.0f),
                LineGlow(30, 185, 170, 0.52f, 18.0f, 26.0f, -66.0f, -1.0f),
            ),
        BeamColorVariant.Mono to
            listOf(
                LineGlow(200, 200, 200, 0.48f, 33.0f, 30.0f, 0.0f, 0.0f),
                LineGlow(170, 170, 170, 0.42f, 24.0f, 26.0f, 39.0f, -3.0f),
                LineGlow(155, 155, 155, 0.48f, 27.0f, 24.0f, -36.0f, 0.0f),
                LineGlow(185, 185, 185, 0.42f, 23.0f, 28.0f, -54.0f, -2.0f),
                LineGlow(165, 165, 165, 0.5f, 24.0f, 24.0f, 51.0f, -1.0f),
                LineGlow(180, 180, 180, 0.45f, 30.0f, 20.0f, 21.0f, 0.0f),
                LineGlow(160, 160, 160, 0.4f, 25.0f, 18.0f, -21.0f, -2.0f),
                LineGlow(175, 175, 175, 0.45f, 21.0f, 24.0f, 66.0f, 0.0f),
                LineGlow(190, 190, 190, 0.52f, 18.0f, 26.0f, -66.0f, -1.0f),
            ),
        BeamColorVariant.Ocean to
            listOf(
                LineGlow(100, 80, 220, 0.48f, 33.0f, 30.0f, 0.0f, 0.0f),
                LineGlow(60, 120, 255, 0.42f, 24.0f, 26.0f, 39.0f, -3.0f),
                LineGlow(80, 100, 200, 0.48f, 27.0f, 24.0f, -36.0f, 0.0f),
                LineGlow(130, 70, 255, 0.42f, 23.0f, 28.0f, -54.0f, -2.0f),
                LineGlow(70, 130, 255, 0.5f, 24.0f, 24.0f, 51.0f, -1.0f),
                LineGlow(120, 80, 255, 0.45f, 30.0f, 20.0f, 21.0f, 0.0f),
                LineGlow(90, 110, 230, 0.4f, 25.0f, 18.0f, -21.0f, -2.0f),
                LineGlow(110, 90, 240, 0.45f, 21.0f, 24.0f, 66.0f, 0.0f),
                LineGlow(140, 100, 255, 0.52f, 18.0f, 26.0f, -66.0f, -1.0f),
            ),
        BeamColorVariant.Sunset to
            listOf(
                LineGlow(255, 100, 60, 0.48f, 33.0f, 30.0f, 0.0f, 0.0f),
                LineGlow(255, 180, 50, 0.42f, 24.0f, 26.0f, 39.0f, -3.0f),
                LineGlow(255, 140, 70, 0.48f, 27.0f, 24.0f, -36.0f, 0.0f),
                LineGlow(255, 80, 80, 0.42f, 23.0f, 28.0f, -54.0f, -2.0f),
                LineGlow(255, 200, 60, 0.5f, 24.0f, 24.0f, 51.0f, -1.0f),
                LineGlow(255, 120, 50, 0.45f, 30.0f, 20.0f, 21.0f, 0.0f),
                LineGlow(255, 160, 80, 0.4f, 25.0f, 18.0f, -21.0f, -2.0f),
                LineGlow(255, 90, 60, 0.45f, 21.0f, 24.0f, 66.0f, 0.0f),
                LineGlow(255, 70, 70, 0.52f, 18.0f, 26.0f, -66.0f, -1.0f),
            ),
    )

/** `lineBloomColors`: five fixed spikes per palette and theme. */
private val lineSpikePalette: Map<Pair<BeamColorVariant, BeamTheme>, List<LineSpike>> =
    mapOf(
        (BeamColorVariant.Colorful to BeamTheme.Dark) to
            listOf(
                LineSpike(LineColor(100, 70, 255, 1.0f), LineColor(100, 70, 255, 1.0f)),
                LineSpike(LineColor(255, 170, 40, 0.59f), LineColor(255, 170, 40, 0.29f)),
                LineSpike(LineColor(50, 200, 100, 1.0f), LineColor(50, 200, 100, 1.0f)),
                LineSpike(LineColor(200, 50, 240, 0.91f), LineColor(200, 50, 240, 0.45f)),
                LineSpike(LineColor(40, 140, 255, 1.0f), LineColor(40, 140, 255, 1.0f)),
            ),
        (BeamColorVariant.Colorful to BeamTheme.Light) to
            listOf(
                LineSpike(LineColor(80, 50, 200, 1.0f), LineColor(80, 50, 200, 0.8f)),
                LineSpike(LineColor(210, 130, 0, 0.7f), LineColor(210, 130, 0, 0.46f)),
                LineSpike(LineColor(30, 160, 70, 1.0f), LineColor(30, 160, 70, 0.82f)),
                LineSpike(LineColor(160, 30, 190, 1.0f), LineColor(160, 30, 190, 0.7f)),
                LineSpike(LineColor(30, 100, 200, 1.0f), LineColor(30, 100, 200, 0.78f)),
            ),
        (BeamColorVariant.Mono to BeamTheme.Dark) to
            listOf(
                LineSpike(LineColor(200, 200, 200, 1.0f), LineColor(200, 200, 200, 1.0f)),
                LineSpike(LineColor(180, 180, 180, 0.59f), LineColor(180, 180, 180, 0.29f)),
                LineSpike(LineColor(190, 190, 190, 1.0f), LineColor(190, 190, 190, 1.0f)),
                LineSpike(LineColor(170, 170, 170, 0.91f), LineColor(170, 170, 170, 0.45f)),
                LineSpike(LineColor(185, 185, 185, 1.0f), LineColor(185, 185, 185, 1.0f)),
            ),
        (BeamColorVariant.Mono to BeamTheme.Light) to
            listOf(
                LineSpike(LineColor(80, 80, 80, 1.0f), LineColor(80, 80, 80, 0.8f)),
                LineSpike(LineColor(100, 100, 100, 0.7f), LineColor(100, 100, 100, 0.46f)),
                LineSpike(LineColor(70, 70, 70, 1.0f), LineColor(70, 70, 70, 0.82f)),
                LineSpike(LineColor(90, 90, 90, 1.0f), LineColor(90, 90, 90, 0.7f)),
                LineSpike(LineColor(85, 85, 85, 1.0f), LineColor(85, 85, 85, 0.78f)),
            ),
        (BeamColorVariant.Ocean to BeamTheme.Dark) to
            listOf(
                LineSpike(LineColor(100, 80, 255, 1.0f), LineColor(100, 80, 255, 1.0f)),
                LineSpike(LineColor(80, 130, 220, 0.59f), LineColor(80, 130, 220, 0.29f)),
                LineSpike(LineColor(60, 100, 255, 1.0f), LineColor(60, 100, 255, 1.0f)),
                LineSpike(LineColor(90, 120, 200, 0.91f), LineColor(90, 120, 200, 0.45f)),
                LineSpike(LineColor(120, 90, 255, 1.0f), LineColor(120, 90, 255, 1.0f)),
            ),
        (BeamColorVariant.Ocean to BeamTheme.Light) to
            listOf(
                LineSpike(LineColor(50, 40, 180, 1.0f), LineColor(50, 40, 180, 0.8f)),
                LineSpike(LineColor(40, 80, 200, 0.7f), LineColor(40, 80, 200, 0.46f)),
                LineSpike(LineColor(30, 50, 190, 1.0f), LineColor(30, 50, 190, 0.82f)),
                LineSpike(LineColor(60, 90, 180, 1.0f), LineColor(60, 90, 180, 0.7f)),
                LineSpike(LineColor(70, 60, 200, 1.0f), LineColor(70, 60, 200, 0.78f)),
            ),
        (BeamColorVariant.Sunset to BeamTheme.Dark) to
            listOf(
                LineSpike(LineColor(255, 100, 80, 1.0f), LineColor(255, 100, 80, 1.0f)),
                LineSpike(LineColor(255, 150, 80, 0.59f), LineColor(255, 150, 80, 0.29f)),
                LineSpike(LineColor(255, 80, 60, 1.0f), LineColor(255, 80, 60, 1.0f)),
                LineSpike(LineColor(255, 120, 50, 0.91f), LineColor(255, 120, 50, 0.45f)),
                LineSpike(LineColor(255, 140, 70, 1.0f), LineColor(255, 140, 70, 1.0f)),
            ),
        (BeamColorVariant.Sunset to BeamTheme.Light) to
            listOf(
                LineSpike(LineColor(200, 60, 30, 1.0f), LineColor(200, 60, 30, 0.8f)),
                LineSpike(LineColor(220, 100, 20, 0.7f), LineColor(220, 100, 20, 0.46f)),
                LineSpike(LineColor(180, 40, 20, 1.0f), LineColor(180, 40, 20, 0.82f)),
                LineSpike(LineColor(210, 80, 10, 1.0f), LineColor(210, 80, 10, 0.7f)),
                LineSpike(LineColor(190, 70, 30, 1.0f), LineColor(190, 70, 30, 0.78f)),
            ),
    )

/** `spike` / `spikeLt` from `colorPalettes`: the two edge spikes, primary then secondary. */
private val lineEdgeSpikeColors:
    Map<Pair<BeamColorVariant, BeamTheme>, Pair<LineColor, LineColor>> =
    mapOf(
        (BeamColorVariant.Colorful to BeamTheme.Dark) to
            (LineColor(255, 60, 80, 1.0f) to LineColor(40, 190, 180, 0.98f)),
        (BeamColorVariant.Colorful to BeamTheme.Light) to
            (LineColor(200, 30, 60, 1.0f) to LineColor(20, 150, 140, 1.0f)),
        (BeamColorVariant.Mono to BeamTheme.Dark) to
            (LineColor(200, 200, 200, 1.0f) to LineColor(170, 170, 170, 1.0f)),
        (BeamColorVariant.Mono to BeamTheme.Light) to
            (LineColor(80, 80, 80, 1.0f) to LineColor(120, 120, 120, 1.0f)),
        (BeamColorVariant.Ocean to BeamTheme.Dark) to
            (LineColor(100, 120, 255, 1.0f) to LineColor(130, 100, 220, 0.98f)),
        (BeamColorVariant.Ocean to BeamTheme.Light) to
            (LineColor(60, 60, 180, 1.0f) to LineColor(80, 100, 200, 1.0f)),
        (BeamColorVariant.Sunset to BeamTheme.Dark) to
            (LineColor(255, 140, 80, 1.0f) to LineColor(255, 100, 60, 0.98f)),
        (BeamColorVariant.Sunset to BeamTheme.Light) to
            (LineColor(200, 80, 40, 1.0f) to LineColor(220, 120, 30, 1.0f)),
    )

internal fun lineInnerGlows(variant: BeamColorVariant): List<LineGlow> =
    lineInnerPalette.getValue(variant)

internal fun lineSpikes(variant: BeamColorVariant, theme: BeamTheme): List<LineSpike> =
    lineSpikePalette.getValue(variant to theme)

internal fun lineEdgeSpikes(
    variant: BeamColorVariant,
    theme: BeamTheme,
): Pair<LineColor, LineColor> = lineEdgeSpikeColors.getValue(variant to theme)
