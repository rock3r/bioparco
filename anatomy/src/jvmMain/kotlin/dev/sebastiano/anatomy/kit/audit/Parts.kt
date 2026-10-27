// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.audit

import dev.sebastiano.anatomy.kit.ArtBuilder
import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Knot
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Projection
import dev.sebastiano.anatomy.kit.Rims
import dev.sebastiano.anatomy.kit.Smooth
import dev.sebastiano.anatomy.kit.SolidPaths
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.circleOf
import dev.sebastiano.anatomy.kit.cross3
import dev.sebastiano.anatomy.kit.disc as discPaths
import dev.sebastiano.anatomy.kit.dot3
import dev.sebastiano.anatomy.kit.dotsOf
import dev.sebastiano.anatomy.kit.frameAlong
import dev.sebastiano.anatomy.kit.iso
import dev.sebastiano.anatomy.kit.jsRound
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.pointOf
import dev.sebastiano.anatomy.kit.saddleOf
import dev.sebastiano.anatomy.kit.scaleOf
import dev.sebastiano.anatomy.kit.slabOf
import dev.sebastiano.anatomy.kit.solidOf
import dev.sebastiano.anatomy.kit.sphereOf
import dev.sebastiano.anatomy.kit.stepsFor
import dev.sebastiano.anatomy.kit.sub3
import dev.sebastiano.anatomy.kit.tubePieces
import dev.sebastiano.anatomy.kit.unit3
import dev.sebastiano.anatomy.kit.viewOf
import kotlin.math.abs
import kotlin.math.sqrt

/*
 * The recording helpers of the original's examples/raptor-engine/build.mjs (`put`, `tubeItems`,
 * `turned`, `band`, `flangeAt`, `boxAt`, `ballAt`, `elbow`, `sideOn`, `boss`, `padOn`, and in
 * PartsPipes.kt `pipe`, `valveOn`, `cable`, `clampAt`, `strut`): each draws a part with the kit,
 * records its true shape into a [Recorder], and puts it as one item of an assembly, so the audit
 * sees everything the figure paints. An assembly is a [Piece]: what it is called and how far it
 * moves when the figure comes apart.
 */

/**
 * An assembly of an exploded figure: its [name], what the readout [say]s, its [move] at share 1.
 */
class Piece(val name: String, var say: String, val move: Point3)

/**
 * A filleted pipe or cable route, and the owner its flanges, bands and valve are recorded under.
 */
class PipeRoute(val points: List<Point3>, val owner: String)

/**
 * Draws and records parts into [recorder] for the assemblies in [pieces]. A part's `move` defaults
 * to its piece's; passing a piece's own move vector (the same instance) makes the part ride that
 * piece's spring, as the original's `rideOf` does.
 *
 * ```
 * val parts = PartRecorder(Recorder(P, order, spring), pieces)
 * val head = parts.band("chamber", ENGINE, 194.0, 205.0, 33.4)
 * parts.boss("chamber", polar(-150.0, 33.4, 200.0), dir(-150.0), 2.4, 3.6, host = listOf(head))
 * parts.pipe("hotgas", listOf(a, b), 3.6, flangeR = listOf(1.3), bands = listOf(0.5))
 * parts.recorder.settle()
 * ```
 */
class PartRecorder(val recorder: Recorder, val pieces: Map<String, Piece>) {
    val projection: Projection = recorder.projection
    /** The camera's toward-viewer direction, the original's `V`. */
    val view: Point3 = viewOf(projection)
    /** Screen px per world unit, the original's `KS`. */
    val scale: Double = scaleOf(projection)
    /** Every cable's piece and route, in the order drawn. */
    val cables = ArrayList<Pair<String, List<Point3>>>()

    fun at(point: Point3): Flat = iso(point, projection)

    /** The move of [piece], or [move] when given. */
    fun moveOf(piece: String, move: Point3?): Point3 = move ?: pieces.getValue(piece).move

    /** The assembly whose spring moves a part of [piece] that moves by [move]. */
    fun rideOf(piece: String, move: Point3?): String =
        if (move == null) piece
        else pieces.entries.firstOrNull { it.value.move === move }?.key ?: piece

    /** Puts one item of [piece] at [ref] with its art and recorded [shapes]. */
    fun put(
        piece: String,
        ref: Point3,
        shapes: List<AuditShape> = emptyList(),
        bias: Double = 0.0,
        move: Point3? = null,
        ride: String? = null,
        name: String? = null,
        route: String? = null,
        chunk: Int = 0,
        draw: ArtBuilder.() -> Unit,
    ): AuditItem =
        recorder.put(
            art(draw),
            shapes,
            at = ref,
            bias = bias,
            name = name ?: recorder.nameFor("$piece.item"),
            piece = piece,
            move = moveOf(piece, move),
            ride = ride ?: rideOf(piece, move),
            route = route,
            chunk = chunk,
        )

    /**
     * Records a tube of [piece] along [route] and puts it chunk by chunk, so it weaves among the
     * parts it passes. Returns its name, which owns its flanges.
     */
    fun tubeItems(
        piece: String,
        route: List<Point3>,
        r: (Double) -> Double,
        name: String? = null,
        options: TubeOptions = TubeOptions(),
    ): String {
        val o = options
        val id = recorder.nameFor(name ?: "$piece.tube")
        recorder.route(
            id,
            route,
            r,
            RouteOptions(
                owner = o.owner ?: id,
                touch = o.touch,
                bundle = o.bundle,
                limp = o.limp,
                gaps = o.gaps,
                closed = o.closed,
                ends = o.ends,
                rims = o.rims,
                free = o.free,
                piece = piece,
                move = moveOf(piece, o.move),
                ride = rideOf(piece, o.move),
            ),
        )
        tubePieces(
                route,
                r,
                projection,
                maxLength = o.maxLength,
                spacing = o.spacing,
                closed = o.closed,
                breaks = o.breaks,
                gaps = o.gaps,
                caps = o.caps,
            )
            .forEachIndexed { index, part ->
                put(
                    piece,
                    part.mid,
                    listOf(tube(part.points, part.radii)),
                    o.bias,
                    o.move,
                    name = "$id:$index",
                    route = id,
                    chunk = index,
                ) {
                    tube(part, o.tone)
                }
            }
        return id
    }

    /** [tubeItems] of one radius. */
    fun tubeItems(
        piece: String,
        route: List<Point3>,
        r: Double,
        name: String? = null,
        options: TubeOptions = TubeOptions(),
    ): String = tubeItems(piece, route, { _: Double -> r }, name, options)

    /**
     * A turned part: [profile] along [frame], closed to the axis at both ends. Smooth knots shade
     * smoothly (3 rows under radius 20, else 5) unless [smooth] says otherwise. Returns its shape.
     */
    fun turned(
        piece: String,
        frame: Frame,
        profile: List<Knot>,
        style: Style = Style(),
        options: TurnedOptions = TurnedOptions(),
    ): AuditShape {
        val o = options
        val smooth =
            if (o.smooth === AUTO_SMOOTH)
                if (profile.any { it.smooth })
                    Smooth(rows = if (profile.maxOf { it.r } < 20) 3 else 5)
                else null
            else o.smooth
        val steps = o.steps
        val paths =
            if (steps != null)
                solidOf(
                    profile,
                    frame,
                    projection,
                    steps,
                    bevel = o.bevel,
                    rims = o.rims,
                    smooth = smooth,
                )
            else
                solidOf(profile, frame, projection, bevel = o.bevel, rims = o.rims, smooth = smooth)
        val mid = (profile.first().s + profile.last().s) / 2
        val id = recorder.nameFor(o.name ?: "$piece.turned${jsRound(profile.first().s).toLong()}")
        val curve = if (smooth != null && smooth.slope == null) profile else null
        val shape =
            recorder.solid(
                solid(frame, profile, meta = ShapeMeta(name = id, owner = o.owner, curve = curve))
            )
        put(piece, add3(frame.o, mul3(frame.a, mid)), listOf(shape), o.bias, o.move, name = id) {
            solid(paths, style.tone, style.crease, inner = innerOf(o.inner ?: style.inner))
            o.extra(this)
        }
        return shape
    }

    /** A plain cylinder from [s0] to [s1] of radius [r] along [frame]: a [turned] band. */
    fun band(
        piece: String,
        frame: Frame,
        s0: Double,
        s1: Double,
        r: Double,
        style: Style = Style(),
        options: TurnedOptions = TurnedOptions(),
    ): AuditShape = turned(piece, frame, listOf(Knot(s0, r), Knot(s1, r)), style, options)

    /**
     * A flange of radius [r] and thickness [thick] centred on [centre] across [axis], with bolts.
     */
    fun flangeAt(
        piece: String,
        centre: Point3,
        axis: Point3,
        r: Double,
        thick: Double,
        options: FlangeOptions = FlangeOptions(),
    ) {
        val o = options
        val frame = frameAlong(centre, axis)
        val id = recorder.nameFor(o.name ?: "$piece.flange")
        val paths = discPaths(-thick / 2, thick / 2, r, frame, projection)
        val facing = dot3(frame.a, view)
        val towards = if (facing > 0) thick / 2 else -thick / 2
        val shape =
            recorder.solid(
                disc(frame, -thick / 2, thick / 2, r, ShapeMeta(name = id, owner = o.owner))
            )
        put(piece, centre, listOf(shape), o.bias, o.move, name = id) {
            solid(paths, o.tone)
            if (abs(facing) > 0.12 && o.bolts > 0) {
                val bolts =
                    dotsOf(frame, towards, o.boltR ?: (r * 0.74), o.bolts, projection, all = true)
                dots(bolts, 0.42, Tone.Mid)
            }
        }
    }

    /** A box on [plan] from [z] up [h], with [style] and [extra] art on it. */
    fun boxAt(
        piece: String,
        plan: Plan,
        z: Double,
        h: Double,
        style: Style = Style(),
        options: BoxOptions = BoxOptions(),
    ) {
        val o = options
        val centre = Point3(plan.x + plan.w / 2, plan.y + plan.d / 2, z + h / 2)
        val id = recorder.nameFor(o.name ?: "$piece.box")
        val shape =
            recorder.solid(
                box(
                    Point3(plan.x, plan.y, z),
                    Point3(plan.x + plan.w, plan.y + plan.d, z + h),
                    ShapeMeta(name = id, owner = o.owner),
                )
            )
        put(piece, centre, listOf(shape), o.bias, o.move, name = id) {
            solid(
                slabOf(plan, z, h, projection, o.steps, o.bevel),
                style.tone,
                style.crease,
                inner = innerOf(style.inner),
            )
            o.extra(this)
        }
    }

    /** A ball of radius [r] at [centre]. */
    fun ballAt(
        piece: String,
        centre: Point3,
        r: Double,
        tone: Tone = Tone.Mid,
        move: Point3? = null,
        bias: Double = 0.0,
        name: String? = null,
        owner: String? = null,
    ) {
        val id = recorder.nameFor(name ?: "$piece.ball")
        val shape = recorder.solid(ball(centre, r, ShapeMeta(name = id, owner = owner)))
        put(piece, centre, listOf(shape), bias, move, name = id) {
            solid(sphereOf(centre, r, projection, stepsFor(r, 32)), tone)
        }
    }

    /**
     * A ball elbow seated on a boss [face] of radius [faceR] along [normal], turning toward
     * [toward]; returns where a pipe of rim radius [rim] leaves it.
     */
    fun elbow(
        piece: String,
        face: Point3,
        normal: Point3,
        faceR: Double,
        toward: Point3,
        ball: Double,
        rim: Double,
        name: String? = null,
        owner: String? = null,
    ): Point3 {
        val centre = add3(face, mul3(normal, sqrt(ball * ball - faceR * faceR)))
        ballAt(piece, centre, ball, Tone.Hi, name = name, owner = owner)
        val axis = unit3(sub3(toward, centre))
        return add3(centre, mul3(axis, sqrt(ball * ball - rim * rim)))
    }

    /** The direction across [d] that lies side-on to the viewer. */
    fun sideOn(d: Point3): Point3 = unit3(cross3(unit3(sub3(view, mul3(d, dot3(view, d)))), d))

    /**
     * A boss of radius [r] standing [length] out of [centre] along [outward]: a pad sunk into
     * [BossOptions.host] when there is one, else a plain cylinder. Returns its face's centre.
     */
    fun boss(
        piece: String,
        centre: Point3,
        outward: Point3,
        r: Double,
        length: Double,
        options: BossOptions = BossOptions(),
    ): Point3 {
        val o = options
        val host = o.host
        if (host != null)
            return padOn(
                piece,
                host,
                centre,
                outward,
                r,
                length,
                o.copy(name = o.name ?: "$piece.boss"),
            )
        val frame = frameAlong(centre, outward)
        val id = recorder.nameFor(o.name ?: "$piece.boss")
        val shape =
            recorder.solid(disc(frame, 0.0, length, r, ShapeMeta(name = id, owner = o.owner)))
        put(
            piece,
            add3(centre, mul3(frame.a, length / 2)),
            listOf(shape),
            o.bias,
            o.move,
            name = id,
        ) {
            solid(discPaths(0.0, length, r, frame, projection), o.tone)
            if (o.ring && dot3(frame.a, view) > 0.15)
                line(circleOf(frame, length, r * 0.55, projection, 16), Tone.Faint)
        }
        return add3(centre, mul3(frame.a, length))
    }

    /**
     * A pad of radius [a] on the [hosts], standing [h] out of [point] along [outward]: its foot
     * follows the hosts' surface, found by bisection on their distance fields. Returns its face.
     */
    fun padOn(
        piece: String,
        hosts: List<AuditShape>,
        point: Point3,
        outward: Point3,
        a: Double,
        h: Double,
        options: BossOptions = BossOptions(ring = false),
    ): Point3 {
        val o = options
        fun inside(p: Point3) = hosts.any { sdf(it, p) < 0 }
        val axis = unit3(outward)
        val frame = frameAlong(point, axis)
        val start = { angle: Double ->
            var lo = -2.5 * a
            var hi = h
            if (!inside(pointOf(frame, lo, a, angle))) 0.0
            else {
                repeat(40) {
                    val mid = (lo + hi) / 2
                    if (inside(pointOf(frame, mid, a, angle))) lo = mid else hi = mid
                }
                hi
            }
        }
        val low = (0 until 48).minOf { start(it.toDouble() / 48 * Math.PI * 2) }
        val id = recorder.nameFor(o.name ?: "$piece.pad")
        val meta = ShapeMeta(name = id, owner = o.owner, seated = true, cut = hosts)
        val shape = recorder.solid(disc(frame, low - 0.05, h, a, meta))
        put(piece, add3(point, mul3(axis, h / 2)), listOf(shape), o.bias, o.move, name = id) {
            solid(saddleOf(frame, a, start, h, projection), o.tone)
            if (o.ring && dot3(axis, view) > 0.15)
                line(circleOf(frame, h, a * 0.55, projection, 16), Tone.Faint)
        }
        return add3(point, mul3(axis, h))
    }

    private fun innerOf(path: IsoPath?) =
        if (path == null || path.isEmpty) null else art { line(path, Tone.Faint) }

    companion object {
        /** `smooth` left to the part: on, with 3 or 5 rows, when any knot is smooth. */
        val AUTO_SMOOTH = Smooth()
    }
}

/** How a solid is inked: its edge [tone], its [crease] tone (null for none), an [inner] line. */
data class Style(
    val tone: Tone = Tone.Hi,
    val crease: Tone? = Tone.Faint,
    val inner: IsoPath? = null,
)

/** The original `turned`'s options. [smooth] is [PartRecorder.AUTO_SMOOTH] unless given. */
data class TurnedOptions(
    val inner: IsoPath? = null,
    val bias: Double = 0.0,
    val move: Point3? = null,
    val rims: Rims = Rims.All,
    val bevel: Double = 0.0,
    val steps: Int? = null,
    val name: String? = null,
    val owner: String? = null,
    val smooth: Smooth? = PartRecorder.AUTO_SMOOTH,
    val extra: ArtBuilder.() -> Unit = {},
)

/** The original `flangeAt`'s options. */
data class FlangeOptions(
    val bolts: Int = 8,
    val tone: Tone = Tone.Hi,
    val move: Point3? = null,
    val bias: Double = 0.0,
    val boltR: Double? = null,
    val name: String? = null,
    val owner: String? = null,
)

/** The original `boxAt`'s options. */
data class BoxOptions(
    val bias: Double = 0.0,
    val move: Point3? = null,
    val bevel: Double = 0.5,
    val steps: Int = 3,
    val name: String? = null,
    val owner: String? = null,
    val extra: ArtBuilder.() -> Unit = {},
)

/** The original `boss`'s and `padOn`'s options; a [host] makes a boss a pad. */
data class BossOptions(
    val move: Point3? = null,
    val tone: Tone = Tone.Hi,
    val bias: Double = 0.1,
    val name: String? = null,
    val host: List<AuditShape>? = null,
    val ring: Boolean = true,
    val owner: String? = null,
)

/** The original `tubeItems`'s options. */
data class TubeOptions(
    val tone: Tone = Tone.Hi,
    val bias: Double = 0.0,
    val move: Point3? = null,
    val maxLength: Double = 30.0,
    val spacing: Double = 2.2,
    val closed: Boolean = false,
    val owner: String? = null,
    val touch: List<String> = emptyList(),
    val bundle: String? = null,
    val limp: Boolean = false,
    val breaks: List<Double> = emptyList(),
    val gaps: List<Pair<Double, Double>> = emptyList(),
    val caps: Pair<Boolean, Boolean> = true to true,
    val ends: Pair<Boolean, Boolean> = true to true,
    val rims: List<Double>? = null,
    val free: Pair<Boolean, Boolean> = false to false,
)

/** Drawn [SolidPaths] with a [Style]: the original's `S(paths, style)`. */
fun ArtBuilder.solid(paths: SolidPaths, style: Style) =
    solid(
        paths,
        style.tone,
        style.crease,
        inner = style.inner?.takeUnless { it.isEmpty }?.let { art { line(it, Tone.Faint) } },
    )
