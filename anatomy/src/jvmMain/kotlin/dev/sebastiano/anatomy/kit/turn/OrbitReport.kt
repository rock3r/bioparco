// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.audit.jsString

/*
 * The orbit audit's report, `orbitReport` and `orbitOrExit`: one line per check with its count
 * and its first forty findings (cycles grouped by the parts they trap), the worst continuity
 * measures, the state counts, the reorders, and a last line that says whether the audit passed.
 */

private class CycleGroup(val first: Finding, var last: String, var count: Int)

/** The report's body, line by line, as the original prints it. */
fun orbitReport(result: OrbitResult): String {
    val lines = ArrayList<String>()
    fun skipped(name: String) = name in result.skipped && result[name].isEmpty()
    fun list(name: String, text: (Finding) -> String = { it.text }) {
        if (skipped(name)) {
            lines.add("$name: skipped")
            return
        }
        lines.add("$name: ${result[name].size}")
        for (item in result[name].take(40)) lines.add("  ${text(item)}")
    }
    list("separation")
    list("unseparated")
    cycles(result, lines, skipped("cycles"))
    list("forced") { "${it.state}  ${it.layer}: ${it.text}" }
    list("swaps")
    list("layers")
    list("rigid")
    list("swept")
    list("posed")
    list("folds")
    list("self")
    list("crowding")
    list("endon")
    list("coverage")
    list("depth")
    list("continuity")
    val s = result.stats
    val w = result.worst
    if (!skipped("continuity")) {
        lines.add(
            "worst tone step ${jsString(w["q"] ?: 0.0)} · worst birth ${f2(w["birth"] ?: 0.0)} px² · worst dot α step ${f3(w["dot"] ?: 0.0)} · " +
                "worst faded α step ${f3(w["fade"] ?: 0.0)} · merges ${jsString(w["merge"] ?: 0.0)} · outline motion over bound " +
                "${f3(w["motion"] ?: 0.0)} px · halving ${result.halvingFails} of ${result.halvingChecked}"
        )
    }
    fun count(key: String) = jsString(s[key] ?: 0.0)
    lines.add(
        "order changes: ${count("changes")} states with swaps · ${count("orderStates")} order states · ${count("posedStates")} posed states · " +
            "${count("planes")} planes (tightest margin ${f3(s["tightest"] ?: 0.0)}) · depth oracle ${count("depthStates")} states, " +
            "${count("depthPairs")} overlapping pairs"
    )
    for (change in result.changes) {
        lines.add("  ${change.family.name} at ${f3(change.x)}: ${change.count} pair${if (change.count == 1) "" else "s"} reorder (${change.pairs.joinToString("; ")})")
    }
    lines.add("turn audit · ${f2(s["seconds"] ?: 0.0)} s")
    return lines.joinToString("\n")
}

private fun cycles(result: OrbitResult, lines: MutableList<String>, skipped: Boolean) {
    val groups = LinkedHashMap<String, CycleGroup>()
    for (c in result["cycles"]) {
        val group = groups.getOrPut("${c.layer}|${c.parts.joinToString(", ")}") { CycleGroup(c, c.state, 0) }
        group.last = c.state
        group.count++
    }
    lines.add(if (skipped) "cycles: skipped" else "cycles: ${result["cycles"].size}")
    for (g in groups.values.take(20)) {
        val c = g.first
        lines.add("  ${c.layer}: ${c.parts.joinToString(", ")} in ${g.count} state${if (g.count == 1) "" else "s"} from ${c.state} to ${g.last}: ${c.text}")
    }
}

/** The report's last line: passed, a partial or quick run with no problems, or failed. */
fun orbitVerdict(result: OrbitResult): String {
    val count = result.failures
    val skipped = result.skipped.size
    val partial = if (skipped > 0) "$skipped line${if (skipped == 1) "" else "s"} skipped by only" else ""
    val undersampled = if (result.quick) "quick run, undersampled: the full run is the proof" else ""
    val notes = listOf(partial, undersampled).filter { it.isNotEmpty() }.joinToString("; ")
    return when {
        count > 0 -> "turn audit failed: $count problem${if (count == 1) "" else "s"}${if (notes.isNotEmpty()) " ($notes)" else ""}"
        notes.isNotEmpty() -> "turn audit ${if (skipped > 0) "partial" else "quick"}: no problems in what ran ($notes)"
        else -> "turn audit passed"
    }
}
