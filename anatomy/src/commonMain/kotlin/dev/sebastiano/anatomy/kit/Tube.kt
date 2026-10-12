// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import kotlin.math.sign

/*
 * Pipes, tubes and cables: `kit/tube.mjs` of the original. A 3D route is cut into depth-sortable
 * chunks, each drawn as a 2D ribbon with a paper body, a shine band toward the light, a shade band
 * away from it and two edge lines, ending on elliptical caps where the route ends or a gap starts.
 */

/** How far neighbouring chunks overlap, and how much further their stripes run. */
private const val LEAD = 0.5

/**
 * One chunk of a tube, drawn with `ArtBuilder.tube`. [mid] is its centre in world space, to
 * depth-sort it. [body] is the fill (the ribbon and its end caps), [shine] and [shade] the stripes,
 * drawn only when [wide], [edges] the two side lines and the visible cap rims. [rings] are the
 * texture's segments with their opacity, stroked in [ringTone]. [points] and [radii] are the
 * chunk's 3D centreline and radii; [ends] says which ends are real ends rather than joints with a
 * neighbour; [from] and [to] are its arc lengths along the route.
 */
class TubePiece(
    val rings: List<Pair<IsoPath, Double>>,
    val ringTone: Tone,
    val mid: Point3,
    val body: IsoPath,
    val shine: IsoPath,
    val shade: IsoPath,
    val edges: IsoPath,
    val wide: Boolean,
    val points: List<Point3>,
    val radii: List<Double>,
    val ends: Pair<Boolean, Boolean>,
    val from: Double,
    val to: Double,
)

/** [tubePieces] with one radius all along the route. */
fun tubePieces(
    points3: List<Point3>,
    radius: Double,
    projection: Projection,
    maxLength: Double = 30.0,
    spacing: Double = 2.2,
    closed: Boolean = false,
    breaks: List<Double> = emptyList(),
    gaps: List<Pair<Double, Double>> = emptyList(),
    caps: Pair<Boolean, Boolean> = true to true,
    stripes: Boolean? = null,
    rings: Rings? = null,
): List<TubePiece> =
    tubePieces(
        points3,
        { radius },
        projection,
        maxLength,
        spacing,
        closed,
        breaks,
        gaps,
        caps,
        stripes,
        rings,
    )

/**
 * Splits a 3D route into chunks of at most [maxLength] world units, each drawn as a ribbon.
 * [radius] is a function of `t`, 0 to 1 along the route, for tapers and volutes. [breaks] are arc
 * lengths where a chunk must end; [gaps] are `from to to` spans not drawn because a collar, clamp
 * or fitting covers them, with an elliptical cap on each side. The route's own ends are capped
 * unless [caps] says otherwise (`false to true` for a branch whose start hides in a tee). [stripes]
 * null decides the shine and shade from the route's widest point (over 1.3 px); true or false
 * forces them. A [closed] route with no gaps wraps its first and last chunks round each other.
 * [rings] adds a texture that follows the cross-section.
 */
fun tubePieces(
    points3: List<Point3>,
    radius: (Double) -> Double,
    projection: Projection,
    maxLength: Double = 30.0,
    spacing: Double = 2.2,
    closed: Boolean = false,
    breaks: List<Double> = emptyList(),
    gaps: List<Pair<Double, Double>> = emptyList(),
    caps: Pair<Boolean, Boolean> = true to true,
    stripes: Boolean? = null,
    rings: Rings? = null,
): List<TubePiece> {
    val route = TubeRoute(points3, spacing, radius, projection, closed && gaps.isEmpty())
    val wide = stripes ?: ((0..32).maxOf { radius(it / 32.0) } * route.k > 1.3)
    val style = TubeStyle(caps, wide, rings)
    return spansOf(route.total, gaps, breaks, maxLength, closed).mapNotNull {
        route.pieceOf(it, style)
    }
}

/** What every chunk of one tube shares. */
private class TubeStyle(val caps: Pair<Boolean, Boolean>, val wide: Boolean, val rings: Rings?)

/**
 * One end of a chunk that is capped: the half ellipse the stripes run onto, and the frame there.
 */
private class Tip(val half: List<Flat>, val frame: TubeFrame)

/**
 * Which ends of a chunk are joints with a neighbour, and how far past them its body (by [LEAD]) and
 * its stripes and rings (by twice that) run.
 */
private class Joints(private val span: TubeSpan, route: TubeRoute) {
    val start = span.open0 && (span.from > 0 || route.looped)
    val end = span.open1 && (span.to < route.total || route.looped)

    fun from(by: Double) = if (start) span.from - by else span.from

    fun to(by: Double) = if (end) span.to + by else span.to
}

/** Which ends of [span] are real ends (the route's own, or a gap's face) rather than joints. */
private fun TubeRoute.endsOf(span: TubeSpan): Pair<Boolean, Boolean> =
    (!span.open0 && !looped && (span.from <= 1e-6 || span.gap0)) to
        (!span.open1 && !looped && (span.to >= total - 1e-6 || span.gap1))

private fun TubeRoute.pieceOf(span: TubeSpan, style: TubeStyle): TubePiece? {
    val joints = Joints(span, this)
    val body = partOf(joints.from(LEAD), joints.to(LEAD))
    if (body.points.size < 2) return null
    val stripe =
        if (joints.start || joints.end) partOf(joints.from(2 * LEAD), joints.to(2 * LEAD)) else body
    val sides = Ribbon(body.frames)
    val bands = if (stripe === body) sides else Ribbon(stripe.frames)
    val outline = sides.left + sides.right.reversed()
    val ends = endsOf(span)
    val capped =
        listOf(
            ends.first && (span.gap0 || style.caps.first),
            ends.second && (span.gap1 || style.caps.second),
        )
    val caps = capsOf(body, capped, sign(areaOf(outline)))
    val rings = style.rings
    val ringSpan = RingSpan(joints.from(2 * LEAD), joints.to(2 * LEAD), !joints.start, !joints.end)
    return TubePiece(
        rings = if (rings != null) ringsOf(this, ringSpan, rings) else emptyList(),
        ringTone = rings?.tone ?: Tone.Lo,
        mid = pointAlong(dense, (span.from + span.to) / 2),
        body = pathOf(outline, true) + caps.body,
        shine = bandOf(bands.shineA, bands.shineB, caps.tips) { it.shine },
        shade = bandOf(bands.shadeA, bands.shadeB, caps.tips) { it.shade },
        edges = pathOf(sides.left) + pathOf(sides.right) + caps.edges,
        wide = style.wide,
        points = body.points,
        radii = body.widths.map { it / k },
        ends = ends,
        from = span.from,
        to = span.to,
    )
}

/** What a chunk's end caps add: to its body, to its edges, and the tips its stripes run onto. */
private class Caps(val body: IsoPath, val edges: IsoPath, val tips: List<Tip?>)

private fun TubeRoute.capsOf(part: TubePart, capped: List<Boolean>, sign: Double): Caps {
    var body = IsoPath.Empty
    var edges = IsoPath.Empty
    val tips = arrayOfNulls<Tip>(2)
    val points = part.points
    val flat = part.flat
    for (side in 0..1) {
        if (!capped[side]) continue
        val at = if (side == 1) points.size - 1 else 0
        val other = if (side == 1) points.size - 2 else 1
        val tangent =
            unit3(
                sub3(
                    points[if (side == 1) at else other],
                    points[if (side == 1) other else at],
                )
            )
        val outward = if (side == 1) tangent else mul3(tangent, -1.0)
        val inward = Flat(flat[other].x - flat[at].x, flat[other].y - flat[at].y)
        val cap = capOf(points[at], tangent, part.widths[at] / k, inward, projection)
        val oriented = if (sign(areaOf(cap.ring)) == sign) cap.ring else cap.ring.reversed()
        body += pathOf(oriented, true)
        edges += pathOf(cap.arc)
        val open = dot3(outward, viewOf(projection)) > 0
        if (open) edges += pathOf(cap.inner)
        tips[side] = Tip(if (open) cap.inner else cap.arc, part.frames[at])
    }
    return Caps(body, edges, tips.toList())
}

/**
 * A stripe between edges [a] and [b], running onto the end caps in [tips]: along `a`, round the end
 * tip, back along `b` and round the start tip.
 */
private fun bandOf(
    a: List<Flat>,
    b: List<Flat>,
    tips: List<Tip?>,
    offsets: (TubeFrame) -> Pair<Double, Double>,
): IsoPath {
    val head =
        tips[1]?.let {
            val (from, to) = offsets(it.frame)
            stripeEnd(it.half, it.frame, from, to)
        } ?: emptyList()
    val tail =
        tips[0]?.let {
            val (from, to) = offsets(it.frame)
            stripeEnd(it.half, it.frame, to, from)
        } ?: emptyList()
    return pathOf(a + head + b.reversed() + tail, true)
}
