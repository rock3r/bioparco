// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.audit.jsFixed
import dev.sebastiano.anatomy.kit.cameraOf
import dev.sebastiano.anatomy.kit.dot3
import dev.sebastiano.anatomy.kit.iso
import dev.sebastiano.anatomy.kit.jsHypot
import dev.sebastiano.anatomy.kit.viewOf
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * Static and live layers (`sweptGroups`, `relation`, `proveLayers`): every static item must keep
 * one side of every live layer over all reachable states, proved by the first rule that holds: a
 * horizontal plane, the cylinder a root turn group sweeps, never overlapping on screen, or a
 * plane against the swept envelope. Live layers must keep their stack order the same way.
 */

/** What one group of one live layer sweeps: its z range, screen box and envelope cloud. */
internal class Swept(
    val group: TurnGroup?,
    val root: Boolean,
    val radius: Double,
    val zmin: Double,
    val zmax: Double,
    val box: DoubleArray,
    val envelope: List<Point3>,
)

/** One proved relation between a static item (or a lower layer) and a live layer's group. */
class LayerProof(
    val item: String,
    val layer: String,
    val group: String,
    val rule: String,
    val side: String,
)

private fun Turning.sweptGroups(
    layer: LiveLayer,
    states: List<TurnValues>,
    scene: TurnScene,
): List<Swept> {
    val byGroup = LinkedHashMap<Int, MutableList<TurnPart>>()
    for (part in parts) {
        if (part.layer != layer || part.dead) continue
        byGroup.getOrPut(part.group?.index ?: -1) { ArrayList() }.add(part)
    }
    return byGroup.map { (key, members) -> sweptGroup(key, members, states, scene) }
}

/** The z range and screen box a group's parts sweep, and every [stride]-th point they pass. */
private class SweptBounds(val stride: Int) {
    var zmin = Double.POSITIVE_INFINITY
    var zmax = Double.NEGATIVE_INFINITY
    val box =
        doubleArrayOf(
            Double.POSITIVE_INFINITY,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
        )
    val cloud = ArrayList<Point3>()
    private var counter = 0

    fun add(p: Point3, x: Double, y: Double) {
        if (p.z < zmin) zmin = p.z
        if (p.z > zmax) zmax = p.z
        if (x < box[0]) box[0] = x
        if (y < box[1]) box[1] = y
        if (x > box[2]) box[2] = x
        if (y > box[3]) box[3] = y
        if (counter++ % stride == 0) cloud.add(p)
    }
}

private fun Turning.sweptGroup(
    key: Int,
    members: List<TurnPart>,
    states: List<TurnValues>,
    scene: TurnScene,
): Swept {
    val group = if (key >= 0) groups[key] else null
    val local = members.flatMap { it.samples }
    val root = group != null && group.kind == GroupKind.Turn && group.parent == null
    val list =
        when {
            root -> (0 until 72).map { TurnValues(mapOf(group!!.name to it * 5.0)) }
            group != null -> groupStates(this, group, states)
            else -> listOf(TurnValues())
        }
    val bounds = SweptBounds(max(1, floor((local.size.toDouble() * list.size) / 4000).toInt()))
    for (values in list) {
        val c = scene.cams(values).of(key)
        for (q in local) {
            val p = place(c, q)
            val (x, y) = iso(p, projection)
            bounds.add(p, x, y)
        }
    }
    if (!root) return Swept(group, false, 0.0, bounds.zmin, bounds.zmax, bounds.box, bounds.cloud)
    return rootSwept(group!!, local, bounds)
}

/** A root turn group sweeps a stack of cylinders: one ring per unit of height, at its reach. */
private fun Turning.rootSwept(group: TurnGroup, local: List<Point3>, b: SweptBounds): Swept {
    var radius = 0.0
    val slices = LinkedHashMap<Int, Double>()
    for (q in local) {
        val band = floor(q.z).toInt()
        slices[band] = max(slices[band] ?: 0.0, jsHypot(q.x, q.y))
        radius = max(radius, jsHypot(q.x, q.y))
    }
    val o = group.origin
    val envelope = slices.flatMap { (band, r) ->
        listOf(band, band + 1).flatMap { z ->
            (0 until 48).map { j ->
                Point3(
                    o.x + r * jsCos((j.toDouble() / 48) * TAU),
                    o.y + r * jsSin((j.toDouble() / 48) * TAU),
                    max(b.zmin, min(b.zmax, z.toDouble())),
                )
            }
        }
    }
    val pad = radius * cameraOf(projection).k
    val c = iso(Point3(o.x, o.y, 0.0), projection)
    b.box[0] = min(b.box[0], c.x - pad)
    b.box[2] = max(b.box[2], c.x + pad)
    return Swept(group, true, radius, b.zmin, b.zmax, b.box, envelope)
}

/** Whether two screen boxes (`x0, y0, x1, y1`) are more than a pixel apart. */
private fun boxesApart(a: DoubleArray, b: DoubleArray) =
    a[2] < b[0] - 1 || b[2] < a[0] - 1 || a[3] < b[1] - 1 || b[3] < a[1] - 1

/** Which side of [swept] the item at [points] (screen [box]) is on, by the first rule; or null. */
private fun Turning.relation(
    points: List<Point3>,
    box: DoubleArray?,
    swept: Swept,
): Pair<String, String>? {
    var zmin = Double.POSITIVE_INFINITY
    var zmax = Double.NEGATIVE_INFINITY
    for (p in points) {
        zmin = min(zmin, p.z)
        zmax = max(zmax, p.z)
    }
    if (zmax <= swept.zmin + 1e-6) return "behind" to "horizontal plane"
    if (zmin >= swept.zmax - 1e-6) return "front" to "horizontal plane"
    val a = projection.azimuth * Math.PI / 180
    if (swept.root) {
        val o = swept.group!!.origin
        val along = points.map { (it.x - o.x) * jsCos(a) + (it.y - o.y) * jsSin(a) }
        if (along.max() <= -swept.radius) return "behind" to "cylinder"
        if (along.min() >= swept.radius) return "front" to "cylinder"
    }
    val s = swept.box
    if (box != null && boxesApart(box, s)) return "apart" to "never overlap"
    val hit =
        separate(
            points,
            swept.envelope,
            listOf(Point3(0.0, 0.0, 1.0), Point3(jsCos(a), jsSin(a), 0.0)),
        )
    if (hit.margin >= -0.08)
        return (if (dot3(hit.n, viewOf(projection)) > 0) "behind" else "front") to "swept envelope"
    return null
}

/** Proves every static item against every live layer, and the live layers among themselves. */
internal fun Turning.proveLayers(
    statics: List<StaticItem>,
    states: List<TurnValues>,
    scene: TurnScene,
): Pair<List<String>, List<LayerProof>> {
    val stack = order.ifEmpty { layers.map { it.name } }
    val rank = stack.withIndex().associate { (i, name) -> name to i }
    val found = LayerFindings(rank, layers.associate { it.name to sweptGroups(it, states, scene) })
    for (layer in layers) if (layer.name !in rank)
        found.problems.add("live layer ${layer.name} is missing from T.stack")
    for (item in statics) {
        if (item.shapes.isEmpty()) continue
        if (item.layer !in rank)
            found.problems.add(
                "static ${item.name} has layer ${item.layer}, which is not in T.stack"
            )
        else proveStatic(item, found)
    }
    proveLive(found)
    return found.problems to found.proofs
}

/** The layers' stack ranks and swept groups, and what the proof found. */
private class LayerFindings(val rank: Map<String, Int>, val swept: Map<String, List<Swept>>) {
    val problems = ArrayList<String>()
    val proofs = ArrayList<LayerProof>()
}

private fun Turning.proveStatic(item: StaticItem, found: LayerFindings) {
    val points = item.shapes.flatMap { sampleShape(it, 24) }
    val flat = points.map { iso(it, projection) }
    val box =
        doubleArrayOf(
            flat.minOf { it.x },
            flat.minOf { it.y },
            flat.maxOf { it.x },
            flat.maxOf { it.y },
        )
    for (layer in layers) {
        val behind = found.rank.getValue(item.layer) < found.rank.getValue(layer.name)
        val want = if (behind) "behind" else "front"
        for (group in found.swept.getValue(layer.name)) {
            val proof = relation(points, box, group)
            val side = proof?.first
            when {
                proof == null -> found.problems.add(interleaves(item, layer, group, points))
                side != "apart" && side != want ->
                    found.problems.add(wrongSide(item, layer, proof, want))
                else ->
                    found.proofs.add(
                        LayerProof(
                            item.name,
                            layer.name,
                            group.group?.name ?: "world",
                            proof.second,
                            proof.first,
                        )
                    )
            }
        }
    }
}

private fun interleaves(
    item: StaticItem,
    layer: LiveLayer,
    group: Swept,
    points: List<Point3>,
): String {
    val top = points.maxOf { it.z }
    val bottom = points.minOf { it.z }
    val axis = group.group?.let { "the ${it.name} axis" } ?: "the layer"
    val where =
        when {
            top > group.zmax + 1e-6 -> "at z = ${jsFixed(group.zmax, 2)}"
            bottom < group.zmin - 1e-6 -> "at z = ${jsFixed(group.zmin, 2)}"
            else -> "into a piece behind and a piece in front of $axis"
        }
    return "static ${item.name} interleaves live layer ${layer.name}: split ${item.name} $where, or move it"
}

private fun wrongSide(
    item: StaticItem,
    layer: LiveLayer,
    proof: Pair<String, String>,
    want: String,
): String {
    val (side, rule) = proof
    val sideText = if (side == "behind") "behind" else "in front of"
    val wanted = if (want == "behind") "under" else "over"
    val needed = if (side == "behind") "under" else "over"
    return "static ${item.name} is $sideText live layer ${layer.name} ($rule) " +
        "but its layer ${item.layer} is $wanted it: move it to a layer $needed ${layer.name}"
}

private fun Turning.proveLive(found: LayerFindings) {
    val live = layers.sortedBy { found.rank[it.name] ?: Int.MAX_VALUE }
    for (i in live.indices) for (j in i + 1 until live.size) proveLivePair(live[i], live[j], found)
}

private fun Turning.proveLivePair(under: LiveLayer, over: LiveLayer, found: LayerFindings) {
    for (lower in found.swept.getValue(under.name)) {
        for (upper in found.swept.getValue(over.name)) {
            val ok = liveRule(lower, upper)
            if (ok == null) {
                found.problems.add(
                    "live layer ${under.name} interleaves live layer ${over.name} " +
                        "(${lower.group?.name ?: "world"} × ${upper.group?.name ?: "world"}): " +
                        "move parts between them or reorder T.stack"
                )
            } else {
                found.proofs.add(
                    LayerProof(
                        "layer ${under.name}",
                        over.name,
                        upper.group?.name ?: "world",
                        ok,
                        "behind",
                    )
                )
            }
        }
    }
}

/** The rule that keeps [lower] under [upper], or null. */
private fun Turning.liveRule(lower: Swept, upper: Swept): String? {
    if (lower.zmax <= upper.zmin + 1e-6) return "horizontal plane"
    if (boxesApart(lower.box, upper.box)) return "never overlap"
    val hit = separate(lower.envelope, upper.envelope, listOf(Point3(0.0, 0.0, 1.0)))
    return if (hit.margin >= -0.08 && dot3(hit.n, viewOf(projection)) > 0) "swept envelope"
    else null
}
