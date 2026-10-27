// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.pathOf
import kotlin.math.floor
import kotlin.math.hypot

data class SpotGlint(val d: IsoPath, val alpha: Double)

data class SpotRow(
    val row: Int,
    val gap: Double,
    val joined: Boolean,
    val glass: List<SpotGlass>,
    val neck: List<SpotGlint>,
)

/** One field per pair, sampled at the original's quarter-pixel key. */
object SpotDrops {
    private val cache = LinkedHashMap<Int, SpotRow>()

    fun ease(t: Double) = t * t * (3 - 2 * t)

    fun keyOf(merge: Double) = floor(merge * 4 + 0.5).toInt()

    fun rowOf(row: Int, merge: Double): SpotRow {
        val gap = SpotRig.GAPS[row]
        val plain = gap >= 2 * merge
        val key = row * 1000 + if (plain) 999 else keyOf(merge)
        cache[key]?.let {
            return it
        }
        val exact = if (plain) 0.0 else keyOf(merge) / 4.0
        val x0 = SpotRig.rowX(row)
        val y0 = SpotRig.rowY(row) + SpotRig.BIG_Y
        fun toWorld(p: Floor) = Floor(x0 + p.y, y0 + p.x)
        val field = SpotModel.fieldOf(gap, exact)
        val kiss = !plain && keyOf(merge) == (gap * 4).toInt()
        val level = if (kiss) field(SpotRig.RA + gap / 2, 0.75) else -0.05
        val found =
            SpotContours.rings(field, gap, level).map { SpotContours.simplifyRing(it, 0.06) }
        val joined = gap < exact && found.size == 1
        val glass =
            found
                .map { SpotGlass(it.map(::toWorld), SpotRig.FLOOR, SpotRig.THICK, found.size == 1) }
                .sortedBy { it.depth }
        val neck =
            if (joined)
                concaveRuns(found[0].map(::toWorld)).flatMap { run ->
                    glintsOf(
                        run.map { index ->
                            val p = toWorld(found[0][index])
                            SpotRig.at(Point3(p.x, p.y, SpotRig.GLASS_TOP))
                        }
                    )
                }
            else emptyList()
        val shape = SpotRow(row, gap, joined, glass, neck)
        if (cache.size >= 420) cache.remove(cache.keys.first())
        cache[key] = shape
        return shape
    }

    fun rowsOf(merge: Double) = SpotRig.GAPS.indices.map { rowOf(it, merge) }

    private fun concaveRuns(ring: List<Floor>): List<List<Int>> {
        val along = arrayListOf(0.0)
        for (index in ring.indices) {
            val a = ring[index]
            val b = ring[(index + 1) % ring.size]
            along.add(along[index] + hypot(b.x - a.x, b.y - a.y))
        }
        val total = along.last()
        fun pointAt(distance: Double): Floor {
            val s = ((distance % total) + total) % total
            var low = 0
            var high = ring.size
            while (high - low > 1) {
                val mid = (low + high) shr 1
                if (along[mid] <= s) low = mid else high = mid
            }
            val a = ring[low]
            val b = ring[(low + 1) % ring.size]
            val t = (s - along[low]) / maxOf(along[low + 1] - along[low], 1e-9)
            return Floor(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        }
        val turn = if (SpotContours.signedArea(ring) > 0) 1 else -1
        val flags = ring.mapIndexed { index, p ->
            val a = pointAt(along[index] - 4)
            val b = pointAt(along[index] + 4)
            turn * ((p.x - a.x) * (b.y - p.y) - (p.y - a.y) * (b.x - p.x)) < -0.3
        }
        return SpotContours.runsOf(flags, true).filter { it.size > 2 }
    }

    private class GlintRun(val points: List<Flat>) {
        val along = buildList {
            add(0.0)
            for (index in 1 until points.size) add(
                last() +
                    hypot(
                        points[index].x - points[index - 1].x,
                        points[index].y - points[index - 1].y,
                    )
            )
        }

        private fun place(distance: Double): Flat {
            var index = 1
            while (index < along.size - 1 && along[index] < distance) index++
            val t = (distance - along[index - 1]) / maxOf(along[index] - along[index - 1], 1e-9)
            val a = points[index - 1]
            val b = points[index]
            return Flat(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        }

        fun cut(from: Double, to: Double): List<Flat> = buildList {
            add(place(from))
            for (index in points.indices) if (along[index] > from && along[index] < to)
                add(points[index])
            add(place(to))
        }
    }

    private fun glintsOf(points: List<Flat>): List<SpotGlint> {
        val run = GlintRun(points)
        val total = run.along.last()
        if (total < 0.5) return emptyList()
        val fade = minOf(total * 0.3, 9.0)
        var before = 0.0
        return (0 until 6).map { layer ->
            val now = if (layer == 5) 1.0 else ease((layer + 1) / 6.0)
            val alpha = floor((1 - (1 - now) / (1 - before)) * 100 + 0.5) / 100
            before = now
            val inset = fade * layer / 5
            SpotGlint(pathOf(run.cut(inset, total - inset)), alpha)
        }
    }
}
