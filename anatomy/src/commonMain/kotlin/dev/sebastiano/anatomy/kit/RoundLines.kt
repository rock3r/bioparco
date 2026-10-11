// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/*
 * Lines and dots on round surfaces: seams, rings, ribs, spokes and bolt circles. They draw only
 * what faces the viewer. `slope` is `dr/ds` of the surface at that ring, so a line on a cone or a
 * bell hides where the real surface turns away.
 */

/** Which end rings of a span get a seam arc: none, both, or the first or last one only. */
enum class Seams {
    None,
    Both,
    First,
    Last,
}

/**
 * The visible arc of a ring on the surface: seams, bands, weld lines, a lip. Each end that stops at
 * the limb is shortened by [trim] px so its round cap stays inside the outline stroke. [inward]
 * draws the arc you see on an inner surface; [least] `-1.0` gives the whole ring.
 */
fun arcOf(
    frame: Frame,
    s: Double,
    r: Double,
    projection: Projection,
    slope: Double = 0.0,
    steps: Int = 0,
    least: Double = 0.02,
    inward: Boolean = false,
    from: Double = 0.0,
    to: Double = TAU,
    trim: Double = 0.3,
): IsoPath {
    val full = abs(to - from - TAU) < 1e-9
    fun at(t: Double) = iso(pointOf(frame, s, r, t), projection)
    fun speed(t: Double): Double {
        val a = at(t - 1e-3)
        val b = at(t + 1e-3)
        return jsHypot(b.x - a.x, b.y - a.y) / 2e-3
    }
    fun count(span: Double): Int {
        val base = if (steps != 0) steps else stepsFor(r, 24, 112)
        return maxOf(2.0, ceil(base * span / TAU), ceil(span * r * scaleOf(projection) / 1.5))
            .toInt()
    }
    val out = ArrayList<IsoPath>()
    for ((a, b) in visibleArcs(frame, projection, slope, least, inward)) {
        if (b - a >= TAU - 1e-9 && full) {
            val n = count(TAU)
            out.add(pathOf(List(n) { j -> at(from + j.toDouble() / n * TAU) }, true))
            continue
        }
        val pieces = ArrayList<ArcPiece>()
        if (full) pieces.add(ArcPiece(a, b, limb0 = true, limb1 = true))
        else {
            for (turn in -2..2) {
                val lo = max(a + turn * TAU, from)
                val hi = min(b + turn * TAU, to)
                if (hi - lo > 1e-6) pieces.add(ArcPiece(lo, hi, lo > from + 1e-9, hi < to - 1e-9))
            }
        }
        for (piece in pieces) {
            val span = piece.hi - piece.lo
            val cut0 =
                if (piece.limb0 && trim > 0) min(span * 0.25, trim / speed(piece.lo).orOne())
                else 0.0
            val cut1 =
                if (piece.limb1 && trim > 0) min(span * 0.25, trim / speed(piece.hi).orOne())
                else 0.0
            val t0 = piece.lo + cut0
            val t1 = piece.hi - cut1
            val n = count(t1 - t0)
            out.add(pathOf(List(n + 1) { j -> at(t0 + (t1 - t0) * j / n) }))
        }
    }
    return IsoPath.join(out)
}

private class ArcPiece(val lo: Double, val hi: Double, val limb0: Boolean, val limb1: Boolean)

/** The angle ranges of a ring that face the viewer, solved exactly at the limbs. */
private fun visibleArcs(
    frame: Frame,
    projection: Projection,
    slope: Double,
    least: Double,
    inward: Boolean,
): List<Pair<Double, Double>> {
    val view = viewOf(projection)
    val aV = dot3(frame.a, view)
    val uV = dot3(frame.u, view)
    val vV = dot3(frame.v, view)
    val flat = jsHypot(uV, vV)
    val phi = atan2(vV, uV)
    val norm = jsHypot(1.0, slope)
    val whole = listOf(phi to phi + TAU)
    return when {
        flat < 1e-9 -> {
            val facing = if (inward) slope * aV / norm else -slope * aV / norm
            if (facing > least) whole else emptyList()
        }
        inward -> {
            val c = (slope * aV - least * norm) / flat
            when {
                c <= -1 -> emptyList()
                c >= 1 -> whole
                else -> listOf(phi + acos(c) to phi + TAU - acos(c))
            }
        }
        else -> {
            val c = (least * norm + slope * aV) / flat
            when {
                c >= 1 -> emptyList()
                c <= -1 -> whole
                else -> listOf(phi - acos(c) to phi + acos(c))
            }
        }
    }
}

/** A full closed circle: a face ring, a bore, a void you fill. */
fun circleOf(frame: Frame, s: Double, r: Double, projection: Projection, steps: Int = 0): IsoPath {
    val count = if (steps != 0) steps else stepsFor(r, 24, 112)
    return pathOf(
        List(count) { j -> iso(pointOf(frame, s, r, j.toDouble() / count * TAU), projection) },
        true,
    )
}

/** Radial lines on a face, for vanes and turbine blades; [sweep] bends each one. */
fun spokesOf(
    frame: Frame,
    s: Double,
    r0: Double,
    r1: Double,
    count: Int,
    projection: Projection,
    phase: Double = 0.0,
    sweep: Double = 0.0,
    from: Double = 0.0,
    to: Double = TAU,
): IsoPath =
    IsoPath.join(
        List(count) { index ->
            val t = phase + from + index.toDouble() / count * (to - from)
            pathOf(
                listOf(
                    iso(pointOf(frame, s, r0, t), projection),
                    iso(pointOf(frame, s, r1, t + sweep), projection),
                )
            )
        }
    )

/** The smoothstep both fades use: 0 at or below `lo`, 1 at or above `hi`. */
fun fadeOf(value: Double, range: Pair<Double, Double>): Double {
    val (lo, hi) = range
    val span = (hi - lo).let { if (it == 0.0 || it.isNaN()) 1e-9 else it }
    val t = max(0.0, min(1.0, (value - lo) / span))
    return t * t * (3 - 2 * t)
}

/** One rib and how much it faces the viewer. */
private class Rib(val d: IsoPath, val facing: Double)

private fun ribs(
    frame: Frame,
    s0: Double,
    s1: Double,
    r: Double,
    count: Int,
    projection: Projection,
    phase: Double,
    twist: Double,
    r1: Double,
): List<Rib> {
    val slope = (r1 - r) / (s1 - s0).orOne()
    return List(count) { index ->
        val t = phase + index.toDouble() / count * TAU
        val d =
            pathOf(
                listOf(
                    iso(pointOf(frame, s0, r, t), projection),
                    iso(pointOf(frame, s1, r1, t + twist), projection),
                )
            )
        Rib(d, facingOf(frame, t + twist / 2, projection, slope))
    }
}

/** The seam arcs at the ends of a rib row that [seams] asks for, dropping hidden ones. */
private fun ribSeams(
    frame: Frame,
    s0: Double,
    s1: Double,
    r: Double,
    r1: Double,
    projection: Projection,
    seams: Seams,
): List<IsoPath> {
    if (seams == Seams.None) return emptyList()
    val slope = (r1 - r) / (s1 - s0).orOne()
    return listOfNotNull(
            if (seams == Seams.Last) null else arcOf(frame, s0, r, projection, slope = slope),
            if (seams == Seams.First) null else arcOf(frame, s1, r1, projection, slope = slope),
        )
        .filter { !it.isEmpty }
}

/**
 * Visible axial lines: a knurl, ribs or fins along any axis. [twist] makes them helical and [r1]
 * runs them along a cone. Ribs facing the viewer less than [least] are dropped. [seams] adds the
 * seam arcs at [s0] and [s1], so a rib row and the seams it ends on always share one `s`.
 */
fun ribsOf(
    frame: Frame,
    s0: Double,
    s1: Double,
    r: Double,
    count: Int,
    projection: Projection,
    phase: Double = 0.0,
    twist: Double = 0.0,
    least: Double = 0.12,
    r1: Double = r,
    seams: Seams = Seams.None,
): IsoPath {
    val lines = ribs(frame, s0, s1, r, count, projection, phase, twist, r1)
    return IsoPath.join(
        lines.filter { it.facing >= least }.map { it.d } +
            ribSeams(frame, s0, s1, r, r1, projection, seams)
    )
}

/**
 * [ribsOf] fading out toward the turn instead of stopping at a cut-off: each rib's opacity is
 * [fadeOf] how much it faces the viewer, over [fade]. Seams come at full opacity. Draw them with
 * `ArtBuilder.faded`.
 */
fun ribsOf(
    frame: Frame,
    s0: Double,
    s1: Double,
    r: Double,
    count: Int,
    projection: Projection,
    fade: Pair<Double, Double>,
    phase: Double = 0.0,
    twist: Double = 0.0,
    r1: Double = r,
    seams: Seams = Seams.None,
): List<Pair<IsoPath, Double>> {
    val lines =
        ribs(frame, s0, s1, r, count, projection, phase, twist, r1).mapNotNull { rib ->
            val alpha = fadeOf(rib.facing, fade)
            if (alpha > 0.02) rib.d to alpha else null
        }
    return lines + ribSeams(frame, s0, s1, r, r1, projection, seams).map { it to 1.0 }
}

/**
 * Screen points of a bolt circle on the visible side. Use [all] for a face you see head-on. A
 * [least] well above 0 stops the circle in the middle of the surface: fade it instead.
 */
fun dotsOf(
    frame: Frame,
    s: Double,
    r: Double,
    count: Int,
    projection: Projection,
    phase: Double = 0.0,
    slope: Double = 0.0,
    least: Double = 0.08,
    all: Boolean = false,
): List<Flat> {
    return (0 until count).mapNotNull { index ->
        val t = phase + index.toDouble() / count * TAU
        if (!all && facingOf(frame, t, projection, slope) < least) null
        else iso(pointOf(frame, s, r, t), projection)
    }
}

/** Dots with an opacity each, for `ArtBuilder.dots(points, alphas = alphas)`. */
class FadedDots(val points: List<Flat>, val alphas: List<Double>)

/** [dotsOf] fading out toward the turn: each dot's opacity is [fadeOf] its facing over [fade]. */
fun dotsOf(
    frame: Frame,
    s: Double,
    r: Double,
    count: Int,
    projection: Projection,
    fade: Pair<Double, Double>,
    phase: Double = 0.0,
    slope: Double = 0.0,
    all: Boolean = false,
): FadedDots {
    val points = ArrayList<Flat>()
    val alphas = ArrayList<Double>()
    for (index in 0 until count) {
        val t = phase + index.toDouble() / count * TAU
        val alpha = if (all) 1.0 else fadeOf(facingOf(frame, t, projection, slope), fade)
        if (alpha <= 0.02) continue
        points.add(iso(pointOf(frame, s, r, t), projection))
        alphas.add(alpha)
    }
    return FadedDots(points, alphas)
}
