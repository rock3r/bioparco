// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.audit

import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Projection
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.dot3
import dev.sebastiano.anatomy.kit.jsHypot
import dev.sebastiano.anatomy.kit.jsRound
import dev.sebastiano.anatomy.kit.resample
import dev.sebastiano.anatomy.kit.sub3
import dev.sebastiano.anatomy.kit.unit3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/*
 * Crowding: a route's bend or end that lands on another line's outline on screen reads as a joint
 * or a line threaded through another, whatever the true depth. Clamps count too.
 */

/** Line [a]'s bend, end or fitting ([where]) landing on line [b] on screen. */
class Crowding(
    val a: String?,
    val b: String?,
    val where: String,
    val gap: Double,
    val depth: Double,
    val need: Double,
    val screen: Flat,
    val at: Point3,
    val state: String,
)

private class Line(
    val route: AuditRoute,
    val step: Double,
    val dense: List<Point3>,
    val screen: List<Flat>,
    val width: Double,
)

private class Nearest(val d: Double, val j: Int)

private fun nearest(screen: List<Flat>, x: Double, y: Double): Nearest {
    var d = BIG
    var at = -1
    for (j in screen.indices) {
        val e = jsHypot(screen[j].x - x, screen[j].y - y)
        if (e < d) {
            d = e
            at = j
        }
    }
    return Nearest(d, at)
}

private fun idOf(a: String?, b: String?): List<String?> = listOf(a, b).sortedWith(nullsLast())

private class CrowdingPass(
    val cam: CameraBasis,
    val entries: List<Entry>?,
    val bundles: Map<String?, String?>,
    val links: Map<String, Set<String>>,
    val allow: List<Pair<String, String>>,
    val gap: Double,
    val bend: Double,
    val label: String,
) {
    val found = LinkedHashMap<List<String?>, Crowding>()

    fun linked(a: String?, b: String?): Boolean = links[a]?.contains(b) == true

    fun shown(owner: String?, x: Double, y: Double, rod: AuditRoute? = null): Boolean {
        if (entries == null) return true
        val entry = frontEntry(entries, cam, x, y)
        if (rod != null) {
            return entry != null &&
                (entry.shape.name == rod.solid ||
                    (entry.shape.name.isNullOrEmpty() && entry.item == rod.solid))
        }
        return belongs(entry, owner, bundles[owner])
    }

    fun marksOf(line: Line): List<Pair<Int, String>> {
        val out = ArrayList<Pair<Int, String>>()
        val reach = max(2, jsRound((2 * line.route.r) / line.step).toInt())
        val last = line.dense.size - 1
        for (i in 0..last) {
            if (i <= reach || i >= last - reach) {
                out.add(i to if (i <= reach) "start" else "end")
            } else {
                val turn =
                    turnOf(
                        sub3(line.dense[i], line.dense[i - reach]),
                        sub3(line.dense[i + reach], line.dense[i]),
                    )
                if (turn > bend) out.add(i to "bend")
            }
        }
        return out
    }

    fun skips(a: Line, b: Line): Boolean {
        val ra = a.route
        val rb = b.route
        if (a === b || (ra.bundle != null && ra.bundle == rb.bundle) || linked(ra.name, rb.name))
            return true
        if (rb.rod && rb.owners.any { it == ra.name || linked(ra.name, it) }) return true
        return listOfNotNull(rb.name, rb.solid)
            .filter { it.isNotEmpty() }
            .any { allowedPair(allow, ra.name, it) }
    }

    /** True when an end's overlap with a straight stretch of [b] is excused: the tip is clear. */
    private fun clearEnd(a: Line, b: Line, where: String, best: Nearest): Boolean {
        val tip = a.screen[if (where == "start") 0 else a.screen.size - 1]
        var clearTip = BIG
        for (q in b.screen) clearTip = min(clearTip, jsHypot(q.x - tip.x, q.y - tip.y))
        val reachB = max(2, jsRound((2 * b.route.r) / b.step).toInt())
        val j0 = max(0, best.j - reachB)
        val j1 = min(b.dense.size - 1, best.j + reachB)
        val straight =
            j1 - j0 >= 2 &&
                dot3(
                    unit3(sub3(b.dense[best.j], b.dense[j0])),
                    unit3(sub3(b.dense[j1], b.dense[best.j])),
                ) > cos((bend * PI) / 180)
        return straight && clearTip > b.width + a.width + 1
    }

    fun check(a: Line, b: Line, i: Int, where: String) {
        val (x, y) = a.screen[i]
        val best = nearest(b.screen, x, y)
        val touch = best.d - a.width - b.width
        if (touch >= gap) return
        if (where != "bend" && touch <= -2 * min(a.width, b.width) && clearEnd(a, b, where, best))
            return
        val (bx, by) = b.screen[best.j]
        val d = if (best.d == 0.0) 1.0 else best.d
        val ux = (bx - x) / d
        val uy = (by - y) / d
        val inA = max(0.2, a.width - 0.35)
        val inB = max(0.2, b.width - 0.35)
        if (!shown(a.route.name, x + ux * inA, y + uy * inA)) return
        val rb = b.route
        val visible =
            if (rb.rod)
                shown(rb.name, bx - ux * inB, by - uy * inB, rb) ||
                    shown(rb.name, bx + ux * inB, by + uy * inB, rb)
            else shown(rb.name, bx - ux * inB, by - uy * inB)
        if (!visible) return
        val depth = abs(dot3(sub3(a.dense[i], b.dense[best.j]), cam.v))
        val name = if (rb.rod) "${rb.solid} (rod)" else rb.name
        record(
            idOf(a.route.name, rb.name),
            Crowding(
                a.route.name,
                name,
                where,
                touch,
                depth,
                2 * (a.route.r + rb.r),
                Flat(x, y),
                a.dense[i],
                label,
            ),
        )
    }

    fun record(id: List<String?>, entry: Crowding) {
        val prior = found[id]
        if (prior == null || abs(entry.gap) < abs(prior.gap)) found[id] = entry
    }

    fun fitting(fitting: Entry, shape: BodyShape, lines: List<Line>, clear: Double) {
        val reach = shape.poly.maxOf { it.r }
        for (b in lines) {
            val rb = b.route
            val own =
                (fitting.bundle != null && rb.bundle == fitting.bundle) ||
                    rb.name == fitting.owner ||
                    linked(fitting.owner, rb.name)
            if (!rb.rod && !own && !allowedPair(allow, fitting.item, rb.name)) {
                fittingOn(fitting, shape, b, reach, clear)
            }
        }
    }

    /** A [fitting] within [clear] px of line [b]'s outline, where the fitting is in front. */
    private fun fittingOn(fitting: Entry, shape: BodyShape, b: Line, reach: Double, clear: Double) {
        val (x, y) = cam.flat(shape.frame.o)
        val best = nearest(b.screen, x, y)
        val touch = best.d - reach * cam.k - b.width
        if (touch >= clear) return
        val front = frontEntry(entries!!, cam, x, y)
        if (!(front === fitting || belongs(front, fitting.owner, fitting.bundle))) return
        val depth = abs(dot3(sub3(shape.frame.o, b.dense[best.j]), cam.v))
        val entry =
            Crowding(
                fitting.item,
                b.route.name,
                "fitting",
                touch,
                depth,
                0.0,
                Flat(x, y),
                shape.frame.o,
                label,
            )
        record(idOf(fitting.item, b.route.name), entry)
    }
}

/**
 * Every place where a route's bend (a 3D turn over [bend] degrees within ±2 radii) or end lands on
 * another route's outline on screen, closer than [gap] px or overlapping, with both lines visible
 * there (ray-cast against [items]); every `fitting` within [clear] px of another line; and every
 * landing on a slender solid in [rods]. [links] excuses lines that really meet, [allow] pairs you
 * have looked at, and [state] poses everything.
 */
fun crowding(
    routes: List<AuditRoute>,
    projection: Projection,
    gap: Double = 1.0,
    bend: Double = 30.0,
    links: Map<String, Set<String>> = emptyMap(),
    items: List<AuditItem>? = null,
    allow: List<Pair<String, String>> = emptyList(),
    state: ExplodeState? = null,
    clear: Double = 2.0,
    rods: List<AuditRoute> = emptyList(),
): List<Crowding> {
    val cam = cameraBasis(projection)
    fun shift(thing: Mover): Point3 = if (state == null) ZERO else offsetOf(thing, state)
    val entries = items?.let { list ->
        entriesOf(list.map { it.withShapes(it.shapes.map { s -> moved(s, shift(it)) }) }, cam)
    }
    val bundles = routes.associate { it.name to it.bundle }
    val pass =
        CrowdingPass(
            cam,
            entries,
            bundles,
            links,
            allow,
            gap,
            bend,
            state?.let { labelOf(it) } ?: "",
        )
    val lines =
        (routes + rods).map { route ->
            val step = max(0.25, route.r * 0.5)
            val offset = shift(route)
            val dense = resample(route.points.map { add3(it, offset) }, step)
            Line(route, step, dense, dense.map { cam.flat(it) }, route.r * cam.k)
        }
    for (a in lines) {
        if (a.route.rod) continue
        val marks = pass.marksOf(a)
        for (b in lines) {
            if (pass.skips(a, b)) continue
            for ((i, where) in marks) pass.check(a, b, i, where)
        }
    }
    entries
        ?.filter { it.shape.meta.fitting && it.shape is BodyShape }
        ?.forEach { pass.fitting(it, it.shape as BodyShape, lines, clear) }
    return pass.found.values.sortedBy { abs(it.gap) }
}
