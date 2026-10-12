// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import kotlin.math.floor
import kotlin.math.sqrt

typealias SpotField = (Double, Double) -> Double

/** Truth and words from `rig.ts`; equality is tangent, not joined. */
object SpotModel {
    fun spanOf(gap: Double) = SpotRig.RA + gap + SpotRig.RB

    fun reachOf(merge: Double) = merge * 2

    fun sideOf(merge: Double) = floor(reachOf(merge) * 0.35 * 10 + 0.5) / 10

    fun seenGapOf(gap: Double, merge: Double): Double =
        when {
            gap >= 2 * merge -> gap
            gap <= merge -> 0.0
            else -> 2 * sqrt(merge * (gap - merge))
        }

    fun joinedAt(merge: Double) = SpotRig.GAPS.filter { it < merge }

    fun fieldOf(gap: Double, merge: Double): SpotField {
        val span = spanOf(gap)
        val most = reachOf(merge)
        return { u, v ->
            val qu = u - span
            val la = sqrt(u * u + v * v)
            val lb = sqrt(qu * qu + v * v)
            val a = la - SpotRig.RA
            val b = lb - SpotRig.RB
            val nearest = if (a < b) a else b
            val spread = if (a < b) b - a else a - b
            if (most <= 0 || spread >= most) nearest
            else {
                val raw = 0.5 - 0.5 * (u * qu + v * v) / maxOf(la * lb, 0.0001)
                val facing = raw.coerceIn(0.0, 1.0)
                val reach = most * (0.35 + 0.65 * facing)
                val h = maxOf(reach - spread, 0.0) / reach
                nearest - h * h * reach * 0.25
            }
        }
    }

    private fun halfWidth(field: SpotField, u: Double): Double {
        if (field(u, 0.0) >= 0) return 0.0
        var low = 0.0
        var high = SpotRig.RA + 12
        repeat(30) {
            val mid = (low + high) / 2
            if (field(u, mid) < 0) low = mid else high = mid
        }
        return low
    }

    fun neckOf(gap: Double, merge: Double): Double {
        if (gap >= merge) return 0.0
        val field = fieldOf(gap, merge)
        val from = SpotRig.RA - 8
        val to = SpotRig.RA + gap + 8
        var best = Double.POSITIVE_INFINITY
        var at = (from + to) / 2
        for (step in 0..48) {
            val u = from + (to - from) * step / 48
            val width = halfWidth(field, u)
            if (width > 0 && width < best) {
                best = width
                at = u
            }
        }
        val golden = (sqrt(5.0) - 1) / 2
        var low = maxOf(from, at - (to - from) / 48)
        var high = minOf(to, at + (to - from) / 48)
        repeat(24) {
            val c = high - golden * (high - low)
            val d = low + golden * (high - low)
            if (halfWidth(field, c) < halfWidth(field, d)) high = d else low = c
        }
        return 2 * halfWidth(field, (low + high) / 2)
    }

    fun mergeAtX(x: Double) =
        ((x / SpotRig.WIDTH - 0.16) / (0.84 - 0.16)).coerceIn(0.0, 1.0) * SpotRig.MERGE_MOST

    fun roundMerge(merge: Double) = floor(merge + 0.5).coerceIn(0.0, SpotRig.MERGE_MOST).toInt()

    fun listText(values: List<Any>): String =
        if (values.size < 2) values.joinToString("")
        else "${values.dropLast(1).joinToString(", ")} and ${values.last()}"

    fun readoutOf(merge: Double): String {
        val shown = roundMerge(merge)
        return "merge $shown px · ${joinedAt(shown.toDouble()).size} of ${SpotRig.ROWS} joined"
    }

    fun joinedListOf(merge: Double): String {
        val joined = joinedAt(roundMerge(merge).toDouble())
        return when (joined.size) {
            0 -> "none of the six"
            SpotRig.ROWS -> "all six"
            else -> "${listText(joined.map { it.toInt() })} px"
        }
    }

    fun bladesOutOf(merge: Double): String =
        when (val out = joinedAt(roundMerge(merge).toDouble()).size) {
            0 -> "no blade is out"
            SpotRig.ROWS -> "all six blades are out"
            1 -> "one blade is out"
            else -> "${listOf("", "", "two", "three", "four", "five")[out]} blades are out"
        }

    fun spokenOf(merge: Double): String {
        val shown = roundMerge(merge)
        val joined = joinedAt(shown.toDouble()).map { it.toInt() }
        val apart = SpotRig.GAPS.filter { it >= shown }.map { it.toInt() }
        val head = "merge $shown px: ${joined.size} of ${SpotRig.ROWS} pairs joined"
        return when {
            joined.isEmpty() -> "$head, all six apart"
            apart.isEmpty() -> "$head, every gap from 4 to 44 px"
            else ->
                "$head, gaps ${listText(joined)} px; ${listText(apart)} px ${if (apart.size > 1) "stay" else "stays"} apart"
        }
    }
}
