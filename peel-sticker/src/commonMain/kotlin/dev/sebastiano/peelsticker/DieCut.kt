package dev.sebastiano.peelsticker

import kotlin.math.sqrt

/**
 * The die-cut around a picture: its coverage grown by [border] pixels, with inside corners rounded
 * to a [fillet] radius, the way a cutting plotter would follow it. [art] and the result are `width
 * × height` coverages from 0 to 1.
 *
 * The border is an exact Euclidean offset, so outside corners come out round. The fillet is a
 * closing: grow by `border + fillet`, then shrink back by `fillet`.
 */
internal fun dieCut(
    art: FloatArray,
    width: Int,
    height: Int,
    border: Float,
    fillet: Float,
): FloatArray {
    // Work on a grid padded past the furthest the closing grows, so the shrink back sees the
    // outside all round, even where the art comes close to the edge.
    val pad = (border + fillet).toInt() + 2
    val paddedWidth = width + 2 * pad
    val paddedHeight = height + 2 * pad
    val artFeatures = BooleanArray(paddedWidth * paddedHeight)
    for (y in 0 until height) {
        val row = (y + pad) * paddedWidth + pad
        for (x in 0 until width) artFeatures[row + x] = art[y * width + x] >= 0.5f
    }
    val toArt = distances(paddedWidth, paddedHeight, artFeatures)
    val padded = FloatArray(paddedWidth * paddedHeight)
    if (fillet <= 0f) {
        for (i in padded.indices) padded[i] = coverage(border - toArt[i] + 0.5f)
    } else {
        val grown = border + fillet
        // The grown edge runs half a pixel past the last pixel centre inside it.
        val outside = BooleanArray(toArt.size) { toArt[it] >= grown + 0.5f }
        val toOutside = distances(paddedWidth, paddedHeight, outside)
        for (i in padded.indices) padded[i] = coverage(toOutside[i] - fillet - 0.5f)
    }
    val cut = FloatArray(width * height)
    for (y in 0 until height) {
        val row = (y + pad) * paddedWidth + pad
        for (x in 0 until width) {
            val target = y * width + x
            cut[target] = maxOf(padded[row + x], art[target])
        }
    }
    return cut
}

private fun coverage(value: Float) = value.coerceIn(0f, 1f)

/**
 * Distance from every pixel to the nearest pixel in [features], exactly, by Meijster, Roerdink and
 * Hesselink's separable transform. Down each column the distance to the nearest feature is a count,
 * found in two sweeps over the rows; along each row it is Felzenszwalb and Huttenlocher's lower
 * envelope of parabolas. Everything runs row by row, through memory in order.
 */
internal fun distances(width: Int, height: Int, features: BooleanArray): FloatArray {
    // Pixels down the column to the nearest feature, or NONE.
    val down = IntArray(width * height)
    for (x in 0 until width) down[x] = if (features[x]) 0 else NONE
    for (y in 1 until height) {
        val row = y * width
        for (x in 0 until width) {
            val above = down[row - width + x]
            down[row + x] = if (features[row + x]) 0 else if (above == NONE) NONE else above + 1
        }
    }
    for (y in height - 2 downTo 0) {
        val row = y * width
        for (x in 0 until width) {
            val below = down[row + width + x]
            if (below != NONE && below + 1 < down[row + x]) down[row + x] = below + 1
        }
    }
    val line = DoubleArray(width)
    val out = DoubleArray(width)
    val hulls = IntArray(width)
    val bounds = DoubleArray(width + 1)
    val result = FloatArray(width * height)
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            val d = down[row + x]
            line[x] = if (d == NONE) FAR else d.toDouble() * d
        }
        transform(line, out, width, hulls, bounds)
        for (x in 0 until width) result[row + x] = sqrt(out[x]).toFloat()
    }
    return result
}

/** The 1D squared distance transform: the lower envelope of parabolas rooted at [f]. */
private fun transform(
    f: DoubleArray,
    out: DoubleArray,
    n: Int,
    hulls: IntArray,
    bounds: DoubleArray,
) {
    var k = 0
    hulls[0] = 0
    bounds[0] = Double.NEGATIVE_INFINITY
    bounds[1] = Double.POSITIVE_INFINITY
    for (q in 1 until n) {
        var s = meet(f, q, hulls[k])
        // bounds[0] is -∞, so this stops at k = 0 at the latest.
        while (s <= bounds[k]) {
            k--
            s = meet(f, q, hulls[k])
        }
        k++
        hulls[k] = q
        bounds[k] = s
        bounds[k + 1] = Double.POSITIVE_INFINITY
    }
    k = 0
    for (q in 0 until n) {
        while (bounds[k + 1] < q) k++
        val v = hulls[k]
        out[q] = (q - v).toDouble() * (q - v) + f[v]
    }
}

/** Where the parabolas rooted at [q] and [v] cross. */
private fun meet(f: DoubleArray, q: Int, v: Int): Double =
    ((f[q] + q.toDouble() * q) - (f[v] + v.toDouble() * v)) / (2.0 * (q - v))

/** Squared distance for "no feature on this line yet". Finite, so envelopes never see NaN. */
private const val FAR = 1e12

/** No feature anywhere down this column. */
private const val NONE = Int.MAX_VALUE
