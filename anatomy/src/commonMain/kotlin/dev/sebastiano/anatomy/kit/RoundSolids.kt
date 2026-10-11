// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt

/* Turned parts made from a lathe, and the round solids that are not: saddles, prisms and balls. */

/** A short cylinder on the axis: pucks, flanges, bosses, pins, plugs. */
fun disc(
    s0: Double,
    s1: Double,
    r: Double,
    frame: Frame,
    projection: Projection,
    steps: Int = stepsFor(r, 32, 160, scaleOf(projection)),
    fill: LatheFill = LatheFill.Auto,
    hole: Knot? = null,
    bevel: Double = 0.0,
    rims: Rims = Rims.All,
    smooth: Smooth? = null,
    waist: Double = 0.5,
): SolidPaths =
    lathe(
        listOf(Knot(s0, 0.0), Knot(s0, r), Knot(s1, r), Knot(s1, 0.0)),
        frame,
        projection,
        steps,
        fill,
        hole,
        bevel,
        rims,
        smooth,
        waist,
    )

/** An annulus with thickness: a flange or collar around something. */
fun ringBand(
    s0: Double,
    s1: Double,
    rIn: Double,
    rOut: Double,
    frame: Frame,
    projection: Projection,
    steps: Int = stepsFor(rOut, 32, 160, scaleOf(projection)),
    fill: LatheFill = LatheFill.Auto,
    hole: Knot? = null,
    bevel: Double = 0.0,
    rims: Rims = Rims.All,
    smooth: Smooth? = null,
    waist: Double = 0.5,
): SolidPaths =
    lathe(
        listOf(Knot(s0, rIn), Knot(s0, rOut), Knot(s1, rOut), Knot(s1, rIn)),
        frame,
        projection,
        steps,
        fill,
        hole,
        bevel,
        rims,
        smooth,
        waist,
    )

/** A profile closed to the axis at both ends: the usual way to make a turned part. */
fun solidOf(
    profile: List<Knot>,
    frame: Frame,
    projection: Projection,
    steps: Int = stepsFor(profile.maxOf { it.r }, 32, 160, scaleOf(projection)),
    fill: LatheFill = LatheFill.Auto,
    hole: Knot? = null,
    bevel: Double = 0.0,
    rims: Rims = Rims.All,
    smooth: Smooth? = Smooth.auto(profile),
    waist: Double = 0.5,
): SolidPaths {
    val meridian =
        listOf(Knot(profile[0].s, 0.0)) + profile + Knot(profile[profile.size - 1].s, 0.0)
    return lathe(meridian, frame, projection, steps, fill, hole, bevel, rims, smooth, waist)
}

/** An open band with no caps. Prefer [bandsOf], which cuts a whole body at once. */
fun bandOfProfile(
    profile: List<Knot>,
    frame: Frame,
    projection: Projection,
    steps: Int = stepsFor(profile.maxOf { it.r }, 32, 160, scaleOf(projection)),
    fill: LatheFill = LatheFill.Auto,
    hole: Knot? = null,
    bevel: Double = 0.0,
    rims: Rims = Rims.All,
    smooth: Smooth? = Smooth.auto(profile),
    waist: Double = 0.5,
): SolidPaths = lathe(profile, frame, projection, steps, fill, hole, bevel, rims, smooth, waist)

/**
 * Where the generator at [angle] of a cylinder of radius [a] leaves a host cylinder of radius
 * [host] (the original's `R`) perpendicular to it.
 */
fun saddleStart(host: Double, a: Double, angle: Double): Double {
    val c = cos(angle)
    return sqrt(max(0.0, host * host - a * a * (c * c)))
}
