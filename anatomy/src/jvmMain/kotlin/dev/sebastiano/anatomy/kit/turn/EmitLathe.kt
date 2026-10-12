// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Projection
import dev.sebastiano.anatomy.kit.jsHypot
import dev.sebastiano.anatomy.kit.jsRound
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * A live lathe, `t3PrepareLathe` and `t3EmitLathe`: a general convex body of revolution given as
 * levels `(s, r, nr, na)`. Levels are refined until neighbouring rings carry the same light bands,
 * the bands are joined into sheets between rings (`LatheSheets.kt`), and the outline is the hull of
 * every level's limb points.
 */

/** One level of a lathe: position, radius, the surface normal across and along, its arcs. */
internal class LatheLevel(
    val s: Double,
    val r: Double,
    val nr: Double,
    val na: Double,
    val arcs: List<Arc> = emptyList(),
) {
    var groups: Array<MutableList<Arc>?> = arrayOfNulls(5)
}

/** `t3LatheMid`: the level halfway between two, on the Hermite curve through them. */
internal fun t3LatheMid(a: LatheLevel, b: LatheLevel): LatheLevel {
    val h = b.s - a.s
    val ga = -a.na / a.nr
    val gb = -b.na / b.nr
    val nr = a.nr + b.nr
    val na = a.na + b.na
    val l = jsHypot(nr, na).let { if (it == 0.0) 1.0 else it }
    return LatheLevel((a.s + b.s) / 2, (a.r + b.r) / 2 + (h * (ga - gb)) / 8, nr / l, na / l)
}

/** A prepared lathe. */
class LatheRun
internal constructor(index: Int, part: PartData, unit: Double, spec: LatheSpec, p: Projection) :
    TurnRun(index, part, unit) {
    val frame: Frame = spec.frame
    val levels: DoubleArray = spec.levels
    internal val fine: Int
    internal val capCount: Int
    internal val seams: List<SeamRing>
    internal val ribs: RibSpec? = part.ribs

    init {
        val k = p.scale * T3_ROOT
        var rmax = 0.0
        for (i in levels.indices step 4) rmax = max(rmax, levels[i + 1])
        fine = max(24, min(96, ceil((T3_TAU * rmax * k * unit) / 2.2).toInt()))
        capCount = max(24, min(160, ceil((T3_TAU * rmax * k * unit) / 1.5).toInt()))
        seams = (part.seams ?: emptyList()).map { latheSeam(levels, it) }
    }

    override fun cloudOf(): DoubleArray {
        val out = ArrayList<Double>()
        val step = max(4, floor(levels.size.toDouble() / 4 / 8).toInt()) * 4
        for (i in levels.indices step step) ringCloud(frame, levels[i], levels[i + 1], 8, out)
        ringCloud(frame, levels[levels.size - 4], levels[levels.size - 3], 8, out)
        return out.toDoubleArray()
    }

    override fun axesOf(): List<Point3> = listOf(frame.a)

    override fun emit(cam: TurnCam, f: TurnFrame) {
        val geo = t3Geo(frame, cam)
        val k = cam.k
        val sheets = Array(5) { IsoPath.Empty }
        t3LatheSheets(refinedLevels(geo, k), geo, T3_TAU / fine, sheets)
        for (t in 0 until 5) f.d[role.getValue("s$t")] = sheets[t]
        val pts = latheOutline(levels, geo, k)
        val outline = t3PathOf(pts, true)
        f.d[role.getValue("fill")] = outline
        f.d[role.getValue("edge")] = outline
        t3BoxOf(pts, f.box)
        t3HullOut(f, pts)
        val lv = levels
        val n = lv.size
        var crease = IsoPath.Empty
        for (end in 0 until 2) {
            val at = role["k$end"] ?: continue
            val s = if (end == 1) lv[n - 4] else lv[0]
            val r = if (end == 1) lv[n - 3] else lv[1]
            val near = if (end == 1) n - 8 else 4
            val sign = if (end == 1) 1.0 else -1.0
            val arc =
                capOf(
                    at,
                    sign,
                    s,
                    r,
                    geo,
                    k,
                    f,
                    capCount,
                    lv[near + 3],
                    lv[near + 2],
                    skipZero = true,
                )
            if (arc != null && role["crease"] != null) crease += arc
        }
        role["crease"]?.let { f.d[it] = crease }
        role["inner"]?.let { inner ->
            var path = IsoPath.Empty
            for (seam in seams) path += t3SeamArc(geo, seam.s, seam.r, seam.slope, k)
            f.d[inner] = path
        }
        val ribs = ribs
        if (ribs != null) {
            val r0 = ribs.r0 ?: 0.0
            val lines =
                if (TurnState.detail < 0.01) FadedLines()
                else t3RibLines(geo, ribs, r0, ribs.r1 ?: r0)
            f.levels = t3Faded(f, this, "r", lines)
        }
        emitPlanes(this, cam, f)
        emitDots(this, cam, f)
    }

    /** The levels, refined until neighbours carry the same bands and the arcs move smoothly. */
    private fun refinedLevels(geo: Geo, k: Double): List<LatheLevel> {
        val lv = levels
        fun levelAt(s: Double, r: Double, nr: Double, na: Double) =
            LatheLevel(s, r, nr, na, t3Arcs(nr, na, geo.view, geo.light))
        val out = ArrayList<LatheLevel>()
        val axis = jsHypot(geo.sa.x, geo.sa.y)
        fun reach(a: LatheLevel, b: LatheLevel) =
            jsHypot((b.s - a.s) * axis, (b.r - a.r) * k) + abs(b.r - a.r) * k
        fun refine(a: LatheLevel, b: LatheLevel, depth: Int) {
            if (depth >= 8 || reach(a, b) < 0.1) {
                out.add(b)
                return
            }
            val m = t3LatheMid(a, b)
            val mid = levelAt(m.s, m.r, m.nr, m.na)
            if (sameArcs(a, b) && sameArcs(a, mid) && closeArcs(a, mid, b, k)) {
                out.add(b)
                return
            }
            refine(a, mid, depth + 1)
            refine(mid, b, depth + 1)
        }
        var previous = levelAt(lv[0], lv[1], lv[2], lv[3])
        out.add(previous)
        for (i in 4 until lv.size step 4) {
            val next = levelAt(lv[i], lv[i + 1], lv[i + 2], lv[i + 3])
            refine(previous, next, 0)
            previous = next
        }
        return out
    }
}

private fun sameArcs(a: LatheLevel, b: LatheLevel): Boolean {
    if (a.arcs.size != b.arcs.size) return false
    for (i in a.arcs.indices) {
        if (a.arcs[i].tone != b.arcs[i].tone || a.arcs[i].full != b.arcs[i].full) return false
    }
    return true
}

private fun wrap(x: Double, ref: Double) = x + T3_TAU * jsRound((ref - x) / T3_TAU)

private fun closeArcs(a: LatheLevel, mid: LatheLevel, b: LatheLevel, k: Double): Boolean {
    val r = max(a.r, b.r) * k
    for (i in mid.arcs.indices) {
        val m = mid.arcs[i]
        if (m.full) continue
        val fromGuess = (wrap(a.arcs[i].from, m.from) + wrap(b.arcs[i].from, m.from)) / 2
        if (abs(fromGuess - m.from) * r > 0.25) return false
        val toGuess = (wrap(a.arcs[i].to, m.to) + wrap(b.arcs[i].to, m.to)) / 2
        if (abs(toGuess - m.to) * r > 0.25) return false
    }
    return true
}

/** `t3LatheSeam`: a seam at [s], with the radius and slope of the levels round it. */
internal fun latheSeam(levels: DoubleArray, s: Double): SeamRing {
    var i = 0
    while (i + 4 < levels.size) {
        val sa = levels[i]
        val sb = levels[i + 4]
        if ((s - sa) * (s - sb) <= 0 && sa != sb) {
            val t = (s - sa) / (sb - sa)
            val r = levels[i + 1] + (levels[i + 5] - levels[i + 1]) * t
            return SeamRing(s, r, (levels[i + 5] - levels[i + 1]) / (sb - sa))
        }
        i += 4
    }
    return SeamRing(s, levels[1], 0.0)
}

/** `t3LatheOutline`: the hull of every level's limb points, refined where they spread. */
private fun latheOutline(lv: DoubleArray, geo: Geo, k: Double): List<Flat> {
    val view = geo.view
    val pts = ArrayList<Flat>()
    fun cut(l: LatheLevel) =
        if (view.flat < 1e-9) Double.POSITIVE_INFINITY else -(l.na * view.a) / (l.nr * view.flat)
    fun inside(l: LatheLevel) = l.r <= 1e-9 || abs(cut(l)) < 1
    fun sides(l: LatheLevel): List<Flat> {
        if (l.r <= 1e-9) {
            val p = Flat(geo.ox + l.s * geo.sa.x, geo.oy + l.s * geo.sa.y)
            return listOf(p, p)
        }
        val d = jsAcos(max(-1.0, min(1.0, cut(l))))
        return listOf(view.phi - d, view.phi + d).map { t3AtFlat(geo, l.s, l.r, it) }
    }
    fun span(a: LatheLevel, b: LatheLevel): Double {
        val pa = sides(a)
        val pb = sides(b)
        return max(
            jsHypot(pa[0].x - pb[0].x, pa[0].y - pb[0].y),
            jsHypot(pa[1].x - pb[1].x, pa[1].y - pb[1].y),
        )
    }
    fun refine(a: LatheLevel, b: LatheLevel, depth: Int) {
        if (depth >= 10 || span(a, b) < 1.2) return
        val mid = t3LatheMid(a, b)
        refine(a, mid, depth + 1)
        pts.addAll(sides(mid))
        refine(mid, b, depth + 1)
    }
    var previous: LatheLevel? = null
    for (i in lv.indices step 4) {
        val l = LatheLevel(lv[i], lv[i + 1], lv[i + 2], lv[i + 3])
        if (inside(l)) pts.addAll(sides(l))
        val prev = previous
        if (prev != null) {
            val pin = inside(prev)
            val lin = inside(l)
            if (pin && lin) {
                refine(prev, l, 0)
            } else if (pin != lin) {
                var a = if (pin) prev else l
                var b = if (pin) l else prev
                val start = a
                repeat(14) {
                    val mid = t3LatheMid(if (a.s < b.s) a else b, if (a.s < b.s) b else a)
                    if (inside(mid)) a = mid else b = mid
                }
                pts.addAll(sides(a))
                refine(if (start.s < a.s) start else a, if (start.s < a.s) a else start, 0)
            }
        }
        previous = l
    }
    for (i in intArrayOf(0, lv.size - 4)) {
        if (lv[i + 1] > 1e-9) t3ArcPoints(geo, lv[i], lv[i + 1], 0.0, T3_TAU, k, pts)
    }
    return t3Hull(pts)
}

/** A level's point at angle [t]. */
internal fun latheAt(geo: Geo, l: LatheLevel, t: Double): Flat {
    val c = jsCos(t)
    val n = jsSin(t)
    return Flat(
        geo.ox + l.s * geo.sa.x + l.r * (c * geo.su.x + n * geo.sv.x),
        geo.oy + l.s * geo.sa.y + l.r * (c * geo.su.y + n * geo.sv.y),
    )
}
