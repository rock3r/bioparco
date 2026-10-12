package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.AuditParityTest.Companion.box
import dev.sebastiano.anatomy.AuditParityTest.Companion.flat
import dev.sebastiano.anatomy.AuditParityTest.Companion.nm
import dev.sebastiano.anatomy.AuditParityTest.Companion.p3
import dev.sebastiano.anatomy.AuditScenes.P
import dev.sebastiano.anatomy.AuditScenes.P57
import dev.sebastiano.anatomy.kit.Art
import dev.sebastiano.anatomy.kit.audit.AuditOptions
import dev.sebastiano.anatomy.kit.audit.AuditResult
import dev.sebastiano.anatomy.kit.audit.Clearance
import dev.sebastiano.anatomy.kit.audit.Crowding
import dev.sebastiano.anatomy.kit.audit.DepthReport
import dev.sebastiano.anatomy.kit.audit.LooseEnd
import dev.sebastiano.anatomy.kit.audit.Recorder
import dev.sebastiano.anatomy.kit.audit.ScreenFold
import dev.sebastiano.anatomy.kit.audit.Support
import dev.sebastiano.anatomy.kit.audit.Terminal
import dev.sebastiano.anatomy.kit.audit.Uniform
import dev.sebastiano.anatomy.kit.audit.artBox
import dev.sebastiano.anatomy.kit.audit.audit
import dev.sebastiano.anatomy.kit.audit.cameraBasis
import dev.sebastiano.anatomy.kit.audit.clearances
import dev.sebastiano.anatomy.kit.audit.coverage
import dev.sebastiano.anatomy.kit.audit.crowding
import dev.sebastiano.anatomy.kit.audit.failures
import dev.sebastiano.anatomy.kit.audit.folds
import dev.sebastiano.anatomy.kit.audit.footprintOf
import dev.sebastiano.anatomy.kit.audit.jsString
import dev.sebastiano.anatomy.kit.audit.linksOf
import dev.sebastiano.anatomy.kit.audit.looseEnds
import dev.sebastiano.anatomy.kit.audit.mountsOf
import dev.sebastiano.anatomy.kit.audit.report
import dev.sebastiano.anatomy.kit.audit.seats
import dev.sebastiano.anatomy.kit.audit.selfOverlaps
import dev.sebastiano.anatomy.kit.audit.slenderOf
import dev.sebastiano.anatomy.kit.audit.solidClearances
import dev.sebastiano.anatomy.kit.audit.supports
import dev.sebastiano.anatomy.kit.audit.terminals
import dev.sebastiano.anatomy.kit.audit.wobble

/** Every check on the check scene, then the whole audit and its report. */
object AuditCheckCases {
    private fun <T> ParityLines.list(name: String, items: List<T>, line: (T) -> String) {
        put("$name.count", items.size)
        items.forEachIndexed { index, item -> put("$name.$index", line(item)) }
    }

    private fun clearance(c: Clearance) =
        "${c.tube} ${c.other} ${c.kind} ${nm(c.clearance)} ${nm(c.need)} ${p3(c.at)} ${nm(c.from)} ${nm(c.to)} " +
            "${nm(c.length)} ${c.state}|${c.solid}"

    private fun terminal(e: Terminal) =
        "${e.tube} ${e.end} ${p3(e.at)} ${nm(e.d)} ${nm(e.high)} ${nm(e.low)} ${e.nearest}"

    private fun loose(e: LooseEnd) = "${e.tube} ${e.end} ${p3(e.at)} ${e.state} ${e.free}"

    private fun support(held: Support) =
        "${held.loose.joinToString(",")} | ${held.hanging.joinToString(",")} | " +
            held.sunk.joinToString(",") { "${it.a}×${it.b}@${nm(it.depth)}" }

    private fun fold(b: ScreenFold) =
        "${b.name} ${b.kind} ${nm(b.flatTurn)} ${nm(b.realTurn)} ${nm(b.radius)} ${nm(b.width)} ${p3(b.at)}"

    private fun crowd(c: Crowding) =
        "${c.a} ${c.b} ${c.where} ${nm(c.gap)} ${nm(c.depth)} ${nm(c.need)} ${flat(c.screen)} ${p3(c.at)} ${c.state}"

    fun checks(out: ParityLines, arts: Map<String, Art>) {
        val r = AuditScenes.checkScene(arts)
        val mounts = mountsOf(r.tubes, r.solids)
        r.tubes.forEachIndexed { index, tube ->
            val ends =
                mounts.getValue(tube).joinToString(" ") { m ->
                    if (m == null) "null"
                    else
                        "${m.shape.name}/${nm(m.score)}/${nm(m.d)}/${nm(m.high)}/${nm(m.low)}/${m.landed}/" +
                            (m.host?.name ?: "-")
                }
            out.put("mountsOf.$index", "${tube.name} $ends")
        }
        out.list("clearances", clearances(r.tubes, r.solids, mounts = mounts), ::clearance)
        out.list(
            "clearances.apart",
            clearances(r.tubes, r.solids, state = Uniform(1.0), mounts = mounts, only = "through"),
            ::clearance,
        )
        out.list(
            "clearances.wide",
            clearances(r.tubes, r.solids, ratio = 2.0, least = 1.0, spacing = 1.5),
            ::clearance,
        )
        out.list("terminals", terminals(r.tubes, r.solids, mounts = mounts), ::terminal)
        out.list("terminals.loose", terminals(r.tubes, r.solids, tolerance = 0.6), ::terminal)
        out.list("looseEnds", looseEnds(r.tubes, r.solids), ::loose)
        out.list("looseEnds.home", looseEnds(r.tubes, r.solids, Uniform(0.0)), ::loose)
        out.put("supports", support(supports(r.solids, r.tubes)))
        out.put(
            "supports.custom",
            support(supports(r.solids, r.tubes, { it.name == "block" }, 0.05, 0.1)),
        )
        r.settle(P)
        out.put("settle.keys", r.items.joinToString(" ") { "${it.name}=${nm(it.key)}" })
        screen(out, r)
        solids(out, r)
        coverageLines(out, r, arts)
        val result = audit(r, P, AuditOptions(crowd = listOf("tap" to "rail*")))
        out.put("failures", failures(result))
        report(result).split("\n").forEachIndexed { index, line -> out.put("report.$index", line) }
        val partial =
            AuditResult(
                depth = DepthReport(emptyList(), missing = listOf("x")),
                wobble = emptyList(),
            )
        out.put("report.partial", report(partial).split("\n").joinToString("¶"))
        out.put("failures.partial", failures(partial))
    }

    private fun screen(out: ParityLines, r: Recorder) {
        out.list("folds", folds(r.routes, P), ::fold)
        out.list("folds.items", folds(r.routes, P, items = r.items), ::fold)
        out.list(
            "folds.custom",
            folds(r.routes, P57, fold = 100.0, turn = 120.0, pinch = 4.0),
            ::fold,
        )
        val links = linksOf(r.tubes, mountsOf(r.tubes, r.solids))
        out.put(
            "linksOf",
            links.entries.joinToString(" ") { (a, set) -> "$a:${set.joinToString("+")}" },
        )
        val rods = slenderOf(r.solids)
        out.put(
            "slenderOf",
            rods.joinToString(" ") {
                val owners = it.owners.joinToString("+")
                "${it.name}/${it.solid}/$owners/${p3(it.points[0])}/${p3(it.points[1])}/${nm(it.r)}"
            },
        )
        out.put(
            "slenderOf.custom",
            slenderOf(r.solids, 2.0, 1.0).joinToString(" ") { it.solid ?: "" },
        )
        out.list("crowding.bare", crowding(r.routes, P), ::crowd)
        out.list(
            "crowding",
            crowding(r.routes, P, links = links, items = r.items, rods = rods),
            ::crowd,
        )
        val apart =
            crowding(
                r.routes,
                P,
                links = links,
                items = r.items,
                rods = rods,
                state = Uniform(1.0),
                allow = listOf("tap" to "rail*"),
            )
        out.list("crowding.apart", apart, ::crowd)
        out.list("selfOverlaps", selfOverlaps(r.items, P)) {
            "${it.name} ${it.route} ${nm(it.radius)} ${nm(it.width)} ${p3(it.at)} ${flat(it.screen)}"
        }
    }

    private fun solids(out: ParityLines, r: Recorder) {
        out.list("solidClearances", solidClearances(r.solids, Uniform(1.0)), ::clearance)
        out.list("solidClearances.home", solidClearances(r.solids), ::clearance)
        out.list("wobble", wobble(r.solids)) {
            "${it.name} ${it.at.joinToString(",") { s -> jsString(s) }} ${nm(it.from)} ${nm(it.to)}"
        }
        out.list("seats", seats(r.solids)) { "${it.name} ${it.host} ${it.piece} ${it.hostPiece}" }
    }

    private fun coverageLines(out: ParityLines, r: Recorder, arts: Map<String, Art>) {
        val cam = cameraBasis(P)
        r.items.forEachIndexed { index, item ->
            if (item.shapes.isNotEmpty()) {
                out.put(
                    "approx.footprintOf.$index",
                    item.shapes.joinToString(" ") { box(footprintOf(it, cam)) },
                )
            }
        }
        for (name in listOf("standin", "good", "clipped")) {
            out.put("approx.markupBox.$name", box(artBox(arts.getValue(name))))
        }
        out.list("approx.coverage", coverage(r.items, P)) {
            "${it.name} ${ParityLines.fixed(it.share, 4)} ${box(it.drawn)} ${box(it.shapes)}"
        }
    }
}
