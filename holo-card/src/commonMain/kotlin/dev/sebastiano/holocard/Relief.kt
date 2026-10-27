package dev.sebastiano.holocard

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A signed distance: negative inside the shape. Coordinates span -1..1, y pointing down. */
fun interface Sdf {
    fun distance(x: Float, y: Float): Float
}

/**
 * Bakes [sdf] into a [size]² RGBA normal map of the shape blown up like a balloon: red and green
 * carry the surface normal's x and y (128 faces the viewer), blue the shape's antialiased coverage,
 * alpha is opaque.
 *
 * The height comes from inflating the silhouette: solve ∇²u = -1 inside it with u = 0 outside, and
 * take 2√u, which is exactly a hemisphere for a disc and a smooth pillow for anything else, with no
 * creases along corners or thin limbs. [puff] flattens it: 1 is a full hemisphere, lower is a
 * flatter face that still rounds over to vertical at the outline. The solve runs on a quarter-size
 * grid first, then a few passes at full size refine it.
 */
fun bakeRelief(sdf: Sdf, size: Int, puff: Float): ByteArray {
    val pixel = 2f / size
    val coverage = FloatArray(size * size)
    val inside = BooleanArray(size * size)
    for (row in 0 until size) {
        val y = (row + 0.5f) * pixel - 1f
        for (column in 0 until size) {
            val d = sdf.distance((column + 0.5f) * pixel - 1f, y)
            coverage[row * size + column] = (0.5f - d / pixel).coerceIn(0f, 1f)
            inside[row * size + column] = d < 0f
        }
    }
    val coarse = max(MIN_COARSE, size / COARSE_SHARE)
    val coarseInside = BooleanArray(coarse * coarse)
    val coarsePixel = 2f / coarse
    for (row in 0 until coarse) {
        val y = (row + 0.5f) * coarsePixel - 1f
        for (column in 0 until coarse) {
            coarseInside[row * coarse + column] =
                sdf.distance((column + 0.5f) * coarsePixel - 1f, y) < 0f
        }
    }
    val coarseU = FloatArray(coarse * coarse)
    relax(coarseU, coarseInside, coarse, coarsePixel, COARSE_PASSES, COARSE_OVERRELAX)
    val u = upsample(coarseU, coarse, size)
    for (i in u.indices) if (!inside[i]) u[i] = 0f
    relax(u, inside, size, pixel, FINE_PASSES, FINE_OVERRELAX)

    val height = FloatArray(size * size) { puff * 2f * sqrt(max(u[it], 0f)) }
    val pixels = ByteArray(size * size * 4)
    for (row in 0 until size) {
        for (column in 0 until size) {
            val i = row * size + column
            val dx =
                (height[row * size + min(column + 1, size - 1)] -
                    height[row * size + max(column - 1, 0)]) / (2f * pixel)
            val dy =
                (height[min(row + 1, size - 1) * size + column] -
                    height[max(row - 1, 0) * size + column]) / (2f * pixel)
            val length = sqrt(dx * dx + dy * dy + 1f)
            pixels[i * 4] = channel(-dx / length * 0.5f + 0.5f)
            pixels[i * 4 + 1] = channel(-dy / length * 0.5f + 0.5f)
            pixels[i * 4 + 2] = channel(coverage[i])
            pixels[i * 4 + 3] = OPAQUE
        }
    }
    return pixels
}

/** The pixels a relief covers, as a half-open box: [right] and [bottom] are exclusive. */
data class PixelBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** The smallest box holding every pixel of a [size]² [relief] with any coverage. */
fun coverageBounds(relief: ByteArray, size: Int): PixelBounds {
    var left = size
    var top = size
    var right = 0
    var bottom = 0
    for (row in 0 until size) {
        for (column in 0 until size) {
            if (relief[(row * size + column) * 4 + 2].toInt() == 0) continue
            left = min(left, column)
            top = min(top, row)
            right = max(right, column + 1)
            bottom = max(bottom, row + 1)
        }
    }
    return if (right == 0) PixelBounds(0, 0, 0, 0) else PixelBounds(left, top, right, bottom)
}

/**
 * Successive over-relaxation of ∇²u = -1 on the [inside] cells of an [n]² grid [spacing] apart.
 * Cells outside stay 0, which is the balloon's outline.
 */
private fun relax(
    u: FloatArray,
    inside: BooleanArray,
    n: Int,
    spacing: Float,
    passes: Int,
    overrelax: Float,
) {
    val source = spacing * spacing
    repeat(passes) {
        for (row in 1 until n - 1) {
            for (column in 1 until n - 1) {
                val i = row * n + column
                if (!inside[i]) continue
                val average = (u[i - 1] + u[i + 1] + u[i - n] + u[i + n] + source) * 0.25f
                u[i] += overrelax * (average - u[i])
            }
        }
    }
}

/** Bilinear upsampling of an [from]² grid to [to]², sampling at cell centres. */
private fun upsample(values: FloatArray, from: Int, to: Int): FloatArray {
    val out = FloatArray(to * to)
    val scale = from.toFloat() / to
    for (row in 0 until to) {
        val fy = ((row + 0.5f) * scale - 0.5f).coerceIn(0f, from - 1f)
        val y0 = fy.toInt().coerceAtMost(from - 2)
        val ty = fy - y0
        for (column in 0 until to) {
            val fx = ((column + 0.5f) * scale - 0.5f).coerceIn(0f, from - 1f)
            val x0 = fx.toInt().coerceAtMost(from - 2)
            val tx = fx - x0
            val top = values[y0 * from + x0] * (1f - tx) + values[y0 * from + x0 + 1] * tx
            val bottom =
                values[(y0 + 1) * from + x0] * (1f - tx) + values[(y0 + 1) * from + x0 + 1] * tx
            out[row * to + column] = top * (1f - ty) + bottom * ty
        }
    }
    return out
}

private const val COARSE_SHARE = 4
private const val MIN_COARSE = 16
private const val COARSE_PASSES = 300
private const val COARSE_OVERRELAX = 1.85f
private const val FINE_PASSES = 40
private const val FINE_OVERRELAX = 1.6f

private fun channel(value: Float): Byte = (value * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()

private const val OPAQUE: Byte = -1

/** The glow friends' silhouettes. Each fits inside -0.9..0.9 and covers the centre. */
enum class Mascot(val sdf: Sdf) {
    /** A five-petal flower blob. */
    Bloom(
        Sdf { x, y ->
            var d = circle(x, y, 0f, 0.02f, 0.5f)
            for (petal in 0 until 5) {
                val angle = (petal * 72f - 90f) * DEGREES
                d =
                    smoothMin(
                        d,
                        circle(x, y, cos(angle) * 0.42f, sin(angle) * 0.42f + 0.02f, 0.34f),
                        0.15f,
                    )
            }
            d
        }
    ),

    /** A pixel critter: a wide body, stubby arms, four legs. */
    Pixie(
        Sdf { x, y ->
            var d = roundBox(x, y, 0f, -0.15f, 0.6f, 0.34f, 0.14f)
            d = smoothMin(d, roundBox(abs(x), y, 0.72f, -0.08f, 0.16f, 0.13f, 0.08f), 0.06f)
            for (leg in LEGS) d =
                smoothMin(d, roundBox(x, y, leg, 0.38f, 0.085f, 0.24f, 0.07f), 0.06f)
            d
        }
    ),

    /** A robot with a cloud for a head. */
    Botto(
        Sdf { x, y ->
            val ax = abs(x)
            var d = circle(x, y, 0f, -0.3f, 0.42f)
            d = smoothMin(d, circle(ax, y, 0.38f, -0.2f, 0.3f), 0.1f)
            d = smoothMin(d, circle(ax, y, 0.2f, -0.58f, 0.26f), 0.1f)
            d = smoothMin(d, roundBox(x, y, 0f, 0.32f, 0.28f, 0.24f, 0.12f), 0.1f)
            d = smoothMin(d, circle(ax, y, 0.4f, 0.3f, 0.11f), 0.06f)
            smoothMin(d, roundBox(ax, y, 0.15f, 0.66f, 0.09f, 0.08f, 0.05f), 0.06f)
        }
    );

    companion object {
        /** How domed the mascots are: 1 would be a hemisphere, lower is a flatter face. */
        const val PUFF = 0.6f
    }
}

private val LEGS = floatArrayOf(-0.42f, -0.14f, 0.14f, 0.42f)

private const val DEGREES = (kotlin.math.PI / 180.0).toFloat()

private fun circle(x: Float, y: Float, cx: Float, cy: Float, radius: Float): Float =
    hypot(x - cx, y - cy) - radius

private fun roundBox(
    x: Float,
    y: Float,
    cx: Float,
    cy: Float,
    halfWidth: Float,
    halfHeight: Float,
    radius: Float,
): Float {
    val qx = abs(x - cx) - halfWidth + radius
    val qy = abs(y - cy) - halfHeight + radius
    val outside = hypot(max(qx, 0f), max(qy, 0f))
    return outside + min(max(qx, qy), 0f) - radius
}

/** A polynomial smooth minimum, which blends two shapes into one blob within [k] of their seam. */
private fun smoothMin(a: Float, b: Float, k: Float): Float {
    val h = (0.5f + 0.5f * (b - a) / k).coerceIn(0f, 1f)
    return b + (a - b) * h - k * h * (1f - h)
}
