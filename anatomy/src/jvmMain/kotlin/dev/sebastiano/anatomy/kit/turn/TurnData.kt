// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Art
import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Projection
import dev.sebastiano.anatomy.kit.Rings
import dev.sebastiano.anatomy.kit.Tone

/*
 * What `T.build` hands the runtime: the original's `out.data` (`DATA.turn`). The original packs it
 * to JSON with every part number rounded to 0.001 and every other number to 0.0001, and the
 * runtime works from the packed numbers; `TurnPack.kt` rounds the same way, so the runtime here
 * sees exactly what the original's sees.
 */

/** The kinds of live part. */
enum class PartKind {
    Prism,
    Round,
    Ball,
    Lathe,
    Tube,
    Fixed,
}

/** How a round's end is closed: flat, a hemispherical dome, or open (a cut). */
enum class RoundEnd {
    Flat,
    Dome,
    Open,
}

/** A cut plane `n·p = d` in the group's rest-local coordinates, left by `T.split`. */
class Cut(val n: Point3, val d: Double)

/** How many faded-line slots a part was given for its bevel and its ribs. */
class Slots(val bevel: Int, val ribs: Int)

/** A part's geometry as the runtime needs it, in its group's rest-local coordinates. */
sealed class PartSpec {
    abstract val slots: Slots?

    abstract fun withSlots(slots: Slots?): PartSpec
}

/** A prism: upright ([up], a plan from [z0] to [z1]) or along any [frame] from [s0] to [s1]. */
class PrismSpec(
    val up: Boolean,
    val ring: DoubleArray,
    val z0: Double,
    val z1: Double,
    val frame: Frame?,
    val s0: Double,
    val s1: Double,
    val bevel: Double,
    val cuts: List<Cut> = emptyList(),
    override val slots: Slots? = null,
) : PartSpec() {
    override fun withSlots(slots: Slots?) =
        PrismSpec(up, ring, z0, z1, frame, s0, s1, bevel, cuts, slots)
}

/** A cylinder or cone frustum along [frame] from [s0] to [s1]. */
class RoundSpec(
    val frame: Frame,
    val s0: Double,
    val s1: Double,
    val r0: Double,
    val r1: Double,
    val ends: List<RoundEnd>,
    val cuts: List<Cut> = emptyList(),
    override val slots: Slots? = null,
) : PartSpec() {
    override fun withSlots(slots: Slots?) = RoundSpec(frame, s0, s1, r0, r1, ends, cuts, slots)
}

/** A flat cut off a ball: `n·(p − c) ≤ d` is kept. */
class Flat3(val n: Point3, val d: Double)

/** A sphere with flats. */
class BallSpec(
    val c: Point3,
    val r: Double,
    val flats: List<Flat3>,
    override val slots: Slots? = null,
) : PartSpec() {
    override fun withSlots(slots: Slots?) = BallSpec(c, r, flats, slots)
}

/** A convex lathe: `(s, r, nr, na)` per level, and whether each end has a cap. */
class LatheSpec(
    val frame: Frame,
    val levels: DoubleArray,
    val caps: Pair<Boolean, Boolean>,
    override val slots: Slots? = null,
) : PartSpec() {
    override fun withSlots(slots: Slots?) = LatheSpec(frame, levels, caps, slots)
}

/** One chunk of a tube route, from [from] to [to] along it. */
class TubeSpec(
    val route: Int,
    val from: Double,
    val to: Double,
    val open0: Boolean,
    val open1: Boolean,
    val gap0: Boolean,
    val gap1: Boolean,
    val caps: Pair<Boolean, Boolean>,
    val wide: Boolean,
    val ringTone: Tone?,
    override val slots: Slots? = null,
) : PartSpec() {
    override fun withSlots(slots: Slots?) =
        TubeSpec(route, from, to, open0, open1, gap0, gap1, caps, wide, ringTone, slots)
}

/**
 * A picture that never changes under its group's motion, moved by a translation: [anchor] in the
 * group's rest-local coordinates, drawn at rest at [home] on screen, its screen [box] and [hull] at
 * rest, and an optional detail frame.
 */
class FixedSpec(
    val anchor: Point3,
    val home: Flat,
    val box: DoubleArray,
    val hull: DoubleArray,
    val frame: Frame?,
    override val slots: Slots? = null,
) : PartSpec() {
    override fun withSlots(slots: Slots?) = FixedSpec(anchor, home, box, hull, frame, slots)
}

/**
 * Knurl rows on a round surface: [count] lines from [s0] to [s1], twisted by [twist] radians, each
 * faded by facing over [fade]. [seams] draws the rows' end rings: both, `"first"`, `"last"`.
 */
data class RibSpec(
    val s0: Double,
    val s1: Double,
    val count: Int,
    val phase: Double = 0.0,
    val twist: Double = 0.0,
    val fade: Pair<Double, Double> = 0.1 to 0.55,
    val seams: RibSeams = RibSeams.None,
    val tone: Tone = Tone.Lo,
    val r0: Double? = null,
    val r1: Double? = null,
)

/** Which end rings of a knurl are drawn. */
enum class RibSeams {
    None,
    Both,
    First,
    Last,
}

/** A plane of line or text markup on a part, placed by one matrix and faded by facing. */
class PlaneData(
    val o: Point3,
    val u: Point3,
    val v: Point3,
    val n: Point3,
    val fade: Pair<Double, Double>,
    val billboard: Boolean,
    /** What the plane draws, in its own `u`, `v` units (a billboard's in viewBox px). */
    val content: PlaneArt = PlaneArt.Empty,
)

/** A dot (a screw head, a rivet) on a part, faded by facing when it has a normal. */
class DotData(
    val p: Point3,
    val n: Point3?,
    val size: Double,
    val tone: Tone,
    val fade: Pair<Double, Double>,
)

/** A live part as the runtime sees it. */
class PartData(
    val name: String,
    val kind: PartKind,
    val group: Int,
    val layer: Int,
    val tone: Tone,
    /** Null for `crease: "none"`. */
    val crease: Tone?,
    val lit: Boolean,
    val material: Material?,
    val spec: PartSpec,
    val seams: List<Double>?,
    val ribs: RibSpec?,
    val planes: List<PlaneData>,
    val dots: List<DotData>,
    /** A fixed part's sample cloud, flat `x, y, z`. */
    val cloud: DoubleArray? = null,
    /** The tube's colour, for a coloured sheath. */
    val hue: Tone? = null,
    /** A fixed part's picture at rest. */
    val art: Art? = null,
)

/** A tube route as the runtime sees it: dense points in its group's rest-local coordinates. */
class RouteData(
    val points: DoubleArray,
    val r: Double,
    val spacing: Double,
    val wide: Boolean,
    val hue: Tone?,
    val rings: Rings?,
)

/** A live layer: its parts and their painting order at rest. */
class LayerData(val name: String, val parts: IntArray, val rest: IntArray)

/**
 * Everything the runtime needs, packed: the camera, groups, layers, parts, routes, and per layer
 * the pair relations the build proved (planes in a shared group as `i, j, nx, ny, nz`, framed
 * planes as `i, j, frame, nx, ny, nz`, group pairs, dynamic pairs, free parts, skipped pairs).
 */
class TurnData(
    val projection: Projection,
    val groups: List<GroupData>,
    val layers: List<LayerData>,
    val parts: List<PartData>,
    val routes: List<RouteData>,
    val planes: Map<String, DoubleArray> = emptyMap(),
    val framed: Map<String, DoubleArray> = emptyMap(),
    val groupPairs: Map<String, DoubleArray> = emptyMap(),
    val dynamic: Map<String, IntArray> = emptyMap(),
    val free: Map<String, IntArray> = emptyMap(),
    val skip: Map<String, IntArray> = emptyMap(),
)
