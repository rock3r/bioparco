// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Projection

/*
 * `t3Prepare`: the packed data turned into runs (one per part, with everything its emit needs
 * computed once) and layers (the pair relations unpacked). A run's cloud (sample points in its
 * group's rest-local coordinates) and axes feed the runtime's separating planes.
 */

/** A prepared part: its roles and what its emit reads. Each kind computes its own frame. */
abstract class TurnRun(val index: Int, val part: PartData, val unit: Double) {
    val name: String = part.name
    val kind: PartKind = part.kind
    val group: Int = part.group
    val layer: Int = part.layer
    val roles: List<Role> = t3Roles(part)

    /** Role index by name. */
    val role: Map<String, Int> = roles.withIndex().associate { (j, r) -> r.name to j }
    val planes: List<PlaneData> = part.planes
    val dots: List<DotData> = part.dots
    val slots: Slots? = part.spec.slots

    /** Sample points, flat `x, y, z`, in the group's rest-local coordinates. */
    lateinit var cloud: DoubleArray
    lateinit var axes: List<Point3>
    lateinit var centroid: Point3

    /** Computes this frame of the part into [f], seen through [cam]. */
    abstract fun emit(cam: TurnCam, f: TurnFrame)

    /** The sample cloud, `t3Cloud`. */
    internal abstract fun cloudOf(): DoubleArray

    /** The axes whose planes may separate this part from another, `t3Axes`. */
    internal abstract fun axesOf(): List<Point3>

    internal fun finishPrepare() {
        cloud = cloudOf()
        axes = axesOf()
        val cn = cloud.size / 3
        val c = DoubleArray(3)
        for (i in 0 until cn) for (k in 0 until 3) c[k] += cloud[3 * i + k] / maxOf(1, cn)
        centroid = Point3(c[0], c[1], c[2])
    }
}

/** A cross-group pair's cached plane while their relative pose holds. */
internal class PairMemo {
    val pose = DoubleArray(12)
    val n = DoubleArray(3)
    var margin = Double.NEGATIVE_INFINITY
    var fresh = false
}

/** Two groups' parts with one plane between them, in a common frame. */
class GroupPair(
    val a: IntArray,
    val b: IntArray,
    val frame: Int,
    val n: Point3,
) {
    internal val boxA = DoubleArray(4)
    internal val boxB = DoubleArray(4)
}

/** A prepared live layer. */
class TurnLayer(
    val index: Int,
    val name: String,
    val parts: IntArray,
    var rest: IntArray,
    val planar: DoubleArray,
    val framed: DoubleArray,
    val groupPairs: List<GroupPair>,
    val dynamic: IntArray,
    val free: IntArray,
) {
    /** Global part index to position in [parts]. */
    val local: Map<Int, Int> = parts.withIndex().associate { (at, global) -> global to at }
    internal val looseAt =
        BooleanArray(parts.size).also { loose ->
            for (global in free) loose[local.getValue(global)] = true
        }
    internal val rel = HashMap<Int, PairMemo>()
    internal val work = OrderWork(parts.size)
}

/** The prepared scene: the camera, the groups, every run and every layer. */
class TurnScene(
    val projection: Projection,
    val data: TurnData,
    val unit: Double,
    val groups: List<GroupData>,
    val runs: List<TurnRun>,
    val layers: List<TurnLayer>,
) {
    /** Every group's camera for [values]. */
    fun cams(values: TurnValues): TurnCams = t3Cams(projection, groups, values)

    /** A new frame for each run. */
    fun frames(): List<TurnFrame> = runs.map { TurnFrame(it.roles.size) }

    /** Emits [run] for [cams] into [f]. */
    fun emit(run: TurnRun, cams: TurnCams, f: TurnFrame): TurnFrame {
        run.emit(cams.of(run.group), f)
        return f
    }
}

/** `t3Prepare`: runs and layers from packed [data], at [unit] screen px per viewBox unit. */
fun prepareTurn(data: TurnData, unit: Double = 1.0): TurnScene {
    val p = data.projection
    val runs = data.parts.mapIndexed { index, part -> runOf(index, part, data, unit) }
    val layers = data.layers.mapIndexed { index, layer -> layerOf(index, layer, data, runs) }
    return TurnScene(p, data, unit, data.groups, runs, layers)
}

private fun layerOf(index: Int, layer: LayerData, data: TurnData, runs: List<TurnRun>): TurnLayer {
    val members = LinkedHashMap<Int, MutableList<Int>>()
    for (global in layer.parts) members.getOrPut(runs[global].group) { ArrayList() }.add(global)
    val raw = data.groupPairs[layer.name] ?: DoubleArray(0)
    val pairs = ArrayList<GroupPair>()
    for (p in raw.indices step 6) {
        pairs.add(
            GroupPair(
                members[raw[p].toInt()]?.toIntArray() ?: IntArray(0),
                members[raw[p + 1].toInt()]?.toIntArray() ?: IntArray(0),
                raw[p + 2].toInt(),
                Point3(raw[p + 3], raw[p + 4], raw[p + 5]),
            )
        )
    }
    return TurnLayer(
        index,
        layer.name,
        layer.parts,
        layer.rest,
        data.planes[layer.name] ?: DoubleArray(0),
        data.framed[layer.name] ?: DoubleArray(0),
        pairs,
        data.dynamic[layer.name] ?: IntArray(0),
        data.free[layer.name] ?: IntArray(0),
    )
}
