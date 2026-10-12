package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Art
import dev.sebastiano.anatomy.kit.ArtBuilder
import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.Knot
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Projection
import dev.sebastiano.anatomy.kit.Rings
import dev.sebastiano.anatomy.kit.Smooth
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.audit.AuditShape
import dev.sebastiano.anatomy.kit.audit.Recorder
import dev.sebastiano.anatomy.kit.audit.RouteOptions
import dev.sebastiano.anatomy.kit.audit.ShapeMeta
import dev.sebastiano.anatomy.kit.audit.ball
import dev.sebastiano.anatomy.kit.audit.box
import dev.sebastiano.anatomy.kit.audit.disc
import dev.sebastiano.anatomy.kit.audit.prism
import dev.sebastiano.anatomy.kit.audit.solid
import dev.sebastiano.anatomy.kit.audit.tube
import dev.sebastiano.anatomy.kit.fillet
import dev.sebastiano.anatomy.kit.frameAlong
import dev.sebastiano.anatomy.kit.iso
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.pathLength
import dev.sebastiano.anatomy.kit.pointAlong
import dev.sebastiano.anatomy.kit.prismOf
import dev.sebastiano.anatomy.kit.resample
import dev.sebastiano.anatomy.kit.saddleOf
import dev.sebastiano.anatomy.kit.saddleStart
import dev.sebastiano.anatomy.kit.slabOf
import dev.sebastiano.anatomy.kit.solidOf
import dev.sebastiano.anatomy.kit.sphereOf
import dev.sebastiano.anatomy.kit.sub3
import dev.sebastiano.anatomy.kit.tangentAlong
import dev.sebastiano.anatomy.kit.tubePieces
import dev.sebastiano.anatomy.kit.viewOf
import kotlin.math.max

/*
 * The figure's recording helpers, after the original Raptor's: every part is drawn and recorded
 * in one call, so the audit sees everything the figure paints. Each helper puts one item with its
 * art and its true shape, and returns the shape for the parts that land on it.
 */

/** One drawn chunk of a pipe that carries water: its route and where along it the chunk lies. */
class WaterChunk(val route: String, val from: Double, val to: Double)

/** A pipe's route as `tubePieces` measures it, its radius and its bore. */
class WaterRoute(val points: List<Point3>, val radius: Double, val bore: Double)

internal class Parts(val projection: Projection) {
    val recorder = Recorder(projection)
    val view: Point3 = viewOf(projection)

    /** Every water-carrying chunk by item name, and every water route by name. */
    val chunks = HashMap<String, WaterChunk>()

    /** The boiler's half-shell, which the fittings on top are seated on. */
    var shellShape: AuditShape? = null

    /** Named shapes later parts land on. */
    val named = HashMap<String, AuditShape>()

    /** Art under everything: the halo. */
    val under = ArrayList<Art>()
    val water = LinkedHashMap<String, WaterRoute>()

    fun at(p: Point3) = iso(p, projection)

    /** Puts one item: its art, its shapes, its reference point. Returns the item's name. */
    fun put(
        name: String,
        ref: Point3,
        shapes: List<AuditShape>,
        bias: Double = 0.0,
        draw: ArtBuilder.() -> Unit,
    ): String {
        val id = recorder.nameFor(name)
        recorder.put(art(draw), shapes, at = ref, bias = bias, name = id)
        return id
    }

    fun box(
        name: String,
        min: Point3,
        max: Point3,
        tone: Tone = Tone.Mid,
        r: Double = 0.6,
        steps: Int = 2,
        bevel: Double = 0.0,
        meta: ShapeMeta = ShapeMeta(),
        inner: Art? = null,
        extra: ArtBuilder.() -> Unit = {},
    ): AuditShape {
        val plan = Plan(min.x, min.y, max.x - min.x, max.y - min.y, r)
        val shape = recorder.solid(box(min, max, meta.copy(name = meta.name ?: name)))
        val centre = mul3(add3(min, max), 0.5)
        put(name, centre, listOf(shape)) {
            solid(
                slabOf(plan, min.z, max.z - min.z, projection, steps, bevel),
                tone = tone,
                inner = inner,
            )
            extra()
        }
        return shape
    }

    /** A turned part: [profile] closed to the axis at both ends, along [frame]. */
    fun turned(
        name: String,
        frame: Frame,
        profile: List<Knot>,
        tone: Tone = Tone.Mid,
        crease: Tone? = Tone.Faint,
        lit: Boolean = false,
        bevel: Double = 0.0,
        smooth: Smooth? = Smooth.auto(profile),
        radius: ((Double) -> Double)? = null,
        owner: String? = null,
        bias: Double = 0.0,
        inner: Art? = null,
        extra: ArtBuilder.() -> Unit = {},
    ): AuditShape {
        val paths = solidOf(profile, frame, projection, bevel = bevel, smooth = smooth)
        val curve =
            if (radius == null && smooth != null && profile.any { it.smooth }) profile else null
        val shape =
            recorder.solid(
                solid(
                    frame,
                    profile,
                    radius,
                    ShapeMeta(name = name, owner = owner, curve = curve),
                )
            )
        val mid = (profile.first().s + profile.last().s) / 2
        put(name, add3(frame.o, mul3(frame.a, mid)), listOf(shape), bias) {
            solid(paths, tone = tone, crease = crease, lit = lit, inner = inner)
            extra()
        }
        return shape
    }

    /** A short cylinder from [s0] to [s1] along [frame]. */
    fun disc(
        name: String,
        frame: Frame,
        s0: Double,
        s1: Double,
        r: Double,
        tone: Tone = Tone.Mid,
        bevel: Double = 0.0,
        owner: String? = null,
        bias: Double = 0.0,
        inner: Art? = null,
        extra: ArtBuilder.() -> Unit = {},
    ): AuditShape =
        turned(
            name,
            frame,
            listOf(Knot(s0, r), Knot(s1, r)),
            tone,
            bevel = bevel,
            owner = owner,
            bias = bias,
            inner = inner,
            extra = extra,
        )

    fun ball(
        name: String,
        centre: Point3,
        r: Double,
        tone: Tone = Tone.Mid,
        owner: String? = null,
    ): AuditShape {
        val shape = recorder.solid(ball(centre, r, ShapeMeta(name = name, owner = owner)))
        put(name, centre, listOf(shape)) { solid(sphereOf(centre, r, projection, 40), tone = tone) }
        return shape
    }

    /** A prism of a convex polygon in the frame's plane, from [s0] to [s1] along its axis. */
    fun prism(
        name: String,
        frame: Frame,
        polygon: List<Floor>,
        s0: Double,
        s1: Double,
        tone: Tone = Tone.Mid,
        owner: String? = null,
        extra: ArtBuilder.() -> Unit = {},
    ): AuditShape {
        val shape =
            recorder.solid(prism(frame, polygon, s0, s1, ShapeMeta(name = name, owner = owner)))
        val cx = polygon.sumOf { it.x } / polygon.size
        val cy = polygon.sumOf { it.y } / polygon.size
        val centre =
            add3(
                add3(add3(frame.o, mul3(frame.a, (s0 + s1) / 2)), mul3(frame.u, cx)),
                mul3(frame.v, cy),
            )
        put(name, centre, listOf(shape)) {
            solid(prismOf(frame, polygon, s0, s1, projection), tone = tone)
            extra()
        }
        return shape
    }

    /**
     * A saddle pad of radius [a] on a round [host] of radius [hostR], along [frame] (whose origin
     * is on the host's axis and whose `v` runs along it), up to [s1] from the host's axis.
     */
    fun pad(
        name: String,
        frame: Frame,
        a: Double,
        hostR: Double,
        s1: Double,
        host: AuditShape,
        tone: Tone = Tone.Mid,
    ): Point3 {
        val low = saddleStart(hostR, a, 0.0)
        val shape =
            recorder.solid(
                disc(
                    frame,
                    low - 0.05,
                    s1,
                    a,
                    ShapeMeta(name = name, owner = host.owner, seated = true, cut = listOf(host)),
                )
            )
        put(name, add3(frame.o, mul3(frame.a, (low + s1) / 2)), listOf(shape)) {
            solid(saddleOf(frame, a, hostR, s1, projection), tone = tone)
        }
        return add3(frame.o, mul3(frame.a, s1))
    }

    /**
     * A pipe on [control] points, filleted, with an end flange or nut at each end that has one, and
     * bands where it is clamped. Water-carrying pipes name their [bore] so the live figure can mark
     * their flow. Returns the filleted route.
     */
    fun pipe(
        name: String,
        control: List<Point3>,
        r: Double,
        tone: Tone = Tone.Hi,
        nuts: Pair<Boolean, Boolean> = true to true,
        nutR: Double = r * 1.7,
        nutT: Double = max(1.6, r * 0.9),
        bands: List<Double> = emptyList(),
        bore: Double? = null,
        rings: Rings? = null,
        limp: Boolean = false,
        touch: List<String> = emptyList(),
        maxLength: Double = 16.0,
        bend: Double = 2.6,
        bias: Double = 0.0,
    ): List<Point3> {
        val route = fillet(control, max(r * bend, 3.0), 10, projection = projection, tube = r)
        val dense = resample(route, 2.2)
        val total = pathLength(dense)
        class Collar(val centre: Point3, val tangent: Point3, val radius: Double, val thick: Double)
        val collars = ArrayList<Collar>()
        val gaps = ArrayList<Pair<Double, Double>>()
        if (nuts.first) {
            val tangent = tangentAlong(dense, 0.0)
            collars += Collar(add3(route.first(), mul3(tangent, nutT / 2)), tangent, nutR, nutT)
            gaps += 0.0 to nutT
        }
        if (nuts.second) {
            val tangent = tangentAlong(dense, total)
            collars += Collar(sub3(route.last(), mul3(tangent, nutT / 2)), tangent, nutR, nutT)
            gaps += total - nutT to total
        }
        for (share in bands) {
            val along = total * share
            val thick = max(1.2, r * 0.8)
            collars += Collar(pointAlong(dense, along), tangentAlong(dense, along), r * 1.3, thick)
            gaps += along - thick / 2 to along + thick / 2
        }
        recorder.route(
            name,
            route,
            r,
            RouteOptions(
                owner = name,
                touch = touch,
                limp = limp,
                gaps = gaps,
                rims = listOf(if (nuts.first) nutR else r, if (nuts.second) nutR else r),
            ),
        )
        if (bore != null) water[name] = WaterRoute(dense, r, bore)
        tubePieces(
                route,
                r,
                projection,
                maxLength = maxLength,
                spacing = 2.2,
                gaps = gaps,
                rings = rings,
            )
            .forEachIndexed { index, piece ->
                val id = "$name:$index"
                recorder.put(
                    art { tube(piece, tone) },
                    listOf(tube(piece.points, piece.radii)),
                    at = piece.mid,
                    bias = bias,
                    name = id,
                    route = name,
                    chunk = index,
                )
                if (bore != null) chunks[id] = WaterChunk(name, piece.from, piece.to)
            }
        for (collar in collars) {
            val frame = frameAlong(collar.centre, collar.tangent)
            disc(
                "$name.nut",
                frame,
                -collar.thick / 2,
                collar.thick / 2,
                collar.radius,
                Tone.Mid,
                owner = name,
                bias = bias,
            )
        }
        return route
    }

    /** A cable or wire: limp, with a crimped sleeve at each end. */
    fun cable(
        name: String,
        control: List<Point3>,
        r: Double = 0.8,
        tone: Tone = Tone.Mid,
    ): List<Point3> =
        pipe(
            name,
            control,
            r,
            tone,
            nutR = r * 1.8,
            nutT = 2.4,
            limp = true,
            bend = 5.0,
            maxLength = 12.0,
        )
}
