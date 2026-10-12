// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Projection
import dev.sebastiano.anatomy.kit.audit.AuditItem
import dev.sebastiano.anatomy.kit.audit.AuditRoute
import dev.sebastiano.anatomy.kit.audit.TubeShape
import dev.sebastiano.anatomy.kit.audit.crowding
import dev.sebastiano.anatomy.kit.audit.folds
import dev.sebastiano.anatomy.kit.audit.jsFixed
import dev.sebastiano.anatomy.kit.audit.linksOf
import dev.sebastiano.anatomy.kit.audit.mountsOf
import dev.sebastiano.anatomy.kit.audit.selfOverlaps
import dev.sebastiano.anatomy.kit.audit.slenderOf
import dev.sebastiano.anatomy.kit.len3
import dev.sebastiano.anatomy.kit.pathLength
import dev.sebastiano.anatomy.kit.resample
import dev.sebastiano.anatomy.kit.sub3
import dev.sebastiano.anatomy.kit.unit3
import kotlin.math.asin
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * The orbit audit's live routes: `folds` and `self` (a cable's outline folding on screen) at every
 * angle and posed state, `endon` (a cable pointing along the view in any pose) and `crowding` (a
 * route's end or bend landing on another outline, slender solids included).
 */

private val ZERO = Point3(0.0, 0.0, 0.0)

/** A live part's shapes as the kit audit's item, posed when [pose] is given. */
internal fun itemOf(part: TurnPart, pose: ShapePose?): AuditItem =
    AuditItem(part.name, 0.0, "", "", ZERO, if (pose != null) part.shapes.map { posed(it, pose) } else part.shapes, null, part.route, part.chunk)

private fun AuditRoute.posedBy(pose: ShapePose?): AuditRoute =
    if (pose == null) this else AuditRoute(name, points.map { place(pose, it) }, r, bundle, move, ride, piece, solid, owners, rod)

/** A view of a route's group: a camera, the group's pose, and the label for the report. */
private class RouteView(val p: Projection, val pose: ShapePose?, val tag: String)

internal class OrbitRoutes(private val o: OrbitRun) {
    private val result = o.result
    private val sample: List<Pair<TurnValues, String>>

    init {
        val all = OrbitChecks(o).posedStates().filter { it.second != "pose" }
        val stride = max(1, floor(all.size.toDouble() / (if (o.quick) 48 else o.options.routeStates)).toInt())
        sample = (all.indices step stride).map { all[it] } + o.extra.take(24).map { it to "pose" }
    }

    fun run() {
        o.say("folds, self, crowding, endon")
        if (!o.wants("routes")) return
        val seen = HashSet<String>()
        for (route in o.t.routes) {
            for (view in viewsOf(route.group)) folds(route, view, seen)
            endon(route)
        }
        crowd()
    }

    private fun viewsOf(g: TurnGroup?): List<RouteView> {
        val p = o.p
        if (g == null) return listOf(RouteView(p, null, "rest"))
        if (g.kind == GroupKind.Turn && g.parent == null) {
            val count = if (o.quick) 72 else 720
            return (0 until count).map { i ->
                val x = (i * 360.0) / count
                RouteView(p.copy(azimuth = p.azimuth - x), null, "${f2(x)}°")
            }
        }
        return sample.map { (values, tag) -> RouteView(p, poseFor(o.scene.cams(values), g), tag) }
    }

    private fun folds(route: TurnRoute, view: RouteView, seen: MutableSet<String>) {
        val records = o.r.routes.filter { it.name == route.name }.map { it.posedBy(view.pose) }
        val items = o.t.parts.filter { it.group == route.group }.map { itemOf(it, view.pose) }
        for (fold in folds(records, view.p, items = items)) {
            if (!seen.add("${fold.name}|${fold.kind}")) continue
            val tail =
                if (fold.kind == "pinch") " round a ${f2(fold.radius)} px bend" else " for ${jsFixed(fold.realTurn, 0)}° in 3D"
            result["folds"].add(Finding("${fold.name} ${fold.kind} at ${view.tag}: turns ${jsFixed(fold.flatTurn, 0)}° on screen$tail"))
        }
        for (s in selfOverlaps(items.filter { it.route == route.name }, view.p)) {
            if (!seen.add("self|${s.name}")) continue
            result["self"].add(Finding("${s.name} folds back at ${view.tag}: radius ${f2(s.radius)} px under its half-width ${f2(s.width)} px"))
        }
    }

    /** Where a route points within 15° of the view, in any pose its group reaches. */
    private fun endon(route: TurnRoute) {
        val g = route.group
        val lo = ((o.p.elevation * Math.PI) / 180 * 180) / Math.PI
        val total = pathLength(route.world)
        val stations = resample(route.world, 0.25)
        val root = g != null && g.kind == GroupKind.Turn && g.parent == null
        val rotations = if (g == null || root || g.upright) listOf(null) else sample.map { poseFor(o.scene.cams(it.first), g).r }
        val bad = ArrayList<Pair<Double, Double>>()
        var walked = 0.0
        for (i in 1 until stations.size - 1) {
            walked += len3(sub3(stations[i], stations[i - 1]))
            if (route.gaps.any { (a, b) -> walked >= a - 1e-6 && walked <= b + 1e-6 }) continue
            if (o.options.fittings.any { (name, a, b) -> name == route.name && walked >= a && walked <= b }) continue
            val t0 = unit3(sub3(stations[i + 1], stations[i - 1]))
            for (rm in rotations) {
                val t = if (rm != null) t3Apply(rm, t0) else t0
                val phi = (asin(max(-1.0, min(1.0, t.z))) * 180) / Math.PI
                if ((phi > lo - 15 && phi < lo + 15) || (phi > -lo - 15 && phi < -lo + 15)) {
                    bad.add(walked to phi)
                    break
                }
            }
        }
        if (bad.isEmpty()) return
        result["endon"].add(
            Finding(
                "${route.name} points within 15° of the view along ${f2(bad.first().first)}–${f2(bad.last().first)} of ${f2(total)} " +
                    "(slope ${jsFixed(bad.first().second, 1)}°): bend flat or inside a fitting"
            )
        )
    }

    private class CrowdView(val group: TurnGroup, val p: Projection, val tag: String, val values: TurnValues?)

    private fun crowd() {
        val views = ArrayList<CrowdView>()
        for (g in o.t.groups.filter { it.kind == GroupKind.Turn && it.parent == null }) {
            val count = if (o.quick) 24 else 72
            for (i in 0 until count) views.add(CrowdView(g, o.p.copy(azimuth = o.p.azimuth - (i * 360.0) / count), "${f2((i * 360.0) / count)}°", null))
        }
        val others = o.t.routes.mapNotNull { it.group }.filter { !(it.kind == GroupKind.Turn && it.parent == null) }.distinct()
        for (g in others) for ((values, tag) in sample.take(if (o.quick) 24 else 72)) views.add(CrowdView(g, o.p, tag, values))
        val links = linksOf(o.r.tubes, mountsOf(o.r.tubes, o.r.solids))
        val seen = HashSet<String>()
        for (view in views) {
            val g = view.group
            val pose = view.values?.let { poseFor(o.scene.cams(it), g) }
            val mine = o.r.routes.filter { r -> o.t.routes.any { it.name == r.name && it.group == g } }.map { it.posedBy(pose) }
            if (mine.isEmpty()) continue
            val items = o.t.parts.filter { it.group == g }.map { itemOf(it, pose) }
            val rods = slenderOf(items.flatMap { it.shapes }.filter { it !is TubeShape })
            for (c in crowding(mine, view.p, links = links, items = items, allow = o.options.crowd, rods = rods)) {
                if (!seen.add(listOf(c.a, c.b, c.where).joinToString("|"))) continue
                val outlines = if (c.gap < 0) "overlap ${f2(-c.gap)}" else "${f2(c.gap)} apart"
                result["crowding"].add(Finding("${c.a} ${c.where} × ${c.b} at ${view.tag}: outlines $outlines px"))
            }
        }
    }
}
