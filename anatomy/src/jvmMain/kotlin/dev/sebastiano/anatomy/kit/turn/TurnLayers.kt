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
    val cam = cameraOf(projection)
    val byGroup = LinkedHashMap<Int, MutableList<TurnPart>>()
    for (part in parts) {
        if (part.layer != layer || part.dead) continue
        byGroup.getOrPut(part.group?.index ?: -1) { ArrayList() }.add(part)
    }
    return byGroup.map { (key, members) ->
        val group = if (key >= 0) groups[key] else null
        val local = members.flatMap { it.samples }
        val root = group != null && group.kind == GroupKind.Turn && group.parent == null
        val list =
            when {
                root -> (0 until 72).map { TurnValues(mapOf(group!!.name to it * 5.0)) }
                group != null -> groupStates(this, group, states)
                else -> listOf(TurnValues())
            }
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
        val stride = max(1, floor((local.size.toDouble() * list.size) / 4000).toInt())
        var counter = 0
        for (values in list) {
            val c = scene.cams(values).of(key)
            for (q in local) {
                val p = place(c, q)
                if (p.z < zmin) zmin = p.z
                if (p.z > zmax) zmax = p.z
                val (x, y) = iso(p, projection)
                if (x < box[0]) box[0] = x
                if (y < box[1]) box[1] = y
                if (x > box[2]) box[2] = x
                if (y > box[3]) box[3] = y
                if (counter++ % stride == 0) cloud.add(p)
            }
        }
        var radius = 0.0
        var envelope: List<Point3> = cloud
        if (root) {
            val slices = LinkedHashMap<Int, Double>()
            for (q in local) {
                val band = floor(q.z).toInt()
                slices[band] = max(slices[band] ?: 0.0, jsHypot(q.x, q.y))
                radius = max(radius, jsHypot(q.x, q.y))
            }
            val o = group!!.origin
            envelope = slices.flatMap { (band, r) ->
                listOf(band, band + 1).flatMap { z ->
                    (0 until 48).map { j ->
                        Point3(
                            o.x + r * jsCos((j.toDouble() / 48) * TAU),
                            o.y + r * jsSin((j.toDouble() / 48) * TAU),
                            max(zmin, min(zmax, z.toDouble())),
                        )
                    }
                }
            }
            val pad = radius * cam.k
            val c = iso(Point3(o.x, o.y, 0.0), projection)
            box[0] = min(box[0], c.x - pad)
            box[2] = max(box[2], c.x + pad)
        }
        Swept(group, root, radius, zmin, zmax, box, envelope)
    }
}

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
    if (
        box != null &&
            (box[2] < s[0] - 1 || s[2] < box[0] - 1 || box[3] < s[1] - 1 || s[3] < box[1] - 1)
    ) {
        return "apart" to "never overlap"
    }
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
    val problems = ArrayList<String>()
    val proofs = ArrayList<LayerProof>()
    val swept = layers.associate { it.name to sweptGroups(it, states, scene) }
    for (layer in layers) if (layer.name !in rank)
        problems.add("live layer ${layer.name} is missing from T.stack")
    for (item in statics) {
        if (item.shapes.isEmpty()) continue
        if (item.layer !in rank) {
            problems.add("static ${item.name} has layer ${item.layer}, which is not in T.stack")
            continue
        }
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
            val want =
                if (rank.getValue(item.layer) < rank.getValue(layer.name)) "behind" else "front"
            for (group in swept.getValue(layer.name)) {
                val proof = relation(points, box, group)
                if (proof == null) {
                    val top = points.maxOf { it.z }
                    val bottom = points.minOf { it.z }
                    val where =
                        when {
                            top > group.zmax + 1e-6 -> "at z = ${jsFixed(group.zmax, 2)}"
                            bottom < group.zmin - 1e-6 -> "at z = ${jsFixed(group.zmin, 2)}"
                            else ->
                                "into a piece behind and a piece in front of ${group.group?.let { "the ${it.name} axis" } ?: "the layer"}"
                        }
                    problems.add(
                        "static ${item.name} interleaves live layer ${layer.name}: split ${item.name} $where, or move it"
                    )
                    continue
                }
                val (side, rule) = proof
                if (side != "apart" && side != want) {
                    problems.add(
                        "static ${item.name} is ${if (side == "behind") "behind" else "in front of"} live layer ${layer.name} ($rule) " +
                            "but its layer ${item.layer} is ${if (want == "behind") "under" else "over"} it: move it to a layer " +
                            "${if (side == "behind") "under" else "over"} ${layer.name}"
                    )
                    continue
                }
                proofs.add(
                    LayerProof(item.name, layer.name, group.group?.name ?: "world", rule, side)
                )
            }
        }
    }
    proveLive(swept, rank, problems, proofs)
    return problems to proofs
}

private fun Turning.proveLive(
    swept: Map<String, List<Swept>>,
    rank: Map<String, Int>,
    problems: MutableList<String>,
    proofs: MutableList<LayerProof>,
) {
    val live = layers.sortedBy { rank[it.name] ?: Int.MAX_VALUE }
    for (i in live.indices) {
        for (j in i + 1 until live.size) {
            for (lower in swept.getValue(live[i].name)) {
                for (upper in swept.getValue(live[j].name)) {
                    var ok: String? =
                        if (lower.zmax <= upper.zmin + 1e-6) "horizontal plane" else null
                    val a = lower.box
                    val b = upper.box
                    if (
                        ok == null &&
                            (a[2] < b[0] - 1 ||
                                b[2] < a[0] - 1 ||
                                a[3] < b[1] - 1 ||
                                b[3] < a[1] - 1)
                    )
                        ok = "never overlap"
                    if (ok == null) {
                        val hit =
                            separate(lower.envelope, upper.envelope, listOf(Point3(0.0, 0.0, 1.0)))
                        if (hit.margin >= -0.08 && dot3(hit.n, viewOf(projection)) > 0)
                            ok = "swept envelope"
                    }
                    if (ok == null) {
                        problems.add(
                            "live layer ${live[i].name} interleaves live layer ${live[j].name} (${lower.group?.name ?: "world"} × " +
                                "${upper.group?.name ?: "world"}): move parts between them or reorder T.stack"
                        )
                    } else {
                        proofs.add(
                            LayerProof(
                                "layer ${live[i].name}",
                                live[j].name,
                                upper.group?.name ?: "world",
                                ok,
                                "behind",
                            )
                        )
                    }
                }
            }
        }
    }
}
