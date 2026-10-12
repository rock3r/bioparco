package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.AuditParityTest.Companion.flat
import dev.sebastiano.anatomy.AuditParityTest.Companion.nm
import dev.sebastiano.anatomy.AuditParityTest.Companion.p3
import dev.sebastiano.anatomy.AuditScenes.P
import dev.sebastiano.anatomy.AuditScenes.p
import dev.sebastiano.anatomy.kit.audit.AuditItem
import dev.sebastiano.anatomy.kit.audit.DepthReport
import dev.sebastiano.anatomy.kit.audit.FrontSide
import dev.sebastiano.anatomy.kit.audit.OverlapPair
import dev.sebastiano.anatomy.kit.audit.Overlaps
import dev.sebastiano.anatomy.kit.audit.Staged
import dev.sebastiano.anatomy.kit.audit.Uniform
import dev.sebastiano.anatomy.kit.audit.cameraBasis
import dev.sebastiano.anatomy.kit.audit.depthOrder
import dev.sebastiano.anatomy.kit.audit.keyAt
import dev.sebastiano.anatomy.kit.audit.labelOf
import dev.sebastiano.anatomy.kit.audit.orderKeys
import dev.sebastiano.anatomy.kit.audit.overlapsOf
import dev.sebastiano.anatomy.kit.audit.sameRun

/** `overlapsOf`, `orderKeys`, `depthOrder` and `keyAt` on the overlap scene and a loop of rules. */
object AuditOverlapCases {
    fun overlaps(out: ParityLines) {
        val r = AuditScenes.overlapScene()
        val states =
            listOf(Uniform(0.0), Uniform(1.0), Uniform(0.5), Staged("custom", mapOf("lid" to 0.3)))
        val result = overlapsOf(r.items, P, states = states, skip = ::sameRun)
        out.put(
            "overlaps.count",
            "${result.pairs.size} ${nm(result.least)} ${nm(result.step)} ${p3(result.v)}",
        )
        result.pairs.forEachIndexed { index, pair ->
            fun side(s: FrontSide) =
                "${s.all}/${s.inner}/${nm(s.area)}/${nm(s.depth)}/${flat(s.at)}"
            out.put(
                "overlaps.$index",
                "${labelOf(pair.state)} ${pair.a} ${pair.b} ${side(pair.front[0])} ${side(pair.front[1])}",
            )
        }
        depthLines(out, "depthOrder.before", depthOrder(r.items, P, result))
        val solved = orderKeys(r.items, result)
        out.put(
            "orderKeys",
            "${solved.keys.joinToString(" ") { nm(it) }} rules ${solved.rules} cycles ${solved.cycles.size} " +
                "dropped ${solved.dropped.size}",
        )
        r.items.forEachIndexed { index, item -> item.key = solved.keys[index] }
        depthLines(out, "depthOrder.after", depthOrder(r.items, P, result, solved))
        depthLines(
            out,
            "depthOrder.allow",
            depthOrder(r.items, P, result, solved, allow = listOf("cross.*" to "cross.b")),
        )
        val staged = Staged("k", mapOf("lid" to 0.6))
        out.put("keyAt", r.items.joinToString(" ") { nm(keyAt(it, staged, result.v)) })
        loop(out)
    }

    private fun loop(out: ParityLines) {
        fun front(a: Double, b: Double) =
            listOf(FrontSide(0, 0, a, 0.0, null), FrontSide(0, 0, b, 0.0, null))
        val pairs =
            listOf(
                OverlapPair(Uniform(0.0), 0, 1, front(3.0, 0.0)),
                OverlapPair(Uniform(0.0), 1, 2, front(2.0, 0.0)),
                OverlapPair(Uniform(0.0), 2, 0, front(1.0, 0.0)),
                OverlapPair(Uniform(1.0), 3, 0, front(0.0, 4.0)),
                OverlapPair(Uniform(1.0), 3, 0, front(0.0, 6.0)),
            )
        val v = cameraBasis(P).v
        val overlaps = Overlaps(pairs, 0.25, emptyList(), 0.25, v)
        fun item(key: Double, move: Double) = AuditItem("", key, "", "", p(0, 0, move), emptyList())
        val items = listOf(item(0.0, 0.0), item(1.0, 0.0), item(2.0, 0.0), item(0.5, 5.0))
        val looped = orderKeys(items, overlaps)
        val dropped =
            looped.dropped.joinToString(",") {
                "${it.front}>${it.back}@${nm(it.weight)}/${nm(it.need)}"
            }
        out.put(
            "orderKeys.loop",
            "${looped.keys.joinToString(" ") { nm(it) }} rules ${looped.rules} dropped $dropped",
        )
        val limited = orderKeys(items, overlaps, gap = 0.5, rounds = 2)
        out.put(
            "orderKeys.rounds",
            "${limited.keys.joinToString(" ") { nm(it) }} dropped ${limited.dropped.size}",
        )
    }

    private fun depthLines(out: ParityLines, name: String, result: DepthReport) {
        out.put(
            "$name.count",
            "${result.problems.size} ${result.passing.size} ${result.missing.joinToString(",")}",
        )
        for ((kind, list) in listOf("problem" to result.problems, "passing" to result.passing)) {
            list.forEachIndexed { index, d ->
                out.put(
                    "$name.$kind.$index",
                    "${d.label} ${d.front} ${d.back} ${nm(d.frontKey)} ${nm(d.backKey)} ${d.cells} ${nm(d.area)} " +
                        "${nm(d.against)} ${d.cycle} ${nm(d.depth)} ${flat(d.at)} ${d.unorderable}",
                )
            }
        }
    }
}
