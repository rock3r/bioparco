// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.audit

import dev.sebastiano.anatomy.kit.Knot
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Smooth
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.arcOf
import dev.sebastiano.anatomy.kit.circleOf
import dev.sebastiano.anatomy.kit.crossingsOf
import dev.sebastiano.anatomy.kit.disc as discPaths
import dev.sebastiano.anatomy.kit.dot3
import dev.sebastiano.anatomy.kit.dotsOf
import dev.sebastiano.anatomy.kit.fillet
import dev.sebastiano.anatomy.kit.frameAlong
import dev.sebastiano.anatomy.kit.insetPlan
import dev.sebastiano.anatomy.kit.lathe
import dev.sebastiano.anatomy.kit.len3
import dev.sebastiano.anatomy.kit.lerp3
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.pathLength
import dev.sebastiano.anatomy.kit.planOutline
import dev.sebastiano.anatomy.kit.pointAlong
import dev.sebastiano.anatomy.kit.sub3
import dev.sebastiano.anatomy.kit.tangentAlong
import dev.sebastiano.anatomy.kit.unit3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The pipework helpers of the original Raptor's build: pipes with flanges, couplings, bands,
 * bellows and a valve; ball valves with their actuators; limp cables with end shells, clamped in
 * bundles; and ball-ended struts between two hosts.
 */

/**
 * The original `pipe`'s options. A [valve] is `(along, r)`: a share of the length or a distance.
 */
data class PipeOptions(
    val bend: Double = 2.6,
    val tone: Tone = Tone.Hi,
    val flanges: Pair<Boolean, Boolean> = true to true,
    val flangeR: List<Double> = listOf(1.55),
    val coupling: Boolean = false,
    val bands: List<Double> = emptyList(),
    val bellows: Triple<Double, Double, Int>? = null,
    val valve: Pair<Double, Double>? = null,
    val move: Point3? = null,
    val bias: Double = 0.0,
    val maxLength: Double = 26.0,
    val spacing: Double? = null,
    val touch: List<String> = emptyList(),
    val breaks: List<Double> = emptyList(),
    val gaps: List<Pair<Double, Double>> = emptyList(),
    val free: Pair<Boolean, Boolean> = false to false,
)

private class Collar(
    val kind: String,
    val centre: Point3,
    val tangent: Point3,
    val radius: Double,
    val thick: Double,
    val gap: Pair<Double, Double>,
)

private fun collarsOf(
    line: List<Point3>,
    r: Double,
    o: PipeOptions,
    flanges: Pair<Boolean, Boolean>,
    rs: List<Double>,
): List<Collar> {
    val total = pathLength(line)
    val collars = ArrayList<Collar>()
    listOf(0.0 to -1.0, total to 1.0).forEachIndexed { index, (along, sign) ->
        if (if (index == 0) !flanges.first else !flanges.second) return@forEachIndexed
        val thick = max(0.9, r * 0.55)
        val tangent = tangentAlong(line, along)
        val centre = add3(pointAlong(line, along), mul3(tangent, -sign * thick / 2))
        val gap = if (sign < 0) 0.0 to thick else total - thick to total
        collars += Collar("flange", centre, tangent, r * rs[index], thick, gap)
    }
    if (o.coupling) {
        val thick = max(1.6, r * 1.4)
        val centre = pointAlong(line, total - thick / 2)
        collars +=
            Collar(
                "coupling",
                centre,
                tangentAlong(line, total),
                r * 1.5,
                thick,
                total - thick to total,
            )
    }
    fun ring(along: Double, radius: Double, thick: Double, kind: String) {
        val gap = along - thick / 2 to along + thick / 2
        collars +=
            Collar(kind, pointAlong(line, along), tangentAlong(line, along), radius, thick, gap)
    }
    for (share in o.bands) ring(total * share, r * 1.18, max(0.8, r * 0.7), "band")
    val bellows = o.bellows
    if (bellows != null) {
        val (from, to, count) = bellows
        for (index in 0 until count) {
            val along = total * (from + (to - from) * index / (count - 1))
            ring(along, r * 1.26, max(0.6, r * 0.3), "bellows")
        }
    }
    return collars
}

/**
 * A pipe of [piece] on [control] points, filleted to `r · bend`, with a bolted flange at each
 * flanged end (or a coupling at the far end), bands, bellows rings and a valve gap, each recorded
 * under the pipe as owner. Returns the route and its owner, for [valveOn].
 */
fun PartRecorder.pipe(
    piece: String,
    control: List<Point3>,
    r: Double,
    name: String? = null,
    options: PipeOptions = PipeOptions(),
): PipeRoute {
    val o = options
    val rs = if (o.flangeR.size > 1) o.flangeR else listOf(o.flangeR[0], o.flangeR[0])
    val flanges = if (o.coupling) o.flanges.first to false else o.flanges
    val line = fillet(control, max(r * o.bend, 3.0), if (r > 3) 24 else 12, projection, r)
    val total = pathLength(line)
    val collars = collarsOf(line, r, o, flanges, rs)
    val gaps = ArrayList(collars.map { it.gap } + o.gaps)
    val valve = o.valve
    if (valve != null) {
        val along = if (valve.first > 1) valve.first else total * valve.first
        gaps += along - valve.second * 2.4 to along + valve.second * 2.4
    }
    fun rim(flanged: Boolean, index: Int) =
        if (flanged) r * rs[index] else if (index > 0 && o.coupling) r * 1.5 else r
    val owner =
        tubeItems(
            piece,
            line,
            r,
            name ?: "$piece.pipe",
            TubeOptions(
                tone = o.tone,
                bias = o.bias,
                move = o.move,
                maxLength = o.maxLength,
                spacing = o.spacing ?: max(0.8, min(2.4, r * 0.18)),
                touch = o.touch,
                gaps = gaps,
                breaks = o.breaks,
                free = o.free,
                rims = listOf(rim(flanges.first, 0), rim(flanges.second, 1)),
            ),
        )
    for (c in collars) {
        val bolts = if (c.kind == "flange" && r > 3) 8 else 0
        val tone = if (c.kind == "flange" || c.kind == "coupling") o.tone else Tone.Mid
        val flange =
            FlangeOptions(bolts, tone, o.move, o.bias, name = "$owner.${c.kind}", owner = owner)
        flangeAt(piece, c.centre, c.tangent, c.radius, c.thick, flange)
    }
    return PipeRoute(line, owner)
}

/** Where a valve's actuator takes a cable: its [port], a point [approach]ing it, its [lid]. */
class ValvePort(val port: Point3, val approach: Point3, val lid: Double? = null)

/**
 * A ball valve of pipe radius [r] on [line] at [share] (of its length, or a distance when over 1),
 * with its neck toward [side] and an actuator on it: a box when it stands straight up, else a round
 * can. Returns the actuator's cable port.
 */
fun PartRecorder.valveOn(
    piece: String,
    line: PipeRoute,
    share: Double,
    r: Double,
    move: Point3? = null,
    tone: Tone = Tone.Hi,
    name: String? = null,
    side: Point3 = Point3(0.0, 0.0, 1.0),
): ValvePort {
    val total = pathLength(line.points)
    val along = if (share > 1) share else total * share
    val centre = pointAlong(line.points, along)
    val frame = frameAlong(centre, tangentAlong(line.points, along))
    val body = r * 1.85
    val id = recorder.nameFor(name ?: "${line.owner}.valve")
    val owner = line.owner
    val crown = body * 1.08
    val waist = r * 1.45
    val reach = r * 1.6
    fun bulge(x: Double) = waist + (crown - waist) * sqrt(max(0.0, 1 - (x / reach) * (x / reach)))
    val bulgeSlope = { x: Double ->
        if (abs(x) >= reach - 1e-9) 0.0
        else {
            val t = max(-0.995, min(0.995, x / reach))
            -(crown - waist) * t / (reach * sqrt(1 - t * t))
        }
    }
    val meridian =
        listOf(Knot(-r * 2.4, 0.0), Knot(-r * 2.4, waist)) +
            List(17) { index ->
                val x = reach * sin((index - 8) / 8.0 * (PI / 2))
                Knot(x, bulge(x), index in 1 until 16)
            } +
            listOf(Knot(r * 2.4, waist), Knot(r * 2.4, 0.0))
    val shape = recorder.solid(body(frame, meridian, ShapeMeta(name = id, owner = owner)))
    put(piece, centre, listOf(shape), move = move, name = id) {
        solid(
            lathe(meridian, frame, projection, 36, smooth = Smooth(rows = 3, slope = bulgeSlope)),
            tone,
        )
        val ends =
            dotsOf(frame, -r * 2.4, r * 1.2, 8, projection, all = true) +
                dotsOf(frame, r * 2.4, r * 1.2, 8, projection, all = true)
        dots(ends.filterIndexed { index, _ -> index % 2 == 0 }, 0.4, Tone.Mid)
    }
    val up = unit3(side)
    val neckFrame = frameAlong(centre, up)
    val s0 = body * 1.08 - 0.25
    val s1 = body * 1.08 + body * 0.45
    val neck =
        recorder.solid(
            disc(neckFrame, s0, s1, r * 0.5, ShapeMeta(name = "$id.neck", owner = owner))
        )
    put(piece, add3(centre, mul3(up, (s0 + s1) / 2)), listOf(neck), 0.2, move, name = "$id.neck") {
        solid(discPaths(s0, s1, r * 0.5, neckFrame, projection), tone)
    }
    return if (up.z > 0.99) boxActuator(piece, centre, s1, r, move, tone, id, owner)
    else canActuator(piece, centre, up, s1, r, move, tone, id, owner)
}

private fun PartRecorder.boxActuator(
    piece: String,
    centre: Point3,
    s1: Double,
    r: Double,
    move: Point3?,
    tone: Tone,
    id: String,
    owner: String,
): ValvePort {
    val top = add3(centre, Point3(0.0, 0.0, s1))
    val plan = Plan(top.x - r * 1.5, top.y - r * 1.5, r * 3, r * 3, 0.8)
    val lid = top.z + r * 2.2
    val extra: dev.sebastiano.anatomy.kit.ArtBuilder.() -> Unit = {
        line(planOutline(insetPlan(plan, 0.8), lid, projection, 2), Tone.Faint)
        dots(
            listOf(
                at(Point3(plan.x + 1.2, plan.y + 1.2, lid)),
                at(Point3(plan.x + plan.w - 1.2, plan.y + plan.d - 1.2, lid)),
            ),
            0.4,
            Tone.Mid,
        )
    }
    boxAt(
        piece,
        plan,
        top.z,
        r * 2.2,
        Style(tone),
        BoxOptions(0.3, move, name = "$id.actuator", owner = owner, extra = extra),
    )
    val port = Point3(top.x, top.y, lid)
    return ValvePort(port, add3(port, Point3(0.0, 0.0, 5.0)), lid)
}

private fun PartRecorder.canActuator(
    piece: String,
    centre: Point3,
    up: Point3,
    s1: Double,
    r: Double,
    move: Point3?,
    tone: Tone,
    id: String,
    owner: String,
): ValvePort {
    val frame = frameAlong(centre, up)
    val end = s1 + r * 2.2
    val can = listOf(Knot(s1, 0.0), Knot(s1, r * 1.5), Knot(end, r * 1.5), Knot(end, 0.0))
    val shape = recorder.solid(body(frame, can, ShapeMeta(name = "$id.actuator", owner = owner)))
    put(
        piece,
        add3(centre, mul3(up, (s1 + end) / 2)),
        listOf(shape),
        0.3,
        move,
        name = "$id.actuator",
    ) {
        solid(lathe(can, frame, projection, 32, bevel = 0.6), tone)
        line(circleOf(frame, end, r * 1.1, projection, 24), Tone.Faint)
        dots(dotsOf(frame, end, r * 1.25, 4, projection, phase = PI / 4, all = true), 0.4, Tone.Mid)
    }
    val port = add3(centre, mul3(up, end))
    return ValvePort(port, add3(port, mul3(up, 5.0)))
}

/** Half the thickness of a cable clamp. */
const val CLAMP_HALF = 0.9

/** Where a clamp holds a bundle: its [centre], its [axis] and its [radius]. */
class Clamp(val centre: Point3, val axis: Point3, val radius: Double)

/** The original `cable`'s options. [shells] puts a connector shell on each end. */
data class CableOptions(
    val move: Point3? = null,
    val bias: Double = 0.2,
    val r: Double = 0.62,
    val bend: Double = 6.0,
    val bundle: String? = null,
    val touch: List<String> = emptyList(),
    val clamps: List<Clamp> = emptyList(),
    val shells: Pair<Boolean, Boolean> = false to false,
    val free: Pair<Boolean, Boolean> = false to false,
)

/**
 * A limp cable of [piece] on [points], cut where it passes through [CableOptions.clamps], with a
 * connector shell or a bare dot at each end. Returns the filleted route.
 */
fun PartRecorder.cable(
    piece: String,
    points: List<Point3>,
    name: String? = null,
    options: CableOptions = CableOptions(),
): List<Point3> {
    val o = options
    val r = o.r
    val route = fillet(points, o.bend, 12, projection, r)
    cables += piece to route
    val total = pathLength(route)
    val shell = 2.2
    val gaps =
        o.clamps.flatMap { c ->
            crossingsOf(route, c.centre, c.axis, c.radius).map {
                it - CLAMP_HALF - 0.05 to it + CLAMP_HALF + 0.05
            }
        } +
            (if (o.shells.first) listOf(0.0 to shell) else emptyList()) +
            (if (o.shells.second) listOf(total - shell to total) else emptyList())
    val id =
        tubeItems(
            piece,
            route,
            r,
            name ?: "$piece.cable",
            TubeOptions(
                tone = Tone.Mid,
                bias = o.bias,
                move = o.move,
                maxLength = 14.0,
                spacing = 1.0,
                bundle = o.bundle,
                touch = o.touch,
                gaps = gaps,
                free = o.free,
                limp = true,
                rims = listOf(if (o.shells.first) r * 2 else r, if (o.shells.second) r * 2 else r),
            ),
        )
    for (index in 0..1) {
        val on = if (index == 0) o.shells.first else o.shells.second
        cableEnd(piece, route, total, index, on, id, o)
    }
    return route
}

private fun PartRecorder.cableEnd(
    piece: String,
    route: List<Point3>,
    total: Double,
    index: Int,
    shell: Boolean,
    id: String,
    o: CableOptions,
) {
    val end = if (index > 0) route.last() else route.first()
    if (!shell) {
        put(piece, end, listOf(ball(end, 0.5 / scale)), o.bias + 0.15, o.move, name = "$id.end") {
            dots(listOf(at(end)), 0.5, Tone.Hi)
        }
        return
    }
    val r = o.r
    val tangent = tangentAlong(route, if (index > 0) total else 0.0)
    val frame = frameAlong(end, tangent)
    val s0 = if (index > 0) -2.2 else 0.0
    val s1 = if (index > 0) 0.0 else 2.2
    val outer = if (index > 0) s0 else s1
    val shape =
        recorder.solid(disc(frame, s0, s1, r * 2, ShapeMeta(name = "$id.shell", owner = id)))
    val facing = dot3(mul3(frame.a, if (index > 0) -1.0 else 1.0), view)
    put(
        piece,
        add3(end, mul3(tangent, (s0 + s1) / 2)),
        listOf(shape),
        o.bias + 0.1,
        o.move,
        name = "$id.shell",
    ) {
        solid(discPaths(s0, s1, r * 2, frame, projection, bevel = 0.25), Tone.Mid)
        if (facing > 0.2) line(circleOf(frame, outer, r * 1.1, projection, 12), Tone.Faint)
    }
}

/** A clamp of radius [r] across [axis] at [centre], holding a [bundle]. */
fun PartRecorder.clampAt(
    piece: String,
    centre: Point3,
    axis: Point3,
    r: Double,
    move: Point3? = null,
    bias: Double = 0.3,
    name: String = "$piece.clamp",
    bundle: String? = null,
) {
    val frame = frameAlong(centre, axis)
    val id = recorder.nameFor(name)
    val meta = ShapeMeta(name = id, anchor = false, bundle = bundle, fitting = true)
    val shape = recorder.solid(disc(frame, -CLAMP_HALF, CLAMP_HALF, r, meta))
    put(piece, centre, listOf(shape), bias, move, name = id) {
        solid(discPaths(-CLAMP_HALF, CLAMP_HALF, r, frame, projection), Tone.Mid)
    }
}

/** One end of a strut: the [host] it is studded into, the [point] on it, the stud's [normal]. */
class StrutEnd(val host: AuditShape, val point: Point3, val normal: Point3)

/**
 * A strut of [piece] between two hosts: a ball stud on each (owned by the host's piece) and a rod
 * of radius [r] between the balls.
 */
fun PartRecorder.strut(
    piece: String,
    a: StrutEnd,
    b: StrutEnd,
    move: Point3? = null,
    r: Double = 1.1,
    ball: Double = 2.2,
    stem: Double = 1.3,
    lift: Double = 1.2,
) {
    val id = recorder.nameFor("$piece.strut")
    val reach = sqrt(ball * ball - stem * stem)
    val (ca, cb) =
        listOf(a, b).map { end ->
            val own = end.host.piece ?: piece
            val ownMove = if (own == piece) move else null
            val stud =
                BossOptions(
                    ownMove,
                    name = "$own.ball-stud",
                    host = listOf(end.host),
                    ring = false,
                    owner = "$id.stud",
                )
            val face = boss(own, end.point, end.normal, stem, lift, stud)
            val centre = add3(face, mul3(end.normal, reach))
            ballAt(own, centre, ball, Tone.Hi, ownMove, name = "$own.ball-stud", owner = "$id.stud")
            centre
        }
    val axis = unit3(sub3(cb, ca))
    val length = len3(sub3(cb, ca))
    val frame = frameAlong(ca, axis)
    val end = sqrt(ball * ball - r * r)
    val shape = recorder.solid(disc(frame, end, length - end, r, ShapeMeta(name = id, owner = id)))
    put(piece, lerp3(ca, cb, 0.5), listOf(shape), move = move, name = id) {
        solid(
            discPaths(end, length - end, r, frame, projection),
            Style(inner = arcOf(frame, length * 0.5, r, projection)),
        )
    }
}
