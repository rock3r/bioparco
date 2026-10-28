package dev.sebastiano.currencyswarm

/**
 * Where (`x`, `y`) falls along a Hilbert curve that fills a [side] × [side] grid. [side] is a power
 * of two. Cells next to each other along the curve are next to each other in the grid, and cells
 * close in the grid are mostly close along the curve.
 */
internal fun hilbertIndex(x: Int, y: Int, side: Int): Int {
    var cx = x
    var cy = y
    var index = 0
    var half = side / 2
    while (half > 0) {
        val rx = if (cx and half != 0) 1 else 0
        val ry = if (cy and half != 0) 1 else 0
        index += half * half * ((3 * rx) xor ry)
        // Rotate the quadrant so the curve inside it starts and ends where the parent expects.
        if (ry == 0) {
            if (rx == 1) {
                cx = side - 1 - cx
                cy = side - 1 - cy
            }
            val swap = cx
            cx = cy
            cy = swap
        }
        half /= 2
    }
    return index
}

/**
 * Pairs every point of [from] with one point of [to], so that a dot flying from one to the other
 * mostly travels a short way. Both arrays hold `x, y` pairs, the same number of them. Each set is
 * put in order along one Hilbert curve laid over both, and the n-th of one goes to the n-th of the
 * other: neighbours stay neighbours, so a shape flows into the next instead of crossing itself.
 *
 * Swapping partners between neighbours along the curve, where that shortens both flights, was
 * tried: between glyphs it cut the mean flight by a tenth for 6 ms a morph, a frame's budget.
 *
 * Returns, for each point of [from], the index of its partner in [to].
 */
internal fun pairAlongCurve(from: FloatArray, to: FloatArray): IntArray {
    require(from.size == to.size && from.size % 2 == 0) {
        "Both sets need the same number of points"
    }
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    for (points in arrayOf(from, to)) {
        for (i in points.indices step 2) {
            minX = minOf(minX, points[i])
            maxX = maxOf(maxX, points[i])
            minY = minOf(minY, points[i + 1])
            maxY = maxOf(maxY, points[i + 1])
        }
    }
    val span = maxOf(maxX - minX, maxY - minY, 1e-6f)
    val cell = (CURVE_SIDE - 1) / span

    // Each key packs a point's place on the curve above its index, so one primitive sort orders a
    // set: a morph happens inside a frame, and boxing thousands of indices costs milliseconds.
    fun order(points: FloatArray): IntArray {
        val keys =
            LongArray(points.size / 2) {
                val gx = ((points[it * 2] - minX) * cell).toInt()
                val gy = ((points[it * 2 + 1] - minY) * cell).toInt()
                (hilbertIndex(gx, gy, CURVE_SIDE).toLong() shl INDEX_BITS) or it.toLong()
            }
        keys.sort()
        return IntArray(keys.size) { (keys[it] and INDEX_MASK).toInt() }
    }

    val fromOrder = order(from)
    val toOrder = order(to)
    val pairs = IntArray(from.size / 2)
    for (rank in fromOrder.indices) pairs[fromOrder[rank]] = toOrder[rank]
    return pairs
}

// Fine enough that two distinct points almost never share a cell.
private const val CURVE_SIDE = 1 shl 15
private const val INDEX_BITS = 32
private const val INDEX_MASK = (1L shl INDEX_BITS) - 1
