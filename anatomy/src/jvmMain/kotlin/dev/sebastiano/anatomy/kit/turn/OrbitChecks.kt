// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.audit.AuditShape
import dev.sebastiano.anatomy.kit.audit.TubeShape
import dev.sebastiano.anatomy.kit.audit.clearances
import dev.sebastiano.anatomy.kit.audit.mountsOf
import dev.sebastiano.anatomy.kit.audit.sdf
import dev.sebastiano.anatomy.kit.audit.solidClearances
import dev.sebastiano.anatomy.kit.audit.supports
import dev.sebastiano.anatomy.kit.audit.terminals
import dev.sebastiano.anatomy.kit.jsHypot
import kotlin.math.floor
import kotlin.math.min

/*
 * The orbit audit's rigid checks (the kit audit's clearances, terminals, supports and solid
 * clearances at rest), the swept check (static shapes inside a turn group's swept envelope) and the
 * posed check (shapes of different groups, and moving live shapes against static ones, passing
 * through each other in any sampled state).
 */

/** A shape and where it is drawn from: a live part and its group, or a static item. */
internal class Entry(val shape: AuditShape, val name: String, val group: TurnGroup?, val live: Boolean, val bubble: Bubble)

internal class OrbitChecks(private val o: OrbitRun) {
    private val result = o.result
    private val points = HashMap<AuditShape, List<Point3>>()
    private val liveShapes = o.t.parts.flatMap { part -> part.shapes.map { Triple(it, part.name, part.group) } }
    private val staticShapes: List<Pair<AuditShape, String>>

    init {
        val live = liveShapes.map { it.first }.toHashSet()
        staticShapes = o.statics.flatMap { item -> item.shapes.filter { it !in live }.map { it to item.name } }
    }

    fun pointsOf(shape: AuditShape): List<Point3> = points.getOrPut(shape) { surfacePoints(shape) }

    fun run() {
        o.say("rigid")
        if (o.wants("rigid")) rigid()
        o.say("swept")
        if (o.wants("swept")) for (g in o.t.groups) if (g.kind == GroupKind.Turn && g.parent == null) swept(g)
        o.say("posed")
        posed()
    }

    private fun rigid() {
        val r = o.r
        val mounts = mountsOf(r.tubes, r.solids)
        fun add(kind: String, text: String) = result["rigid"].add(Finding("$kind  $text"))
        for (c in clearances(r.tubes, r.solids, mounts = mounts)) add("clearance", "${c.kind} ${c.tube} × ${c.other} clearance ${f2(c.clearance)} < ${f2(c.need)}")
        for (e in terminals(r.tubes, r.solids, mounts = mounts)) {
            add("terminal", "${e.tube} ${e.end} ${if (e.d.isFinite()) f2(e.d) else "∞"} from ${e.nearest}")
        }
        val held = supports(r.solids, r.tubes)
        for (name in held.loose) add("support", "loose $name")
        for (name in held.hanging) add("support", "hanging $name")
        for (d in held.sunk) add("support", "sunk ${d.a} × ${d.b} ${f2(d.depth)} deep")
        for (c in solidClearances(r.solids)) add("solid", "${c.tube} × ${c.other} sunk ${f2(-c.clearance)}")
    }

    /** Static shapes that the turn group [g] sweeps through. */
    private fun swept(g: TurnGroup) {
        val mine = liveShapes.filter { it.third == g }.map { it.first }
        val slices = HashMap<Int, Double>()
        for (shape in mine) {
            for (p in pointsOf(shape)) {
                val band = floor(p.z * 2).toInt()
                slices[band] = maxOf(slices[band] ?: 0.0, jsHypot(p.x - g.origin.x, p.y - g.origin.y))
            }
        }
        for ((shape, name) in staticShapes) {
            var worst = Double.POSITIVE_INFINITY
            for (p in pointsOf(shape)) {
                val r = jsHypot(p.x - g.origin.x, p.y - g.origin.y)
                for (band in intArrayOf(floor(p.z * 2).toInt(), floor(p.z * 2 - 0.2).toInt(), floor(p.z * 2 + 0.2).toInt())) {
                    slices[band]?.let { worst = min(worst, r - it) }
                }
            }
            if (worst < -0.1) sweptThrough(g, shape, name, mine)
        }
    }

    private fun sweptThrough(g: TurnGroup, shape: AuditShape, name: String, mine: List<AuditShape>) {
        var deepest = 0.0
        var where = 0.0
        val count = if (o.quick) 90 else 720
        val b = shape.bounds
        for (i in 0 until count) {
            val angle = (i * 360.0) / count
            val pose = poseFor(o.scene.cams(TurnValues(mapOf(g.name to angle))), g)
            for (other in mine) {
                for (p in pointsOf(other)) {
                    val q = place(pose, p)
                    if (q.x < b.min.x - 0.5 || q.x > b.max.x + 0.5 || q.y < b.min.y - 0.5 || q.y > b.max.y + 0.5 || q.z < b.min.z - 0.5 || q.z > b.max.z + 0.5) continue
                    val d = sdf(shape, q)
                    if (d < deepest) {
                        deepest = d
                        where = angle
                    }
                }
            }
        }
        if (deepest < -0.1) result["swept"].add(Finding("$name × ${g.name} sweep: ${f2(-deepest)} deep at ${f2(where)}°"))
    }

    /** The states the posed and route checks sample: every family, and the extra poses. */
    fun posedStates(): List<Pair<TurnValues, String>> {
        val out = ArrayList<Pair<TurnValues, String>>()
        for (family in o.families) {
            val frame = o.options.posedStep ?: if (family.periodic) (if (o.quick) 4.0 else 0.5) else family.frame * (if (o.quick) 4 else 1)
            for (x in samplesOf(family, frame)) out.add(family.at(x) to o.label(family, x))
        }
        for (values in o.extra.take(if (o.quick) 40 else o.extra.size)) out.add(values to "pose")
        return out
    }

    private fun posed() {
        val states = posedStates()
        result.stats["posedStates"] = states.size.toDouble()
        if (!o.wants("posed")) return
        val entries =
            liveShapes.map { (shape, name, group) -> Entry(shape, name, group, true, bubbleOf(pointsOf(shape))) } +
                staticShapes.map { (shape, name) -> Entry(shape, name, null, false, bubbleOf(pointsOf(shape))) }
        val needsStatic = o.t.groups.any { !(it.kind == GroupKind.Turn && it.parent == null) }
        val seen = HashSet<String>()
        for ((values, tag) in states) {
            val cams = o.scene.cams(values)
            val placed =
                entries.map { e ->
                    if (!e.live || e.group == null) Triple(e, e.bubble.c, null as ShapePose?)
                    else poseFor(cams, e.group).let { Triple(e, place(it, e.bubble.c), it) }
                }.sortedWith { a, b -> (a.second.x - a.first.bubble.r - (b.second.x - b.first.bubble.r)).let { if (it < 0) -1 else if (it > 0) 1 else 0 } }
            for (i in placed.indices) {
                val (a, ca, pa) = placed[i]
                val reach = ca.x + a.bubble.r + 0.5
                for (j in i + 1 until placed.size) {
                    val (b, cb, pb) = placed[j]
                    if (cb.x - b.bubble.r > reach) break
                    if (!pair(a, b, needsStatic)) continue
                    if (jsHypot(ca.x - cb.x, ca.y - cb.y, ca.z - cb.z) > a.bubble.r + b.bubble.r + 0.5) continue
                    val key = "${a.name}|${b.name}"
                    if (key in seen) continue
                    val deepest = through(a, pa ?: IDENTITY, b, pb ?: IDENTITY)
                    val limit = if (a.shape is TubeShape || b.shape is TubeShape) -0.1 else -0.5
                    if (deepest < limit) {
                        seen.add(key)
                        result["posed"].add(Finding("${a.name} × ${b.name} through ${f2(-deepest)} at $tag"))
                    }
                }
            }
        }
    }

    /** Whether two shapes are a pair the posed check measures. */
    private fun pair(a: Entry, b: Entry, needsStatic: Boolean): Boolean {
        if (a.live && b.live && a.group == b.group) return false
        if (!a.live && !b.live) return false
        if ((!a.live || !b.live) && !needsStatic) return false
        if (!a.live || !b.live) {
            val live = if (a.live) a else b
            val g = live.group ?: return false
            if (g.kind == GroupKind.Turn && g.parent == null) return false
        }
        return !(a.shape is TubeShape && b.shape is TubeShape)
    }

    /** How deep two posed shapes pass through each other (negative), or 0. */
    private fun through(a: Entry, pa: ShapePose, b: Entry, pb: ShapePose): Double {
        var deepest = 0.0
        for (p in pointsOf(a.shape)) deepest = min(deepest, sdf(b.shape, unplace(pb, place(pa, p))))
        for (p in pointsOf(b.shape)) deepest = min(deepest, sdf(a.shape, unplace(pa, place(pb, p))))
        return deepest
    }
}
