package dev.sebastiano.peelsticker

import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * A peel in progress, in stage pixels. The sheet lifts off the table along the line through
 * ([axisX], [axisY]) perpendicular to ([dirX], [dirY]); everything further along the direction is
 * off the table and follows [curve]. [depth] is how far behind the axis the grabbed edge sits.
 */
internal class PeelFold(
    val axisX: Float,
    val axisY: Float,
    val dirX: Float,
    val dirY: Float,
    val curve: PeelCurve,
    val depth: Float,
) {
    /**
     * A convex outline, x then y, around everywhere the part of [left], [top], [right], [bottom]
     * past the axis can land, so the renderer only shades where the sticker can be.
     *
     * A lifted point moves back along the fold by at most the larger of two affine shifts: the flat
     * part's mirror image, and the drop to the foot of the loose curl. So it lands between itself
     * and one of those two images, and the hull of the lifted corners and their images holds them
     * all.
     */
    fun liftedOutline(left: Float, top: Float, right: Float, bottom: Float): FloatArray =
        liftedOutline(floatArrayOf(left, top, right, top, right, bottom, left, bottom))

    /** As [liftedOutline] for a box, for any convex [shape], x then y: the sticker's own hull. */
    fun liftedOutline(shape: FloatArray): FloatArray {
        val lifted = liftedCorners(shape)
        val tight = curve.tight
        val loose = curve.loose
        val mirror = tight - loose + (PI.toFloat() / 2f) * (tight + loose)
        val points = ArrayList<Pair<Float, Float>>()
        for ((x, y) in lifted) {
            val q = (x - axisX) * dirX + (y - axisY) * dirY
            points += x to y
            for (shift in floatArrayOf(mirror - 2f * q, tight - loose - q)) {
                points += (x + dirX * shift) to (y + dirY * shift)
            }
        }
        return convexHull(points)
    }

    /** The corners of the convex [shape] cut down to the side of the axis that lifts off. */
    private fun liftedCorners(shape: FloatArray): List<Pair<Float, Float>> {
        val corners = (shape.indices step 2).map { shape[it] to shape[it + 1] }
        fun q(point: Pair<Float, Float>) =
            (point.first - axisX) * dirX + (point.second - axisY) * dirY
        val cut = ArrayList<Pair<Float, Float>>()
        for (i in corners.indices) {
            val a = corners[i]
            val b = corners[(i + 1) % corners.size]
            val qa = q(a)
            val qb = q(b)
            if (qa >= 0f) cut += a
            if ((qa >= 0f) != (qb >= 0f)) {
                val t = qa / (qa - qb)
                cut += (a.first + (b.first - a.first) * t) to (a.second + (b.second - a.second) * t)
            }
        }
        return cut
    }
}

/**
 * The fold for a sticker grabbed at ([grabX], [grabY]) and dragged to ([pointerX], [pointerY]).
 *
 * The peel starts from the sticker's edge in the direction the drag came from: [extent] says how
 * far the sticker reaches along a unit direction. A grab right on that edge stays under the
 * pointer; a grab further in peels the edge by the same drag, so a peel never pops in halfway
 * across the sticker. Both curls grow with the drag, up to a share of the sticker's [size].
 */
internal fun peelFold(
    grabX: Float,
    grabY: Float,
    pointerX: Float,
    pointerY: Float,
    extent: (Float, Float) -> Float,
    size: Float,
): PeelFold? {
    val dx = grabX - pointerX
    val dy = grabY - pointerY
    val distance = hypot(dx, dy)
    if (distance < MIN_DRAG) return null
    val dirX = dx / distance
    val dirY = dy / distance
    val edge = max(0f, extent(dirX, dirY) - (grabX * dirX + grabY * dirY))
    val curve =
        PeelCurve(
            tight = min(TIGHT_CURL * size, TIGHT_RAMP * distance),
            loose = min(LOOSE_CURL * size, LOOSE_RAMP * distance),
        )
    val depth = curve.depthFor(distance)
    return PeelFold(
        axisX = grabX + dirX * (edge - depth),
        axisY = grabY + dirY * (edge - depth),
        dirX = dirX,
        dirY = dirY,
        curve = curve,
        depth = depth,
    )
}

private const val MIN_DRAG = 0.01f

/** The tight curl's radius, as a share of the sticker, and how fast the drag grows it. */
private const val TIGHT_CURL = 0.035f
private const val TIGHT_RAMP = 0.3f

/** The loose curl's radius, as a share of the sticker, and how fast the drag grows it. */
private const val LOOSE_CURL = 0.55f
private const val LOOSE_RAMP = 0.75f
