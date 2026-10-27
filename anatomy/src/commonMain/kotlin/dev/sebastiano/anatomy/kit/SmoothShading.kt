// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos

/*
 * The tone arcs of smooth shading. On a ring of a surface of revolution with normal `(nr, na)` in
 * the meridian plane, the light score is `nr·flat·cos(angle − phi) + na·a`, so each tone threshold
 * cuts the ring at two angles solved exactly. The visible part of the ring is cut the same way.
 */

/** A direction (the view or the light) seen from a frame: along the axis, across it, and where. */
internal class Seen(val a: Double, val flat: Double, val phi: Double) {
    companion object {
        fun of(frame: Frame, direction: Point3) =
            Seen(
                dot3(frame.a, direction),
                jsHypot(dot3(frame.u, direction), dot3(frame.v, direction)),
                atan2(dot3(frame.v, direction), dot3(frame.u, direction)),
            )
    }
}

/** An arc of a ring in one [tone], from angle [from] to [to]. */
internal class ToneArc(val tone: Int, var from: Double, var to: Double) {
    fun copy(): ToneArc = ToneArc(tone, from, to)
}

internal fun wrapAngle(angle: Double): Double = ((angle % TAU) + TAU) % TAU

/**
 * The visible arcs of a ring by tone, for a shading normal `(nr, na)` and the drawn facet's normal
 * `(gr, ga)`, which decides visibility so the tones always reach the outline.
 */
internal fun labelledArcs(
    nr: Double,
    na: Double,
    view: Seen,
    light: Seen,
    gr: Double = nr,
    ga: Double = na,
): List<ToneArc> {
    val cuts = ArrayList<Double>()
    if (abs(gr) > 1e-9) {
        val visibleCut = -(ga * view.a) / (gr * view.flat)
        if (visibleCut > -1 && visibleCut < 1) {
            val d = acos(visibleCut)
            cuts.add(view.phi - d)
            cuts.add(view.phi + d)
        }
    }
    if (abs(nr) > 1e-9) {
        for (threshold in BANDS) {
            val c = (threshold - na * light.a) / (nr * light.flat)
            if (c > -1 && c < 1) {
                val d = acos(c)
                cuts.add(light.phi - d)
                cuts.add(light.phi + d)
            }
        }
    }
    val sorted = cuts.map { jsRound(wrapAngle(it) * 1e9) / 1e9 }.distinct().sorted()
    fun label(angle: Double): Int {
        val facing = gr * view.flat * cos(angle - view.phi) + ga * view.a
        if (facing <= 1e-9) return -1
        return bandOf(nr * light.flat * cos(angle - light.phi) + na * light.a)
    }
    if (sorted.isEmpty()) {
        val tone = label(0.0)
        return if (tone < 0) emptyList() else listOf(ToneArc(tone, 0.0, TAU))
    }
    val arcs = sorted.mapIndexed { index, from ->
        val to = if (index + 1 < sorted.size) sorted[index + 1] else sorted[0] + TAU
        ToneArc(label((from + to) / 2), from, to)
    }
    return mergeArcs(arcs).filter { it.tone >= 0 }
}

/** Joins neighbouring arcs of one tone, across the wrap too. */
private fun mergeArcs(arcs: List<ToneArc>): List<ToneArc> {
    val merged = ArrayList<ToneArc>()
    for (arc in arcs) {
        val last = merged.lastOrNull()
        if (last != null && last.tone == arc.tone && abs(last.to - arc.from) < 1e-9)
            last.to = arc.to
        else merged.add(arc.copy())
    }
    if (
        merged.size > 1 &&
            merged[0].tone == merged.last().tone &&
            abs(merged.last().to - TAU - merged[0].from) < 1e-9
    ) {
        val last = merged.removeAt(merged.size - 1)
        merged[0].from = last.from - TAU
    }
    return merged
}

/** The arcs of one ring of a meridian segment, grouped by tone. */
internal class Level(val i: Int, val u: Double, val groups: Map<Int, List<ToneArc>>) {
    fun count(tone: Int): Int = groups[tone]?.size ?: 0

    /** Whether [other] has the same tones with the same number of arcs each. */
    fun same(other: Level): Boolean =
        groups.size == other.groups.size &&
            groups.all { (tone, list) -> other.count(tone) == list.size }
}

/** [arc] moved by whole turns so its centre is within half a turn of [reference]'s. */
internal fun align(reference: ToneArc, arc: ToneArc): ToneArc {
    val out = arc.copy()
    val centre = (reference.from + reference.to) / 2
    while ((out.from + out.to) / 2 - centre > PI) {
        out.from -= TAU
        out.to -= TAU
    }
    while (centre - (out.from + out.to) / 2 > PI) {
        out.from += TAU
        out.to += TAU
    }
    return out
}
