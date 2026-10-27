// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.audit

import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.jsRound
import kotlin.math.roundToLong

/*
 * The audit's result as one text, and how many of its lines fail the build. A null list is a
 * check that wasn't run, and its line is left out, as the original leaves out a missing key.
 */

private fun f(v: Double): String = jsFixed(jsRound(v * 100) / 100, 2)

private fun p3(p: Point3): String = "[${jsFixed(p.x, 1)}, ${jsFixed(p.y, 1)}, ${jsFixed(p.z, 1)}]"

private fun plural(count: Int, word: String): String = "$count $word${if (count == 1) "" else "s"}"

private fun name(value: String?): String = value ?: "undefined"

private fun clash(c: Clearance): String {
    val state = if (c.state.isNotEmpty()) "${c.state}  " else ""
    return if (c.solid) {
        "  solid   $state${name(c.tube)}  ×  ${name(c.other)}   sunk ${f(-c.clearance)} deep   at ${p3(c.at)}"
    } else {
        "  ${c.kind.padEnd(7)} $state${name(c.tube)}  ×  ${name(c.other)}   clearance ${f(c.clearance)} < " +
            "${f(c.need)}   along ${f(c.from)}–${f(c.to)} of ${f(c.length)}   at ${p3(c.at)}"
    }
}

private class Lines(val limit: Int) {
    val out = ArrayList<String>()

    fun add(line: String) {
        out.add(line)
    }

    fun <T> each(list: List<T>, line: (T) -> String) {
        for (item in list.take(limit)) out.add(line(item))
    }
}

private fun Lines.clearances(result: AuditResult) {
    add("clearance: ${plural(result.clearance.size, "problem")}")
    each(result.clearance, ::clash)
    result.apart?.let {
        add("clearance apart: ${plural(it.size, "problem")}")
        each(it, ::clash)
    }
    result.terminals?.let { ends ->
        add("terminals: ${plural(ends.size, "end")} not on a mount")
        each(ends) { e ->
            val d = if (e.d.isFinite()) f(e.d) else "∞"
            val rim = if (e.high.isFinite()) "${f(e.low)}…${f(e.high)}" else "-"
            "  ${name(e.tube)} ${e.end}  $d from ${name(e.nearest)} (rim $rim)   at ${p3(e.at)}"
        }
    }
    result.loose?.let { loose ->
        val hanging = loose.filter { !it.free }
        add("loose ends: ${hanging.size}")
        each(hanging) {
            "  ${it.state}  ${name(it.tube)} ${it.end} touches nothing that moves with it   at ${p3(it.at)}"
        }
    }
}

private fun Lines.foldsAndSupport(result: AuditResult) {
    result.folds?.let { bends ->
        add("screen folds: ${bends.size}")
        each(bends) { b ->
            if (b.kind == "pinch") {
                "  ${name(b.name)}  turns ${jsFixed(b.flatTurn, 0)}° on screen round a ${f(b.radius)} px bend, " +
                    "under ${f(b.width * 2)} px for a ${f(b.width)} px tube   at ${p3(b.at)}"
            } else {
                "  ${name(b.name)}  turns ${jsFixed(b.flatTurn, 0)}° on screen for ${jsFixed(b.realTurn, 0)}° in 3D   at ${p3(b.at)}"
            }
        }
    }
    result.supports?.let { held ->
        add(
            "support: ${plural(held.loose.size, "unsupported solid")} · " +
                "${plural(held.hanging.size, "hanging tube")} · ${plural(held.sunk.size, "sunk pair")}"
        )
        each(held.loose) { "  loose   ${name(it)}" }
        each(held.hanging) { "  hanging ${name(it)}" }
        each(held.sunk) { "  sunk    ${name(it.a)} × ${name(it.b)}   ${f(it.depth)} deep" }
    }
}

private fun Lines.depthOrder(result: AuditResult) {
    val depth = result.depth
    add("depth order: ${plural(depth.problems.size, "problem")}")
    each(depth.problems) { d ->
        val against = if (d.against != 0.0) " (${f(d.against)} px² the other way)" else ""
        val at = d.at?.let { "${jsFixed(it.x, 1)},${jsFixed(it.y, 1)}" } ?: ""
        "  ${if (d.cycle) "cycle " else "order "} ${d.label}  ${d.front} (k ${f(d.frontKey)}) is in front of " +
            "${d.back} (k ${f(d.backKey)}) but drawn first   ${f(d.area)} px²$against, up to ${f(d.depth)} nearer, at $at"
    }
    result.flight?.let {
        add("through in mid-flight: ${plural(it.size, "pair")}")
        each(it, ::clash)
    }
}

private fun Lines.shapes(result: AuditResult) {
    result.self?.let { self ->
        add("self-overlap: ${plural(self.size, "chunk")} whose outline crosses itself on screen")
        each(self) { c ->
            "  ${c.name}  its outline folds back on screen: the line turns on a ${f(c.radius)} px radius, under its " +
                "${f(c.width)} px half-width   at ${p3(c.at)}, screen ${jsFixed(c.screen.x, 1)},${jsFixed(c.screen.y, 1)}"
        }
    }
    result.wobble?.let { wobbly ->
        add("wobble: ${plural(wobbly.size, "smoothed profile")} whose slope turns between its ends")
        each(wobbly) { w ->
            "  ${name(w.name)}  dr/ds turns at s = ${w.at.joinToString(", ") { jsString(it) }} on the run " +
                "${f(w.from)}–${f(w.to)}; give it an analytic profile and smooth.slope"
        }
    }
    result.seats?.let { seated ->
        add("saddles off their host apart: ${seated.size}")
        each(seated) {
            "  ${name(it.name)} (${name(it.piece)}) is cut to ${name(it.host)} (${name(it.hostPiece)}); " +
                "apart, its cut base is on show"
        }
    }
    result.cover?.let { covered ->
        add(
            "stand-in shapes: ${plural(covered.size, "item")} whose recorded shape covers too little of what it draws"
        )
        each(covered) { c ->
            val drawn =
                listOf(c.drawn.x0, c.drawn.y0, c.drawn.x1, c.drawn.y1).joinToString(",") {
                    jsFixed(it, 0)
                }
            val shapes =
                listOf(c.shapes.x0, c.shapes.y0, c.shapes.x1, c.shapes.y1).joinToString(",") {
                    jsFixed(it, 0)
                }
            "  ${c.name}  its shapes cover ${jsRound(c.share * 100).roundToLong()}% of its drawing (drawn $drawn, " +
                "recorded $shapes); record the true shape of what you draw"
        }
    }
}

private fun Lines.crowdingAndPassing(result: AuditResult) {
    result.crowding?.let { crowded ->
        add(
            "crowding: ${plural(crowded.size, "pair")} where a bend or end touches another line on screen"
        )
        each(crowded) { c ->
            val state = if (c.state.isNotEmpty()) "${c.state}  " else ""
            val gap = if (c.gap < 0) "overlap ${f(-c.gap)}" else "${f(c.gap)} apart"
            "  $state${name(c.a)} ${c.where}  ×  ${name(c.b)}   outlines $gap px, ${f(c.depth)} apart in depth   " +
                "at screen ${jsFixed(c.screen.x, 1)},${jsFixed(c.screen.y, 1)}"
        }
    }
    val depth = result.depth
    if (depth.passing.isNotEmpty()) {
        fun pairs(list: List<DepthProblem>) =
            list.map { listOf(it.front, it.back).sorted() }.toSet().size
        add(
            "in passing (uniform explode states only, not failing): ${pairs(depth.passing.filter { it.cycle })} " +
                "interlocked pairs, ${pairs(depth.passing.filter { !it.cycle })} pairs no linear key can order"
        )
        each(depth.passing) {
            "  ${if (it.cycle) "cycle " else "unorderable"} ${it.label}  ${it.front} / ${it.back}   ${f(it.area)} px²"
        }
    }
    if (depth.missing.isNotEmpty()) {
        add(
            "items with no shape: ${depth.missing.size}  ${depth.missing.take(20).joinToString(", ")}"
        )
    }
    val free = result.loose?.filter { it.free }.orEmpty()
    if (free.isNotEmpty()) {
        add("free ends apart (marked free, not failing): ${free.size}")
        each(free) { "  ${name(it.tube)} ${it.end}   at ${p3(it.at)}" }
    }
}

/** Formats every finding of [result], at most [limit] lines per check. */
fun report(result: AuditResult, limit: Int = 4000): String {
    val lines = Lines(limit)
    lines.clearances(result)
    lines.foldsAndSupport(result)
    lines.depthOrder(result)
    lines.shapes(result)
    lines.crowdingAndPassing(result)
    return lines.out.joinToString("\n")
}

/** How many of [result]'s findings fail the build: everything but `in passing` and free ends. */
fun failures(result: AuditResult): Int {
    val held = result.supports
    return result.cover.orEmpty().size +
        result.clearance.size +
        result.apart.orEmpty().size +
        result.flight.orEmpty().size +
        result.crowding.orEmpty().size +
        result.terminals.orEmpty().size +
        result.loose.orEmpty().count { !it.free } +
        result.folds.orEmpty().size +
        (held?.let { it.loose.size + it.hanging.size + it.sunk.size } ?: 0) +
        result.depth.problems.size +
        result.depth.missing.size +
        result.self.orEmpty().size +
        result.wobble.orEmpty().size +
        result.seats.orEmpty().size
}
