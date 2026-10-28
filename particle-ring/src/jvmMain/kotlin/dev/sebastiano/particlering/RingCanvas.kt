package dev.sebastiano.particlering

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import dev.sebastiano.bioparco.tracing.Tracing
import kotlin.math.PI
import kotlin.math.sin
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap

/** Draws the icon, the particles and the logo, each only while it shows. */
@Composable
internal fun RingCanvas(state: RingStageState, modifier: Modifier = Modifier) {
    val particles = remember(state) { ParticleBuffers(state.ring) }
    val heart = remember { heartPath() }
    val logo = remember { LogoBrushes() }
    val primer = remember { IntArray(1) }
    Canvas(modifier) {
        // The frame is read here, in the draw phase, so each step redraws without recomposing.
        state.frame.longValue
        val ring = state.ring
        val now = state.seconds
        // The first draw of each kind of thing builds its shaders: on OpenGL, 9 ms for the dots
        // and 6 ms for the logo, on the frame the burst or the logo starts. Draw each once, at
        // 1/255 alpha, over the first frames instead, while the icon waits.
        if (primer[0] < PRIME_FRAMES) {
            primer[0]++
            Tracing.section("prime") {
                particles.prime(this)
                drawLogo(state, heart, logo, PRIME_ALPHA)
            }
        }
        val icon = ring.iconShare(now)
        val logoShare = ring.logoShare(now)
        if (icon > 0f) Tracing.section("icon draw") { drawIcon(state, heart, icon) }
        if (ring.phase != RingPhase.Icon && logoShare < 1f) {
            Tracing.section("ring draw") { particles.draw(this, state, 1f - logoShare) }
        }
        if (logoShare > 0f) Tracing.section("logo draw") { drawLogo(state, heart, logo, logoShare) }
    }
}

/** The heart, one unit wide, centred on the origin. */
private fun heartPath(): Path {
    val outline = HeartShape.outline()
    return Path().apply {
        moveTo(outline[0], outline[1])
        for (i in 2 until outline.size step 2) lineTo(outline[i], outline[i + 1])
        close()
    }
}

private val heartBrush =
    Brush.verticalGradient(
        listOf(RingPalette.heartTop, RingPalette.heartBottom),
        startY = -HeartShape.HEIGHT / 2f,
        endY = HeartShape.HEIGHT / 2f,
    )

private fun DrawScope.drawHeart(heart: Path, at: Offset, width: Float, alpha: Float) {
    translate(at.x, at.y) {
        scale(width, width, pivot = Offset.Zero) { drawPath(heart, heartBrush, alpha = alpha) }
    }
}

/**
 * The Health icon: a white tile with the heart in it. It breathes while it waits for a click. Its
 * heart drains as the particles leave it, then the tile greys and fades away.
 */
private fun DrawScope.drawIcon(state: RingStageState, heart: Path, share: Float) {
    val ring = state.ring
    val now = state.seconds
    val unit = state.unit
    val centre = state.centre + Offset(0f, ParticleRing.ICON_Y * unit)
    val breath =
        if (ring.phase == RingPhase.Icon) 1f + BREATH * sin(now * 2f * PI.toFloat() * BREATH_HZ)
        else 1f
    val half = ParticleRing.ICON_HALF * unit * breath
    val tile = lerp(RingPalette.iconTileFaded, RingPalette.iconTile, smoothStep(share))
    drawRoundRect(
        tile,
        topLeft = centre - Offset(half, half),
        size = Size(half * 2f, half * 2f),
        cornerRadius = CornerRadius(half * ICON_CORNER),
        alpha = (share * 3f).coerceAtMost(1f),
    )
    val heartLeft = 1f - ring.launchedShare(now)
    if (heartLeft > 0f) {
        drawHeart(heart, centre, ParticleRing.ICON_HEART_WIDTH * unit * breath, heartLeft * share)
    }
}

/** The colours of the logo's arc, and its glow, built for one size of stage. */
private class LogoBrushes {
    private var forCentre = Offset.Unspecified
    private var forUnit = 0f
    lateinit var arc: Brush
    lateinit var glow: Brush

    fun at(centre: Offset, unit: Float): LogoBrushes {
        if (centre == forCentre && unit == forUnit) return this
        forCentre = centre
        forUnit = unit
        // A sweep starts at 3 o'clock and turns clockwise. The arc runs from 135° round to 45°,
        // so its start is 0.375 of the way round and its end 0.125.
        arc =
            Brush.sweepGradient(
                0f to lerp(RingPalette.arcMiddle, RingPalette.arcEnd, 2f / 3f),
                0.125f to RingPalette.arcEnd,
                0.375f to RingPalette.arcStart,
                0.75f to RingPalette.arcMiddle,
                1f to lerp(RingPalette.arcMiddle, RingPalette.arcEnd, 2f / 3f),
                center = centre,
            )
        glow =
            Brush.radialGradient(
                listOf(RingPalette.glow, RingPalette.glow.copy(alpha = 0f)),
                center = centre,
                radius = unit * GLOW_RADIUS,
            )
        return this
    }
}

/** The finished logo: a glow, the arc, and the heart, fading in over the landed particles. */
private fun DrawScope.drawLogo(
    state: RingStageState,
    heart: Path,
    brushes: LogoBrushes,
    share: Float,
) {
    val unit = state.unit
    val centre = state.centre
    val logo = brushes.at(centre, unit)
    drawCircle(logo.glow, radius = unit * GLOW_RADIUS, center = centre, alpha = share)
    val radius = ParticleRing.ARC_RADIUS * unit
    drawArc(
        logo.arc,
        startAngle = ARC_START_DEGREES,
        sweepAngle = ARC_SWEEP_DEGREES,
        useCenter = false,
        topLeft = centre - Offset(radius, radius),
        size = Size(radius * 2f, radius * 2f),
        alpha = share,
        style = Stroke(width = ParticleRing.ARC_WIDTH * unit, cap = StrokeCap.Round),
    )
    drawHeart(
        heart,
        centre + Offset(0f, ParticleRing.HEART_Y * unit),
        ParticleRing.HEART_WIDTH * unit,
        share,
    )
}

/**
 * Draws the particles: one Skia `drawPoints` call per tone and depth band, from float arrays filled
 * in place. No `Offset` lists, no per-particle draw calls, nothing allocated per frame.
 *
 * A band's array holds room for every particle of its tone, since particles move between bands as
 * they swirl; `drawPoints` has no count, so the unused tail is parked off the canvas, where Skia
 * drops it before drawing. Dots under [ROUND_FROM_PX] across are square: anti-aliased, a square
 * that small looks like a disc, and Skia draws it as a rectangle instead of a little path.
 */
private class ParticleBuffers(private val ring: ParticleRing) {
    private val bands = ParticleRing.TONES * DEPTH_BANDS
    private val coordinates =
        Array(bands) { FloatArray(ring.toneSizes[it / DEPTH_BANDS] * 2) { PARKED } }
    private val used = IntArray(bands)
    private val wasUsed = IntArray(bands)
    private val paints =
        Array(bands) {
            Paint().apply {
                isAntiAlias = true
                mode = PaintMode.STROKE
            }
        }

    /** Draws a dot with every paint, at 1/255 alpha, in the corner: see [RingCanvas]. */
    fun prime(scope: DrawScope) {
        scope.drawIntoCanvas { canvas ->
            for (cap in arrayOf(PaintStrokeCap.SQUARE, PaintStrokeCap.ROUND)) {
                for (paint in paints) {
                    paint.color = withAlpha(RingPalette.toneArgb[0], PRIME_ALPHA)
                    paint.strokeWidth = ROUND_FROM_PX
                    paint.strokeCap = cap
                    canvas.nativeCanvas.drawPoints(primeDot, paint)
                }
            }
        }
    }

    private val primeDot = floatArrayOf(1f, 1f)

    fun draw(scope: DrawScope, state: RingStageState, alpha: Float) {
        val unit = state.unit
        val cx = state.centre.x
        val cy = state.centre.y
        used.fill(0)
        for (i in 0 until ring.count) {
            val depth = ring.depth[i]
            if (depth < 0f) continue
            val band =
                ring.tone[i] * DEPTH_BANDS +
                    (depth * DEPTH_BANDS).toInt().coerceAtMost(DEPTH_BANDS - 1)
            val at = used[band]
            val buffer = coordinates[band]
            buffer[at] = cx + ring.x[i] * unit
            buffer[at + 1] = cy + ring.y[i] * unit
            used[band] = at + 2
        }
        scope.drawIntoCanvas { canvas ->
            for (band in 0 until bands) {
                val buffer = coordinates[band]
                // Park whatever this band held last frame and does not hold now.
                if (wasUsed[band] > used[band]) buffer.fill(PARKED, used[band], wasUsed[band])
                wasUsed[band] = used[band]
                if (used[band] == 0) continue
                val tone = band / DEPTH_BANDS
                val depth = band % DEPTH_BANDS
                val paint = paints[band]
                val diameter =
                    RingPalette.toneDiameterDp[tone] * RingPalette.depthScale[depth] * scope.density
                paint.color =
                    withAlpha(RingPalette.toneArgb[tone], RingPalette.depthAlpha[depth] * alpha)
                paint.strokeWidth = diameter
                paint.strokeCap =
                    if (diameter < ROUND_FROM_PX) PaintStrokeCap.SQUARE else PaintStrokeCap.ROUND
                canvas.nativeCanvas.drawPoints(buffer, paint)
            }
        }
    }
}

private fun withAlpha(argb: Int, alpha: Float): Int =
    ((argb ushr 24) * alpha.coerceIn(0f, 1f)).toInt() shl 24 or (argb and 0xFFFFFF)

private fun smoothStep(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

private const val DEPTH_BANDS = 3
private const val PRIME_FRAMES = 2
private const val PRIME_ALPHA = 1f / 255f
private const val PARKED = -10_000f
private const val ROUND_FROM_PX = 2.5f
private const val BREATH = 0.025f
private const val BREATH_HZ = 0.6f
private const val ICON_CORNER = 0.45f
private const val GLOW_RADIUS = 0.95f
private const val ARC_START_DEGREES = 135f
private const val ARC_SWEEP_DEGREES = 270f
