// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.audit.jsFixed
import dev.sebastiano.anatomy.kit.dot3
import dev.sebastiano.anatomy.kit.len3
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.sub3
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * The build's pair proofs (`T.build`'s main loop): every pair of parts in a live layer that can
 * overlap on screen in a reachable state gets a relation. Same group: a plane fixed in the group
 * (planar), found by `separate` and failed below −0.08 (`interlock`). Different groups: one plane
 * for every part of two whole groups if their swept clouds allow (group pairs), else a plane in
 * the common ancestor's frame with margin ≥ 0.1 over the sampled states (framed), else GJK each
 * frame (dynamic). Neighbouring chunks of one route are skipped; free parts pair at runtime.
 */

/** A bounding sphere of a part's samples. */
internal class Sphere(val c: Point3, val r: Double)

/** One pair's margin, as the report lists it. */
class PairMargin(val a: String, val b: String, val margin: Double)

/** What the pair loop found, per layer, and its problems. */
internal class PairResults {
    val planes = LinkedHashMap<String, List<Double>>()
    val dynamic = LinkedHashMap<String, IntArray>()
    val framed = LinkedHashMap<String, List<Double>>()
    val groupPairs = LinkedHashMap<String, List<Double>>()
    val free = LinkedHashMap<String, IntArray>()
    val skip = LinkedHashMap<String, IntArray>()
    val layers = ArrayList<LayerData>()
    val problems = ArrayList<String>()
    val margins = ArrayList<PairMargin>()
    val planesByName = LinkedHashMap<String, Plane>()
}

internal class PairSolver(
    val t: Turning,
    val scene: () -> TurnScene,
    val states: List<TurnValues>,
    val strict: Boolean,
    val fullClouds: Boolean = false,
) {
    val out = PairResults()
    lateinit var spheres: List<Sphere>
    private val sampleCams = HashMap<Int, List<TurnCams>>()
    private var stateCamList: List<TurnCams>? = null
    private val boxCache = HashMap<String, DoubleArray>()
    private val cloudCache = HashMap<String, List<Point3>>()

    fun camsFor(group: TurnGroup?): List<TurnCams> =
        sampleCams.getOrPut(group?.index ?: -1) {
            groupStates(t, group, states, tumble = true).map { scene().cams(it) }
        }

    fun stateCams(): List<TurnCams> =
        stateCamList ?: states.map { scene().cams(it) }.also { stateCamList = it }

    private fun boxesOf(part: TurnPart, list: List<TurnCams>, key: String): DoubleArray =
        boxCache.getOrPut("${part.id}|$key") {
            val local = thinCloud(part.samples, 40)
            val out = DoubleArray(4 * list.size + 1)
            val steps = ArrayList<Double>()
            list.forEachIndexed { at, cams ->
                val c = cams.of(part.group?.index ?: -1)
                var x0 = Double.POSITIVE_INFINITY
                var y0 = Double.POSITIVE_INFINITY
                var x1 = Double.NEGATIVE_INFINITY
                var y1 = Double.NEGATIVE_INFINITY
                for (q in local) {
                    val x = c.ox + c.m[0] * q.x + c.m[1] * q.y + c.m[2] * q.z
                    val y = c.oy + c.m[3] * q.x + c.m[4] * q.y + c.m[5] * q.z
                    if (x < x0) x0 = x
                    if (x > x1) x1 = x
                    if (y < y0) y0 = y
                    if (y > y1) y1 = y
                }
                if (at > 0) {
                    steps.add(
                        maxOf(
                            kotlin.math.abs(x0 - out[4 * at - 4]),
                            kotlin.math.abs(y0 - out[4 * at - 3]),
                            kotlin.math.abs(x1 - out[4 * at - 2]),
                            kotlin.math.abs(y1 - out[4 * at - 1]),
                        )
                    )
                }
                out[4 * at] = x0
                out[4 * at + 1] = y0
                out[4 * at + 2] = x1
                out[4 * at + 3] = y1
            }
            steps.sort()
            out[4 * list.size] =
                if (steps.isNotEmpty()) steps[floor(steps.size * 0.95).toInt()] else 0.0
            out
        }

    private fun boxesMeet(a: TurnPart, b: TurnPart, list: List<TurnCams>, key: String): Boolean {
        val ba = boxesOf(a, list, key)
        val bb = boxesOf(b, list, key)
        val pad = 1.5 + ba[4 * list.size] + bb[4 * list.size]
        for (i in list.indices) {
            val o = 4 * i
            if (
                ba[o] < bb[o + 2] + pad &&
                    bb[o] < ba[o + 2] + pad &&
                    ba[o + 1] < bb[o + 3] + pad &&
                    bb[o + 1] < ba[o + 3] + pad
            )
                return true
        }
        return false
    }

    private fun commonOf(g1: TurnGroup?, g2: TurnGroup?): TurnGroup? {
        val list = g1?.ancestors() ?: emptyList()
        var x = g2
        while (x != null) {
            if (x in list) return x
            x = x.parent
        }
        return null
    }

    private fun sampleStates(): List<TurnCams> {
        val all = stateCams()
        val count = min(all.size, 24)
        return (0 until count).map { all[floor((it.toDouble() * all.size) / count).toInt()] }
    }

    private fun toFrame(dir: Point3, from: TurnGroup?, frame: TurnGroup?): Point3 {
        if (from == frame) return dir
        val cams = sampleStates().firstOrNull() ?: scene().cams(TurnValues())
        val rf = if (from != null) cams[from.index].r else EYE
        val w = t3Apply(rf, dir)
        val rc = if (frame != null) cams[frame.index].r else EYE
        return Point3(
            rc[0] * w.x + rc[3] * w.y + rc[6] * w.z,
            rc[1] * w.x + rc[4] * w.y + rc[7] * w.z,
            rc[2] * w.x + rc[5] * w.y + rc[8] * w.z,
        )
    }

    private fun framedCloud(part: TurnPart, frame: TurnGroup?): List<Point3> =
        cloudCache.getOrPut("${part.id}|${frame?.index ?: -1}") {
            val local = thinCloud(part.samples, 32)
            val out = ArrayList<Point3>()
            for (cams in sampleStates()) {
                val c = cams.of(part.group?.index ?: -1)
                val f = cams.of(frame?.index ?: -1)
                for (q in local) {
                    val r = c.r
                    val p =
                        Point3(
                            r[0] * q.x + r[1] * q.y + r[2] * q.z + c.t.x - f.t.x,
                            r[3] * q.x + r[4] * q.y + r[5] * q.z + c.t.y - f.t.y,
                            r[6] * q.x + r[7] * q.y + r[8] * q.z + c.t.z - f.t.z,
                        )
                    val fr = f.r
                    out.add(
                        Point3(
                            fr[0] * p.x + fr[3] * p.y + fr[6] * p.z,
                            fr[1] * p.x + fr[4] * p.y + fr[7] * p.z,
                            fr[2] * p.x + fr[5] * p.y + fr[8] * p.z,
                        )
                    )
                }
            }
            out
        }

    fun spheresOf() {
        spheres =
            t.parts.map { part ->
                var sum = Point3(0.0, 0.0, 0.0)
                for (p in part.samples) sum = add3(sum, p)
                val c = mul3(sum, 1.0 / max(1, part.samples.size))
                var r = 0.0
                for (p in part.samples) r = max(r, len3(sub3(p, c)))
                Sphere(c, r)
            }
    }

    fun layer(layer: LiveLayer) {
        val members = t.parts.filter { it.layer == layer }
        val settled = HashSet<String>()
        out.groupPairs[layer.name] = groupPairsOf(members, settled)
        val plan = ArrayList<Double>()
        val dyn = ArrayList<Int>()
        val framed = ArrayList<Double>()
        val skipped = ArrayList<Int>()
        for (x in members.indices) {
            for (y in x + 1 until members.size) {
                val a = members[x]
                val b = members[y]
                if (
                    a.route != null && a.route == b.route && kotlin.math.abs(a.chunk - b.chunk) <= 1
                ) {
                    skipped.add(a.id)
                    skipped.add(b.id)
                    continue
                }
                if (a.group == b.group) samePair(a, b, plan)
                else crossPair(a, b, settled, framed, dyn)
            }
        }
        out.planes[layer.name] = plan
        out.dynamic[layer.name] = dyn.toIntArray()
        out.framed[layer.name] = framed
        out.free[layer.name] = members.filter { it.group?.loose == true }.map { it.id }.toIntArray()
        out.skip[layer.name] = skipped.toIntArray()
        out.layers.add(LayerData(layer.name, members.map { it.id }.toIntArray(), IntArray(0)))
    }

    private fun groupPairsOf(members: List<TurnPart>, settled: MutableSet<String>): List<Double> {
        val byGroup = LinkedHashMap<Int, MutableList<TurnPart>>()
        for (part in members) {
            if (part.group?.loose == true) continue
            byGroup.getOrPut(part.group?.index ?: -1) { ArrayList() }.add(part)
        }
        val keys = byGroup.keys.toList()
        val pairs = ArrayList<Double>()
        if (states.isEmpty()) return pairs
        for (x in keys.indices) {
            for (y in x + 1 until keys.size) {
                val a = byGroup.getValue(keys[x])
                val b = byGroup.getValue(keys[y])
                val ga = if (keys[x] >= 0) t.groups[keys[x]] else null
                val gb = if (keys[y] >= 0) t.groups[keys[y]] else null
                val common = commonOf(ga, gb)
                val ca = a.flatMap { part ->
                    framedCloud(part, common).filterIndexed { i, _ -> fullClouds || i % 2 == 0 }
                }
                val cb = b.flatMap { part ->
                    framedCloud(part, common).filterIndexed { i, _ -> fullClouds || i % 2 == 0 }
                }
                val hints =
                    listOf(Point3(0.0, 0.0, 1.0)) +
                        a.flatMap { it.hints }.take(6).map { toFrame(it, ga, common) } +
                        b.flatMap { it.hints }.take(6).map { toFrame(it, gb, common) }
                val hit = quickSeparate(ca, cb, hints)
                if (hit.margin < 0.1) continue
                pairs.addAll(
                    listOf(
                        keys[x].toDouble(),
                        keys[y].toDouble(),
                        (common?.index ?: -1).toDouble(),
                        hit.n.x,
                        hit.n.y,
                        hit.n.z,
                    )
                )
                settled.add("${keys[x]}|${keys[y]}")
                settled.add("${keys[y]}|${keys[x]}")
            }
        }
        return pairs
    }

    private fun samePair(a: TurnPart, b: TurnPart, plan: MutableList<Double>) {
        val sa = spheres[a.id]
        val sb = spheres[b.id]
        val g = a.group?.index ?: -1
        val near =
            if (a.group?.loose == true) len3(sub3(sb.c, sa.c)) < sa.r + sb.r + 1
            else boxesMeet(a, b, camsFor(a.group), "g$g")
        if (!near) return
        val hit = separate(a.samples, b.samples, listOf(Point3(0.0, 0.0, 1.0)) + a.hints + b.hints)
        out.margins.add(PairMargin(a.name, b.name, hit.margin))
        val o = t.origin(a.group)
        out.planesByName["${a.name}|${b.name}"] = Plane(hit.n, hit.d + dot3(hit.n, o), hit.margin)
        if (hit.margin < -0.08) {
            out.problems.add(
                "interlock ${a.name} × ${b.name} ${jsFixed(hit.margin, 2)}: split, flats or a flush contact"
            )
            if (strict) return
        }
        plan.addAll(listOf(a.id.toDouble(), b.id.toDouble(), hit.n.x, hit.n.y, hit.n.z))
    }

    private fun crossPair(
        a: TurnPart,
        b: TurnPart,
        settled: Set<String>,
        framed: MutableList<Double>,
        dyn: MutableList<Int>,
    ) {
        if (a.group?.loose == true || b.group?.loose == true) return
        if ("${a.group?.index ?: -1}|${b.group?.index ?: -1}" in settled) return
        val near = states.isEmpty() || boxesMeet(a, b, stateCams(), "states")
        if (!near) return
        val common = commonOf(a.group, b.group)
        val ca = framedCloud(a, common)
        val cb = framedCloud(b, common)
        val up = Point3(0.0, 0.0, 1.0)
        val hints =
            (listOf(up) + a.hints + b.hints).map { toFrame(it, a.group, common) } +
                (listOf(up) + b.hints.map { toFrame(it, b.group, common) })
        val hit = quickSeparate(ca, cb, hints)
        if (hit.margin >= 0.1) {
            framed.addAll(
                listOf(
                    a.id.toDouble(),
                    b.id.toDouble(),
                    (common?.index ?: -1).toDouble(),
                    hit.n.x,
                    hit.n.y,
                    hit.n.z,
                )
            )
        } else {
            dyn.add(a.id)
            dyn.add(b.id)
        }
    }
}
