// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Projection
import dev.sebastiano.anatomy.kit.Rings
import dev.sebastiano.anatomy.kit.jsHypot
import dev.sebastiano.anatomy.kit.jsRound
import kotlin.math.abs

/*
 * The build's data as the runtime receives it (`partData`, `groupData`, `pack`, `packPairs`):
 * part numbers rounded to 0.001, everything else to 0.0001, and pair normals squeezed into 16-bit
 * octahedral pairs and unpacked again, exactly as the original's JSON and base64 round trip does.
 */

/** `Math.round(v · 10^k) / 10^k`, with JSON's `-0` → `0`. */
private fun roundTo(v: Double, scale: Double): Double {
    val t = jsRound(v * scale) / scale
    return if (t == 0.0) 0.0 else t
}

internal fun r3(v: Double) = roundTo(v, 1e3)

internal fun r4(v: Double) = roundTo(v, 1e4)

private fun Point3.r(round: (Double) -> Double) = Point3(round(x), round(y), round(z))

private fun Frame.r(round: (Double) -> Double) =
    Frame(o.r(round), a.r(round), u.r(round), v.r(round))

private fun Pair<Double, Double>.r(round: (Double) -> Double) = round(first) to round(second)

private fun DoubleArray.r(round: (Double) -> Double) = DoubleArray(size) { round(this[it]) }

/** A part's data before packing: the original's `partData`. */
internal fun partData(part: TurnPart): PartData {
    val seams =
        when (part.kind) {
            PartKind.Fixed -> null
            else -> part.seams
        }?.takeIf { it.isNotEmpty() }
    return PartData(
        part.name,
        part.kind,
        part.group?.index ?: -1,
        part.layer.index,
        part.style.tone,
        part.style.crease,
        part.style.lit,
        part.style.material,
        part.spec,
        seams,
        part.ribs,
        part.planes.map { PlaneData(it.o, it.u, it.v, it.n, it.fade, it.billboard, it.content) },
        part.dots.toList(),
        hue = part.hue,
        art = part.art,
    )
}

internal fun groupData(t: Turning): List<GroupData> =
    t.groups.map { g ->
        GroupData(
            g.name,
            g.parent?.index ?: -1,
            g.kind,
            g.origin,
            g.pivot?.let { Point3(it.x, it.y, 0.0) },
            g.direction,
            if (g.kind == GroupKind.Slide) g.travel else null,
            g.point,
            g.axis,
            g.range,
            g.upright,
            g.loose,
        )
    }

private fun packSpec(spec: PartSpec): PartSpec {
    val r = ::r3
    val slots = spec.slots
    return when (spec) {
        is PrismSpec ->
            PrismSpec(
                spec.up,
                spec.ring.r(r),
                r(spec.z0),
                r(spec.z1),
                spec.frame?.r(r),
                r(spec.s0),
                r(spec.s1),
                r(spec.bevel),
                spec.cuts.map { Cut(it.n.r(r), r(it.d)) },
                slots,
            )
        is RoundSpec ->
            RoundSpec(
                spec.frame.r(r),
                r(spec.s0),
                r(spec.s1),
                r(spec.r0),
                r(spec.r1),
                spec.ends,
                spec.cuts.map { Cut(it.n.r(r), r(it.d)) },
                slots,
            )
        is BallSpec ->
            BallSpec(spec.c.r(r), r(spec.r), spec.flats.map { Flat3(it.n.r(r), r(it.d)) }, slots)
        is LatheSpec -> LatheSpec(spec.frame.r(r), spec.levels.r(r), spec.caps, slots)
        is TubeSpec ->
            TubeSpec(
                spec.route,
                r(spec.from),
                r(spec.to),
                spec.open0,
                spec.open1,
                spec.gap0,
                spec.gap1,
                spec.caps,
                spec.wide,
                spec.ringTone,
                slots,
            )
        is FixedSpec ->
            FixedSpec(
                spec.anchor.r(r),
                Flat(r(spec.home.x), r(spec.home.y)),
                spec.box.r(r),
                spec.hull.r(r),
                spec.frame?.r(r),
                slots,
            )
    }
}

/** A part packed: every number to 0.001 (`pack3`). */
internal fun packPart(p: PartData, cloud: DoubleArray?): PartData {
    val r = ::r3
    return PartData(
        p.name,
        p.kind,
        p.group,
        p.layer,
        p.tone,
        p.crease,
        p.lit,
        p.material,
        packSpec(p.spec),
        p.seams?.map(r),
        p.ribs?.let {
            it.copy(
                s0 = r(it.s0),
                s1 = r(it.s1),
                phase = r(it.phase),
                twist = r(it.twist),
                fade = it.fade.r(r),
                r0 = it.r0?.let(r),
                r1 = it.r1?.let(r),
            )
        },
        p.planes.map {
            PlaneData(
                it.o.r(r),
                it.u.r(r),
                it.v.r(r),
                it.n.r(r),
                it.fade.r(r),
                it.billboard,
                it.content,
            )
        },
        p.dots.map { DotData(it.p.r(r), it.n?.r(r), r(it.size), it.tone, it.fade.r(r)) },
        cloud?.r(::r4)?.r(r),
        p.hue,
        p.art,
    )
}

internal fun packGroup(g: GroupData): GroupData {
    val r = ::r4
    return GroupData(
        g.name,
        g.parent,
        g.kind,
        g.origin.r(r),
        g.pivot?.r(r),
        g.direction?.r(r),
        g.travel?.let(r),
        g.point?.r(r),
        g.axis?.r(r),
        g.range?.r(r),
        g.upright,
        g.loose,
    )
}

internal fun packProjection(p: Projection) =
    Projection(Flat(r4(p.origin.x), r4(p.origin.y)), r4(p.scale), r4(p.azimuth), r4(p.elevation))

internal fun packRoute(route: RouteData): RouteData {
    val r = ::r4
    val rings =
        route.rings?.let {
            Rings(r(it.pitch), r(it.twist), it.cross, it.fade?.r(r), r(it.phase), it.steps, it.tone)
        }
    return RouteData(route.points.r(r), r(route.r), r(route.spacing), route.wide, route.hue, rings)
}

/** The original's `octOf`: a unit normal as two 16-bit numbers. */
private fun octOf(n: Point3): Pair<Int, Int> {
    val l = (abs(n.x) + abs(n.y) + abs(n.z)).let { if (it == 0.0) 1.0 else it }
    var x = n.x / l
    var y = n.y / l
    if (n.z < 0) {
        val ox = (1 - abs(y)) * (if (x >= 0) 1 else -1)
        val oy = (1 - abs(x)) * (if (y >= 0) 1 else -1)
        x = ox
        y = oy
    }
    return jsRound((x * 0.5 + 0.5) * 65535).toInt() to jsRound((y * 0.5 + 0.5) * 65535).toInt()
}

/** The original's `t3Unpack` of one octahedral pair. */
private fun unoct(u: Int, v: Int): Point3 {
    var x = (u.toDouble() / 65535) * 2 - 1
    var y = (v.toDouble() / 65535) * 2 - 1
    val z = 1 - abs(x) - abs(y)
    if (z < 0) {
        val ox = (1 - abs(y)) * (if (x >= 0) 1 else -1)
        val oy = (1 - abs(x)) * (if (y >= 0) 1 else -1)
        x = ox
        y = oy
    }
    val l = jsHypot(x, y, z).let { if (it == 0.0) 1.0 else it }
    return Point3(x / l, y / l, z / l)
}

/**
 * Pair records (`i, j, [frame,] nx, ny, nz`) through the original's packing: ids as 16-bit numbers,
 * the frame shifted by one, the normal octahedral. Returns what the runtime unpacks.
 */
internal fun packPairs(list: List<Double>, stride: Int, grouped: Boolean): DoubleArray {
    val count = list.size / stride
    val out = DoubleArray(count * stride)
    for (e in 0 until count) {
        val at = e * stride
        out[at] = (list[at].toInt() and 0xffff).toDouble()
        out[at + 1] = (list[at + 1].toInt() and 0xffff).toDouble()
        if (grouped) out[at + 2] = (((list[at + 2].toInt() + 1) and 0xffff) - 1).toDouble()
        val (u, v) =
            octOf(Point3(list[at + stride - 3], list[at + stride - 2], list[at + stride - 1]))
        val n = unoct(u, v)
        out[at + stride - 3] = n.x
        out[at + stride - 2] = n.y
        out[at + stride - 1] = n.z
    }
    return out
}
