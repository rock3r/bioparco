package dev.sebastiano.achievementbadge

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The badge, rebuilt from the bitmap of the source SVG that Adrian (@adriankuleszo) shared (see
 * `art/badge.svg`).
 *
 * Art units are the source bitmap's pixels, moved so the stars sit at the centre of an 800×800 box.
 * Every number here was measured on that bitmap.
 */
internal object BadgeArt {
    const val SIZE = 800f
    val CENTER = Offset(400f, 400f)

    /** Where the art box's origin sits on the 1038×1104 source bitmap. */
    val SOURCE_ORIGIN = Offset(105.5f, 159f)

    const val STAR_RADIUS = 395.5f
    private const val TIP_ROUNDING = 22f
    private const val VALLEY_ROUNDING = 10f

    /** The fan, the sparkle and the trophy sit 5 px left of the stars in the source art. */
    const val TROPHY_AXIS = 395f

    /** Tips at 0°, ±60°, ±120° and 180°. Yellow at the top, orange at the sides, pink below. */
    val backStar: List<PathOp> = starShape(offsetDegrees = 0f)

    /** Tips at ±30°, ±90° and ±150°: the purple face. */
    val frontStar: List<PathOp> = starShape(offsetDegrees = -90f)

    // The neon line: the face's outline pushed 38 px inward, broken for the handles and the base.
    private const val NEON_INSET = 38f
    private const val NEON_TIP_ROUNDING = 26f
    private const val NEON_VALLEY_ROUNDING = 10f
    private val neonCorners = insetPolygon(starVertices(-90f, STAR_RADIUS), NEON_INSET)

    /** From the left handle, over the top tip, to the right handle. */
    val neonTopRun: List<PathOp> = run {
        val p = neonCorners
        val endY = 473.5f - SOURCE_ORIGIN.y
        roundedPolyline(
            listOf(atY(p[10], p[9], endY), p[10], p[11], p[0], p[1], p[2], atY(p[2], p[3], endY)),
            ::neonRounding,
        )
    }

    /** The lower-left corner run, from beside the handle to beside the base. */
    val neonLeftRun: List<PathOp> = run {
        val p = neonCorners
        val startY = 630.5f - SOURCE_ORIGIN.y
        val endX = 363.5f - SOURCE_ORIGIN.x
        roundedPolyline(listOf(atY(p[8], p[9], startY), p[8], atX(p[8], p[7], endX))) {
            NEON_TIP_ROUNDING
        }
    }

    val neonRightRun: List<PathOp> = mirror(neonLeftRun, CENTER.x)

    private fun neonRounding(index: Int): Float =
        // Along the top run, tips and valleys alternate starting from a tip at index 1.
        if (index % 2 == 1) NEON_TIP_ROUNDING else NEON_VALLEY_ROUNDING

    // The light fan: five bands from the cup up past the top tip, clipped by the front star.
    private val outerEdge = fanEdge(x480 = 350f, x405 = 322f, x345 = 295f)
    private val pinkEdge = fanEdge(x480 = 407f, x405 = 391f, x345 = 377f)
    private val centralEdge = fanEdge(x480 = 457f, x405 = 450f, x345 = 441f)

    val fanLeftBand: List<PathOp> = band(outerEdge.points(), pinkEdge.points())
    val fanRightBand: List<PathOp> =
        band(pinkEdge.points(TROPHY_AXIS), outerEdge.points(TROPHY_AXIS))
    val fanPink: List<PathOp> = band(pinkEdge.points(), pinkEdge.points(TROPHY_AXIS))
    val fanCentral: List<PathOp> = band(centralEdge.points(), centralEdge.points(TROPHY_AXIS))
    val fanOuterLines: List<List<PathOp>> =
        listOf(edgeLine(outerEdge.points()), edgeLine(outerEdge.points(TROPHY_AXIS)))
    val fanCentralLines: List<List<PathOp>> =
        listOf(edgeLine(centralEdge.points()), edgeLine(centralEdge.points(TROPHY_AXIS)))

    /** Where the fan's light pours out, just above the cup. */
    val fanBottom: Float = 490f - SOURCE_ORIGIN.y

    val sparkleCenter: Offset = sourcePoint(501.5f, 329.5f)
    val sparkle: List<PathOp> = sparkleShape(sparkleCenter, size = 34.5f, waist = 0.27f, 3f, 9f)

    /** A slimmer 20-unit sparkle around the origin, for the twinkles around the badge. */
    val twinkle: List<PathOp> = sparkleShape(Offset.Zero, size = 20f, waist = 0.2f, 1f, 3f)

    // The trophy.
    val rim: Rect = Rect(sourcePoint(308f, 482f), sourcePoint(2 * 500.5f - 308f, 597f))
    const val RIM_ROUNDING = 9f
    val rimHighlights: List<Rect> =
        listOf(
            Rect(sourcePoint(392f, 490f), sourcePoint(480f, 588f)),
            Rect(sourcePoint(595f, 490f), sourcePoint(643f, 588f)),
        )
    const val HIGHLIGHT_ROUNDING = 4f

    val bowl: List<PathOp> = run {
        val left = sourcePoint(317f, 593f)
        val depth = 721f - 593f
        listOf(
            PathOp.MoveTo(left),
            PathOp.LineTo(Offset(2 * TROPHY_AXIS - left.x, left.y)),
            PathOp.ArcTo(Offset(TROPHY_AXIS, left.y), TROPHY_AXIS - left.x, depth, 0f, 180f),
            PathOp.Close,
        )
    }

    val neck: List<PathOp> = run {
        val left = sourcePoint(431f, 721f)
        val bottom = 800f - SOURCE_ORIGIN.y
        val r = 42f
        val right = 2 * TROPHY_AXIS - left.x
        listOf(
            PathOp.MoveTo(Offset(left.x, bottom)),
            PathOp.LineTo(Offset(left.x, left.y + r)),
            PathOp.ArcTo(Offset(left.x + r, left.y + r), r, r, 180f, 90f),
            PathOp.LineTo(Offset(right - r, left.y)),
            PathOp.ArcTo(Offset(right - r, left.y + r), r, r, 270f, 90f),
            PathOp.LineTo(Offset(right, bottom)),
            PathOp.Close,
        )
    }

    /** A tapered block. Its foot runs past the badge and is clipped to the front star's tip. */
    val base: List<PathOp> = run {
        val top = 765f - SOURCE_ORIGIN.y
        val foot = 990f - SOURCE_ORIGIN.y
        val topLeft = 421f - SOURCE_ORIGIN.x
        val footLeft = 431f - SOURCE_ORIGIN.x
        roundedPolygon(
            listOf(
                Offset(topLeft, top),
                Offset(2 * TROPHY_AXIS - topLeft, top),
                Offset(2 * TROPHY_AXIS - footLeft, foot),
                Offset(footLeft, foot),
            )
        ) {
            6f
        }
    }

    val leftHandle: List<PathOp> = run {
        val o =
            listOf(
                    310f to 513f,
                    256f to 513f,
                    245f to 513f,
                    245f to 526f,
                    245f to 600f,
                    300f to 668f,
                )
                .map { (x, y) -> sourcePoint(x, y) }
        val bowlJoin = sourcePoint(388f, 690f)
        val i =
            listOf(
                    380f to 668f,
                    320f to 640f,
                    278f to 594f,
                    278f to 548f,
                    278f to 541f,
                    282f to 539f,
                )
                .map { (x, y) -> sourcePoint(x, y) }
        val rimJoin = sourcePoint(310f, 539f)
        listOf(
            PathOp.MoveTo(o[0]),
            PathOp.LineTo(o[1]),
            PathOp.QuadTo(o[2], o[3]),
            PathOp.CubicTo(o[4], o[5], bowlJoin),
            PathOp.LineTo(i[0]),
            PathOp.CubicTo(i[1], i[2], i[3]),
            PathOp.QuadTo(i[4], i[5]),
            PathOp.LineTo(rimJoin),
            PathOp.Close,
        )
    }

    val rightHandle: List<PathOp> = mirror(leftHandle, TROPHY_AXIS)

    /** The cup's pivot while it pops in: the middle of the rim's lower edge. */
    val cupPivot: Offset = Offset(TROPHY_AXIS, rim.bottom)

    private fun starShape(offsetDegrees: Float): List<PathOp> =
        roundedPolygon(starVertices(offsetDegrees, STAR_RADIUS)) { i ->
            if (i % 2 == 0) TIP_ROUNDING else VALLEY_ROUNDING
        }

    private fun sparkleShape(
        center: Offset,
        size: Float,
        waist: Float,
        tipRounding: Float,
        waistRounding: Float,
    ): List<PathOp> {
        val points =
            List(8) { k ->
                val r = if (k % 2 == 0) size else size * waist * sqrt(2f)
                val angle = (-90f + 45f * k) * (PI.toFloat() / 180f)
                Offset(center.x + r * cos(angle), center.y + r * sin(angle))
            }
        return roundedPolygon(points) { k -> if (k % 2 == 0) tipRounding else waistRounding }
    }

    /** A band between two fan edges given as (bottom, control, bend, top), left edge first. */
    private fun band(left: List<Offset>, right: List<Offset>): List<PathOp> =
        listOf(
            PathOp.MoveTo(left[0]),
            PathOp.QuadTo(left[1], left[2]),
            PathOp.LineTo(left[3]),
            PathOp.LineTo(right[3]),
            PathOp.LineTo(right[2]),
            PathOp.QuadTo(right[1], right[0]),
            PathOp.Close,
        )

    private fun edgeLine(edge: List<Offset>): List<PathOp> =
        listOf(PathOp.MoveTo(edge[0]), PathOp.QuadTo(edge[1], edge[2]), PathOp.LineTo(edge[3]))

    private fun atY(a: Offset, b: Offset, y: Float): Offset =
        a + (b - a) * ((y - a.y) / (b.y - a.y))

    private fun atX(a: Offset, b: Offset, x: Float): Offset =
        a + (b - a) * ((x - a.x) / (b.x - a.x))
}

/** Mirrors a path across the vertical line x = [axis]. Arcs flip their direction. */
internal fun mirror(ops: List<PathOp>, axis: Float): List<PathOp> {
    fun m(p: Offset) = Offset(2 * axis - p.x, p.y)
    return ops.map { op ->
        when (op) {
            is PathOp.MoveTo -> PathOp.MoveTo(m(op.point))
            is PathOp.LineTo -> PathOp.LineTo(m(op.point))
            is PathOp.QuadTo -> PathOp.QuadTo(m(op.control), m(op.point))
            is PathOp.CubicTo -> PathOp.CubicTo(m(op.control1), m(op.control2), m(op.point))
            is PathOp.ArcTo ->
                PathOp.ArcTo(
                    m(op.center),
                    op.radiusX,
                    op.radiusY,
                    180f - op.startDegrees,
                    -op.sweepDegrees,
                )
            PathOp.Close -> PathOp.Close
        }
    }
}
