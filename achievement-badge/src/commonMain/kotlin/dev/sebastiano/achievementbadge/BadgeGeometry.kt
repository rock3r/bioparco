package dev.sebastiano.achievementbadge

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** One drawing step in art units. The JVM side turns a list of these into a Compose `Path`. */
internal sealed interface PathOp {
    data class MoveTo(val point: Offset) : PathOp

    data class LineTo(val point: Offset) : PathOp

    data class QuadTo(val control: Offset, val point: Offset) : PathOp

    data class CubicTo(val control1: Offset, val control2: Offset, val point: Offset) : PathOp

    /** An elliptic arc around [center]. Degrees run clockwise, because y points down. */
    data class ArcTo(
        val center: Offset,
        val radiusX: Float,
        val radiusY: Float,
        val startDegrees: Float,
        val sweepDegrees: Float,
    ) : PathOp

    data object Close : PathOp
}

private const val DEGREES_TO_RADIANS = (PI / 180.0).toFloat()
private const val RADIANS_TO_DEGREES = (180.0 / PI).toFloat()

/** A six-point star with 90-degree tips has its valleys at (√3 − 1) of the tip radius. */
internal val STAR_VALLEY_FRACTION = sqrt(3f) - 1f

/**
 * The 12 sharp vertices of a six-point star around [center]: tips on even indices, valleys on odd
 * ones. The first tip points at [offsetDegrees] (clockwise from +x).
 */
internal fun starVertices(
    offsetDegrees: Float,
    radius: Float,
    center: Offset = BadgeArt.CENTER,
): List<Offset> =
    List(12) { index ->
        val angle = (offsetDegrees + 30f * index) * DEGREES_TO_RADIANS
        val r = if (index % 2 == 0) radius else radius * STAR_VALLEY_FRACTION
        Offset(center.x + r * cos(angle), center.y + r * sin(angle))
    }

/** Moves every edge of a closed polygon towards [center] by [inset], keeping it parallel. */
internal fun insetPolygon(
    points: List<Offset>,
    inset: Float,
    center: Offset = BadgeArt.CENTER,
): List<Offset> {
    val edges =
        points.indices.map { i ->
            val a = points[i]
            val b = points[(i + 1) % points.size]
            val direction = b - a
            var normal = Offset(-direction.y, direction.x) / direction.getDistance()
            val middle = (a + b) / 2f
            if (dot(center - middle, normal) < 0f) normal = -normal
            (a + normal * inset) to direction
        }
    return points.indices.map { i ->
        intersect(edges[(i + points.size - 1) % points.size], edges[i])
    }
}

private fun intersect(first: Pair<Offset, Offset>, second: Pair<Offset, Offset>): Offset {
    val (p1, d1) = first
    val (p2, d2) = second
    val det = d1.x * d2.y - d1.y * d2.x
    val t = ((p2.x - p1.x) * d2.y - (p2.y - p1.y) * d2.x) / det
    return p1 + d1 * t
}

private class Corner(val start: Offset, val end: Offset, val arc: PathOp.ArcTo)

/** Rounds the corner at [vertex] with a circle of [radius] that touches both edges. */
private fun corner(previous: Offset, vertex: Offset, next: Offset, radius: Float): Corner {
    val toPrevious = (previous - vertex).normalized()
    val toNext = (next - vertex).normalized()
    val theta = acos(dot(toPrevious, toNext).coerceIn(-1f, 1f))
    val cut = radius / tan(theta / 2f)
    val start = vertex + toPrevious * cut
    val end = vertex + toNext * cut
    val center = vertex + (toPrevious + toNext).normalized() * (radius / sin(theta / 2f))
    val startDegrees = angleDegrees(start - center)
    var sweep = angleDegrees(end - center) - startDegrees
    while (sweep > 180f) sweep -= 360f
    while (sweep <= -180f) sweep += 360f
    return Corner(start, end, PathOp.ArcTo(center, radius, radius, startDegrees, sweep))
}

/** A closed polygon whose corner `i` is rounded with `radius(i)`. */
internal fun roundedPolygon(points: List<Offset>, radius: (Int) -> Float): List<PathOp> {
    val n = points.size
    val corners =
        points.indices.map { i ->
            corner(points[(i + n - 1) % n], points[i], points[(i + 1) % n], radius(i))
        }
    return buildList {
        add(PathOp.MoveTo(corners[0].end))
        for (i in 1..n) {
            val c = corners[i % n]
            add(PathOp.LineTo(c.start))
            add(c.arc)
        }
        add(PathOp.Close)
    }
}

/** An open polyline whose interior vertex `i` is rounded with `radius(i)`. */
internal fun roundedPolyline(points: List<Offset>, radius: (Int) -> Float): List<PathOp> =
    buildList {
        add(PathOp.MoveTo(points.first()))
        for (i in 1 until points.lastIndex) {
            val c = corner(points[i - 1], points[i], points[i + 1], radius(i))
            add(PathOp.LineTo(c.start))
            add(c.arc)
        }
        add(PathOp.LineTo(points.last()))
    }

/**
 * One edge of the light fan behind the cup, fitted through three x positions measured on the source
 * bitmap at y = 480, 405 and 345. It is a parabola in x(y) up to [MID_Y], then its tangent straight
 * up past the top of the badge, where the front star clips it.
 */
internal class FanEdge(private val x480: Float, private val x405: Float, private val x345: Float) {
    /** Lagrange interpolation through the three measurements, in source bitmap pixels. */
    fun xAt(y: Float): Float {
        val l0 = (y - 405f) * (y - 345f) / ((480f - 405f) * (480f - 345f))
        val l1 = (y - 480f) * (y - 345f) / ((405f - 480f) * (405f - 345f))
        val l2 = (y - 480f) * (y - 405f) / ((345f - 480f) * (345f - 405f))
        return x480 * l0 + x405 * l1 + x345 * l2
    }

    private fun slopeAt(y: Float): Float = (xAt(y + 0.5f) - xAt(y - 0.5f))

    /** Bottom, quadratic control, parabola end, and the top of the straight run, in art units. */
    fun points(mirrorAxis: Float? = null): List<Offset> {
        val middle = (MID_Y + BOTTOM_Y) / 2f
        val x0 = xAt(BOTTOM_Y)
        val x2 = xAt(MID_Y)
        val x1 = 2f * xAt(middle) - (x0 + x2) / 2f
        val x3 = x2 + slopeAt(MID_Y) * (TOP_Y - MID_Y)
        val points =
            listOf(
                sourcePoint(x0, BOTTOM_Y),
                sourcePoint(x1, middle),
                sourcePoint(x2, MID_Y),
                sourcePoint(x3, TOP_Y),
            )
        return if (mirrorAxis == null) points
        else points.map { Offset(2f * mirrorAxis - it.x, it.y) }
    }

    private companion object {
        const val BOTTOM_Y = 490f
        const val MID_Y = 300f
        const val TOP_Y = 150f
    }
}

internal fun fanEdge(x480: Float, x405: Float, x345: Float): FanEdge = FanEdge(x480, x405, x345)

/** Converts a position measured on the 1038×1104 source bitmap into art units. */
internal fun sourcePoint(x: Float, y: Float): Offset =
    Offset(x - BadgeArt.SOURCE_ORIGIN.x, y - BadgeArt.SOURCE_ORIGIN.y)

internal fun dot(a: Offset, b: Offset): Float = a.x * b.x + a.y * b.y

internal fun Offset.normalized(): Offset = this / getDistance()

internal fun angleDegrees(v: Offset): Float = atan2(v.y, v.x) * RADIANS_TO_DEGREES
