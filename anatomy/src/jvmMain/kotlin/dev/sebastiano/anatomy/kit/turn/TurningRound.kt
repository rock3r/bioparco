// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Art
import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Knot
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Smooth
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.arcOf
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.audit.AuditShape
import dev.sebastiano.anatomy.kit.audit.BallFlat
import dev.sebastiano.anatomy.kit.audit.ball as ballShape
import dev.sebastiano.anatomy.kit.audit.jsFixed
import dev.sebastiano.anatomy.kit.audit.solid as solidShape
import dev.sebastiano.anatomy.kit.frameAlong
import dev.sebastiano.anatomy.kit.hullOf
import dev.sebastiano.anatomy.kit.iso
import dev.sebastiano.anatomy.kit.lathe
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.pointOf
import dev.sebastiano.anatomy.kit.profileCurve
import dev.sebastiano.anatomy.kit.sphereOf
import dev.sebastiano.anatomy.kit.unit3
import kotlin.math.PI
import kotlin.math.abs

/*
 * The round constructors of `kit/turn-build.mjs`: `T.round` (cylinder or cone, with flat, domed or
 * open ends), `T.ball` (a sphere with flats), `T.lathe` (a convex body of revolution) and `T.fixed`
 * (kit art whose picture never changes under the group's motion). A vertical round or lathe in an
 * upright group, and a plain ball, become fixed pictures automatically: nearly free to draw.
 */

/** A cylinder or cone frustum along [frame] from [s0] to [s1]; each end flat, a dome or open. */
fun Turning.round(
    name: String,
    group: TurnGroup?,
    layer: LiveLayer,
    frame: Frame,
    s0: Double,
    s1: Double,
    r0: Double,
    r1: Double = r0,
    ends: Pair<RoundEnd, RoundEnd> = RoundEnd.Flat to RoundEnd.Flat,
    fixed: Boolean? = null,
    style: PartStyle = PartStyle(),
    details: Details = Details(),
): TurnPart {
    val f = frameFrom(frame)
    require(s1 >= s0) { "turning: round $name needs s0 < s1" }
    val endList = listOf(ends.first, ends.second)
    require(endList.none { it == RoundEnd.Dome } || abs(r0 - r1) <= 1e-9) {
        "turning: round $name can only have a dome where the end radius equals the side's (a capsule)"
    }
    val body = RoundBody(f, s0, s1, r0, r1, endList)
    val vertical = abs(f.a.z) > 0.9999
    val groupOk = group == null || group.upright
    if (vertical && groupOk && fixed != false)
        return fixedRound(name, group, layer, body, style, details)
    val part = common(name, PartKind.Round, group, layer, style, details)
    record(part, solidShape(f, body.profile, meta = meta(part)))
    part.world = body.world()
    part.spec = RoundSpec(Frame(local(group, f.o), f.a, f.u, f.v), s0, s1, r0, r1, endList)
    part.seams = details.seams.toList()
    detailParts(part, f, body::radiusAt, s0 to s1)
    part.samples.addAll(body.samples().map { local(group, it) })
    part.hints.add(f.a)
    parts.add(part)
    return part
}

/** A round's axis span, radii and ends, its recorded profile and its radius along the axis. */
private class RoundBody(
    val f: Frame,
    val s0: Double,
    val s1: Double,
    val r0: Double,
    val r1: Double,
    val ends: List<RoundEnd>,
) {
    val profile = roundProfile(s0, s1, r0, r1, ends)
    val span = (s1 - s0).let { if (it == 0.0) 1.0 else it }

    fun radiusAt(s: Double) = r0 + ((r1 - r0) * (maxOf(s0, minOf(s1, s)) - s0)) / span

    fun world() = RoundWorld(f, s0, s1, r0, r1, ends)

    fun ring(s: Double, r: Double, count: Int = 48) =
        (0 until count).map { pointOf(f, s, r, (it.toDouble() / count) * TAU) }

    /** Points on the surface: both end rims, and rings up each dome to its pole. */
    fun samples(): List<Point3> {
        val pts = ArrayList(ring(s0, r0) + ring(s1, r1))
        for (end in 0 until 2) {
            if (ends[end] != RoundEnd.Dome) continue
            val sAt = if (end == 1) s1 else s0
            val r = if (end == 1) r1 else r0
            val sign = if (end == 1) 1.0 else -1.0
            for (j in 1..6) {
                val b = (j.toDouble() / 6) * (PI / 2)
                if (j == 6) pts.add(add3(f.o, mul3(f.a, sAt + sign * r)))
                else pts.addAll(ring(sAt + sign * r * jsSin(b), r * jsCos(b), 32))
            }
        }
        return pts
    }
}

/** A vertical round in an upright group: the kit's own lathe picture, moved by a translation. */
private fun Turning.fixedRound(
    name: String,
    group: TurnGroup?,
    layer: LiveLayer,
    body: RoundBody,
    style: PartStyle,
    details: Details,
): TurnPart {
    val f = body.f
    val profile = body.profile
    val meridian =
        listOf(Knot(profile[0].s, 0.0)) +
            profile.filter { it.r > 1e-9 } +
            Knot(profile.last().s, 0.0)
    val slope = (body.r1 - body.r0) / body.span
    val seams =
        IsoPath.join(
            details.seams.map {
                arcOf(f, it, body.radiusAt(it), projection, slope = slope, least = 0.0)
            }
        )
    val smooth = if (RoundEnd.Dome in body.ends) Smooth() else Smooth.auto(meridian)
    val paths = lathe(meridian, f, projection, smooth = smooth)
    val picture = art {
        solid(
            paths,
            style.tone,
            style.crease,
            lit = style.lit,
            inner = if (seams.isEmpty) null else art { line(seams, Tone.Faint) },
        )
    }
    val ribs =
        details.ribs?.let {
            it.copy(r0 = it.r0 ?: body.radiusAt(it.s0), r1 = it.r1 ?: body.radiusAt(it.s1))
        }
    val part =
        fixed(
            name,
            group,
            layer,
            picture,
            pointOf(f, body.s0, 0.0, 0.0),
            listOf(solidShape(f, profile, meta = metaOf(name, style))),
            frame = f,
            style = style,
            details = details.copy(ribs = ribs, seams = emptyList()),
            skipDetails = true,
        )
    part.round = body.world()
    detailParts(part, f, body::radiusAt, body.s0 to body.s1)
    return part
}

private fun metaOf(name: String, style: PartStyle) =
    dev.sebastiano.anatomy.kit.audit.ShapeMeta(name = name, owner = style.owner)

/** The profile a round records: its side, with each dome as a quarter arc of smooth knots. */
private fun roundProfile(
    s0: Double,
    s1: Double,
    r0: Double,
    r1: Double,
    ends: List<RoundEnd>,
): List<Knot> {
    fun arc(sAt: Double, sign: Double, r: Double) =
        (0..8)
            .map { (it.toDouble() / 8) * (PI / 2) }
            .map { b -> Knot(sAt + sign * r * jsCos(b), r * jsSin(b), true) }
    val profile = ArrayList<Knot>()
    if (ends[0] == RoundEnd.Dome) {
        val a = arc(s0, -1.0, r0).dropLast(1)
        a.forEachIndexed { j, p -> profile.add(if (j == 0) Knot(p.s, 0.0) else p) }
        profile.add(Knot(s0, r0, true))
    } else {
        profile.add(Knot(s0, r0))
    }
    if (ends[1] == RoundEnd.Dome) {
        profile.add(Knot(s1, r1, true))
        val a = arc(s1, 1.0, r1).reversed().drop(1)
        a.forEachIndexed { j, p -> profile.add(if (j == a.size - 1) Knot(p.s, 0.0) else p) }
    } else {
        profile.add(Knot(s1, r1))
    }
    return profile
}

/**
 * A sphere round [c]; each flat keeps `n·(p − c) ≤ d` and draws its rim, so `n` is the flat's
 * outward normal. Without flats it is a fixed picture and takes no details.
 */
fun Turning.ball(
    name: String,
    group: TurnGroup?,
    layer: LiveLayer,
    c: Point3,
    r: Double,
    flats: List<BallFlat> = emptyList(),
    fixed: Boolean? = null,
    style: PartStyle = PartStyle(),
    details: Details = Details(),
): TurnPart {
    val units = flats.map { BallFlat(unit3(it.n), it.d) }
    val shape = ballShape(c, r, metaOf(name, style), units)
    if (units.isEmpty() && fixed != false) {
        val picture = art {
            solid(sphereOf(c, r, projection, 48), style.tone, style.crease, lit = style.lit)
        }
        return fixed(
            name,
            group,
            layer,
            picture,
            c,
            listOf(shape),
            style = style,
            details = details,
        )
    }
    val part = common(name, PartKind.Ball, group, layer, style, details)
    record(part, shape)
    part.world = shape
    part.spec = BallSpec(local(group, c), r, units.map { Flat3(it.n, it.d) })
    part.samples.addAll(sampleShape(shape, 48).map { local(group, it) })
    for (flat in units) part.hints.add(flat.n)
    detailParts(
        part,
        frameAlong(c, if (units.isNotEmpty()) units[0].n else Point3(0.0, 0.0, 1.0)),
        null,
        null,
    )
    parts.add(part)
    return part
}

/**
 * A general convex smooth body along [frame]: [profile] `(s, r)` with an optional analytic [slope]
 * and [radius]. `r(s)` must be concave. Costly: prefer rounds.
 */
fun Turning.lathe(
    name: String,
    group: TurnGroup?,
    layer: LiveLayer,
    frame: Frame,
    profile: List<Knot>,
    slope: ((Double) -> Double)? = null,
    radius: ((Double) -> Double)? = null,
    fixed: Boolean? = null,
    style: PartStyle = PartStyle(),
    details: Details = Details(),
): TurnPart {
    val f = frameFrom(frame)
    val curve = profileCurve(profile.map { Knot(it.s, it.r) }, slope, radius)
    val knots = latheKnots(curve, profile, dev.sebastiano.anatomy.kit.cameraOf(projection).k, name)
    val levels = ArrayList<Double>()
    for (sv in knots) {
        val g = curve.slope(sv)
        val norm = dev.sebastiano.anatomy.kit.jsHypot(1.0, g)
        levels.addAll(listOf(sv, curve.radius(sv), 1 / norm, -g / norm))
    }
    val caps = (curve.radius(curve.s0) > 1e-6) to (curve.radius(curve.s1) > 1e-6)
    val shape =
        solidShape(f, profile.map { Knot(it.s, it.r, true) }, curve.radius, metaOf(name, style))
    val vertical = abs(f.a.z) > 0.9999
    if (vertical && (group == null || group.upright) && fixed != false) {
        val meridian = ArrayList<Knot>()
        meridian.add(Knot(curve.s0, 0.0))
        knots.forEachIndexed { i, sv ->
            meridian.add(Knot(sv, curve.radius(sv), i != 0 && i != knots.size - 1))
        }
        meridian.add(Knot(curve.s1, 0.0))
        val seams =
            IsoPath.join(
                details.seams.map {
                    arcOf(f, it, curve.radius(it), projection, slope = curve.slope(it), least = 0.0)
                }
            )
        val paths =
            lathe(
                meridian,
                f,
                projection,
                smooth = Smooth(slope = curve.slope, radius = curve.radius),
            )
        val picture = art {
            solid(
                paths,
                style.tone,
                style.crease,
                lit = style.lit,
                inner = if (seams.isEmpty) null else art { line(seams, Tone.Faint) },
            )
        }
        val part =
            fixed(
                name,
                group,
                layer,
                picture,
                pointOf(f, curve.s0, 0.0, 0.0),
                listOf(shape),
                frame = f,
                style = style,
                details = details.copy(seams = emptyList()),
                skipDetails = true,
            )
        detailParts(part, f, curve.radius, curve.s0 to curve.s1)
        return part
    }
    val part = common(name, PartKind.Lathe, group, layer, style, details)
    record(part, shape)
    part.spec = LatheSpec(Frame(local(group, f.o), f.a, f.u, f.v), levels.toDoubleArray(), caps)
    part.seams = details.seams.toList()
    part.world = curve
    for (sv in knots) for (j in 0 until 32) part.samples.add(
        local(group, pointOf(f, sv, curve.radius(sv), (j.toDouble() / 32) * TAU))
    )
    part.hints.add(f.a)
    detailParts(part, f, curve.radius, curve.s0 to curve.s1)
    parts.add(part)
    return part
}

/** The knots a lathe is sampled at, split until each is within 0.1 px of a chord. */
private fun latheKnots(
    curve: dev.sebastiano.anatomy.kit.ProfileCurve,
    profile: List<Knot>,
    kk: Double,
    name: String,
): List<Double> {
    val knots = arrayListOf(curve.s0)
    fun split(a: Double, b: Double, level: Int) {
        val mid = (a + b) / 2
        if (
            level < 7 &&
                (abs(curve.radius(mid) - (curve.radius(a) + curve.radius(b)) / 2) * kk > 0.1 ||
                    b - a > 4)
        ) {
            split(a, mid, level + 1)
            knots.add(mid)
            split(mid, b, level + 1)
        }
    }
    val stops =
        listOf(curve.s0) + profile.map { it.s }.filter { it > curve.s0 && it < curve.s1 } + curve.s1
    for (i in 0 until stops.size - 1) {
        split(stops[i], stops[i + 1], 0)
        knots.add(stops[i + 1])
    }
    for (i in 1 until knots.size - 1) {
        val a = curve.radius(knots[i - 1])
        val b = curve.radius(knots[i])
        val c = curve.radius(knots[i + 1])
        val h0 = knots[i] - knots[i - 1]
        val h1 = knots[i + 1] - knots[i]
        require((c - b) / h1 - (b - a) / h0 <= 1e-6) {
            "turning: lathe $name is not convex near s = ${jsFixed(knots[i], 2)} (r(s) must be concave); split it"
        }
    }
    return knots
}

/**
 * Any kit art whose picture never changes under the group's motion, moved by a translation: [art]
 * drawn at rest, [anchor] the world point it moves with, [shapes] its true shapes, and an optional
 * detail [frame].
 */
fun Turning.fixed(
    name: String,
    group: TurnGroup?,
    layer: LiveLayer,
    art: Art,
    anchor: Point3,
    shapes: List<AuditShape>,
    hull: List<Flat>? = null,
    frame: Frame? = null,
    style: PartStyle = PartStyle(),
    details: Details = Details(),
    skipDetails: Boolean = false,
): TurnPart {
    val part = common(name, PartKind.Fixed, group, layer, style, details)
    for (shape in shapes) record(part, shape)
    val world = part.shapes.flatMap { sampleShape(it, 48) }
    val flat = world.map { iso(it, projection) }
    val outline = hull ?: hullOf(flat)
    val box =
        markupBox(art)
            ?: doubleArrayOf(
                outline.minOf { it.x },
                outline.minOf { it.y },
                outline.maxOf { it.x },
                outline.maxOf { it.y },
            )
    part.art = art
    val f = frame?.let { frameFrom(it) }
    part.spec =
        FixedSpec(
            local(group, anchor),
            iso(anchor, projection),
            doubleArrayOf(box[0] - 0.5, box[1] - 0.5, box[2] + 0.5, box[3] + 0.5),
            outline.flatMap { listOf(it.x, it.y) }.toDoubleArray(),
            f?.let { Frame(local(group, it.o), it.a, it.u, it.v) },
        )
    if (f != null && !skipDetails) detailParts(part, f, null, null)
    part.samples.addAll(world.map { local(group, it) })
    if (f != null) part.hints.add(f.a)
    parts.add(part)
    return part
}
