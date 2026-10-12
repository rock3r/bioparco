// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.audit

import dev.sebastiano.anatomy.kit.Art
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Projection
import dev.sebastiano.anatomy.kit.cut
import dev.sebastiano.anatomy.kit.dot3
import dev.sebastiano.anatomy.kit.pathLength
import dev.sebastiano.anatomy.kit.tangentAlong
import kotlin.math.max
import kotlin.math.min

/*
 * The recorder: a figure puts each drawn item into it with its art and its true shapes, records
 * every solid and every pipe or cable route, settles the painter's keys from the geometry, and
 * paints the items in the settled order. The original's `R.put({ svg, … })` takes the item's SVG
 * string; here it takes the item's [Art].
 */

/**
 * One drawn item: its [art], the [shapes] it draws, its painter's [key] (settled by
 * [Recorder.settle]), and the assembly it highlights with ([piece]) and rides ([ride], moving by
 * [move] at share 1). A tube chunk carries its [route] and [chunk] index.
 */
class AuditItem(
    val name: String,
    var key: Double,
    override val piece: String,
    override val ride: String,
    override val move: Point3,
    val shapes: List<AuditShape>,
    val art: Art? = null,
    val route: String? = null,
    val chunk: Int = 0,
) : Mover {
    internal fun withShapes(shapes: List<AuditShape>): AuditItem =
        AuditItem(name, key, piece, ride, move, shapes, art, route, chunk)
}

/** What [Recorder.settle] measured: the items, their overlaps, the solved keys, the states. */
class Settled(
    val items: List<AuditItem>,
    val overlaps: Overlaps,
    val solved: OrderSolution,
    val states: List<ExplodeState>,
)

/**
 * Records a figure for the audit. [order] and [spring] are the live page's stagger and springs, so
 * the audit replays what the viewer sees; [ground] says what stands on the ground.
 *
 * ```
 * val R = Recorder(P)
 * val base = R.solid(slab(plan, 0.0, 4.0, ShapeMeta(name = "stand.base")))
 * R.put(art { solid(slabOf(plan, 0.0, 4.0, P)) }, listOf(base), at = centre, name = "base")
 * R.route("pipe", legs, 1.2)
 * R.settle()
 * val drawn = R.paintedArt()          // every item's art, in settled painter's order
 * val run = auditReport(R)            // the report, and how many problems fail it
 * ```
 */
class Recorder(
    val projection: Projection,
    val order: List<String> = emptyList(),
    val spring: SpringSpec = SpringSpec(),
    val ground: (AuditShape) -> Boolean = ::grounded,
) {
    /** The camera's toward-viewer direction. */
    val v: Point3 = cameraBasis(projection).v
    private val counts = HashMap<String, Int>()
    private val itemList = ArrayList<AuditItem>()
    private val solidList = ArrayList<AuditShape>()
    private val tubeList = ArrayList<TubeShape>()
    private val routeList = ArrayList<AuditRoute>()

    val items: List<AuditItem>
        get() = itemList

    val solids: List<AuditShape>
        get() = solidList

    val tubes: List<TubeShape>
        get() = tubeList

    val routes: List<AuditRoute>
        get() = routeList

    /** What the last [settle] measured, or null before it runs. */
    var settled: Settled? = null
        private set

    /** Numbers repeated names: `base`, `base#2`, … */
    fun nameFor(base: String): String {
        val count = (counts[base] ?: 0) + 1
        counts[base] = count
        return if (count == 1) base else "$base#$count"
    }

    /** Records [shape] as a solid and returns it, for an item's shapes or for static parts. */
    fun <T : AuditShape> solid(shape: T): T = shape.also { solidList.add(it) }

    /**
     * Adds a drawn item: its [art] and true [shapes]. Its key is `dot3(at, V) + bias` unless you
     * pass [key]; its [shapes] are stamped with its [move], [ride] and [piece]. A tube chunk passes
     * its [route] and [chunk] index, so neighbouring chunks aren't measured against each other.
     */
    fun put(
        art: Art?,
        shapes: List<AuditShape> = emptyList(),
        at: Point3? = null,
        bias: Double = 0.0,
        key: Double? = null,
        name: String? = null,
        piece: String = "",
        move: Point3 = ZERO,
        ride: String? = null,
        route: String? = null,
        chunk: Int = 0,
    ): AuditItem {
        val item =
            AuditItem(
                name ?: nameFor("${piece.ifEmpty { "item" }}.item"),
                key ?: ((if (at != null) dot3(at, v) else 0.0) + bias),
                piece,
                ride ?: piece,
                move,
                shapes,
                art,
                route,
                chunk,
            )
        for (shape in shapes) {
            shape.move = item.move
            shape.ride = item.ride
            shape.piece = piece
        }
        itemList.add(item)
        return item
    }

    /** Records a pipe or cable of one radius. See the other [route]. */
    fun route(
        name: String,
        points: List<Point3>,
        radius: Double,
        options: RouteOptions = RouteOptions(),
    ): List<TubeShape> = route(name, points, { _: Double -> radius }, options)

    /**
     * Records a pipe or cable whose radius is a function of the share 0..1 of its length: one tube
     * per drawn run between the [RouteOptions.gaps], with its tips, tip axes, rims and free ends,
     * plus the route for `folds` and `crowding`. Returns the runs.
     */
    fun route(
        name: String,
        points: List<Point3>,
        radius: (Double) -> Double,
        options: RouteOptions = RouteOptions(),
    ): List<TubeShape> {
        val o = options
        val total = pathLength(points)
        val runs = runsAlong(total, o.gaps)
        val ride = o.ride ?: o.piece
        val meta =
            ShapeMeta(
                name = name,
                owner = o.owner ?: name,
                touch = o.touch,
                bundle = o.bundle,
                limp = o.limp,
                joined = true,
            )
        val axes = listOf(tangentAlong(points, 0.0), tangentAlong(points, total))
        val recorded = runs.mapIndexed { index, (from, to) ->
            val first = index == 0
            val last = index == runs.size - 1
            val shape =
                tube(cut(points, from, to), { t -> radius((from + (to - from) * t) / total) }, meta)
            TubeShape(
                    shape.points,
                    shape.radii,
                    meta,
                    tips =
                        if (o.closed) listOf(null, null)
                        else
                            listOf(
                                if (first && o.ends.first) points[0] else null,
                                if (last && o.ends.second) points[points.size - 1] else null,
                            ),
                    tipAxes = axes,
                    tipRims = o.rims ?: listOf(radius(0.0), radius(1.0)),
                    free = listOf(first && o.free.first, last && o.free.second),
                    offset = from,
                    total = total,
                )
                .also {
                    it.move = o.move
                    it.ride = ride
                    it.piece = o.piece
                }
        }
        tubeList.addAll(recorded)
        routeList.add(AuditRoute(name, points, radius(0.5), o.bundle, o.move, ride, o.piece))
        return recorded
    }

    /**
     * Measures true depth wherever two items overlap, in every one of [states] (by default every
     * state the figure rests in and every frame of its real explode), and writes each item's
     * painter's key. It keeps the order the items had; paint them with [painted]. Run it on every
     * build.
     */
    fun settle(
        projection: Projection = this.projection,
        states: List<ExplodeState> = explodeStates(order, spring).depth,
        skip: (AuditItem, AuditItem) -> Boolean = ::sameRun,
        step: Double = 0.25,
        tolerance: Double = 0.3,
        least: Double = 0.25,
    ): Settled {
        val items = itemList.toList()
        val overlaps = overlapsOf(items, projection, step, tolerance, least, states, skip)
        val solved = orderKeys(items, overlaps)
        items.forEachIndexed { index, item -> item.key = solved.keys[index] }
        return Settled(items, overlaps, solved, states).also { settled = it }
    }

    /** The items in painter's order at rest: ascending key, ties in the order they were put. */
    fun painted(): List<AuditItem> = itemList.sortedBy { it.key }

    /**
     * The items in painter's order in an explode [state]: by `key + lift · share` (see [keyAt]),
     * which an exploded figure re-sorts every frame. Each item moves by `move · share` on screen.
     */
    fun painted(state: ExplodeState): List<AuditItem> =
        itemList.indices
            .sortedWith(compareBy<Int> { keyAt(itemList[it], state, v) }.thenBy { it })
            .map { itemList[it] }

    /** How far toward the viewer [item]'s move takes it at share 1: its key grows by this. */
    fun liftOf(item: AuditItem): Double = dot3(item.move, v)

    /** Every item's art, in settled painter's order at rest, as one [Art]. */
    fun paintedArt(): Art = Art(painted().flatMap { it.art?.marks.orEmpty() })
}

/**
 * How a route is recorded: its [owner] (its own flanges, bands and valve), what it may [touch], its
 * [bundle], [limp] for a cable, the [gaps] it is cut at (from and to, by arc length), [closed],
 * which [ends] are tips, the flange or connector radius at each end ([rims]), which ends are [free]
 * to come off apart, and the assembly that moves it.
 */
data class RouteOptions(
    val owner: String? = null,
    val touch: List<String> = emptyList(),
    val bundle: String? = null,
    val limp: Boolean = false,
    val gaps: List<Pair<Double, Double>> = emptyList(),
    val closed: Boolean = false,
    val ends: Pair<Boolean, Boolean> = true to true,
    val rims: List<Double>? = null,
    val free: Pair<Boolean, Boolean> = false to false,
    val piece: String = "",
    val move: Point3 = ZERO,
    val ride: String? = null,
)

/**
 * The drawn runs of a route of length [total] between [gaps], by arc length. This is
 * `kit/tube.mjs`'s `runsOf(total, gaps)`, the one thing the original audit imports from it.
 */
internal fun runsAlong(
    total: Double,
    gaps: List<Pair<Double, Double>>,
): List<Pair<Double, Double>> {
    val cuts =
        gaps
            .map { (a, b) -> max(0.0, min(a, b)) to min(total, max(a, b)) }
            .filter { (a, b) -> b - a > 1e-6 }
            .sortedBy { it.first }
    val runs = ArrayList<Pair<Double, Double>>()
    var at = 0.0
    for ((a, b) in cuts) {
        if (a > at + 1e-6) runs.add(at to a)
        at = max(at, b)
    }
    if (total > at + 1e-6) runs.add(at to total)
    return runs
}
