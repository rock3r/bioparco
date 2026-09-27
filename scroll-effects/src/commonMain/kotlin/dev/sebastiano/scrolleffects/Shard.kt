package dev.sebastiano.scrolleffects

/**
 * One piece of a shattered card: a convex polygon in card space (`0..1` on both axes), listed as
 * `u, v` pairs in order around it.
 */
class Shard(val polygon: FloatArray) {
    val corners: Int
        get() = polygon.size / 2

    /** The polygon's area-weighted centroid. */
    val centroidU: Float

    val centroidV: Float

    /** Area in card space. All the shards of one card add up to 1. */
    val area: Float

    init {
        var twiceArea = 0f
        var cu = 0f
        var cv = 0f
        for (i in 0 until corners) {
            val j = (i + 1) % corners
            val cross = polygon[i * 2] * polygon[j * 2 + 1] - polygon[j * 2] * polygon[i * 2 + 1]
            twiceArea += cross
            cu += (polygon[i * 2] + polygon[j * 2]) * cross
            cv += (polygon[i * 2 + 1] + polygon[j * 2 + 1]) * cross
        }
        area = twiceArea / 2f
        centroidU = cu / (3f * twiceArea)
        centroidV = cv / (3f * twiceArea)
    }
}

/**
 * Breaks the unit card into Voronoi cells around a jittered `cols × rows` grid of seeds. The same
 * [seed] always gives the same pieces. Cells are convex, so each one fans into triangles.
 */
fun shatter(cols: Int, rows: Int, seed: Int): List<Shard> {
    val seeds = FloatArray(cols * rows * 2)
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val i = row * cols + col
            seeds[i * 2] = (col + JITTER_MARGIN + hash01(seed, i, 1) * JITTER_SPAN) / cols
            seeds[i * 2 + 1] = (row + JITTER_MARGIN + hash01(seed, i, 2) * JITTER_SPAN) / rows
        }
    }
    val count = cols * rows
    return List(count) { i ->
        var cell = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
        val sx = seeds[i * 2]
        val sy = seeds[i * 2 + 1]
        for (j in 0 until count) {
            if (j == i) continue
            val ox = seeds[j * 2]
            val oy = seeds[j * 2 + 1]
            // Keep the half of the plane that is closer to seed i than to seed j.
            cell = clip(cell, nx = ox - sx, ny = oy - sy, mx = (sx + ox) / 2f, my = (sy + oy) / 2f)
        }
        Shard(cell)
    }
}

/** Sutherland–Hodgman: keeps the part of [polygon] where `(p - m) · n <= 0`. */
private fun clip(polygon: FloatArray, nx: Float, ny: Float, mx: Float, my: Float): FloatArray {
    val corners = polygon.size / 2
    val out = ArrayList<Float>(polygon.size + 4)
    for (i in 0 until corners) {
        val j = (i + 1) % corners
        val ax = polygon[i * 2]
        val ay = polygon[i * 2 + 1]
        val bx = polygon[j * 2]
        val by = polygon[j * 2 + 1]
        val da = (ax - mx) * nx + (ay - my) * ny
        val db = (bx - mx) * nx + (by - my) * ny
        if (da <= 0f) {
            out += ax
            out += ay
        }
        if ((da < 0f && db > 0f) || (da > 0f && db < 0f)) {
            val t = da / (da - db)
            out += ax + (bx - ax) * t
            out += ay + (by - ay) * t
        }
    }
    return out.toFloatArray()
}

private const val JITTER_MARGIN = 0.15f
private const val JITTER_SPAN = 0.7f
