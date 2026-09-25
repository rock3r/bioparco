package dev.sebastiano.achievementbadge

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure

/** Turns art-unit path steps into a Compose path. */
internal fun List<PathOp>.toPath(): Path =
    Path().also { path ->
        for (op in this) {
            when (op) {
                is PathOp.MoveTo -> path.moveTo(op.point.x, op.point.y)
                is PathOp.LineTo -> path.lineTo(op.point.x, op.point.y)
                is PathOp.QuadTo ->
                    path.quadraticTo(op.control.x, op.control.y, op.point.x, op.point.y)
                is PathOp.CubicTo ->
                    path.cubicTo(
                        op.control1.x,
                        op.control1.y,
                        op.control2.x,
                        op.control2.y,
                        op.point.x,
                        op.point.y,
                    )
                is PathOp.ArcTo ->
                    path.arcTo(
                        Rect(
                            op.center - Offset(op.radiusX, op.radiusY),
                            Size(op.radiusX * 2f, op.radiusY * 2f),
                        ),
                        op.startDegrees,
                        op.sweepDegrees,
                        forceMoveTo = false,
                    )
                PathOp.Close -> path.close()
            }
        }
    }

/** A path you can draw a growing piece of, measured once. */
internal class TrimmablePath(val path: Path) {
    private val measure = PathMeasure().apply { setPath(path, forceClosed = false) }
    private val length = measure.length
    private val scratch = Path()

    /** The middle [fraction] of the path, growing outwards from its halfway point. */
    fun fromMiddle(fraction: Float): Path {
        if (fraction >= 1f) return path
        scratch.reset()
        val half = length * fraction.coerceAtLeast(0f) / 2f
        measure.getSegment(length / 2f - half, length / 2f + half, scratch, startWithMoveTo = true)
        return scratch
    }
}

/** Paths and brushes for the whole badge, in art units. Built once and reused every frame. */
internal class BadgePaint {
    val backStar = BadgeArt.backStar.toPath()
    val frontStar = BadgeArt.frontStar.toPath()
    val fanLeftBand = BadgeArt.fanLeftBand.toPath()
    val fanRightBand = BadgeArt.fanRightBand.toPath()
    val fanPink = BadgeArt.fanPink.toPath()
    val fanCentral = BadgeArt.fanCentral.toPath()
    val fanOuterLines = BadgeArt.fanOuterLines.map { it.toPath() }
    val fanCentralLines = BadgeArt.fanCentralLines.map { it.toPath() }
    val neonTop = TrimmablePath(BadgeArt.neonTopRun.toPath())
    val neonLow =
        listOf(BadgeArt.neonLeftRun, BadgeArt.neonRightRun).map { TrimmablePath(it.toPath()) }
    val sparkle = BadgeArt.sparkle.toPath()
    val twinkle = BadgeArt.twinkle.toPath()
    val leftHandle = BadgeArt.leftHandle.toPath()
    val rightHandle = BadgeArt.rightHandle.toPath()
    val bowl = BadgeArt.bowl.toPath()
    val neck = BadgeArt.neck.toPath()
    val base = BadgeArt.base.toPath()

    val backFill =
        vertical(
            60f,
            740f,
            0f to Color(0xFFFBE08A),
            0.5f to Color(0xFFF2AA4F),
            1f to Color(0xFFDE4F87),
        )
    val backRim = whiteFade(5f, 795f, 0.7f, 0.05f)
    val frontFill = vertical(207f, 593f, 0f to Color(0xFF603FF5), 1f to Color(0xFF8746F5))
    val frontRim = whiteFade(5f, 795f, 0.32f, 0.12f)

    // Light ramps inside the fan, from the tip down to the cup (source bitmap y, white opacity).
    val fanBand =
        sourceRamp(
            250f to 0.06f,
            350f to 0.14f,
            400f to 0.18f,
            440f to 0.28f,
            470f to 0.36f,
            490f to 0.42f,
        )
    val fanPinkSide =
        sourceRamp(
            250f to 0f,
            350f to 0f,
            400f to 0.17f,
            440f to 0.36f,
            470f to 0.48f,
            490f to 0.56f,
        )
    val fanPinkCentral =
        sourceRamp(
            250f to 0f,
            300f to 0f,
            350f to 0.15f,
            400f to 0.4f,
            440f to 0.64f,
            470f to 0.86f,
            490f to 0.94f,
        )

    val rimFill =
        vertical(
            BadgeArt.rim.top,
            BadgeArt.rim.bottom,
            0f to Color(0xFFFCE874),
            1f to Color(0xFFF8D45B),
        )
    val rimShine = whiteFade(BadgeArt.rim.top, BadgeArt.rim.bottom, 0.6f, 0f)
    val bowlFill =
        vertical(BadgeArt.rim.bottom, 562f, 0f to Color(0xFFF3BA56), 1f to Color(0xFFE46A33))
    val handleFill = vertical(354f, 531f, 0f to Color(0xFFFADC62), 1f to Color(0xFFEF9A4A))
    val handleRim = whiteFade(354f, 531f, 0.55f, 0.05f)
    val neckFill = vertical(562f, 611f, 0f to Color(0xFFE46A30), 1f to Color(0xFFF09A48))
    val baseFill = vertical(606f, 785f, 0f to Color(0xFFF3B955), 1f to Color(0xFFF1944B))
    val highlightFill = whiteFade(331f, 429f, 0.75f, 0.45f)
    val sparkleFill =
        Brush.radialGradient(
            0f to Color(0xFFFAD84E),
            0.6f to Color(0xFFF9DC62),
            1f to Color(0xFFFBE58E),
            center = BadgeArt.sparkleCenter,
            radius = 30f,
        )

    private fun vertical(startY: Float, endY: Float, vararg stops: Pair<Float, Color>): Brush =
        Brush.verticalGradient(colorStops = stops, startY = startY, endY = endY)

    private fun whiteFade(startY: Float, endY: Float, from: Float, to: Float): Brush =
        vertical(
            startY,
            endY,
            0f to Color.White.copy(alpha = from),
            1f to Color.White.copy(alpha = to),
        )

    private fun sourceRamp(vararg stops: Pair<Float, Float>): Brush {
        val top = 250f
        val bottom = 490f
        val colorStops =
            stops
                .map { (y, alpha) -> (y - top) / (bottom - top) to Color.White.copy(alpha = alpha) }
                .toTypedArray()
        return vertical(
            top - BadgeArt.SOURCE_ORIGIN.y,
            bottom - BadgeArt.SOURCE_ORIGIN.y,
            *colorStops,
        )
    }
}

internal object BadgeColors {
    val stage = Color(0xFF121216)
    val stageCenter = Color(0xFF1B1B21)
    val ghost = Color(0xFF212127)
    val pink = Color(0xFFDF518D)
    val rimLip = Color(0xFFF2B24E)
    val baseEdge = Color(0xFFEBA140)
    val sparkleEdge = Color(0xFFFCEBA8)
    val neonGlow = Color(0xFFB9A6FF)
    val warmGlow = Color(0xFFFFC266)
    val ray = Color(0xFFF2C46A)
    val goldRing = Color(0xFFCFAE56)
    val pinkRing = Color(0xFFC8508C)
    val confetti =
        listOf(
            Color(0xFFF2629F),
            Color(0xFFFFD35C),
            Color(0xFF8A6BFF),
            Color(0xFFFF9442),
            Color(0xFFC9B8FF),
        )
    val twinkles = listOf(Color(0xFFFFE89A), Color.White, Color(0xFFCDBEFF))
}
