package dev.sebastiano.peelsticker

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The die-cut outline of a sticker as a [width] × [height] mask, one cell per pixel. It answers
 * whether a point is on the sticker, and how far the sticker reaches in a direction, which is where
 * a peel starts from.
 */
internal class Silhouette(val width: Int, val height: Int, private val coverage: BooleanArray) {
    /** Convex hull corners, x then y, of every covered cell, in cells. */
    val hull: FloatArray = convexHull(rowEnds())

    fun contains(x: Float, y: Float): Boolean {
        if (x < 0f || y < 0f) return false
        val column = x.toInt()
        val row = y.toInt()
        if (column >= width || row >= height) return false
        return coverage[row * width + column]
    }

    /** Whether the sticker is within about [slop] cells of the point: a forgiving grab. */
    fun near(x: Float, y: Float, slop: Float): Boolean {
        if (contains(x, y)) return true
        if (slop <= 0f) return false
        for (ring in 1..2) {
            val radius = slop * ring / 2f
            for (step in 0 until PROBES) {
                val angle = step * 2.0 * PI / PROBES
                val probeX = x + radius * cos(angle).toFloat()
                val probeY = y + radius * sin(angle).toFloat()
                if (contains(probeX, probeY)) return true
            }
        }
        return false
    }

    /** The largest `x * dx + y * dy` over the sticker, or 0 when there is no sticker. */
    fun extent(dx: Float, dy: Float): Float {
        if (hull.isEmpty()) return 0f
        var best = Float.NEGATIVE_INFINITY
        var i = 0
        while (i < hull.size) {
            best = maxOf(best, hull[i] * dx + hull[i + 1] * dy)
            i += 2
        }
        return best
    }

    /** The outer corners of the first and last covered cell in each row: enough for the hull. */
    private fun rowEnds(): List<Pair<Float, Float>> {
        val points = ArrayList<Pair<Float, Float>>()
        for (row in 0 until height) {
            var first = -1
            var last = -1
            for (column in 0 until width) {
                if (coverage[row * width + column]) {
                    if (first < 0) first = column
                    last = column
                }
            }
            if (first < 0) continue
            val top = row.toFloat()
            val bottom = row + 1f
            points += first.toFloat() to top
            points += first.toFloat() to bottom
            points += last + 1f to top
            points += last + 1f to bottom
        }
        return points
    }
}

private const val PROBES = 12

/** Andrew's monotone chain: the hull's corners, x then y. */
internal fun convexHull(points: List<Pair<Float, Float>>): FloatArray {
    val sorted = points.distinct().sortedWith(compareBy({ it.first }, { it.second }))
    if (sorted.size < 3) return sorted.flatMap { listOf(it.first, it.second) }.toFloatArray()
    val hull = ArrayList<Pair<Float, Float>>()
    fun cross(o: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>) =
        (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)
    for (pass in 0..1) {
        val start = hull.size
        val ordered = if (pass == 0) sorted else sorted.asReversed()
        for (point in ordered) {
            while (hull.size >= start + 2 && cross(hull[hull.size - 2], hull.last(), point) <= 0f) {
                hull.removeAt(hull.size - 1)
            }
            hull += point
        }
        hull.removeAt(hull.size - 1)
    }
    return hull.flatMap { listOf(it.first, it.second) }.toFloatArray()
}
