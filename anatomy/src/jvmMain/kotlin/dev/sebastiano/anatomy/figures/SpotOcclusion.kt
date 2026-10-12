// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Point3
import kotlin.math.abs

/** Concave glass hides its own lower outline; never draw a seam through its neck. */
internal class SpotOcclusion(private val ring: List<Floor>, private val top: Double) {
    private fun inside(x: Double, y: Double): Boolean {
        var hit = false
        var previous = ring.size - 1
        for (index in ring.indices) {
            val a = ring[index]
            val b = ring[previous]
            if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x)
                hit = !hit
            previous = index
        }
        return hit
    }

    fun hidden(x: Double, y: Double, z: Double, edge: Int): Boolean {
        val reach = (top - z) / SpotRig.CLIMB
        if (reach <= 0) return false
        val view = SpotRig.VIEW
        if (inside(x + view.x * 0.05, y + view.y * 0.05)) return true
        val fx = x + view.x * reach
        val fy = y + view.y * reach
        val left = minOf(x, fx)
        val right = maxOf(x, fx)
        val low = minOf(y, fy)
        val high = maxOf(y, fy)
        for (index in ring.indices) {
            if (
                index == edge ||
                    (edge < 0 && (index == -edge - 1 || (index + 1) % ring.size == -edge - 1))
            )
                continue
            val a = ring[index]
            val b = ring[(index + 1) % ring.size]
            if (
                (a.x < left && b.x < left) ||
                    (a.x > right && b.x > right) ||
                    (a.y < low && b.y < low) ||
                    (a.y > high && b.y > high)
            )
                continue
            val ex = b.x - a.x
            val ey = b.y - a.y
            val cross = view.x * ey - view.y * ex
            if (abs(cross) < 1e-12) continue
            val ox = a.x - x
            val oy = a.y - y
            val t = (ox * ey - oy * ex) / cross
            val u = (ox * view.y - oy * view.x) / cross
            if (t > 0.05 && t < reach && u >= 0 && u <= 1) return true
        }
        return false
    }

    private fun cut(edge: Int, fromShown: Boolean, z: Double): Floor {
        val a = ring[edge]
        val b = ring[(edge + 1) % ring.size]
        var low = 0.0
        var high = 1.0
        repeat(14) {
            val mid = (low + high) / 2
            val seen = !hidden(a.x + (b.x - a.x) * mid, a.y + (b.y - a.y) * mid, z, edge)
            if (seen == fromShown) low = mid else high = mid
        }
        val t = (low + high) / 2
        return Floor(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    }

    fun visibleRuns(run: List<Int>, z: Double): List<List<Flat>> {
        val runs = ArrayList<List<Flat>>()
        var line = ArrayList<Flat>()
        val shown = run.map { !hidden(ring[it].x, ring[it].y, z, -it - 1) }
        fun lift(point: Floor) = SpotRig.at(Point3(point.x, point.y, z))
        for (index in run.indices) {
            val point = ring[run[index]]
            if (index > 0 && shown[index] != shown[index - 1]) {
                val edge = cut(run[index - 1], shown[index - 1], z)
                if (shown[index - 1]) {
                    line.add(lift(edge))
                    if (line.size > 1) runs.add(line)
                    line = ArrayList()
                } else line = arrayListOf(lift(edge))
            }
            if (shown[index]) line.add(lift(point))
        }
        if (line.size > 1) runs.add(line)
        return runs
    }

    fun from(index: Int, z: Double, height: Double): Double {
        val point = ring[index]
        if (!hidden(point.x, point.y, z, -index - 1)) return z
        var low = z
        var high = z + height
        repeat(12) {
            val mid = (low + high) / 2
            if (hidden(point.x, point.y, mid, -index - 1)) low = mid else high = mid
        }
        return high
    }
}
