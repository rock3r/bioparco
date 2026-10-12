// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Art
import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.Free
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Projection
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.audit.AuditShape
import dev.sebastiano.anatomy.kit.audit.Recorder
import dev.sebastiano.anatomy.kit.audit.ShapeMeta
import dev.sebastiano.anatomy.kit.audit.prism as prismShape
import dev.sebastiano.anatomy.kit.cross3
import dev.sebastiano.anatomy.kit.jsHypot
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.roundedPlan
import dev.sebastiano.anatomy.kit.sub3
import dev.sebastiano.anatomy.kit.unit3
import kotlin.math.abs

/*
 * The builder of `kit/turn-build.mjs`: `turning(P, { recorder })`. Groups (turn, slide, hinge,
 * free) carry convex parts in live layers; every constructor takes world coordinates at rest and
 * records the part's true shape in the kit's recorder, so the kit audit sees live parts too.
 * `build` (TurnBuild.kt) proves every pair and every static layer and packs the runtime's data.
 */

/** A rigid frame with a pose: what parts ride. */
class TurnGroup
internal constructor(
    val name: String,
    val index: Int,
    val parent: TurnGroup?,
    val kind: GroupKind,
    /** World rest point the group's parts are measured from. */
    val origin: Point3,
    val pivot: Floor? = null,
    val direction: Point3? = null,
    val travel: Double = 0.0,
    val point: Point3? = null,
    val axis: Point3? = null,
    /** Value range: degrees for a hinge, `0…travel` for a slide, null for turn and free. */
    val range: Pair<Double, Double>? = null,
) {
    /** True while every rotation up the chain is about a vertical axis. */
    val upright: Boolean =
        kind != GroupKind.Free &&
            (parent?.upright ?: true) &&
            (kind != GroupKind.Hinge || abs(axis!!.z) > 0.9999)

    /** True for a free group and everything it carries. */
    val loose: Boolean = kind == GroupKind.Free || parent?.loose == true

    internal fun ancestors(): List<TurnGroup> = generateSequence(this) { it.parent }.toList()
}

/** A live layer: one independently stacked sheet of live parts. */
class LiveLayer internal constructor(val name: String, val index: Int)

/** How a part is drawn: edge tone, crease tone (null: none), the lit accent, its material. */
data class PartStyle(
    val tone: Tone = Tone.Hi,
    val crease: Tone? = Tone.Faint,
    val lit: Boolean = false,
    val material: Material? = null,
    /** The tube or route that owns this part (its port, gland or clamp). */
    val owner: String? = null,
)

/** A bolt circle at [s] along a part's axis: [count] dots on radius [r]. */
data class Bolt(
    val s: Double,
    val r: Double,
    val count: Int,
    val phase: Double = 0.0,
    val size: Double = 0.5,
    val tone: Tone = Tone.Mid,
    val fade: Pair<Double, Double> = 0.04 to 0.42,
)

/** A dot at `(s, x, y)` in the part's frame (see `references/3d.md`, "Details"). */
data class DetailDot(
    val at: Point3,
    val normal: Point3? = null,
    val size: Double = 0.5,
    val tone: Tone = Tone.Mid,
    val fade: Pair<Double, Double> = 0.04 to 0.42,
)

/** A ring of radius [r] round `(s, x, y)`, in the plane normal to [normal]. */
data class DetailRing(
    val at: Point3,
    val r: Double,
    val normal: Point3? = null,
    val tone: Tone = Tone.Lo,
    val fade: Pair<Double, Double> = 0.04 to 0.3,
)

/** A rule line through `(s, x, y)` points, in their plane. */
data class DetailRule(
    val points: List<Point3>,
    val tone: Tone = Tone.Lo,
    val free: Free = Free.None,
    val normal: Point3? = null,
    val fade: Pair<Double, Double> = 0.04 to 0.3,
)

/** A part's details, in its frame. Each constructor takes its own kinds; others are ignored. */
data class Details(
    val seams: List<Double> = emptyList(),
    val ribs: RibSpec? = null,
    val bolts: List<Bolt> = emptyList(),
    val dots: List<DetailDot> = emptyList(),
    val rings: List<DetailRing> = emptyList(),
    val rules: List<DetailRule> = emptyList(),
    val bevel: Double? = null,
)

/** A plane on a part as the builder keeps it: rest-local placement, fade, and what it draws. */
class BuildPlane(
    val o: Point3,
    val u: Point3,
    val v: Point3,
    val n: Point3,
    val fade: Pair<Double, Double>,
    val billboard: Boolean,
    val content: PlaneArt,
)

/** A live part while the figure is built. */
class TurnPart
internal constructor(
    var id: Int,
    val name: String,
    val kind: PartKind,
    val group: TurnGroup?,
    val layer: LiveLayer,
    val style: PartStyle,
    val details: Details,
) {
    val shapes = ArrayList<AuditShape>()
    val planes = ArrayList<BuildPlane>()
    val dots = ArrayList<DotData>()

    /** Sample points in the group's rest-local coordinates. */
    val samples = ArrayList<Point3>()

    /** Directions that may separate this part from others. */
    val hints = ArrayList<Point3>()
    internal lateinit var spec: PartSpec
    internal var seams: List<Double>? = null
    internal var ribs: RibSpec? = details.ribs
    internal var art: Art? = null
    internal var world: Any? = null
    internal var round: RoundWorld? = null

    /** The route a tube chunk belongs to, and its index along it. */
    var route: String? = null
        internal set

    var chunk: Int = 0
        internal set

    internal var hue: Tone? = null
    internal var dead = false
}

/** A prism at rest in the world. */
internal class PrismWorld(val frame: Frame, val ring: List<Floor>, val s0: Double, val s1: Double)

/** A round at rest in the world. */
internal class RoundWorld(
    val frame: Frame,
    val s0: Double,
    val s1: Double,
    val r0: Double,
    val r1: Double,
    val ends: List<RoundEnd>,
)

/** A tube route as the builder keeps it. */
class TurnRoute
internal constructor(
    val name: String,
    val group: TurnGroup?,
    /** Dense points in the group's rest-local coordinates. */
    val points: List<Point3>,
    val r: Double,
    val spacing: Double,
    val wide: Boolean,
    val hue: Tone?,
    val rings: dev.sebastiano.anatomy.kit.Rings?,
    /** The route at rest in the world, as given. */
    val world: List<Point3>,
    val gaps: List<Pair<Double, Double>>,
    val breaks: List<Double>,
    val chunk: Double,
    val caps: Pair<Boolean, Boolean>,
)

/** A static item and the layer of the stack it is painted in. */
class StaticItem(val name: String, val layer: String, val shapes: List<AuditShape>)

/**
 * The builder `T`. Pass the kit [recorder] so every live shape is recorded for the kit audit too.
 *
 * ```
 * val T = Turning(P, R)
 * val dial = T.turnGroup("dial", pivot = Floor(0.0, 0.0))
 * val hand = T.layer("hand")
 * T.stack(listOf("back", "hand", "front"))
 * T.round("dial.body", dial, hand, VERT, 10.0, 14.4, 50.0, style = PartStyle(material = Material.Steel))
 * val built = T.build(statics, poses)
 * ```
 */
class Turning(val projection: Projection, val recorder: Recorder? = null, val scale: Double = 1.0) {
    val groups = ArrayList<TurnGroup>()
    val layers = ArrayList<LiveLayer>()
    val parts = ArrayList<TurnPart>()
    val routes = ArrayList<TurnRoute>()

    /** The full stack of live and static layer names, back to front. */
    var order: List<String> = emptyList()
        private set

    /** What [build] made, once it ran. */
    var built: TurnBuild? = null
        internal set

    private fun add(group: TurnGroup): TurnGroup {
        require(groups.none { it.name == group.name }) { "turning: duplicate group ${group.name}" }
        groups.add(group)
        return group
    }

    /** A rotation about the vertical through [pivot], value in degrees. */
    fun turnGroup(
        name: String,
        pivot: Floor = Floor(0.0, 0.0),
        parent: TurnGroup? = null,
    ): TurnGroup =
        add(
            TurnGroup(
                name,
                groups.size,
                parent,
                GroupKind.Turn,
                Point3(pivot.x, pivot.y, 0.0),
                pivot = pivot,
            )
        )

    /** A translation along [direction], value `0…travel` world units. */
    fun slideGroup(
        name: String,
        direction: Point3,
        travel: Double,
        parent: TurnGroup? = null,
    ): TurnGroup =
        add(
            TurnGroup(
                name,
                groups.size,
                parent,
                GroupKind.Slide,
                parent?.origin ?: Point3(0.0, 0.0, 0.0),
                direction = unit3(direction),
                travel = travel,
                range = 0.0 to travel,
            )
        )

    /** A rotation about [axis] through [point], value in degrees within [range]. */
    fun hingeGroup(
        name: String,
        point: Point3,
        axis: Point3,
        range: Pair<Double, Double>,
        parent: TurnGroup? = null,
    ): TurnGroup =
        add(
            TurnGroup(
                name,
                groups.size,
                parent,
                GroupKind.Hinge,
                point,
                point = point,
                axis = unit3(axis),
                range = range,
            )
        )

    /** A free rigid body whose pose the figure sets every frame (`null` is rest). */
    fun freeGroup(name: String, origin: Point3? = null, parent: TurnGroup? = null): TurnGroup =
        add(
            TurnGroup(
                name,
                groups.size,
                parent,
                GroupKind.Free,
                origin ?: parent?.origin ?: Point3(0.0, 0.0, 0.0),
            )
        )

    /** A live layer. */
    fun layer(name: String): LiveLayer = LiveLayer(name, layers.size).also { layers.add(it) }

    /** The full stack of live and static layer names, back to front. */
    fun stack(names: List<String>): Turning = apply { order = names.toList() }

    internal fun origin(group: TurnGroup?) = group?.origin ?: Point3(0.0, 0.0, 0.0)

    internal fun local(group: TurnGroup?, p: Point3) = sub3(p, origin(group))

    internal fun common(
        name: String,
        kind: PartKind,
        group: TurnGroup?,
        layer: LiveLayer,
        style: PartStyle,
        details: Details,
    ) = TurnPart(parts.size, name, kind, group, layer, style, details)

    internal fun meta(part: TurnPart) = ShapeMeta(name = part.name, owner = part.style.owner)

    internal fun record(part: TurnPart, shape: AuditShape): AuditShape {
        part.shapes.add(shape)
        recorder?.solid(shape)
        return shape
    }

    internal fun toLocalDots(part: TurnPart, f: Frame, list: List<DetailDot>) {
        for (dot in list) {
            val (s, x, y) = dot.at
            val p = add3(add3(f.o, mul3(f.a, s)), add3(mul3(f.u, x), mul3(f.v, y)))
            part.dots.add(
                DotData(
                    local(part.group, p),
                    dot.normal?.let { unit3(it) },
                    dot.size,
                    dot.tone,
                    dot.fade,
                )
            )
        }
    }

    /** An upright kit slab: [plan] from [z] up by [h], with the kit `extrude` rule. */
    fun prism(
        name: String,
        group: TurnGroup?,
        layer: LiveLayer,
        plan: Plan,
        z: Double,
        h: Double,
        steps: Int = 4,
        bevel: Double? = null,
        style: PartStyle = PartStyle(),
        details: Details = Details(),
    ): TurnPart {
        val floor =
            Frame(
                Point3(0.0, 0.0, 0.0),
                Point3(0.0, 0.0, 1.0),
                Point3(1.0, 0.0, 0.0),
                Point3(0.0, 1.0, 0.0),
            )
        return prismOf(
            name,
            group,
            layer,
            floor,
            roundedPlan(plan, steps),
            z,
            z + h,
            bevel,
            style,
            details,
        )
    }

    /**
     * A convex [polygon] in frame [frame] (`u`, `v`) extruded along its axis from [s0] to [s1]: the
     * `prismOf` rule, any axis. [round] rounds its corners.
     */
    fun prism(
        name: String,
        group: TurnGroup?,
        layer: LiveLayer,
        frame: Frame,
        polygon: List<Floor>,
        s0: Double,
        s1: Double,
        round: Double = 0.0,
        steps: Int = 4,
        bevel: Double? = null,
        style: PartStyle = PartStyle(),
        details: Details = Details(),
    ): TurnPart =
        prismOf(
            name,
            group,
            layer,
            frameFrom(frame),
            roundedPolygon(polygon, round, steps),
            s0,
            s1,
            bevel,
            style,
            details,
        )

    private fun prismOf(
        name: String,
        group: TurnGroup?,
        layer: LiveLayer,
        f: Frame,
        ring0: List<Floor>,
        a0: Double,
        b0: Double,
        bevelOption: Double?,
        style: PartStyle,
        details: Details,
    ): TurnPart {
        val part = common(name, PartKind.Prism, group, layer, style, details)
        val s0 = minOf(a0, b0)
        val s1 = maxOf(a0, b0)
        val ring = if (signedArea(ring0) < 0) ring0.reversed() else ring0
        convexOrThrow(ring, part.name)
        val bevel = bevelOption ?: details.bevel ?: 0.0
        val upright = abs(f.a.z) > 0.9999 && (group == null || group.upright)
        record(part, prismShape(f, ring, s0, s1, meta(part)))
        fun corner(s: Double, p: Floor) =
            add3(add3(f.o, mul3(f.a, s)), add3(mul3(f.u, p.x), mul3(f.v, p.y)))
        part.world = PrismWorld(f, ring, s0, s1)
        if (upright) {
            val flip = f.a.z < 0
            val pts = ring.map { local(group, corner(if (flip) s1 else s0, it)) }
            var xy = pts.map { Floor(it.x, it.y) }
            if (signedArea(xy) < 0) xy = xy.reversed()
            val z0 = pts[0].z
            val z1 = z0 + (s1 - s0)
            part.spec =
                PrismSpec(
                    true,
                    xy.flatMap { listOf(it.x, it.y) }.toDoubleArray(),
                    z0,
                    z1,
                    null,
                    0.0,
                    0.0,
                    bevel,
                )
            part.seams = details.seams.map { if (flip) z1 - (it - s0) else z0 + (it - s0) }
        } else {
            val local = Frame(local(group, f.o), f.a, f.u, f.v)
            part.spec =
                PrismSpec(
                    false,
                    ring.flatMap { listOf(it.x, it.y) }.toDoubleArray(),
                    0.0,
                    0.0,
                    local,
                    s0,
                    s1,
                    bevel,
                )
            part.seams = details.seams.toList()
        }
        toLocalDots(part, f, details.dots)
        for (s in doubleArrayOf(s0, s1)) for (p in ring) part.samples.add(
            local(group, corner(s, p))
        )
        part.hints.add(f.a)
        val n = ring.size
        for (i in 0 until n) {
            val a = ring[i]
            val b = ring[(i + 1) % n]
            val l = jsHypot(b.x - a.x, b.y - a.y)
            if (l > 1e-6)
                part.hints.add(unit3(add3(mul3(f.u, (b.y - a.y) / l), mul3(f.v, -(b.x - a.x) / l))))
        }
        parts.add(part)
        return part
    }

    /** Line or text markup in a plane of [part], placed by one matrix and faded by facing. */
    fun plane(
        part: TurnPart,
        o: Point3,
        u: Point3,
        v: Point3,
        content: PlaneArt,
        fade: Pair<Double, Double> = 0.04 to 0.3,
        normal: Point3? = null,
    ): TurnPart {
        val uu = unit3(u)
        val ww = unit3(v)
        val n = if (normal != null) unit3(normal) else unit3(cross3(uu, ww))
        part.planes.add(BuildPlane(local(part.group, o), uu, ww, n, fade, false, content))
        return part
    }

    /** Screen-aligned markup at a projected point (in viewBox px), faded by facing. */
    fun billboard(
        part: TurnPart,
        at: Point3,
        normal: Point3,
        content: PlaneArt,
        fade: Pair<Double, Double> = 0.04 to 0.3,
    ): TurnPart {
        part.planes.add(
            BuildPlane(
                local(part.group, at),
                Point3(1.0, 0.0, 0.0),
                Point3(0.0, 1.0, 0.0),
                unit3(normal),
                fade,
                true,
                content,
            )
        )
        return part
    }

    internal fun drop(part: TurnPart) {
        parts.remove(part)
        parts.forEachIndexed { index, p -> p.id = index }
    }

    /** Proves every pair and every static item's layer, and packs the runtime's data. */
    fun build(
        statics: List<StaticItem> = emptyList(),
        poses: List<TurnValues> = emptyList(),
        strict: Boolean = true,
    ): TurnBuild = buildTurning(this, statics, poses, strict).also { built = it }
}
