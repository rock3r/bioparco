package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.AuditScenes.P
import dev.sebastiano.anatomy.AuditScenes.P57
import dev.sebastiano.anatomy.AuditScenes.PROBES
import dev.sebastiano.anatomy.AuditScenes.SIDE
import dev.sebastiano.anatomy.AuditScenes.THROAT
import dev.sebastiano.anatomy.AuditScenes.p
import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Knot
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.audit.BodyShape
import dev.sebastiano.anatomy.kit.audit.ExplodeState
import dev.sebastiano.anatomy.kit.audit.Mover
import dev.sebastiano.anatomy.kit.audit.ScreenBox
import dev.sebastiano.anatomy.kit.audit.SpringSpec
import dev.sebastiano.anatomy.kit.audit.Staged
import dev.sebastiano.anatomy.kit.audit.Uniform
import dev.sebastiano.anatomy.kit.audit.boundsOf
import dev.sebastiano.anatomy.kit.audit.cameraBasis
import dev.sebastiano.anatomy.kit.audit.explodeStates
import dev.sebastiano.anatomy.kit.audit.labelOf
import dev.sebastiano.anatomy.kit.audit.moved
import dev.sebastiano.anatomy.kit.audit.offsetOf
import dev.sebastiano.anatomy.kit.audit.prefixStates
import dev.sebastiano.anatomy.kit.audit.reversalsOf
import dev.sebastiano.anatomy.kit.audit.sdf
import dev.sebastiano.anatomy.kit.audit.shareOf
import dev.sebastiano.anatomy.kit.audit.slide
import dev.sebastiano.anatomy.kit.audit.springStates
import dev.sebastiano.anatomy.kit.audit.traced
import dev.sebastiano.anatomy.kit.audit.uniformStates
import java.util.zip.GZIPInputStream
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Holds the Kotlin audit to the original's `kit/audit.mjs`. The golden
 * `resources/goldens/audit.txt.gz` is captured by `anatomy/parity/audit.mjs` (see the note at its
 * top), which calls the original with the same cases on the same scenes ([AuditScenes]), in the
 * same order. Every number must match to the digits it prints, except the `approx.` lines: screen
 * boxes the original measures from SVG markup it writes rounded to 0.1, which match within 0.06.
 */
class AuditParityTest {
    @Test
    fun auditMatchesTheOriginal() {
        val out = ParityLines()
        out.shapes()
        out.states()
        AuditOverlapCases.overlaps(out)
        AuditCheckCases.checks(out, AuditScenes.arts())
        val expected =
            GZIPInputStream(javaClass.getResourceAsStream("/goldens/audit.txt.gz")!!)
                .bufferedReader()
                .readText()
                .trimEnd('\n')
                .split('\n')
        expected.zip(out.lines).forEachIndexed { index, (want, got) ->
            if (want.startsWith("approx.")) assertClose(want, got, index)
            else assertEquals(want, got, "line ${index + 1} differs")
        }
        assertEquals(expected.size, out.lines.size, "line count")
    }

    private fun assertClose(want: String, got: String, index: Int) {
        val numbers = Regex("-?\\d+(\\.\\d+)?")
        val a = numbers.findAll(want).map { it.value.toDouble() }.toList()
        val b = numbers.findAll(got).map { it.value.toDouble() }.toList()
        assertEquals(numbers.replace(want, "#"), numbers.replace(got, "#"), "line ${index + 1}")
        a.zip(b).forEach { (x, y) ->
            assertTrue(abs(x - y) <= 0.06, "line ${index + 1}: $x vs $y\n$want\n$got")
        }
    }

    private fun ParityLines.shapes() {
        val offset = p(3, -2, 1.5)
        for ((name, shape) in AuditScenes.shapeCases()) {
            val b = boundsOf(shape)
            put("bounds.$name", "${p3(b.min)} ${p3(b.max)}")
            put("sdf.$name", PROBES.joinToString(" ") { nm(sdf(shape, it)) })
            val moved = moved(shape, offset)
            val mb = boundsOf(moved)
            val probes = PROBES.take(12).joinToString(" ") { nm(sdf(moved, it)) }
            put("moved.$name", "${p3(mb.min)} ${p3(mb.max)} $probes")
        }
        val slide = slide(SIDE, 0.0, 8.0, 1.5)
        val out = moved(slide, p(5, 1, 0)) as BodyShape
        put("moved.slide.stretch", "${knots(out.poly)} ${nm(out.slide!!.to)}")
        put("moved.slide.back", knots((moved(slide, p(-20, 0, 0)) as BodyShape).poly))
        put("moved.zero", moved(slide, p(0, 0, 0)) === slide)
        put("traced.bell", knots(traced(AuditScenes.BELL)))
        put("traced.radius", knots(traced(THROAT, AuditScenes::throat, 0.001, 4)))
        for ((name, projection) in listOf("P" to P, "P57" to P57)) {
            val c = cameraBasis(projection)
            put("cameraBasis.$name", "${p3(c.r)} ${p3(c.d)} ${p3(c.v)} ${nm(c.k)}")
        }
    }

    private fun knots(list: List<Knot>) = list.joinToString(" ") { "${nm(it.s)}/${nm(it.r)}" }

    private fun ParityLines.list(name: String, states: List<ExplodeState>) {
        put("$name.count", states.size)
        states.forEachIndexed { index, state -> put("$name.$index", shares(state)) }
    }

    private fun ParityLines.states() {
        val order = listOf("base", "arm", "lid")
        list("springStates", springStates(order, stride = 5, reversals = listOf(0.05)))
        val custom =
            SpringSpec(
                stagger = 0.05,
                stiffness = 80.0,
                damping = 12.0,
                substeps = 2,
                fps = 30.0,
                settle = 1e-2,
            )
        list("springStates.custom", springStates(listOf("a", "b"), custom))
        put("uniformStates", uniformStates(4).joinToString("|") { labelOf(it) })
        put("uniformStates.default", uniformStates().joinToString("|") { labelOf(it) })
        put("reversalsOf", reversalsOf(order).joinToString(" ") { nm(it) })
        put("reversalsOf.custom", reversalsOf(order, 0.05, 0.01).joinToString(" ") { nm(it) })
        list("prefixStates", prefixStates(order))
        list("prefixStates.custom", prefixStates(order, listOf(0.5, 1.0)))
        val explode = explodeStates(order, uniform = 3, stride = 6)
        put("explodeStates.counts", "${explode.depth.size} ${explode.frames.size} ${explode.apart}")
        put("explodeStates.depth", explode.depth.joinToString("|") { labelOf(it) })
        val none = explodeStates(emptyList())
        put(
            "explodeStates.none",
            "${none.depth.joinToString(",") { shares(it) }} ${none.frames.size} ${none.apart}",
        )
        val item = mover(p(2, 0, -4), "arm", "lid")
        val staged = Staged("x", mapOf("lid" to 0.25, "arm" to 0.75))
        val arm = mover(null, "arm", null)
        val shares =
            listOf(
                shareOf(item, Uniform(0.4)),
                shareOf(item, staged),
                shareOf(arm, staged),
                shareOf(mover(null, "zz", null), staged),
            )
        put("shareOf", shares.joinToString(" ") { nm(it) })
        put(
            "offsetOf",
            "${p3(offsetOf(item, Uniform(0.4)))} ${p3(offsetOf(item, staged))} ${p3(offsetOf(arm, staged))}",
        )
    }

    private fun mover(move: Point3?, piece: String?, ride: String?) =
        object : Mover {
            override val move = move
            override val ride = ride
            override val piece = piece
        }

    companion object {
        /** The parity scripts' `num`, with JavaScript's `Infinity`. */
        fun nm(value: Double): String =
            when {
                value == Double.POSITIVE_INFINITY -> "Infinity"
                value == Double.NEGATIVE_INFINITY -> "-Infinity"
                value.isNaN() -> "NaN"
                else -> ParityLines.fixed(value, 9)
            }

        fun p3(p: Point3) = "${nm(p.x)},${nm(p.y)},${nm(p.z)}"

        fun flat(p: Flat?) = if (p == null) "-" else "${nm(p.x)}:${nm(p.y)}"

        fun box(b: ScreenBox?) =
            if (b == null) "null"
            else listOf(b.x0, b.y0, b.x1, b.y1).joinToString(",") { ParityLines.fixed(it, 4) }

        fun shares(state: ExplodeState): String =
            when (state) {
                is Uniform -> nm(state.share)
                is Staged ->
                    "${state.label}=" +
                        state.shares.entries.joinToString(",") { "${it.key}:${nm(it.value)}" }
            }
    }
}
