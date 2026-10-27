// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import kotlin.math.abs
import kotlin.math.floor

/** A point on screen, in viewBox units. */
data class Flat(val x: Double, val y: Double) {
    operator fun plus(other: Flat): Flat = Flat(x + other.x, y + other.y)

    operator fun minus(other: Flat): Flat = Flat(x - other.x, y - other.y)

    operator fun times(by: Double): Flat = Flat(x * by, y * by)
}

/** A point on the floor: x and y, no height. */
data class Floor(val x: Double, val y: Double)

/** A point in the world: x and y on the floor, z up, in units of about 1 px of the real thing. */
data class Point3(val x: Double, val y: Double, val z: Double)

/** A rounded rectangle on the floor. */
data class Plan(val x: Double, val y: Double, val w: Double, val d: Double, val r: Double)

/**
 * Screen geometry: a list of polylines, each open or closed. This is what the kit returns where the
 * original returns an SVG `d` string; [svg] writes exactly that string, rounded to 0.1, and [plus]
 * is the original's string concatenation.
 */
class IsoPath(val runs: List<Run>) {
    /** One polyline. */
    class Run(val points: List<Flat>, val closed: Boolean)

    val isEmpty: Boolean
        get() = runs.isEmpty()

    operator fun plus(other: IsoPath): IsoPath =
        when {
            other.isEmpty -> this
            isEmpty -> other
            else -> IsoPath(runs + other.runs)
        }

    /** The SVG path data, rounded to 0.1 as the original writes it. */
    fun svg(): String = buildString {
        for (run in runs) {
            run.points.forEachIndexed { at, point ->
                append(if (at == 0) 'M' else 'L')
                append(tenth(point.x))
                append(' ')
                append(tenth(point.y))
            }
            if (run.closed) append('Z')
        }
    }

    override fun toString(): String = svg()

    companion object {
        val Empty: IsoPath = IsoPath(emptyList())

        fun join(paths: Iterable<IsoPath>): IsoPath =
            IsoPath(paths.flatMap { it.runs }).let { if (it.runs.isEmpty()) Empty else it }
    }
}

/** One polyline as a path. An empty list is an empty path. */
fun pathOf(points: List<Flat>, closed: Boolean = false): IsoPath =
    if (points.isEmpty()) IsoPath.Empty else IsoPath(listOf(IsoPath.Run(points, closed)))

/** JavaScript's `Math.round`: halves go up, towards +∞. */
internal fun jsRound(value: Double): Double = floor(value + 0.5)

/** A number to one decimal, the way the original writes path data: `12`, `-3.5`, never `-0`. */
internal fun tenth(value: Double): String {
    val tenths = jsRound(value * 10).toLong()
    if (tenths == 0L) return "0"
    val whole = abs(tenths) / 10
    val fraction = abs(tenths) % 10
    val sign = if (tenths < 0) "-" else ""
    return if (fraction == 0L) "$sign$whole" else "$sign$whole.$fraction"
}
