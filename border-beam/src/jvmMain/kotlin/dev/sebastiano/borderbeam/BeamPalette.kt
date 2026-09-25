package dev.sebastiano.borderbeam

/** Palettes transcribed from border-beam `styles.ts`. Ellipse sizes are CSS radii. */
internal data class BeamBlob(
    val r: Int,
    val g: Int,
    val b: Int,
    val a: Float,
    val x: Float,
    val y: Float,
    val rx: Float,
    val ry: Float,
    val region: Int,
    val corner: BeamCorner,
)

internal data class LineSpot(
    val r: Int,
    val g: Int,
    val b: Int,
    val rx: Float,
    val ry: Float,
    val dx: Float,
    val dy: Float,
)

private val borderPalette: Map<BeamColorVariant, List<BeamBlob>> =
    mapOf(
        BeamColorVariant.Colorful to
            listOf(
                BeamBlob(255, 50, 100, 1.000f, 0.3300f, -0.0740f, 70.0f, 40.0f, 1, BeamCorner.Tl),
                BeamBlob(40, 140, 255, 1.000f, 0.1200f, -0.0500f, 60.0f, 35.0f, 2, BeamCorner.Tl),
                BeamBlob(50, 200, 80, 1.000f, 0.0210f, 0.6830f, 40.0f, 70.0f, 3, BeamCorner.Bl),
                BeamBlob(30, 185, 170, 1.000f, 0.0210f, 0.6830f, 20.0f, 35.0f, 1, BeamCorner.Bl),
                BeamBlob(100, 70, 255, 1.000f, 0.7440f, 1.0000f, 180.0f, 32.0f, 2, BeamCorner.Br),
                BeamBlob(40, 140, 255, 1.000f, 0.5500f, 1.0000f, 85.0f, 26.0f, 3, BeamCorner.Br),
                BeamBlob(255, 120, 40, 1.000f, 0.9390f, 0.0000f, 74.0f, 32.0f, 1, BeamCorner.Tr),
                BeamBlob(240, 50, 180, 1.000f, 1.0000f, 0.2710f, 26.0f, 42.0f, 2, BeamCorner.Tr),
                BeamBlob(180, 40, 240, 1.000f, 1.0000f, 0.2710f, 52.0f, 48.0f, 3, BeamCorner.Tr),
            ),
        BeamColorVariant.Mono to
            listOf(
                BeamBlob(180, 180, 180, 1.000f, 0.3300f, -0.0740f, 70.0f, 40.0f, 1, BeamCorner.Tl),
                BeamBlob(140, 140, 140, 1.000f, 0.1200f, -0.0500f, 60.0f, 35.0f, 2, BeamCorner.Tl),
                BeamBlob(160, 160, 160, 1.000f, 0.0210f, 0.6830f, 40.0f, 70.0f, 3, BeamCorner.Bl),
                BeamBlob(130, 130, 130, 1.000f, 0.0210f, 0.6830f, 20.0f, 35.0f, 1, BeamCorner.Bl),
                BeamBlob(170, 170, 170, 1.000f, 0.7440f, 1.0000f, 180.0f, 32.0f, 2, BeamCorner.Br),
                BeamBlob(150, 150, 150, 1.000f, 0.5500f, 1.0000f, 85.0f, 26.0f, 3, BeamCorner.Br),
                BeamBlob(190, 190, 190, 1.000f, 0.9390f, 0.0000f, 74.0f, 32.0f, 1, BeamCorner.Tr),
                BeamBlob(145, 145, 145, 1.000f, 1.0000f, 0.2710f, 26.0f, 42.0f, 2, BeamCorner.Tr),
                BeamBlob(165, 165, 165, 1.000f, 1.0000f, 0.2710f, 52.0f, 48.0f, 3, BeamCorner.Tr),
            ),
        BeamColorVariant.Ocean to
            listOf(
                BeamBlob(100, 80, 220, 1.000f, 0.3300f, -0.0740f, 70.0f, 40.0f, 1, BeamCorner.Tl),
                BeamBlob(60, 120, 255, 1.000f, 0.1200f, -0.0500f, 60.0f, 35.0f, 2, BeamCorner.Tl),
                BeamBlob(80, 100, 200, 1.000f, 0.0210f, 0.6830f, 40.0f, 70.0f, 3, BeamCorner.Bl),
                BeamBlob(50, 140, 220, 1.000f, 0.0210f, 0.6830f, 20.0f, 35.0f, 1, BeamCorner.Bl),
                BeamBlob(120, 80, 255, 1.000f, 0.7440f, 1.0000f, 180.0f, 32.0f, 2, BeamCorner.Br),
                BeamBlob(70, 130, 255, 1.000f, 0.5500f, 1.0000f, 85.0f, 26.0f, 3, BeamCorner.Br),
                BeamBlob(140, 100, 240, 1.000f, 0.9390f, 0.0000f, 74.0f, 32.0f, 1, BeamCorner.Tr),
                BeamBlob(90, 110, 230, 1.000f, 1.0000f, 0.2710f, 26.0f, 42.0f, 2, BeamCorner.Tr),
                BeamBlob(130, 70, 255, 1.000f, 1.0000f, 0.2710f, 52.0f, 48.0f, 3, BeamCorner.Tr),
            ),
        BeamColorVariant.Sunset to
            listOf(
                BeamBlob(255, 80, 50, 1.000f, 0.3300f, -0.0740f, 70.0f, 40.0f, 1, BeamCorner.Tl),
                BeamBlob(255, 160, 40, 1.000f, 0.1200f, -0.0500f, 60.0f, 35.0f, 2, BeamCorner.Tl),
                BeamBlob(255, 120, 60, 1.000f, 0.0210f, 0.6830f, 40.0f, 70.0f, 3, BeamCorner.Bl),
                BeamBlob(255, 200, 50, 1.000f, 0.0210f, 0.6830f, 20.0f, 35.0f, 1, BeamCorner.Bl),
                BeamBlob(255, 100, 80, 1.000f, 0.7440f, 1.0000f, 180.0f, 32.0f, 2, BeamCorner.Br),
                BeamBlob(255, 180, 60, 1.000f, 0.5500f, 1.0000f, 85.0f, 26.0f, 3, BeamCorner.Br),
                BeamBlob(255, 60, 60, 1.000f, 0.9390f, 0.0000f, 74.0f, 32.0f, 1, BeamCorner.Tr),
                BeamBlob(255, 140, 50, 1.000f, 1.0000f, 0.2710f, 26.0f, 42.0f, 2, BeamCorner.Tr),
                BeamBlob(255, 90, 70, 1.000f, 1.0000f, 0.2710f, 52.0f, 48.0f, 3, BeamCorner.Tr),
            ),
    )

private val smallBorderPalette: Map<BeamColorVariant, List<BeamBlob>> =
    mapOf(
        BeamColorVariant.Colorful to
            listOf(
                BeamBlob(50, 200, 80, 1.000f, 0.0200f, 0.6800f, 9.0f, 18.0f, 1, BeamCorner.Tl),
                BeamBlob(30, 185, 170, 1.000f, 0.0200f, 0.6800f, 4.0f, 8.0f, 2, BeamCorner.Tl),
                BeamBlob(255, 120, 40, 1.000f, 0.7200f, -0.0300f, 59.0f, 9.0f, 3, BeamCorner.Bl),
                BeamBlob(100, 70, 255, 1.000f, 0.7400f, 1.0000f, 42.0f, 7.0f, 1, BeamCorner.Bl),
                BeamBlob(240, 50, 180, 1.000f, 1.0000f, 0.2700f, 10.0f, 17.0f, 2, BeamCorner.Br),
                BeamBlob(180, 40, 240, 1.000f, 1.0000f, 0.2700f, 10.0f, 18.0f, 3, BeamCorner.Br),
                BeamBlob(40, 140, 255, 1.000f, 1.0000f, 0.2700f, 5.0f, 10.0f, 1, BeamCorner.Tr),
                BeamBlob(255, 50, 100, 1.000f, 1.0000f, 0.2700f, 11.0f, 12.0f, 2, BeamCorner.Tr),
            ),
        BeamColorVariant.Mono to
            listOf(
                BeamBlob(160, 160, 160, 1.000f, 0.0200f, 0.6800f, 9.0f, 18.0f, 1, BeamCorner.Tl),
                BeamBlob(140, 140, 140, 1.000f, 0.0200f, 0.6800f, 4.0f, 8.0f, 2, BeamCorner.Tl),
                BeamBlob(180, 180, 180, 1.000f, 0.7200f, -0.0300f, 59.0f, 9.0f, 3, BeamCorner.Bl),
                BeamBlob(150, 150, 150, 1.000f, 0.7400f, 1.0000f, 42.0f, 7.0f, 1, BeamCorner.Bl),
                BeamBlob(170, 170, 170, 1.000f, 1.0000f, 0.2700f, 10.0f, 17.0f, 2, BeamCorner.Br),
                BeamBlob(155, 155, 155, 1.000f, 1.0000f, 0.2700f, 10.0f, 18.0f, 3, BeamCorner.Br),
                BeamBlob(145, 145, 145, 1.000f, 1.0000f, 0.2700f, 5.0f, 10.0f, 1, BeamCorner.Tr),
                BeamBlob(165, 165, 165, 1.000f, 1.0000f, 0.2700f, 11.0f, 12.0f, 2, BeamCorner.Tr),
            ),
        BeamColorVariant.Ocean to
            listOf(
                BeamBlob(60, 140, 200, 1.000f, 0.0200f, 0.6800f, 9.0f, 18.0f, 1, BeamCorner.Tl),
                BeamBlob(50, 120, 180, 1.000f, 0.0200f, 0.6800f, 4.0f, 8.0f, 2, BeamCorner.Tl),
                BeamBlob(100, 80, 220, 1.000f, 0.7200f, -0.0300f, 59.0f, 9.0f, 3, BeamCorner.Bl),
                BeamBlob(80, 100, 255, 1.000f, 0.7400f, 1.0000f, 42.0f, 7.0f, 1, BeamCorner.Bl),
                BeamBlob(120, 70, 240, 1.000f, 1.0000f, 0.2700f, 10.0f, 17.0f, 2, BeamCorner.Br),
                BeamBlob(90, 80, 220, 1.000f, 1.0000f, 0.2700f, 10.0f, 18.0f, 3, BeamCorner.Br),
                BeamBlob(70, 110, 255, 1.000f, 1.0000f, 0.2700f, 5.0f, 10.0f, 1, BeamCorner.Tr),
                BeamBlob(110, 90, 230, 1.000f, 1.0000f, 0.2700f, 11.0f, 12.0f, 2, BeamCorner.Tr),
            ),
        BeamColorVariant.Sunset to
            listOf(
                BeamBlob(255, 180, 50, 1.000f, 0.0200f, 0.6800f, 9.0f, 18.0f, 1, BeamCorner.Tl),
                BeamBlob(255, 150, 40, 1.000f, 0.0200f, 0.6800f, 4.0f, 8.0f, 2, BeamCorner.Tl),
                BeamBlob(255, 80, 60, 1.000f, 0.7200f, -0.0300f, 59.0f, 9.0f, 3, BeamCorner.Bl),
                BeamBlob(255, 100, 80, 1.000f, 0.7400f, 1.0000f, 42.0f, 7.0f, 1, BeamCorner.Bl),
                BeamBlob(255, 60, 80, 1.000f, 1.0000f, 0.2700f, 10.0f, 17.0f, 2, BeamCorner.Br),
                BeamBlob(255, 120, 60, 1.000f, 1.0000f, 0.2700f, 10.0f, 18.0f, 3, BeamCorner.Br),
                BeamBlob(255, 200, 50, 1.000f, 1.0000f, 0.2700f, 5.0f, 10.0f, 1, BeamCorner.Tr),
                BeamBlob(255, 90, 70, 1.000f, 1.0000f, 0.2700f, 11.0f, 12.0f, 2, BeamCorner.Tr),
            ),
    )

private val smallInnerPalette: Map<BeamColorVariant, List<BeamBlob>> =
    mapOf(
        BeamColorVariant.Colorful to
            listOf(
                BeamBlob(50, 200, 80, 0.500f, 0.0200f, 0.6800f, 9.0f, 18.0f, 1, BeamCorner.Tl),
                BeamBlob(30, 185, 170, 0.450f, 0.0200f, 0.6800f, 4.0f, 8.0f, 2, BeamCorner.Tl),
                BeamBlob(255, 120, 40, 0.350f, 0.7200f, -0.0300f, 59.0f, 9.0f, 3, BeamCorner.Bl),
                BeamBlob(100, 70, 255, 0.350f, 0.7400f, 1.0000f, 42.0f, 7.0f, 1, BeamCorner.Bl),
                BeamBlob(240, 50, 180, 0.300f, 1.0000f, 0.2700f, 10.0f, 17.0f, 2, BeamCorner.Br),
                BeamBlob(180, 40, 240, 0.400f, 1.0000f, 0.2700f, 10.0f, 18.0f, 3, BeamCorner.Br),
                BeamBlob(40, 140, 255, 0.300f, 1.0000f, 0.2700f, 5.0f, 10.0f, 1, BeamCorner.Tr),
                BeamBlob(255, 50, 100, 0.300f, 1.0000f, 0.2700f, 11.0f, 12.0f, 2, BeamCorner.Tr),
            ),
        BeamColorVariant.Mono to
            listOf(
                BeamBlob(160, 160, 160, 0.250f, 0.0200f, 0.6800f, 9.0f, 18.0f, 1, BeamCorner.Tl),
                BeamBlob(140, 140, 140, 0.220f, 0.0200f, 0.6800f, 4.0f, 8.0f, 2, BeamCorner.Tl),
                BeamBlob(180, 180, 180, 0.170f, 0.7200f, -0.0300f, 59.0f, 9.0f, 3, BeamCorner.Bl),
                BeamBlob(150, 150, 150, 0.170f, 0.7400f, 1.0000f, 42.0f, 7.0f, 1, BeamCorner.Bl),
                BeamBlob(170, 170, 170, 0.150f, 1.0000f, 0.2700f, 10.0f, 17.0f, 2, BeamCorner.Br),
                BeamBlob(155, 155, 155, 0.200f, 1.0000f, 0.2700f, 10.0f, 18.0f, 3, BeamCorner.Br),
                BeamBlob(145, 145, 145, 0.150f, 1.0000f, 0.2700f, 5.0f, 10.0f, 1, BeamCorner.Tr),
                BeamBlob(165, 165, 165, 0.150f, 1.0000f, 0.2700f, 11.0f, 12.0f, 2, BeamCorner.Tr),
            ),
        BeamColorVariant.Ocean to
            listOf(
                BeamBlob(60, 140, 200, 0.500f, 0.0200f, 0.6800f, 9.0f, 18.0f, 1, BeamCorner.Tl),
                BeamBlob(50, 120, 180, 0.450f, 0.0200f, 0.6800f, 4.0f, 8.0f, 2, BeamCorner.Tl),
                BeamBlob(100, 80, 220, 0.350f, 0.7200f, -0.0300f, 59.0f, 9.0f, 3, BeamCorner.Bl),
                BeamBlob(80, 100, 255, 0.350f, 0.7400f, 1.0000f, 42.0f, 7.0f, 1, BeamCorner.Bl),
                BeamBlob(120, 70, 240, 0.300f, 1.0000f, 0.2700f, 10.0f, 17.0f, 2, BeamCorner.Br),
                BeamBlob(90, 80, 220, 0.400f, 1.0000f, 0.2700f, 10.0f, 18.0f, 3, BeamCorner.Br),
                BeamBlob(70, 110, 255, 0.300f, 1.0000f, 0.2700f, 5.0f, 10.0f, 1, BeamCorner.Tr),
                BeamBlob(110, 90, 230, 0.300f, 1.0000f, 0.2700f, 11.0f, 12.0f, 2, BeamCorner.Tr),
            ),
        BeamColorVariant.Sunset to
            listOf(
                BeamBlob(255, 180, 50, 0.500f, 0.0200f, 0.6800f, 9.0f, 18.0f, 1, BeamCorner.Tl),
                BeamBlob(255, 150, 40, 0.450f, 0.0200f, 0.6800f, 4.0f, 8.0f, 2, BeamCorner.Tl),
                BeamBlob(255, 80, 60, 0.350f, 0.7200f, -0.0300f, 59.0f, 9.0f, 3, BeamCorner.Bl),
                BeamBlob(255, 100, 80, 0.350f, 0.7400f, 1.0000f, 42.0f, 7.0f, 1, BeamCorner.Bl),
                BeamBlob(255, 60, 80, 0.300f, 1.0000f, 0.2700f, 10.0f, 17.0f, 2, BeamCorner.Br),
                BeamBlob(255, 120, 60, 0.400f, 1.0000f, 0.2700f, 10.0f, 18.0f, 3, BeamCorner.Br),
                BeamBlob(255, 200, 50, 0.300f, 1.0000f, 0.2700f, 5.0f, 10.0f, 1, BeamCorner.Tr),
                BeamBlob(255, 90, 70, 0.300f, 1.0000f, 0.2700f, 11.0f, 12.0f, 2, BeamCorner.Tr),
            ),
    )

private val linePalette: Map<Pair<BeamColorVariant, BeamTheme>, List<LineSpot>> =
    mapOf(
        (BeamColorVariant.Colorful to BeamTheme.Dark) to
            listOf(
                LineSpot(255, 50, 100, 36.0f, 36.0f, 0.0f, 2.0f),
                LineSpot(40, 180, 220, 30.0f, 32.0f, 39.0f, 0.0f),
                LineSpot(50, 200, 80, 33.0f, 28.0f, -36.0f, 2.0f),
                LineSpot(180, 40, 240, 29.0f, 34.0f, -54.0f, 0.0f),
                LineSpot(255, 160, 30, 27.0f, 30.0f, 51.0f, -1.0f),
                LineSpot(100, 70, 255, 36.0f, 24.0f, 21.0f, 1.0f),
                LineSpot(40, 140, 255, 30.0f, 22.0f, -21.0f, 0.0f),
                LineSpot(240, 50, 180, 25.0f, 28.0f, 66.0f, 1.0f),
                LineSpot(30, 185, 170, 23.0f, 30.0f, -66.0f, -1.0f),
            ),
        (BeamColorVariant.Colorful to BeamTheme.Light) to
            listOf(
                LineSpot(255, 50, 100, 45.0f, 36.0f, 0.0f, 2.0f),
                LineSpot(40, 140, 255, 35.0f, 32.0f, 65.0f, 0.0f),
                LineSpot(50, 200, 80, 40.0f, 28.0f, -60.0f, 2.0f),
                LineSpot(180, 40, 240, 35.0f, 34.0f, -90.0f, 0.0f),
                LineSpot(30, 185, 170, 38.0f, 30.0f, 85.0f, -1.0f),
                LineSpot(100, 70, 255, 50.0f, 24.0f, 35.0f, 1.0f),
                LineSpot(40, 140, 255, 40.0f, 22.0f, -35.0f, 0.0f),
                LineSpot(255, 120, 40, 35.0f, 28.0f, 110.0f, 1.0f),
                LineSpot(240, 50, 180, 30.0f, 30.0f, -110.0f, -1.0f),
            ),
        (BeamColorVariant.Mono to BeamTheme.Dark) to
            listOf(
                LineSpot(200, 200, 200, 36.0f, 36.0f, 0.0f, 2.0f),
                LineSpot(170, 170, 170, 30.0f, 32.0f, 39.0f, 0.0f),
                LineSpot(155, 155, 155, 33.0f, 28.0f, -36.0f, 2.0f),
                LineSpot(185, 185, 185, 29.0f, 34.0f, -54.0f, 0.0f),
                LineSpot(165, 165, 165, 27.0f, 30.0f, 51.0f, -1.0f),
                LineSpot(180, 180, 180, 36.0f, 24.0f, 21.0f, 1.0f),
                LineSpot(160, 160, 160, 30.0f, 22.0f, -21.0f, 0.0f),
                LineSpot(175, 175, 175, 25.0f, 28.0f, 66.0f, 1.0f),
                LineSpot(190, 190, 190, 23.0f, 30.0f, -66.0f, -1.0f),
            ),
        (BeamColorVariant.Mono to BeamTheme.Light) to
            listOf(
                LineSpot(100, 100, 100, 45.0f, 36.0f, 0.0f, 2.0f),
                LineSpot(80, 80, 80, 35.0f, 32.0f, 65.0f, 0.0f),
                LineSpot(90, 90, 90, 40.0f, 28.0f, -60.0f, 2.0f),
                LineSpot(70, 70, 70, 35.0f, 34.0f, -90.0f, 0.0f),
                LineSpot(85, 85, 85, 38.0f, 30.0f, 85.0f, -1.0f),
                LineSpot(95, 95, 95, 50.0f, 24.0f, 35.0f, 1.0f),
                LineSpot(75, 75, 75, 40.0f, 22.0f, -35.0f, 0.0f),
                LineSpot(105, 105, 105, 35.0f, 28.0f, 110.0f, 1.0f),
                LineSpot(65, 65, 65, 30.0f, 30.0f, -110.0f, -1.0f),
            ),
        (BeamColorVariant.Ocean to BeamTheme.Dark) to
            listOf(
                LineSpot(100, 80, 220, 36.0f, 36.0f, 0.0f, 2.0f),
                LineSpot(60, 120, 255, 30.0f, 32.0f, 39.0f, 0.0f),
                LineSpot(80, 100, 200, 33.0f, 28.0f, -36.0f, 2.0f),
                LineSpot(130, 70, 255, 29.0f, 34.0f, -54.0f, 0.0f),
                LineSpot(70, 130, 255, 27.0f, 30.0f, 51.0f, -1.0f),
                LineSpot(120, 80, 255, 36.0f, 24.0f, 21.0f, 1.0f),
                LineSpot(90, 110, 230, 30.0f, 22.0f, -21.0f, 0.0f),
                LineSpot(110, 90, 240, 25.0f, 28.0f, 66.0f, 1.0f),
                LineSpot(140, 100, 255, 23.0f, 30.0f, -66.0f, -1.0f),
            ),
        (BeamColorVariant.Ocean to BeamTheme.Light) to
            listOf(
                LineSpot(80, 60, 200, 45.0f, 36.0f, 0.0f, 2.0f),
                LineSpot(50, 100, 220, 35.0f, 32.0f, 65.0f, 0.0f),
                LineSpot(70, 90, 190, 40.0f, 28.0f, -60.0f, 2.0f),
                LineSpot(110, 60, 220, 35.0f, 34.0f, -90.0f, 0.0f),
                LineSpot(60, 110, 230, 38.0f, 30.0f, 85.0f, -1.0f),
                LineSpot(100, 70, 240, 50.0f, 24.0f, 35.0f, 1.0f),
                LineSpot(80, 100, 210, 40.0f, 22.0f, -35.0f, 0.0f),
                LineSpot(90, 80, 225, 35.0f, 28.0f, 110.0f, 1.0f),
                LineSpot(120, 90, 245, 30.0f, 30.0f, -110.0f, -1.0f),
            ),
        (BeamColorVariant.Sunset to BeamTheme.Dark) to
            listOf(
                LineSpot(255, 100, 60, 36.0f, 36.0f, 0.0f, 2.0f),
                LineSpot(255, 180, 50, 30.0f, 32.0f, 39.0f, 0.0f),
                LineSpot(255, 140, 70, 33.0f, 28.0f, -36.0f, 2.0f),
                LineSpot(255, 80, 80, 29.0f, 34.0f, -54.0f, 0.0f),
                LineSpot(255, 200, 60, 27.0f, 30.0f, 51.0f, -1.0f),
                LineSpot(255, 120, 50, 36.0f, 24.0f, 21.0f, 1.0f),
                LineSpot(255, 160, 80, 30.0f, 22.0f, -21.0f, 0.0f),
                LineSpot(255, 90, 60, 25.0f, 28.0f, 66.0f, 1.0f),
                LineSpot(255, 70, 70, 23.0f, 30.0f, -66.0f, -1.0f),
            ),
        (BeamColorVariant.Sunset to BeamTheme.Light) to
            listOf(
                LineSpot(220, 80, 40, 45.0f, 36.0f, 0.0f, 2.0f),
                LineSpot(230, 150, 30, 35.0f, 32.0f, 65.0f, 0.0f),
                LineSpot(210, 110, 50, 40.0f, 28.0f, -60.0f, 2.0f),
                LineSpot(200, 60, 60, 35.0f, 34.0f, -90.0f, 0.0f),
                LineSpot(220, 170, 40, 38.0f, 30.0f, 85.0f, -1.0f),
                LineSpot(210, 100, 30, 50.0f, 24.0f, 35.0f, 1.0f),
                LineSpot(230, 130, 60, 40.0f, 22.0f, -35.0f, 0.0f),
                LineSpot(190, 70, 50, 35.0f, 28.0f, 110.0f, 1.0f),
                LineSpot(180, 50, 50, 30.0f, 30.0f, -110.0f, -1.0f),
            ),
    )

private val outerCorePalette: Map<BeamColorVariant, List<BeamBlob>> =
    mapOf(
        BeamColorVariant.Colorful to
            listOf(
                BeamBlob(255, 50, 100, 1f, 0.2700f, 0.0000f, 80.0f, 19.0f, 1, BeamCorner.Tl),
                BeamBlob(255, 120, 40, 1f, 0.7300f, -0.0100f, 74.0f, 11.0f, 2, BeamCorner.Tr),
                BeamBlob(240, 50, 180, 1f, 1.0000f, 0.3300f, 15.0f, 44.0f, 3, BeamCorner.Tr),
                BeamBlob(180, 40, 240, 1f, 1.0100f, 0.7200f, 19.0f, 38.0f, 1, BeamCorner.Br),
                BeamBlob(100, 70, 255, 1f, 0.6700f, 1.0000f, 84.0f, 13.0f, 2, BeamCorner.Br),
                BeamBlob(40, 140, 255, 1f, 0.2400f, 1.0100f, 60.0f, 21.0f, 3, BeamCorner.Bl),
                BeamBlob(50, 200, 80, 1f, 0.0000f, 0.6000f, 17.0f, 40.0f, 1, BeamCorner.Bl),
                BeamBlob(30, 185, 170, 1f, -0.0100f, 0.2800f, 13.0f, 32.0f, 2, BeamCorner.Tl),
            ),
        BeamColorVariant.Mono to
            listOf(
                BeamBlob(180, 180, 180, 1f, 0.2700f, 0.0000f, 80.0f, 19.0f, 1, BeamCorner.Tl),
                BeamBlob(190, 190, 190, 1f, 0.7300f, -0.0100f, 74.0f, 11.0f, 2, BeamCorner.Tr),
                BeamBlob(145, 145, 145, 1f, 1.0000f, 0.3300f, 15.0f, 44.0f, 3, BeamCorner.Tr),
                BeamBlob(165, 165, 165, 1f, 1.0100f, 0.7200f, 19.0f, 38.0f, 1, BeamCorner.Br),
                BeamBlob(170, 170, 170, 1f, 0.6700f, 1.0000f, 84.0f, 13.0f, 2, BeamCorner.Br),
                BeamBlob(140, 140, 140, 1f, 0.2400f, 1.0100f, 60.0f, 21.0f, 3, BeamCorner.Bl),
                BeamBlob(160, 160, 160, 1f, 0.0000f, 0.6000f, 17.0f, 40.0f, 1, BeamCorner.Bl),
                BeamBlob(130, 130, 130, 1f, -0.0100f, 0.2800f, 13.0f, 32.0f, 2, BeamCorner.Tl),
            ),
        BeamColorVariant.Ocean to
            listOf(
                BeamBlob(100, 80, 220, 1f, 0.2700f, 0.0000f, 80.0f, 19.0f, 1, BeamCorner.Tl),
                BeamBlob(140, 100, 240, 1f, 0.7300f, -0.0100f, 74.0f, 11.0f, 2, BeamCorner.Tr),
                BeamBlob(90, 110, 230, 1f, 1.0000f, 0.3300f, 15.0f, 44.0f, 3, BeamCorner.Tr),
                BeamBlob(130, 70, 255, 1f, 1.0100f, 0.7200f, 19.0f, 38.0f, 1, BeamCorner.Br),
                BeamBlob(120, 80, 255, 1f, 0.6700f, 1.0000f, 84.0f, 13.0f, 2, BeamCorner.Br),
                BeamBlob(60, 120, 255, 1f, 0.2400f, 1.0100f, 60.0f, 21.0f, 3, BeamCorner.Bl),
                BeamBlob(80, 100, 200, 1f, 0.0000f, 0.6000f, 17.0f, 40.0f, 1, BeamCorner.Bl),
                BeamBlob(50, 140, 220, 1f, -0.0100f, 0.2800f, 13.0f, 32.0f, 2, BeamCorner.Tl),
            ),
        BeamColorVariant.Sunset to
            listOf(
                BeamBlob(255, 80, 50, 1f, 0.2700f, 0.0000f, 80.0f, 19.0f, 1, BeamCorner.Tl),
                BeamBlob(255, 60, 60, 1f, 0.7300f, -0.0100f, 74.0f, 11.0f, 2, BeamCorner.Tr),
                BeamBlob(255, 140, 50, 1f, 1.0000f, 0.3300f, 15.0f, 44.0f, 3, BeamCorner.Tr),
                BeamBlob(255, 90, 70, 1f, 1.0100f, 0.7200f, 19.0f, 38.0f, 1, BeamCorner.Br),
                BeamBlob(255, 100, 80, 1f, 0.6700f, 1.0000f, 84.0f, 13.0f, 2, BeamCorner.Br),
                BeamBlob(255, 160, 40, 1f, 0.2400f, 1.0100f, 60.0f, 21.0f, 3, BeamCorner.Bl),
                BeamBlob(255, 120, 60, 1f, 0.0000f, 0.6000f, 17.0f, 40.0f, 1, BeamCorner.Bl),
                BeamBlob(255, 200, 50, 1f, -0.0100f, 0.2800f, 13.0f, 32.0f, 2, BeamCorner.Tl),
            ),
    )

private val outerBloomPalette: Map<BeamColorVariant, List<BeamBlob>> =
    mapOf(
        BeamColorVariant.Colorful to
            listOf(
                BeamBlob(255, 50, 100, 1f, 0.2700f, 0.0300f, 110.0f, 30.0f, 1, BeamCorner.Tl),
                BeamBlob(255, 120, 40, 1f, 0.7300f, 0.0100f, 100.0f, 20.0f, 2, BeamCorner.Tr),
                BeamBlob(240, 50, 180, 1f, 1.0000f, 0.3300f, 26.0f, 62.0f, 3, BeamCorner.Tr),
                BeamBlob(180, 40, 240, 1f, 1.0100f, 0.7200f, 30.0f, 56.0f, 1, BeamCorner.Br),
                BeamBlob(100, 70, 255, 1f, 0.6700f, 0.9900f, 120.0f, 22.0f, 2, BeamCorner.Br),
                BeamBlob(40, 140, 255, 1f, 0.2400f, 0.9900f, 88.0f, 32.0f, 3, BeamCorner.Bl),
                BeamBlob(50, 200, 80, 1f, 0.0000f, 0.6000f, 28.0f, 58.0f, 1, BeamCorner.Bl),
            ),
        BeamColorVariant.Mono to
            listOf(
                BeamBlob(180, 180, 180, 1f, 0.2700f, 0.0300f, 110.0f, 30.0f, 1, BeamCorner.Tl),
                BeamBlob(190, 190, 190, 1f, 0.7300f, 0.0100f, 100.0f, 20.0f, 2, BeamCorner.Tr),
                BeamBlob(145, 145, 145, 1f, 1.0000f, 0.3300f, 26.0f, 62.0f, 3, BeamCorner.Tr),
                BeamBlob(165, 165, 165, 1f, 1.0100f, 0.7200f, 30.0f, 56.0f, 1, BeamCorner.Br),
                BeamBlob(170, 170, 170, 1f, 0.6700f, 0.9900f, 120.0f, 22.0f, 2, BeamCorner.Br),
                BeamBlob(140, 140, 140, 1f, 0.2400f, 0.9900f, 88.0f, 32.0f, 3, BeamCorner.Bl),
                BeamBlob(160, 160, 160, 1f, 0.0000f, 0.6000f, 28.0f, 58.0f, 1, BeamCorner.Bl),
            ),
        BeamColorVariant.Ocean to
            listOf(
                BeamBlob(100, 80, 220, 1f, 0.2700f, 0.0300f, 110.0f, 30.0f, 1, BeamCorner.Tl),
                BeamBlob(140, 100, 240, 1f, 0.7300f, 0.0100f, 100.0f, 20.0f, 2, BeamCorner.Tr),
                BeamBlob(90, 110, 230, 1f, 1.0000f, 0.3300f, 26.0f, 62.0f, 3, BeamCorner.Tr),
                BeamBlob(130, 70, 255, 1f, 1.0100f, 0.7200f, 30.0f, 56.0f, 1, BeamCorner.Br),
                BeamBlob(120, 80, 255, 1f, 0.6700f, 0.9900f, 120.0f, 22.0f, 2, BeamCorner.Br),
                BeamBlob(60, 120, 255, 1f, 0.2400f, 0.9900f, 88.0f, 32.0f, 3, BeamCorner.Bl),
                BeamBlob(80, 100, 200, 1f, 0.0000f, 0.6000f, 28.0f, 58.0f, 1, BeamCorner.Bl),
            ),
        BeamColorVariant.Sunset to
            listOf(
                BeamBlob(255, 80, 50, 1f, 0.2700f, 0.0300f, 110.0f, 30.0f, 1, BeamCorner.Tl),
                BeamBlob(255, 60, 60, 1f, 0.7300f, 0.0100f, 100.0f, 20.0f, 2, BeamCorner.Tr),
                BeamBlob(255, 140, 50, 1f, 1.0000f, 0.3300f, 26.0f, 62.0f, 3, BeamCorner.Tr),
                BeamBlob(255, 90, 70, 1f, 1.0100f, 0.7200f, 30.0f, 56.0f, 1, BeamCorner.Br),
                BeamBlob(255, 100, 80, 1f, 0.6700f, 0.9900f, 120.0f, 22.0f, 2, BeamCorner.Br),
                BeamBlob(255, 160, 40, 1f, 0.2400f, 0.9900f, 88.0f, 32.0f, 3, BeamCorner.Bl),
                BeamBlob(255, 120, 60, 1f, 0.0000f, 0.6000f, 28.0f, 58.0f, 1, BeamCorner.Bl),
            ),
    )

internal fun borderBlobs(variant: BeamColorVariant): List<BeamBlob> =
    borderPalette.getValue(variant)

internal fun smallBorderBlobs(variant: BeamColorVariant): List<BeamBlob> =
    smallBorderPalette.getValue(variant)

internal fun smallInnerBlobs(variant: BeamColorVariant): List<BeamBlob> =
    smallInnerPalette.getValue(variant)

internal fun lineSpots(variant: BeamColorVariant, theme: BeamTheme): List<LineSpot> =
    linePalette.getValue(variant to theme)

internal fun outerCoreBlobs(variant: BeamColorVariant): List<BeamBlob> =
    outerCorePalette.getValue(variant)

internal fun outerBloomBlobs(variant: BeamColorVariant): List<BeamBlob> =
    outerBloomPalette.getValue(variant)

internal fun derivedInnerBlobs(variant: BeamColorVariant): List<BeamBlob> {
    val alpha = if (variant == BeamColorVariant.Mono) 0.225f else 0.45f
    return borderBlobs(variant).map { blob ->
        blob.copy(a = alpha, rx = blob.rx * 0.9f, ry = blob.ry * 0.9f)
    }
}
