package dev.sebastiano.grabbystepper

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Dark chrome: a stadium that stretches toward the thumb, plus a gooey
 * metaball neck while the thumb is still coupled. The thumb circle itself is
 * drawn separately (lighter fill) so the number can ride the finger.
 */
fun elasticTrackPath(
    stretch: StretchRect,
    thumbCenter: Offset,
    thumbRadius: Float,
    gooey: Float,
    maxNeckDistance: Float,
): Path {
    val path = Path()
    val radius = stretch.cornerRadius
    path.addRoundRect(
        RoundRect(
            left = stretch.left,
            top = stretch.top,
            right = stretch.right,
            bottom = stretch.bottom,
            radiusX = radius,
            radiusY = radius,
        )
    )
    if (gooey > 0.02f) {
        val attach =
            Offset(
                x = stretch.centerX,
                y =
                    when {
                        thumbCenter.y > stretch.centerY -> stretch.bottom - radius
                        thumbCenter.y < stretch.centerY -> stretch.top + radius
                        else -> stretch.centerY
                    },
            )
        val attachRadius = radius * (0.72f + 0.28f * gooey)
        path.addMetaball(
            c1 = attach,
            r1 = attachRadius,
            c2 = thumbCenter,
            r2 = thumbRadius * (0.88f + 0.12f * gooey),
            v = 0.52f * gooey,
            handleLenRate = 2.35f,
            maxDistance = maxNeckDistance,
        )
    }
    return path
}

/**
 * Classic two-circle metaball (Zhang Xin / Cuberto gooey). Adds a closed
 * bezier "neck" joining [c1] and [c2]. No-ops when the circles are too far
 * apart or one radius is degenerate.
 */
fun Path.addMetaball(
    c1: Offset,
    r1: Float,
    c2: Offset,
    r2: Float,
    v: Float,
    handleLenRate: Float,
    maxDistance: Float,
) {
    if (r1 <= 0f || r2 <= 0f || v <= 0f) return
    val dx = c2.x - c1.x
    val dy = c2.y - c1.y
    val d = hypot(dx, dy)
    if (d <= 1e-3f || d > maxDistance) return

    val u1: Float
    val u2: Float
    if (d < r1 + r2) {
        u1 = acos(((r1 * r1 + d * d - r2 * r2) / (2f * r1 * d)).coerceIn(-1f, 1f))
        u2 = acos(((r2 * r2 + d * d - r1 * r1) / (2f * r2 * d)).coerceIn(-1f, 1f))
    } else {
        u1 = 0f
        u2 = 0f
    }

    val angleBetween = atan2(dy, dx)
    val maxSpread = acos(((r1 - r2) / d).coerceIn(-1f, 1f))
    val pi = PI.toFloat()

    val angle1 = angleBetween + u1 + (maxSpread - u1) * v
    val angle2 = angleBetween - u1 - (maxSpread - u1) * v
    val angle3 = angleBetween + pi - u2 - (pi - u2 - maxSpread) * v
    val angle4 = angleBetween - pi + u2 + (pi - u2 - maxSpread) * v

    val p1 = polar(c1, r1, angle1)
    val p2 = polar(c1, r1, angle2)
    val p3 = polar(c2, r2, angle3)
    val p4 = polar(c2, r2, angle4)

    val totalRadius = r1 + r2
    val d2 = min(v * handleLenRate, (p1 - p3).getDistance() / totalRadius) * min(1f, d * 2f / totalRadius)
    val r1d2 = r1 * d2
    val r2d2 = r2 * d2
    val halfPi = pi / 2f

    val h1 = polar(p1, r1d2, angle1 - halfPi)
    val h2 = polar(p2, r1d2, angle2 + halfPi)
    val h3 = polar(p3, r2d2, angle3 + halfPi)
    val h4 = polar(p4, r2d2, angle4 - halfPi)

    moveTo(p1.x, p1.y)
    cubicTo(h1.x, h1.y, h3.x, h3.y, p3.x, p3.y)
    lineTo(p4.x, p4.y)
    cubicTo(h4.x, h4.y, h2.x, h2.y, p2.x, p2.y)
    close()
}

private fun polar(origin: Offset, radius: Float, angle: Float): Offset =
    Offset(origin.x + radius * cos(angle), origin.y + radius * sin(angle))

private fun Offset.getDistance(): Float = hypot(x, y)
