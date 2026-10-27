// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import kotlin.math.max
import kotlin.math.min

/*
 * One curved body cut into depth-sortable bands that share one normal field, one surface and one
 * facet count, so the tone curves cross every joint without a step, and its outline drawn as one
 * continuous curve instead of a chord per band.
 */

/**
 * One band of [bandsOf], from [s0] to [s1]. [paths] is ready for `solid` with its outline cleared,
 * [outline] is the band's own chord outline, [seam] the visible arc where it meets the next band
 * (draw it as the solid's `inner`), and [profile] the meridian it was turned from.
 */
class Band(
    val s0: Double,
    val s1: Double,
    val profile: List<Knot>,
    val paths: SolidPaths,
    val outline: IsoPath,
    val seam: IsoPath,
)

/** A body's side edges over a span, as one path [d], and their 3D [points]. */
class Sides(val d: IsoPath, val points: List<List<Point3>>)

/** The result of [bandsOf]: the [bands], and the body's curve and facet count. */
class Bands(
    val bands: List<Band>,
    val radius: (Double) -> Double,
    val slope: (Double) -> Double,
    val steps: Int,
    val s0: Double,
    val s1: Double,
    private val frame: Frame,
    private val projection: Projection,
    private val samples: Int,
) {
    /**
     * The body's two analytic side edges from [from] to [to] as one path, with the visible rim arcs
     * at [ends], plus their 3D points. Draw it as one item over the bands.
     */
    fun sidesOf(
        from: Double = s0,
        to: Double = s1,
        ends: Seams = Seams.None,
        count: Int = samples,
    ): Sides {
        val start = max(s0, from)
        val end = min(s1, to)
        val dense =
            List(count + 1) { index ->
                val s = start + (end - start) * index / count
                Knot(s, radius(s), true)
            }
        val points = silhouettePoints(dense, frame, projection, count, slope, radius)
        fun rim(s: Double) = arcOf(frame, s, radius(s), projection, slope = slope(s))
        val rims =
            when (ends) {
                Seams.Both -> listOf(start, end)
                Seams.First -> listOf(start)
                Seams.Last -> listOf(end)
                Seams.None -> emptyList()
            }
        val sides = IsoPath.join(points.map { list -> pathOf(list.map { iso(it, projection) }) })
        return Sides(sides + IsoPath.join(rims.map(::rim)), points)
    }
}

/**
 * One curved body ([profile], `s` rising) cut at the `s` values in [cuts] into bands. Pass the
 * analytic [slope] and [radius], or neither: then [profileCurve] makes them from the profile.
 */
fun bandsOf(
    profile: List<Knot>,
    cuts: List<Double>,
    frame: Frame,
    projection: Projection,
    slope: ((Double) -> Double)? = null,
    radius: ((Double) -> Double)? = null,
    steps: Int? = null,
    rows: Int = 6,
    rims: Rims = Rims.None,
    fill: LatheFill = LatheFill.Auto,
    samples: Int = 72,
): Bands {
    val curve = profileCurve(profile, slope, radius)
    val s0 = curve.s0
    val s1 = curve.s1
    val inner = cuts.distinct().filter { it > s0 + 1e-6 && it < s1 - 1e-6 }.sorted()
    val stops = listOf(s0) + inner + s1
    val most = max(profile.maxOf { it.r }, stops.maxOf { curve.radius(it) })
    val n = steps ?: stepsFor(most, 32, 160, scaleOf(projection))
    fun knot(s: Double, soft: Boolean = false) = Knot(s, curve.radius(s), soft)
    fun meridianOf(a: Double, b: Double) =
        listOf(knot(a)) +
            profile.filter { it.s > a + 1e-9 && it.s < b - 1e-9 }.map { knot(it.s, it.smooth) } +
            knot(b)
    val shading = Smooth(rows = rows, slope = curve.slope, radius = curve.radius)
    val bands =
        (0 until stops.size - 1).map { index ->
            val a = stops[index]
            val b = stops[index + 1]
            val meridian = meridianOf(a, b)
            val paths = lathe(meridian, frame, projection, n, fill, rims = rims, smooth = shading)
            val seam =
                if (index < stops.size - 2) {
                    arcOf(frame, b, curve.radius(b), projection, slope = curve.slope(b))
                } else IsoPath.Empty
            Band(a, b, meridian, paths.withOutline(IsoPath.Empty), paths.outline, seam)
        }
    return Bands(bands, curve.radius, curve.slope, n, s0, s1, frame, projection, samples)
}

private fun SolidPaths.withOutline(outline: IsoPath) =
    SolidPaths(fill, outline, crease, top, shades, bevel, seamless)
