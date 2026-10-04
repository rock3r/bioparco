// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline

/** One polyline of a path: straight segments through [points], closed back to the first or not. */
class SubPath(val points: List<Vec2>, val closed: Boolean)

/**
 * What the original writes into an SVG `d` attribute. Every path a figure draws is made of straight
 * segments, so this is a list of polylines. Paths join with `+`, as the original joins strings.
 */
class PathData(val parts: List<SubPath>) {
    operator fun plus(other: PathData): PathData =
        when {
            other.parts.isEmpty() -> this
            parts.isEmpty() -> other
            else -> PathData(parts + other.parts)
        }

    fun isEmpty(): Boolean = parts.isEmpty()

    /** The SVG `d` the original would write, with its two-decimal rounding. */
    fun toSvg(): String = buildString {
        for (part in parts) {
            append('M')
            part.points.forEachIndexed { i, p ->
                if (i > 0) append('L')
                append(num(p.x)).append(' ').append(num(p.y))
            }
            if (part.closed) append('Z')
        }
    }

    companion object {
        val EMPTY = PathData(emptyList())
    }
}

/** A closed path through the points. */
fun poly(pts: List<Vec2>): PathData = PathData(listOf(SubPath(pts, closed = true)))

/** One straight segment, as its own subpath, so several can be joined into one path. */
fun seg(a: Vec2, b: Vec2): PathData = PathData(listOf(SubPath(listOf(a, b), closed = false)))

/** An open polyline; empty for fewer than two points. */
fun open(pts: List<Vec2>): PathData =
    if (pts.size < 2) PathData.EMPTY else PathData(listOf(SubPath(pts, closed = false)))

/** Joins paths into one, like `.join("")` on the original's strings. */
fun List<PathData>.join(): PathData = PathData(flatMap { it.parts })

/** A number as JavaScript's `String(r2(n))` writes it: no trailing zeros, no `.0`. */
internal fun num(n: Double): String {
    val v = r2(n)
    if (v == 0.0) return "0"
    val whole = v.toLong()
    if (v == whole.toDouble()) return whole.toString()
    return toFixed(v, 2).trimEnd('0')
}
