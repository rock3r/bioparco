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
    fun inside(i: Int): Int {
        val x = i % paddedWidth - pad
        val y = i / paddedWidth - pad
        return if (x in 0 until width && y in 0 until height) y * width + x else -1
    }
    val toArt =
        distances(paddedWidth, paddedHeight) {
            val source = inside(it)
            source >= 0 && art[source] >= 0.5f
        }
    val padded = FloatArray(paddedWidth * paddedHeight)
    if (fillet <= 0f) {
        for (i in padded.indices) padded[i] = coverage(border - toArt[i] + 0.5f)
    } else {
        val grown = border + fillet
        // The grown edge runs half a pixel past the last pixel centre inside it.
        val toOutside = distances(paddedWidth, paddedHeight) { toArt[it] >= grown + 0.5f }
        for (i in padded.indices) padded[i] = coverage(toOutside[i] - fillet - 0.5f)
    }
    val cut = FloatArray(width * height)
    for (i in padded.indices) {
        val target = inside(i)
        if (target >= 0) cut[target] = maxOf(padded[i], art[target])
    }
    return cut
}

private fun coverage(value: Float) = value.coerceIn(0f, 1f)

/**
 * Distance from every pixel to the nearest pixel where [feature] holds, by Felzenszwalb and
 * Huttenlocher's separable transform: columns first, then rows.
 */
private fun distances(width: Int, height: Int, feature: (Int) -> Boolean): FloatArray {
    val squared = DoubleArray(width * height) { if (feature(it)) 0.0 else FAR }
    val longest = maxOf(width, height)
    val line = DoubleArray(longest)
    val out = DoubleArray(longest)
    val hulls = IntArray(longest)
    val bounds = DoubleArray(longest + 1)
    for (x in 0 until width) {
        for (y in 0 until height) line[y] = squared[y * width + x]
        transform(line, out, height, hulls, bounds)
        for (y in 0 until height) squared[y * width + x] = out[y]
    }
    for (y in 0 until height) {
        for (x in 0 until width) line[x] = squared[y * width + x]
        transform(line, out, width, hulls, bounds)
        for (x in 0 until width) squared[y * width + x] = out[x]
    }
    return FloatArray(width * height) { sqrt(squared[it]).toFloat() }
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
