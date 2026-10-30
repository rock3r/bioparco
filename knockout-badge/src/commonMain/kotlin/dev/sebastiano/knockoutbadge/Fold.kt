package dev.sebastiano.knockoutbadge

import kotlin.math.hypot

/** A point in the badge's own space, in pixels from its top left corner. */
data class Point(val x: Float, val y: Float)

/**
 * The sticker's [corner] lifted and laid down at [grip], as paper folds: the crease is the line
 * halfway between the two, square to the line joining them. Everything on the corner's side of the
 * crease is lifted, and lands mirrored across it.
 */
class Fold(val corner: Point, val grip: Point) {
    private val length = hypot(corner.x - grip.x, corner.y - grip.y)

    // A unit vector from the grip towards the corner, square to the crease. A grip on the corner
    // folds nothing; any direction will do then.
    private val nx = if (length > 0f) (corner.x - grip.x) / length else 1f
    private val ny = if (length > 0f) (corner.y - grip.y) / length else 0f
    private val mid = Point((corner.x + grip.x) / 2f, (corner.y + grip.y) / 2f)
    private val creaseOffset = mid.x * nx + mid.y * ny

    /** How far the corner travelled. */
    val depth: Float
        get() = length

    /** How far [p] is past the crease, towards the corner. */
    private fun past(p: Point): Float = p.x * nx + p.y * ny - creaseOffset

    fun isLifted(p: Point): Boolean = past(p) > 0f

    /** Where [p] lands when folded over the crease. */
    fun reflect(p: Point): Point {
        val d = past(p)
        return Point(p.x - 2f * d * nx, p.y - 2f * d * ny)
    }

    /**
     * The reflection as an affine matrix, `[a, b, c, d, tx, ty]`: a point (x, y) goes to (a·x +
     * c·y + tx, b·x + d·y + ty).
     */
    fun reflection(): FloatArray {
        val a = 1f - 2f * nx * nx
        val bc = -2f * nx * ny
        val d = 1f - 2f * ny * ny
        return floatArrayOf(a, bc, bc, d, 2f * creaseOffset * nx, 2f * creaseOffset * ny)
    }

    /**
     * A quad on the lifted side of the crease, reaching [reach] along the crease either way from
     * its middle and [reach] past it: clip to it for the lifted part, or to its outside for the
     * part that stays down.
     */
    fun liftedSide(reach: Float): List<Point> {
        // Along the crease.
        val ax = -ny
        val ay = nx
        return listOf(
            Point(mid.x + ax * reach, mid.y + ay * reach),
            Point(mid.x + ax * reach + nx * reach, mid.y + ay * reach + ny * reach),
            Point(mid.x - ax * reach + nx * reach, mid.y - ay * reach + ny * reach),
            Point(mid.x - ax * reach, mid.y - ay * reach),
        )
    }
}

/**
 * Where the top right [corner] goes when the hand pulls it to [pointer]: never out past the badge's
 * top or right edges, and never more than [maxReach] from where it started.
 */
fun gripFor(corner: Point, pointer: Point, maxReach: Float): Point {
    val dx = minOf(pointer.x - corner.x, 0f)
    val dy = maxOf(pointer.y - corner.y, 0f)
    val distance = hypot(dx, dy)
    val scale = if (distance > maxReach) maxReach / distance else 1f
    return Point(corner.x + dx * scale, corner.y + dy * scale)
}
