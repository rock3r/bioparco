// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * The rings of `kit/tube.mjs`: a texture that follows a pipe's cross-section (braid, corrugation,
 * tape wrap, armour), placed by 3D arc length along the whole route so it runs through every joint.
 */

/**
 * A texture on a tube. Stations sit every [pitch] units of arc length from the route's start
 * ([phase] shifts them); each is the visible front arc of the cross-section there, cut into [steps]
 * segments whose opacity is `fadeOf(facing, fade)` (null [fade]: full strength). [twist] (in radii)
 * turns each ring into a helix that advances that far from limb to limb, and [cross] adds the other
 * hand, for a braid. [tone] is the rings' stroke.
 */
data class Rings(
    val pitch: Double = 3.0,
    val twist: Double = 0.0,
    val cross: Boolean = false,
    val fade: Pair<Double, Double>? = 0.1 to 0.55,
    val phase: Double = 0.0,
    val steps: Int = 16,
    val tone: Tone = Tone.Lo,
)

/** The arc lengths a chunk's rings may cover, and whether each end is a real end or gap face. */
internal class RingSpan(val lo: Double, val hi: Double, val real0: Boolean, val real1: Boolean)

/** A point of a ring: where along the route, and how far round the front of the section. */
private class RingAt(val t: Double, val theta: Double)

/** A point on the tube's surface on screen, and how much its normal faces the viewer. */
private class SurfacePoint(val p: Flat, val facing: Double)

/** The original's `ringsOf`: the ring segments over [span], each with its opacity. */
internal fun ringsOf(route: TubeRoute, span: RingSpan, rings: Rings): List<Pair<IsoPath, Double>> {
    val pitch = rings.pitch
    val twist = rings.twist
    val margin = if (twist != 0.0) 0.0 else min(pitch * 0.35, (span.hi - span.lo) / 2)
    val from = max(span.lo, if (span.real0) span.lo + margin else span.lo)
    val to = min(span.hi, if (span.real1) span.hi - margin else span.hi)
    if (to <= from) return emptyList()
    val out = ArrayList<Pair<IsoPath, Double>>()
    val hands =
        when {
            twist == 0.0 -> listOf(0.0)
            rings.cross -> listOf(1.0, -1.0)
            else -> listOf(1.0)
        }
    val radiusAt = route.radiusAt
    val reach = abs(twist) * maxOf(radiusAt(0.0), radiusAt(0.5), radiusAt(1.0))
    val first = ceil((from - reach - rings.phase) / pitch)
    val last = floor((to + reach - rings.phase) / pitch)
    var index = first
    while (index <= last) {
        val station = rings.phase + index * pitch
        for (hand in hands) {
            val samples =
                List(rings.steps + 1) { step ->
                    val share = step.toDouble() / rings.steps
                    val at = max(0.0, min(1.0, station / route.total.orOne()))
                    RingAt(station + hand * twist * radiusAt(at) * (share - 0.5), share * PI)
                }
            for (step in 0 until rings.steps) {
                segmentOf(route, samples[step], samples[step + 1], from, to, rings.fade)
                    ?.let(out::add)
            }
        }
        index += 1
    }
    return out
}

/** One segment of a ring between two samples, clipped to `from..to`, or null if it is hidden. */
private fun segmentOf(
    route: TubeRoute,
    start: RingAt,
    end: RingAt,
    from: Double,
    to: Double,
    fade: Pair<Double, Double>?,
): Pair<IsoPath, Double>? {
    var a = start
    var b = end
    if ((a.t < from && b.t < from) || (a.t > to && b.t > to)) return null
    if (a.t < from) a = clipTo(a, b, from)
    if (b.t < from) b = clipTo(b, a, from)
    if (a.t > to) a = clipTo(a, b, to)
    if (b.t > to) b = clipTo(b, a, to)
    val pa = route.surface(a.t, a.theta) ?: return null
    val pb = route.surface(b.t, b.theta) ?: return null
    val alpha = if (fade != null) fadeOf((pa.facing + pb.facing) / 2, fade) else 1.0
    if (alpha <= 0.02) return null
    return pathOf(listOf(pa.p, pb.p)) to alpha
}

private fun clipTo(x: RingAt, y: RingAt, edge: Double): RingAt {
    val span = (y.t - x.t).let { if (it == 0.0 || it.isNaN()) 1e-9 else it }
    return RingAt(edge, x.theta + ((y.theta - x.theta) * (edge - x.t)) / span)
}

/** The point at [theta] round the front of the cross-section at arc length [t]. */
private fun TubeRoute.surface(t: Double, theta: Double): SurfacePoint? {
    val view = viewOf(projection)
    val at = max(0.0, min(total, t))
    val c = pointAlong(dense, at)
    val tangent = tangentAlong(dense, at)
    var n1 = cross3(tangent, view)
    if (len3(n1) < 1e-6) return null
    n1 = unit3(n1)
    var n2 = unit3(cross3(tangent, n1))
    if (dot3(n2, view) < 0) n2 = mul3(n2, -1.0)
    val r = radiusAt(at / total.orOne())
    val normal = add3(mul3(n1, cos(theta)), mul3(n2, sin(theta)))
    return SurfacePoint(iso(add3(c, mul3(normal, r)), projection), dot3(normal, view))
}

/**
 * The original's `ringsSvg` grouping: ring segments joined per opacity level, in steps of 0.05, in
 * the order the levels first appear. Levels that round to 0 are dropped.
 */
fun ringLevels(rings: List<Pair<IsoPath, Double>>): List<Pair<Double, IsoPath>> {
    val groups = LinkedHashMap<Double, IsoPath>()
    for ((d, alpha) in rings) {
        val level = min(1.0, jsRound(alpha * 20) / 20)
        if (level <= 0) continue
        groups[level] = (groups[level] ?: IsoPath.Empty) + d
    }
    return groups.map { (level, d) -> level to d }
}
