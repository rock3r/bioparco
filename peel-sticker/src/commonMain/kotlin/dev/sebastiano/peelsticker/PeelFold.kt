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
     * A box around [left], [top], [right], [bottom] and everywhere the peel can move that box to,
     * so the renderer only shades where the sticker can be.
     *
     * A point `q` past the axis moves back along the fold by at most the larger of two affine
     * shifts: the flat part's mirror image, and the drop to the foot of the loose curl. So its
     * landing lies between it and one of those two images, and the images of the corners bound them
     * all.
     */
    fun reach(left: Float, top: Float, right: Float, bottom: Float): FloatArray {
        val tight = curve.tight
        val loose = curve.loose
        val mirror = tight - loose + (PI.toFloat() / 2f) * (tight + loose)
        val box = floatArrayOf(left, top, right, bottom)
        for (x in floatArrayOf(left, right)) {
            for (y in floatArrayOf(top, bottom)) {
                val q = (x - axisX) * dirX + (y - axisY) * dirY
                for (shift in floatArrayOf(mirror - 2f * q, tight - loose - q)) {
                    val landedX = x + dirX * shift
                    val landedY = y + dirY * shift
                    box[0] = min(box[0], landedX)
                    box[1] = min(box[1], landedY)
                    box[2] = max(box[2], landedX)
                    box[3] = max(box[3], landedY)
                }
            }
        }
        return box
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
