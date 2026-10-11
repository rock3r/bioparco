// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * The general solid of revolution, `lathe` of `kit/lathe.mjs`: a meridian of `Knot(s, r)` turned
 * around any frame's axis, shaded in four tones by the kit's light and bucketed like `extrude`. It
 * returns the same `SolidPaths` as `slabOf`, so `ArtBuilder.solid` paints it.
 */

/** Which end rings keep their outline: the original's `rims`. */
enum class Rims {
    All,
    None,
    First,
    Last,
    Ends,
}

/** How a lathe is filled and outlined: the original's `fill`. */
enum class LatheFill {
    /** The hull of the rings, unless the true silhouette lies more than `waist` px inside it. */
    Auto,

    /** The silhouette on both sides, joined by the visible end arcs. */
    Surface,

    /** The hull of the rings. */
    Hull,

    /** Only the visible facets, and no outline: a concave or open shape seen from inside. */
    Facets,
}

/**
 * The general solid of revolution. [hole] (`Knot(s, r)`) cuts a round hole into the fill and
 * outline, for the end of a tube. [bevel] adds a highlight inset from a closed cap (a meridian end
 * at `r = 0`). [smooth] defaults to on when any knot is smooth; pass null to opt out. See `kit.md`.
 */
fun lathe(
    meridian: List<Knot>,
    frame: Frame,
    projection: Projection,
    steps: Int = stepsFor(meridian.maxOf { it.r }, 32, 160, scaleOf(projection)),
    fill: LatheFill = LatheFill.Auto,
    hole: Knot? = null,
    bevel: Double = 0.0,
    rims: Rims = Rims.All,
    smooth: Smooth? = Smooth.auto(meridian),
    waist: Double = 0.5,
): SolidPaths {
    val turning = Turning(meridian, frame, projection, steps)
    val buckets = List(SHADES + 1) { ArrayList<IsoPath>() }
    val all = ArrayList<IsoPath>()
    val smoothed = smooth?.let { SmoothRows(meridian, frame, projection, it, steps).build() }
    smoothed?.forEachIndexed { tone, list ->
        buckets[tone].addAll(list)
        all.addAll(list)
    }
    turning.facets(smoothed != null, buckets, all)
    val outline =
        latheOutline(meridian, frame, projection, steps, fill, hole, rims, smooth, waist, all)
    val bevelPath = turning.bevel(bevel)
    return SolidPaths(
        fill = outline.fill,
        outline = outline.outline,
        crease = turning.creases(),
        top = IsoPath.join(buckets[SHADES]),
        shades = (0 until SHADES).map { IsoPath.join(buckets[it]) },
        bevel = if (bevelPath.isEmpty) null else bevelPath,
        seamless = smoothed != null,
    )
}

/** The facets, creases and bevel of one lathe. */
private class Turning(
    private val meridian: List<Knot>,
    private val frame: Frame,
    private val projection: Projection,
    private val n: Int,
) {
    private val view = viewOf(projection)
    private val light = lightOf(projection)
    private val aV = dot3(frame.a, view)
    private val uV = dot3(frame.u, view)
    private val vV = dot3(frame.v, view)
    private val aL = dot3(frame.a, light)
    private val uL = dot3(frame.u, light)
    private val vL = dot3(frame.v, light)
    private val m = meridian.size
    private val rings = arrayOfNulls<List<Flat>>(m)
    private val phi = atan2(vV, uV)
    private val flatV = jsHypot(uV, vV)

    private fun ring(i: Int): List<Flat> =
        rings[i]
            ?: List(n) { j ->
                    iso(
                        pointOf(frame, meridian[i].s, meridian[i].r, j.toDouble() / n * TAU),
                        projection,
                    )
                }
                .also { rings[i] = it }

    /** Each facet of row [i]: its tone if it faces the viewer, else null. */
    private fun keysOf(i: Int): List<Int?> {
        val ds = meridian[i + 1].s - meridian[i].s
        val dr = meridian[i + 1].r - meridian[i].r
        val length = jsHypot(ds, dr)
        return List(n) { j ->
            if (length < 1e-9) return@List null
            val t = (j + 0.5) / n * TAU
            val c = cos(t)
            val s = sin(t)
            val facing = ds * (c * uV + s * vV) - dr * aV
            val lit = (ds * (c * uL + s * vL) - dr * aL) / length
            if (facing > 1e-6 * length) bandOf(lit) else null
        }
    }

    /** The facet rows, one polygon per run of one tone; with [smoothed], only the flat caps. */
    fun facets(smoothed: Boolean, buckets: List<MutableList<IsoPath>>, all: MutableList<IsoPath>) {
        for (i in 0 until m - 1) {
            if (smoothed && abs(meridian[i + 1].s - meridian[i].s) > 1e-9) continue
            for (group in groupsOf(keysOf(i))) {
                val a = group.run.map { ring(i)[it] }
                val b = group.run.map { ring(i + 1)[it] }.reversed()
                val d = pathOf(windOf(a + b), true)
                buckets[group.key].add(d)
                all.add(d)
            }
        }
    }

    /** The range of `cos(angle − phi)` over which segment [i] faces the viewer, or null. */
    private fun rangeOf(i: Int): Pair<Double, Double>? {
        val ds = meridian[i + 1].s - meridian[i].s
        val dr = meridian[i + 1].r - meridian[i].r
        if (jsHypot(ds, dr) < 1e-9) return null
        if (abs(ds) < 1e-9 || flatV < 1e-9) return if (-dr * aV > 1e-9) -1.0 to 1.0 else null
        val c = dr * aV / (ds * flatV)
        return if (ds > 0) max(-1.0, c) to 1.0 else -1.0 to min(1.0, c)
    }

    /** The arcs of a ring where both of its segments face the viewer. */
    private fun arcsOf(first: Pair<Double, Double>?, second: Pair<Double, Double>?) =
        if (first == null || second == null) emptyList()
        else {
            val lo = max(first.first, second.first)
            val hi = min(first.second, second.second)
            val near = acos(min(1.0, hi))
            val far = acos(max(-1.0, lo))
            when {
                lo >= hi - 1e-9 -> emptyList()
                hi >= 1 && lo <= -1 -> listOf(phi to phi + TAU)
                hi >= 1 -> listOf(phi - far to phi + far)
                lo <= -1 -> listOf(phi + near to phi + TAU - near)
                else -> listOf(phi + near to phi + far, phi - far to phi - near)
            }
        }

    private fun along(s: Double, r: Double, arc: Pair<Double, Double>): List<Flat> {
        val (from, to) = arc
        val count = max(2, ceil((to - from) * max(r, 1e-6) * scaleOf(projection) / 1.5).toInt())
        return List(count + 1) { j ->
            iso(pointOf(frame, s, r, from + (to - from) * j / count), projection)
        }
    }

    /** The visible arcs of every hard ring between two segments. */
    fun creases(): IsoPath {
        val out = ArrayList<IsoPath>()
        for (i in 1 until m - 1) {
            if (meridian[i].smooth || meridian[i].r <= 1e-9) continue
            for (arc in arcsOf(rangeOf(i - 1), rangeOf(i))) {
                out.add(pathOf(along(meridian[i].s, meridian[i].r, arc)))
            }
        }
        return IsoPath.join(out)
    }

    /** A highlight inset by [by] from each closed cap, ending on the cap's visible rim. */
    fun bevel(by: Double): IsoPath {
        if (by <= 0 || m <= 2) return IsoPath.Empty
        val ends = ArrayList<Triple<Int, Int, Int>>()
        if (meridian[0].r <= 1e-9) ends.add(Triple(0, 1, 1))
        if (meridian[m - 1].r <= 1e-9) ends.add(Triple(m - 2, m - 2, m - 3))
        val out = ArrayList<IsoPath>()
        for ((cap, rim, side) in ends) {
            val (s, r) = meridian[rim]
            for (arc in arcsOf(rangeOf(cap), rangeOf(side))) {
                val inset = along(s, max(0.0, r - by), arc)
                val edge = along(s, r, arc)
                out.add(pathOf(listOf(edge[0]) + inset.subList(1, inset.size - 1) + edge.last()))
            }
        }
        return IsoPath.join(out)
    }
}
