package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.ParityLines.Companion.fixed
import dev.sebastiano.anatomy.ParityLines.Companion.num
import dev.sebastiano.anatomy.ParityLines.Companion.p3
import dev.sebastiano.anatomy.figures.RaptorEngine
import dev.sebastiano.anatomy.kit.Art
import dev.sebastiano.anatomy.kit.DotsMark
import dev.sebastiano.anatomy.kit.FadeLineMark
import dev.sebastiano.anatomy.kit.FaceMatrix
import dev.sebastiano.anatomy.kit.Free
import dev.sebastiano.anatomy.kit.GroupMark
import dev.sebastiano.anatomy.kit.HaloMark
import dev.sebastiano.anatomy.kit.LineMark
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.SolidMark
import dev.sebastiano.anatomy.kit.TextMark
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.TubeMark
import dev.sebastiano.anatomy.kit.VoidMark
import dev.sebastiano.anatomy.kit.WireMark
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Holds Fig 6 to the original's examples/raptor-engine/build.mjs. The golden
 * `resources/goldens/raptor.txt.gz` is captured by `anatomy/parity/raptor.mjs` (see the note at
 * its top): the static art behind the engine, then every recorded item in settled painter's order
 * with its name, assembly, ride, key, lift and move and every element of its drawing, then every
 * recorded route. Every path must match to the 0.1 the original writes, every key to 6 digits.
 */
class RaptorParityTest {
    @Test
    fun raptorMatchesTheOriginalBuild() {
        val expected =
            GZIPInputStream(javaClass.getResourceAsStream("/goldens/raptor.txt.gz")!!)
                .bufferedReader()
                .readText()
                .trimEnd('\n')
                .split('\n')
        val actual = raptorLines()
        val wrong = expected.indices.filter { it >= actual.size || expected[it] != actual[it] }
        val report =
            wrong.take(6).joinToString("\n") { index ->
                "line ${index + 1}\n  want ${expected[index].take(240)}\n  got  ${actual.getOrNull(index)?.take(240)}"
            }
        assertEquals(0, wrong.size, "${wrong.size} of ${expected.size} lines differ\n$report")
        assertEquals(expected.size, actual.size, "line count")
    }

    private fun raptorLines(): List<String> {
        val build = SETTLED
        val lines = ArrayList<String>()
        lines += "back"
        lines.serialize(build.backUnder)
        lines += element("path", "rp-deckglow", "-", "-", "-", "-", build.deckTop.svg())
        lines += element("path", "iso-halo rp-shadow", "-", "-", "-", "-", build.shadow.svg())
        lines.serialize(build.backOver)
        val recorder = build.recorder
        for (item in recorder.painted()) {
            val lift = recorder.liftOf(item)
            lines += element("item", item.name, item.piece, item.ride, fixed(item.key, 6), fixed(lift, 6), p3(item.move))
            lines.serialize(item.art!!)
        }
        for (route in recorder.routes) {
            val points = route.points.joinToString(" ") { p3(it) }
            lines += element("route", route.name ?: "", route.piece ?: "", route.ride ?: "", p3(route.move ?: Point3(0.0, 0.0, 0.0)), num(route.r), points)
        }
        lines += "lip\t${build.lip.svg()}"
        return lines
    }

    /**
     * The live figure paints in the order the settle wrote, from `raptor-order.txt`, as the
     * original's page does from its `data-k` keys. A stale file is rewritten, and the test fails.
     */
    @Test
    fun bakedOrderMatchesTheSettle() {
        val recorder = SETTLED.recorder
        val index = recorder.items.withIndex().associate { (at, item) -> item to at }
        val lines = recorder.painted().map { "${index.getValue(it)}\t${toFixed(it.key, 2)}" }
        val baked = runCatching { RaptorEngine.bakedOrder().map { (at, key) -> "$at\t${toFixed(key, 2)}" } }
        if (baked.getOrNull() != lines) {
            java.io.File("src/jvmMain/resources/raptor-order.txt").apply { parentFile.mkdirs() }.writeText(lines.joinToString("\n", postfix = "\n"))
        }
        assertEquals(lines, baked.getOrNull(), "raptor-order.txt was stale: rewritten, run again")
    }

    private companion object {
        val SETTLED by lazy { RaptorEngine.settled() }
    }

    private fun element(vararg fields: String) = fields.joinToString("\t")

    private fun MutableList<String>.serialize(art: Art) {
        for (mark in art.marks) {
            when (mark) {
                is HaloMark -> add(element("path", "iso-halo", "-", "-", "-", "-", mark.shape.iso.svg()))
                is VoidMark -> add(element("path", "rp-void", "-", "-", "-", "-", mark.shape.iso.svg()))
                is SolidMark -> solid(mark)
                is LineMark -> line(mark)
                is DotsMark -> dots(mark)
                is TubeMark -> tube(mark)
                is TextMark ->
                    add(element("text", matrix(mark.matrix), fixed(mark.size, 6), mark.anchor.name.lowercase(), mark.text))
                is WireMark,
                is FadeLineMark,
                is GroupMark -> error("not in this figure")
            }
        }
    }

    private fun MutableList<String>.solid(mark: SolidMark) {
        val p = mark.paths
        add(element("g", "iso-solid", if (p.seamless) "seamless" else "-", if (mark.lit) "lit" else "-", "-", "-"))
        add(element("path", "iso-fill", "-", "-", "-", "-", p.fill.svg()))
        if (!mark.flat) {
            p.shades.forEachIndexed { index, shade ->
                if (!shade.isEmpty) add(element("path", "iso-shade", "-", "$index", "-", "-", shade.svg()))
            }
            add(element("path", "iso-top", "-", "-", "-", "-", p.top.svg()))
        }
        val crease = mark.crease
        if (crease != null && !p.crease.isEmpty) add(element("path", "iso-line iso-crease", crease.css, "-", "-", "-", p.crease.svg()))
        val bevel = p.bevel
        if (crease != null && bevel != null && !bevel.isEmpty) add(element("path", "iso-line iso-bevel", crease.css, "-", "-", "-", bevel.svg()))
        mark.inner?.let { serialize(it) }
        add(element("path", "iso-line iso-edge", mark.tone.css, "-", "-", "-", p.outline.svg()))
    }

    private fun MutableList<String>.line(mark: LineMark) {
        if (mark.alpha < 1) add(element("g", "-", "-", "-", "-", toFixed(mark.alpha, 2)))
        val free =
            when (mark.free) {
                Free.None -> "-"
                Free.Both -> "free"
                else -> mark.free.name.lowercase()
            }
        add(element("path", "iso-line", mark.tone.css, "-", free, "-", mark.shape.iso.svg()))
    }

    private fun MutableList<String>.dots(mark: DotsMark) {
        add(element("g", "iso-dots", "-", "-", mark.tone.css, "-"))
        mark.points.forEachIndexed { index, p ->
            val alpha = mark.alphas?.get(index) ?: 1.0
            val opacity = if (alpha < 1) toFixed(maxOf(0.0, alpha), 2) else "-"
            add(element("dot", fixed(p.x, 1), fixed(p.y, 1), fixed(mark.size, 6), opacity))
        }
    }

    private fun MutableList<String>.tube(mark: TubeMark) {
        val piece = mark.piece
        add(element("g", "tb", "-", "-", "-", "-"))
        add(element("path", "tb-body", "-", "-", "-", "-", piece.body.svg()))
        if (piece.wide) {
            add(element("path", "tb-shine", "-", "-", "-", "-", piece.shine.svg()))
            add(element("path", "tb-shade", "-", "-", "-", "-", piece.shade.svg()))
        }
        for ((alpha, ring) in mark.rings) {
            val opacity = if (alpha < 1) toFixed(alpha, 2) else "-"
            add(element("path", "tb-ring", piece.ringTone.css, "-", "-", opacity, ring.iso.svg()))
        }
        add(element("path", "iso-line tb-edge", mark.tone.css, "-", "-", "-", piece.edges.svg()))
    }

    private val Tone.css: String
        get() = name.lowercase()

    private fun toFixed(value: Double, digits: Int) = String.format(Locale.ROOT, "%.${digits}f", value)

    private fun matrix(m: FaceMatrix): String =
        listOf(m.u.x, m.u.y, m.v.x, m.v.y, m.origin.x, m.origin.y).joinToString(" ", "matrix(", ")") {
            fixed(it, 3)
        }
}
