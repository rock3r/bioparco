// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Art
import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.LineMark
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.SolidMark
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.cameraOf
import dev.sebastiano.anatomy.kit.cross3
import dev.sebastiano.anatomy.kit.dot3
import dev.sebastiano.anatomy.kit.frameAlong
import dev.sebastiano.anatomy.kit.jsRound
import dev.sebastiano.anatomy.kit.len3
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.pathOf
import dev.sebastiano.anatomy.kit.pointOf
import dev.sebastiano.anatomy.kit.radialOf
import dev.sebastiano.anatomy.kit.sub3
import dev.sebastiano.anatomy.kit.unit3
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/*
 * Details and splits from `kit/turn-build.mjs`: bolt circles and dots become the part's dots,
 * rings and rules become planes (`detailParts`), and `T.split` cuts a prism or a round by a plane
 * into two parts to break a genuine cycle.
 */

/** The original's `fine`: a coordinate to 0.001, as plane markup writes it. */
private fun fine(v: Double) = jsRound(v * 1000) / 1000

/**
 * A part's bolts, dots, rings and rules in frame [f]. [radiusAt] is the surface radius along the
 * axis (null for none) and [ends] the axis span whose end faces bolts may sit on.
 */
internal fun Turning.detailParts(
    part: TurnPart,
    f: Frame,
    radiusAt: ((Double) -> Double)?,
    ends: Pair<Double, Double>?,
) {
    val details = part.details
    for (bolt in details.bolts) {
        val atEnd =
            when {
                ends == null -> 0
                abs(bolt.s - ends.first) < 1e-6 -> -1
                abs(bolt.s - ends.second) < 1e-6 -> 1
                else -> 0
            }
        val onCap = atEnd != 0 && (radiusAt == null || bolt.r < radiusAt(bolt.s) - 1e-6)
        val slope =
            if (radiusAt != null) (radiusAt(bolt.s + 1e-3) - radiusAt(bolt.s - 1e-3)) / 2e-3
            else 0.0
        for (j in 0 until bolt.count) {
            val t = bolt.phase + (j.toDouble() / bolt.count) * TAU
            val p = pointOf(f, bolt.s, bolt.r, t)
            val radial = radialOf(f, t)
            val n =
                if (onCap) mul3(f.a, atEnd.toDouble()) else unit3(sub3(radial, mul3(f.a, slope)))
            part.dots.add(DotData(local(part.group, p), n, bolt.size, bolt.tone, bolt.fade))
        }
    }
    toLocalDots(part, f, details.dots)
    for (ring in details.rings) {
        val (s, x, y) = ring.at
        val o = add3(add3(f.o, mul3(f.a, s)), add3(mul3(f.u, x), mul3(f.v, y)))
        val n = unit3(ring.normal ?: f.a)
        val b = frameAlong(Point3(0.0, 0.0, 0.0), n)
        val steps = max(12, ceil((TAU * ring.r * cameraOf(projection).k) / 1.2).toInt())
        val pts =
            (0 until steps).map { j ->
                Flat(
                    fine(ring.r * jsCos((j.toDouble() / steps) * TAU)),
                    fine(ring.r * jsSin((j.toDouble() / steps) * TAU)),
                )
            }
        plane(
            part,
            o,
            b.u,
            b.v,
            PlaneArt(listOf(PlaneLine(pathOf(pts, true), ring.tone))),
            ring.fade,
            n,
        )
    }
    for (rule in details.rules) {
        val pts =
            rule.points.map { (s, x, y) ->
                add3(add3(f.o, mul3(f.a, s)), add3(mul3(f.u, x), mul3(f.v, y)))
            }
        var n = rule.normal?.let { unit3(it) }
        var i = 2
        while (i < pts.size && n == null) {
            val c = cross3(sub3(pts[1], pts[0]), sub3(pts[i], pts[0]))
            if (len3(c) > 1e-9) n = unit3(c)
            i++
        }
        val normal = n ?: f.a
        val b = frameAlong(Point3(0.0, 0.0, 0.0), normal)
        val o = pts[0]
        val flat = pts.map { Flat(fine(dot3(sub3(it, o), b.u)), fine(dot3(sub3(it, o), b.v))) }
        plane(
            part,
            o,
            b.u,
            b.v,
            PlaneArt(listOf(PlaneLine(pathOf(flat), rule.tone, rule.free))),
            rule.fade,
            normal,
        )
    }
}

/**
 * Cuts [part] by the plane `n·p = d` into two parts, named `.a` and `.b`: a prism along or across
 * its axis, a round across it. The cut edges are not stroked. Use it to break a genuine cycle.
 */
fun Turning.split(part: TurnPart, n: Point3, d: Double): List<TurnPart> {
    val normal = unit3(n)
    val made =
        when {
            part.kind == PartKind.Prism -> splitPrism(part, normal, d)
            part.kind == PartKind.Round || (part.kind == PartKind.Fixed && part.round != null) ->
                splitRound(part, normal, d)
            else -> error("turning: split supports prisms and rounds (${part.name})")
        }
    for (half in made) {
        val spec = half.spec
        val cut = Cut(normal, d - dot3(normal, origin(half.group)))
        half.spec =
            when (spec) {
                is PrismSpec ->
                    PrismSpec(
                        spec.up,
                        spec.ring,
                        spec.z0,
                        spec.z1,
                        spec.frame,
                        spec.s0,
                        spec.s1,
                        spec.bevel,
                        partCuts(part) + cut,
                        spec.slots,
                    )
                is RoundSpec ->
                    RoundSpec(
                        spec.frame,
                        spec.s0,
                        spec.s1,
                        spec.r0,
                        spec.r1,
                        spec.ends,
                        partCuts(part) + cut,
                        spec.slots,
                    )
                else -> spec
            }
        half.hints.add(0, normal)
    }
    drop(part)
    return made
}

private fun partCuts(part: TurnPart): List<Cut> =
    when (val spec = part.spec) {
        is PrismSpec -> spec.cuts
        is RoundSpec -> spec.cuts
        else -> emptyList()
    }

private fun Turning.forget(part: TurnPart) {
    part.dead = true
    recorder?.let { r -> (r.solids as MutableList).remove(part.shapes[0]) }
}

private fun Turning.splitPrism(part: TurnPart, normal: Point3, d: Double): List<TurnPart> {
    val world = part.world as PrismWorld
    val f = world.frame
    val along = dot3(normal, f.a)
    val bevel = (part.spec as PrismSpec).bevel
    val halves: List<Triple<List<Floor>, Double, Double>> =
        if (abs(abs(along) - 1) < 1e-6) {
            val sCut = (d - dot3(normal, f.o)) / along
            require(sCut > world.s0 + 1e-6 && sCut < world.s1 - 1e-6) {
                "turning: split plane misses ${part.name}"
            }
            listOf(Triple(world.ring, world.s0, sCut), Triple(world.ring, sCut, world.s1))
        } else if (abs(along) < 1e-6) {
            val nu = dot3(normal, f.u)
            val nv = dot3(normal, f.v)
            val c = d - dot3(normal, f.o)
            fun clip(sign: Double): List<Floor> {
                val ring = world.ring
                val out = ArrayList<Floor>()
                for (i in ring.indices) {
                    val a = ring[i]
                    val b = ring[(i + 1) % ring.size]
                    val sa = sign * (nu * a.x + nv * a.y - c)
                    val sb = sign * (nu * b.x + nv * b.y - c)
                    if (sa <= 0) out.add(a)
                    if ((sa < 0 && sb > 0) || (sa > 0 && sb < 0)) {
                        val t = sa / (sa - sb)
                        out.add(Floor(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t))
                    }
                }
                return out
            }
            listOf(clip(1.0), clip(-1.0))
                .also { list ->
                    require(list.all { it.size >= 3 }) {
                        "turning: split plane misses ${part.name}"
                    }
                }
                .map { Triple(it, world.s0, world.s1) }
        } else
            error(
                "turning: a prism can only be split by a plane along or across its axis (${part.name})"
            )
    forget(part)
    return halves.mapIndexed { index, (polygon, s0, s1) ->
        val details = if (index == 1) part.details.copy(dots = emptyList()) else part.details
        prism(
            "${part.name}.${if (index == 1) "b" else "a"}",
            part.group,
            part.layer,
            f,
            polygon,
            s0,
            s1,
            0.0,
            4,
            bevel,
            part.style,
            details,
        )
    }
}

private fun Turning.splitRound(part: TurnPart, normal: Point3, d: Double): List<TurnPart> {
    val w = part.round ?: (part.world as RoundWorld)
    val f = w.frame
    val along = dot3(normal, f.a)
    require(abs(abs(along) - 1) <= 1e-6) {
        "turning: a round can only be split across its axis (${part.name})"
    }
    val sCut = (d - dot3(normal, f.o)) / along
    require(sCut > w.s0 + 1e-6 && sCut < w.s1 - 1e-6) { "turning: split plane misses ${part.name}" }
    val rAt = w.r0 + ((w.r1 - w.r0) * (sCut - w.s0)) / (w.s1 - w.s0)
    forget(part)
    return listOf(
        round(
            "${part.name}.a",
            part.group,
            part.layer,
            f,
            w.s0,
            sCut,
            w.r0,
            rAt,
            w.ends[0] to RoundEnd.Open,
            false,
            part.style,
            part.details,
        ),
        round(
            "${part.name}.b",
            part.group,
            part.layer,
            f,
            sCut,
            w.s1,
            rAt,
            w.r1,
            RoundEnd.Open to w.ends[1],
            false,
            part.style,
            Details(),
        ),
    )
}

/**
 * The screen box of fixed [art] as the original measures its markup (`markupBox`): every path point
 * as the SVG writes it, to 0.1, without halos and text. Null for nothing.
 */
internal fun markupBox(art: Art): DoubleArray? {
    var box: DoubleArray? = null
    fun add(path: IsoPath) {
        for (run in path.runs) {
            for (p in run.points) {
                val x = jsRound(p.x * 10) / 10
                val y = jsRound(p.y * 10) / 10
                val b = box
                box =
                    if (b == null) doubleArrayOf(x, y, x, y)
                    else doubleArrayOf(min(b[0], x), min(b[1], y), max(b[2], x), max(b[3], y))
            }
        }
    }
    fun visit(a: Art) {
        for (mark in a.marks) {
            when (mark) {
                is SolidMark -> {
                    val paths = mark.paths
                    add(paths.fill)
                    if (!mark.flat) {
                        paths.shades.forEach { add(it) }
                        add(paths.top)
                    }
                    if (mark.crease != null) {
                        add(paths.crease)
                        paths.bevel?.let { add(it) }
                    }
                    mark.inner?.let { visit(it) }
                    add(paths.outline)
                }
                is LineMark -> add(mark.shape.iso)
                else -> Unit
            }
        }
    }
    visit(art)
    return box
}
