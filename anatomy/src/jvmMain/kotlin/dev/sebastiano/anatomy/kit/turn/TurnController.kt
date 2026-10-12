// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Point3
import kotlin.math.max
import kotlin.math.min

/*
 * `TURN.mount` for Compose: the controller a figure drives from its own frame loop. `set` re-emits
 * every part whose group moved, re-solves each layer's order and keeps the frames for
 * `drawTurnLayer` to paint, so motion redraws the canvas and never recomposes. The original binds
 * SVG elements and writes attributes; here the frames are the state the renderer reads.
 */

/** What one render cost and did. */
data class TurnStats(
    val ms: Double = 0.0,
    val moved: Int = 0,
    val forced: Int = 0,
    val refined: Int = 0,
    val unseparated: Int = 0,
    val emitted: Int = 0,
    val edges: Int = 0,
)

/** A change of painting order in one layer: the parts that moved. */
class OrderEvent(val layer: String, val moved: List<String>)

/** The runtime of a turning figure. */
class TurnController(
    val data: TurnData,
    /** Maps figure values onto group values on every [set] (one input drives coupled groups). */
    private val follow: ((TurnValues) -> TurnValues)? = null,
    /** Runs after each render (draw shaders here). */
    private val onFrame: ((TurnController) -> Unit)? = null,
    unit: Double = 1.0,
) {
    var scene: TurnScene = prepareTurn(data, unit)
        private set

    /** Every part's frame, by part index. */
    val frames: List<TurnFrame> = scene.frames()

    private var current =
        TurnValues(
            data.groups.filter { it.kind != GroupKind.Free }.associate { it.name to 0.0 },
            data.groups.filter { it.kind == GroupKind.Free }.associate { it.name to null },
        )

    /** The values as of the last [set]. */
    val values: TurnValues
        get() = current

    /** This frame's cameras. */
    var cams: TurnCams = scene.cams(current)
        private set

    private var lastPoses: List<GroupPose>? = null
    private val orders = scene.layers.map { it.rest.copyOf() }.toMutableList()
    private val changes = ArrayList<OrderEvent>()
    private var detail = 1.0
    private var unitChanged = false

    /** What the last render cost and did. */
    var stats = TurnStats()
        private set

    /** How many times the frames changed: read it in the draw phase to redraw on every render. */
    var generation = 0L
        private set

    init {
        follow?.let { current = current + it(current) }
        render()
    }

    /**
     * Sets group (and figure) values, renders now, returns the stats. [detail] (0…1) scales faded
     * lines, ribs and dots, for fast motion.
     */
    fun set(next: TurnValues, detail: Double? = null): TurnStats {
        if (detail != null) {
            val value = max(0.0, min(1.0, detail))
            if (value != this.detail) {
                this.detail = value
                lastPoses = null
            }
        }
        current = current + next
        follow?.let { current = current + it(current) }
        return render()
    }

    /** Sets the first turn group to [angle] degrees. */
    fun set(angle: Double): TurnStats {
        val g = data.groups.firstOrNull { it.kind == GroupKind.Turn } ?: return render()
        return set(TurnValues(mapOf(g.name to angle)))
    }

    /** Re-prepares for a new screen scale (sampling densities follow device pixels). */
    fun resize(unit: Double) {
        if (kotlin.math.abs(unit - scene.unit) < 0.05) return
        scene = prepareTurn(data, unit)
        unitChanged = true
        render()
    }

    /** Re-emits what moved and re-solves the order. */
    fun render(): TurnStats {
        val t0 = System.nanoTime()
        val cams = scene.cams(current)
        this.cams = cams
        TurnState.detail = detail
        val poses = cams.poses
        val last = lastPoses
        val fresh = unitChanged || last == null
        var emitted = 0
        for (run in scene.runs) {
            val g = run.group
            val changed = fresh || (g >= 0 && !samePose(poses[g], last!![g]))
            if (!changed) continue
            run.emit(cams.of(g), frames[run.index])
            emitted++
        }
        lastPoses = poses
        unitChanged = false
        val orderStats = OrderStats()
        var moved = 0
        scene.layers.forEachIndexed { index, layer ->
            val previous = orders[index]
            val order = turnOrder(layer, frames, cams, previous, orderStats, scene)
            if (order.contentEquals(previous)) return@forEachIndexed
            val shifted = movedParts(previous, order)
            moved += shifted.size
            orders[index] = order
            if (shifted.isNotEmpty()) changes.add(OrderEvent(layer.name, shifted))
        }
        TurnState.detail = 1.0
        stats =
            TurnStats(
                (System.nanoTime() - t0) / 1e6,
                moved,
                orderStats.forced,
                orderStats.refined,
                orderStats.unseparated,
                emitted,
                orderStats.edges,
            )
        generation++
        onFrame?.invoke(this)
        return stats
    }

    /** The parts that move when [previous] becomes [order]: those off its longest kept run. */
    private fun movedParts(previous: IntArray, order: IntArray): List<String> {
        val position = HashMap<Int, Int>()
        previous.forEachIndexed { at, global -> position[global] = at }
        val keep = t3Keep(IntArray(order.size) { position.getValue(order[it]) })
        return order.indices.reversed().filter { !keep[it] }.map { scene.runs[order[it]].name }
    }

    private fun samePose(a: GroupPose, b: GroupPose): Boolean {
        for (i in 0 until 9) if (a.r[i] != b.r[i]) return false
        return a.t.x == b.t.x && a.t.y == b.t.y && a.t.z == b.t.z
    }

    private val unwrap = HashMap<String, Double>()

    /**
     * A viewBox point as an angle about [group]'s pivot (the first turn group's by default) on the
     * plane at height [z], unwrapped against the last call so a drag can turn many times.
     */
    fun angleAt(x: Double, y: Double, z: Double = 0.0, group: String? = null): Double {
        val c = t3Camera(scene.projection, null)
        val p = (x - c.ox) / c.k
        val q = (y - c.oy + z * c.ce * c.k) / (c.se * c.k)
        val wx = p * c.sa + q * c.ca
        val wy = -p * c.ca + q * c.sa
        val g =
            data.groups.firstOrNull { it.name == group }
                ?: data.groups.firstOrNull { it.kind == GroupKind.Turn }
        val pivot = g?.origin ?: Point3(0.0, 0.0, 0.0)
        val raw = (jsAtan2(wy - pivot.y, wx - pivot.x) * 180) / Math.PI
        val key = g?.name ?: ""
        val last = unwrap[key]
        val angle = if (last != null) last + ((((raw - last) % 360) + 540) % 360) - 180 else raw
        unwrap[key] = angle
        return angle
    }

    /** The painting order of a layer, by part name. */
    fun order(layer: String? = null): List<String> =
        orderIndex(layer)?.map { scene.runs[it].name } ?: emptyList()

    /** The painting order of a layer, by part index. */
    fun orderIndex(layer: String? = null): IntArray? {
        val index = if (layer == null) 0 else scene.layers.indexOfFirst { it.name == layer }
        return if (index < 0) null else orders[index]
    }

    /** The order changes since the last call. */
    fun events(): List<OrderEvent> = changes.toList().also { changes.clear() }

    /** A group's world pose. */
    fun pose(name: String): GroupPose = poseOf(scene, cams, name)

    /** The free value that continues [name]'s current pose in the world frame, with no jump. */
    fun detach(name: String): FreePose {
        val index = scene.groups.indexOfFirst { it.name == name }
        val pose = poseOf(scene, cams, name)
        val o = scene.groups[index].origin
        return FreePose(
            r = pose.r,
            t = Point3(pose.t.x - o.x, pose.t.y - o.y, pose.t.z - o.z),
            world = true,
        )
    }

    /** Where a rest-world point (and direction) of [group] is now. */
    fun anchor(group: String?, point: Point3, direction: Point3? = null): TurnAnchor =
        turnAnchor(scene, cams, group, point, direction)
}

/** The longest increasing run of [values], flagged: what can stay put. `t3Keep`. */
internal fun t3Keep(values: IntArray): BooleanArray {
    val n = values.size
    val tails = ArrayList<Int>()
    val tailAt = ArrayList<Int>()
    val back = IntArray(n) { -1 }
    for (i in 0 until n) {
        var lo = 0
        var hi = tails.size
        while (lo < hi) {
            val mid = (lo + hi) shr 1
            if (tails[mid] < values[i]) lo = mid + 1 else hi = mid
        }
        if (lo == tails.size) {
            tails.add(values[i])
            tailAt.add(i)
        } else {
            tails[lo] = values[i]
            tailAt[lo] = i
        }
        back[i] = if (lo > 0) tailAt[lo - 1] else -1
    }
    val keep = BooleanArray(n)
    var k = if (tails.isNotEmpty()) tailAt[tails.size - 1] else -1
    while (k >= 0) {
        keep[k] = true
        k = back[k]
    }
    return keep
}

/**
 * Review tools' hold on a turning figure, the original's `window.__isoTurn.set`: while [values]
 * is set, a figure that honours it stops its tour and shows those values (`capture --turn`).
 */
object TurnHook {
    @Volatile var values: Map<String, Double>? = null
}
