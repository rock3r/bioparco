package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.kit.Art
import dev.sebastiano.anatomy.kit.DotsMark
import dev.sebastiano.anatomy.kit.FaceMatrix
import dev.sebastiano.anatomy.kit.GroupMark
import dev.sebastiano.anatomy.kit.HaloMark
import dev.sebastiano.anatomy.kit.LineMark
import dev.sebastiano.anatomy.kit.SolidMark
import dev.sebastiano.anatomy.kit.TubeMark
import dev.sebastiano.anatomy.kit.VoidMark
import java.util.Locale

/** One line per actual drawing path, at the original's output precision. */
internal fun MutableList<String>.arcadeArt(art: Art) {
    art.marks.forEachIndexed { index, mark ->
        when (mark) {
            is SolidMark -> arcadeSolid(mark)
            is LineMark -> {
                // A slot is one filled/stroked SVG path, expressed with two kit marks.
                val previous = art.marks.getOrNull(index - 1)
                if (previous !is VoidMark || previous.shape.iso.svg() != mark.shape.iso.svg())
                    add("path\t${mark.shape.iso.svg()}")
            }
            is VoidMark -> add("path\t${mark.shape.iso.svg()}")
            is HaloMark -> add("path\t${mark.shape.iso.svg()}")
            is DotsMark ->
                mark.points.forEachIndexed { i, point ->
                    val a = mark.alphas?.get(i) ?: 1.0
                    val opacity =
                        if (a < 1) String.format(Locale.ROOT, "%.2f", maxOf(0.0, a)) else "-"
                    val size =
                        java.math
                            .BigDecimal(mark.size.toString())
                            .stripTrailingZeros()
                            .toPlainString()
                    add(
                        listOf(
                                "dot",
                                ParityLines.fixed(point.x, 1),
                                ParityLines.fixed(point.y, 1),
                                size,
                                opacity,
                            )
                            .joinToString("\t")
                    )
                }
            is TubeMark -> {
                val p = mark.piece
                add("path\t${p.body.svg()}")
                if (p.wide) {
                    add("path\t${p.shine.svg()}")
                    add("path\t${p.shade.svg()}")
                }
                for (r in mark.rings) add("path\t${r.second.iso.svg()}")
                add("path\t${p.edges.svg()}")
            }
            is GroupMark -> arcadeArt(mark.art)
            else -> error("Not in the cabinet: ${mark.javaClass.simpleName}")
        }
    }
}

private fun MutableList<String>.arcadeSolid(mark: SolidMark) {
    val p = mark.paths
    add("path\t${p.fill.svg()}")
    if (!mark.flat) {
        for (s in p.shades) if (!s.isEmpty) add("path\t${s.svg()}")
        add("path\t${p.top.svg()}")
    }
    if (mark.crease != null) {
        if (!p.crease.isEmpty) add("path\t${p.crease.svg()}")
        p.bevel?.takeUnless { it.isEmpty }?.let { add("path\t${it.svg()}") }
    }
    mark.inner?.let { arcadeArt(it) }
    add("path\t${p.outline.svg()}")
}

internal fun arcadeMatrixLine(m: FaceMatrix) =
    "matrix\tmatrix(" +
        listOf(m.u.x, m.u.y, m.v.x, m.v.y).joinToString(" ") {
            String.format(Locale.ROOT, "%.4f", it)
        } +
        " " +
        listOf(m.origin.x, m.origin.y).joinToString(" ") {
            String.format(Locale.ROOT, "%.2f", it)
        } +
        ")"
