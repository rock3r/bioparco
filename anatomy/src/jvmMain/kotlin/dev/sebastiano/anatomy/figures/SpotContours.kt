// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Floor
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot

/** Float32 marching squares and ring simplification from `drops.ts`. */
internal object SpotContours {
    private class Grid(val values: FloatArray, val us: List<Double>, val vs: List<Double>) {
        val nx = us.size

        fun at(i: Int, j: Int) = values[j * nx + i].toDouble()

        fun pointOf(id: Int, level: Double): Floor {
            val cell = id shr 1
            val i = cell % nx
            val j = (cell - i) / nx
            val down = id and 1 != 0
            val a = at(i, j)
            val b = if (down) at(i, j + 1) else at(i + 1, j)
            val t = ((level - a) / (b - a)).coerceIn(0.0, 1.0)
            return if (down) Floor(us[i], vs[j] + (vs[j + 1] - vs[j]) * t)
            else Floor(us[i] + (us[i + 1] - us[i]) * t, vs[j])
        }
    }

    private fun ticksOf(centre: Double, before: Double, after: Double): List<Double> = buildList {
        for (k in -ceil(before / 2).toInt()..ceil(after / 2).toInt()) {
            if (abs(k * 2) > 6) add(centre + k * 2)
        }
        for (k in -12..12) add(centre + k * 0.5)
    }
        .sorted()

    private fun sample(field: SpotField, gap: Double): Grid {
        val middle = SpotRig.RA + gap / 2
        val us =
            ticksOf(
                middle,
                middle + SpotRig.RA + 6,
                SpotModel.spanOf(gap) + SpotRig.RB + 6 - middle,
            )
        val vs = ticksOf(0.0, SpotRig.RA + 6, SpotRig.RA + 6)
        val nx = us.size
        val values = FloatArray(nx * vs.size)
        for (j in vs.indices) {
            val mirror = vs.size - 1 - j
            if (mirror < j) values.copyInto(values, j * nx, mirror * nx, mirror * nx + nx)
            else for (i in us.indices) values[j * nx + i] = field(us[i], vs[j]).toFloat()
        }
        return Grid(values, us, vs)
    }

    private val SEGMENTS =
        mapOf(
            1 to listOf(3 to 2),
            2 to listOf(2 to 1),
            3 to listOf(3 to 1),
            4 to listOf(0 to 1),
            6 to listOf(0 to 2),
            7 to listOf(3 to 0),
            8 to listOf(3 to 0),
            9 to listOf(0 to 2),
            11 to listOf(0 to 1),
            12 to listOf(3 to 1),
            13 to listOf(2 to 1),
            14 to listOf(3 to 2),
        )

    private fun pairs(code: Int, centre: Boolean): List<Pair<Int, Int>> =
        SEGMENTS[code]
            ?: when {
                (code == 5) == centre -> listOf(3 to 0, 2 to 1)
                else -> listOf(0 to 1, 3 to 2)
            }

    private fun links(grid: Grid, level: Double): LinkedHashMap<Int, MutableList<Int>> {
        val nx = grid.nx
        val links = LinkedHashMap<Int, MutableList<Int>>()
        for (j in 0 until grid.vs.size - 1) {
            for (i in 0 until nx - 1) {
                val tl = grid.at(i, j)
                val tr = grid.at(i + 1, j)
                val br = grid.at(i + 1, j + 1)
                val bl = grid.at(i, j + 1)
                val code =
                    (if (tl < level) 8 else 0) or
                        (if (tr < level) 4 else 0) or
                        (if (br < level) 2 else 0) or
                        (if (bl < level) 1 else 0)
                if (code == 0 || code == 15) continue
                val edges =
                    listOf(
                        (j * nx + i) * 2,
                        ((j * nx + i + 1) * 2) or 1,
                        ((j + 1) * nx + i) * 2,
                        ((j * nx + i) * 2) or 1,
                    )
                for ((a, b) in pairs(code, (tl + tr + br + bl) / 4 < level)) {
                    links.getOrPut(edges[a]) { ArrayList() }.add(edges[b])
                    links.getOrPut(edges[b]) { ArrayList() }.add(edges[a])
                }
            }
        }
        return links
    }

    fun rings(field: SpotField, gap: Double, level: Double): List<List<Floor>> {
        val grid = sample(field, gap)
        val links = links(grid, level)
        val seen = HashSet<Int>()
        val found = ArrayList<List<Floor>>()
        for (start in links.keys) {
            if (start in seen) continue
            val ring = ArrayList<Floor>()
            var previous = -1
            var current = start
            while (current !in seen) {
                seen.add(current)
                ring.add(grid.pointOf(current, level))
                val next =
                    links.getValue(current).firstOrNull { it != previous && it !in seen } ?: break
                previous = current
                current = next
            }
            if (ring.size > 3) found.add(ring)
        }
        return found
    }

    private fun simplify(points: List<Floor>, tolerance: Double): List<Floor> {
        if (points.size < 3) return points
        val first = points.first()
        val last = points.last()
        val dx = last.x - first.x
        val dy = last.y - first.y
        val length = hypot(dx, dy).takeIf { it != 0.0 } ?: 1.0
        var worst = 0.0
        var index = 0
        for (step in 1 until points.size - 1) {
            val point = points[step]
            val off = abs((point.x - first.x) * dy - (point.y - first.y) * dx) / length
            if (off > worst) {
                worst = off
                index = step
            }
        }
        if (worst <= tolerance) return listOf(first, last)
        return simplify(points.take(index + 1), tolerance).dropLast(1) +
            simplify(points.drop(index), tolerance)
    }

    fun simplifyRing(ring: List<Floor>, tolerance: Double): List<Floor> {
        var far = 0
        var best = 0.0
        for (index in 1 until ring.size) {
            val distance = hypot(ring[index].x - ring[0].x, ring[index].y - ring[0].y)
            if (distance > best) {
                best = distance
                far = index
            }
        }
        val one = simplify(ring.take(far + 1), tolerance)
        val two = simplify(ring.drop(far) + ring[0], tolerance)
        return one.dropLast(1) + two.dropLast(1)
    }

    fun signedArea(points: List<Floor>): Double {
        var area = 0.0
        for (index in points.indices) {
            val a = points[index]
            val b = points[(index + 1) % points.size]
            area += a.x * b.y - b.x * a.y
        }
        return area / 2
    }

    fun normalsOf(ring: List<Floor>): List<Floor> {
        val counter = signedArea(ring) > 0
        return ring.mapIndexed { index, a ->
            val b = ring[(index + 1) % ring.size]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val length = hypot(dx, dy).takeIf { it != 0.0 } ?: 1.0
            if (counter) Floor(dy / length, -dx / length) else Floor(-dy / length, dx / length)
        }
    }

    fun runsOf(flags: List<Boolean>, want: Boolean): List<List<Int>> {
        val count = flags.size
        val runs = ArrayList<List<Int>>()
        val start = flags.indices.firstOrNull { flags[it] != flags[(it + count - 1) % count] } ?: -1
        if (start < 0)
            return if (flags[0] == want) listOf((0..count).map { it % count }) else emptyList()
        var run: MutableList<Int>? = null
        for (step in 0 until count) {
            val index = (start + step) % count
            if (flags[index] == want) {
                if (run == null) run = arrayListOf(index)
                run.add((index + 1) % count)
            } else if (run != null) {
                runs.add(run)
                run = null
            }
        }
        if (run != null) runs.add(run)
        return runs
    }
}
