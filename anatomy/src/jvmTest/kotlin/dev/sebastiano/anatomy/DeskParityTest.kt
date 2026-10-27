// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.figures.DeskComputer
import dev.sebastiano.anatomy.figures.DeskKeys
import dev.sebastiano.anatomy.figures.DeskRecess
import dev.sebastiano.anatomy.figures.DeskScreen
import dev.sebastiano.anatomy.kit.Art
import dev.sebastiano.anatomy.kit.DotsMark
import dev.sebastiano.anatomy.kit.FaceMatrix
import dev.sebastiano.anatomy.kit.Free
import dev.sebastiano.anatomy.kit.HaloMark
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.LineMark
import dev.sebastiano.anatomy.kit.SolidMark
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every element of both committed built pages, including local face planes and nested clips. */
class DeskParityTest {
    @Test
    fun deskComputerMatchesBothOriginalPages() {
        val actual = deskLines()
        for (theme in listOf("light", "dark")) {
            val expected =
                javaClass
                    .getResource("/goldens/desk-computer-$theme.txt")!!
                    .readText()
                    .trimEnd('\n')
                    .lines()
            expected.zip(actual).forEachIndexed { index, (want, got) ->
                assertEquals(want, got, "$theme line ${index + 1}")
            }
            assertEquals(expected.size, actual.size, "$theme line count")
        }
    }

    private fun deskLines(): List<String> = buildList {
        val d = DeskComputer
        serialize(d.BASE)
        node("g", "class" to "pc-computer", "data-part" to "computer")
        serialize(d.SHELL)
        plane(d.plane(d.FRONT)) { path(d.FACE_TRIM, "iso-line", "faint") }
        recess(
            DeskScreen.BEZEL,
            1,
            "pc-bezel",
            inside = { recess(DeskScreen.CRT, 0, "pc-glass", back = { screen() }) },
        )
        recess(
            DeskScreen.SLOT,
            2,
            "pc-dark",
            back = { path(DeskScreen.SLOT_LINE, "iso-line", "lo") },
        )
        serialize(d.SLOT_DOT)
        plane(d.plane(d.FRONT)) { path(d.GRILLE, "iso-line", "lo") }
        node("g", "data-part" to "led")
        serialize(d.LED)
        add("/g")
        serialize(d.ROCKER_BASE)
        node("g", "data-rocker" to "on")
        serialize(d.ROCKER_ON)
        add("/g")
        node("g", "data-rocker" to "off", "style" to "display:none")
        serialize(d.ROCKER_OFF)
        add("/g")
        serialize(d.PORT)
        add("/g")
        serialize(d.KEYBOARD)
        DeskKeys.keys.forEachIndexed { index, key ->
            node("g", "class" to "pc-key", "data-key" to "$index")
            serialize(key.art)
            add("/g")
        }
        serialize(d.KEYBOARD_PORT)
        serialize(d.CABLE)
    }

    private fun MutableList<String>.screen() {
        val s = DeskScreen
        node("g", "data-part" to "crt", "transform" to "")
        path(s.GLOW, "pc-glow")
        path(s.SCAN, "pc-scan")
        path(s.LOGO, "pc-logo")
        node(
            "text",
            "class" to "pc-text",
            "data-part" to "text",
            "x" to num(s.TEXT_X),
            "y" to num(-s.TEXT_Z),
            "font-size" to num(s.TEXT_SIZE),
            "text" to "> ",
        )
        node(
            "rect",
            "class" to "pc-cursor",
            "data-part" to "cursor",
            "x" to num(s.TEXT_X + 6.6),
            "y" to num(-s.TEXT_Z - 4.2),
            "width" to "3",
            "height" to "5",
        )
        add("/g")
        node(
            "path",
            "class" to "pc-beam",
            "data-part" to "beam",
            "d" to s.BEAM.svg(),
            "style" to "opacity:0",
        )
        node(
            "circle",
            "class" to "pc-dot",
            "data-part" to "dot",
            "cx" to "0",
            "cy" to num(-s.MID),
            "r" to "1.4",
            "style" to "opacity:0",
        )
        val p = s.REFLECTION
        val reflection =
            "M${num(p[0].x)} ${num(p[0].y)}Q${num(p[1].x)} ${num(p[1].y)} ${num(p[2].x)} ${num(p[2].y)}"
        node("path", "class" to "iso-line", "data-tone" to "lo", "d" to reflection)
    }

    private fun MutableList<String>.recess(
        r: DeskRecess,
        id: Int,
        backClass: String,
        back: MutableList<String>.() -> Unit = {},
        inside: MutableList<String>.() -> Unit = {},
    ) {
        node("clipPath", "id" to "pc-clip-$id")
        node("path", "d" to r.shape.svg(), "transform" to matrix(r.plane))
        add("/clipPath")
        node("g", "clip-path" to "url(#pc-clip-$id)")
        path(r.top, "iso-top")
        r.sides.forEachIndexed { index, shape ->
            if (!shape.isEmpty) path(shape, "iso-shade", shade = "$index")
        }
        plane(r.backPlane) {
            path(r.shape, backClass)
            back()
            path(r.shape, "iso-line", "faint")
        }
        inside()
        add("/g")
        plane(r.plane) { path(r.shape, "iso-line", "lo") }
    }

    private fun MutableList<String>.serialize(art: Art) {
        for (mark in art.marks) when (mark) {
            is HaloMark -> path(mark.shape.iso, "iso-halo")
            is SolidMark -> solid(mark)
            is DotsMark -> {
                node("g", "class" to "iso-dots", "data-tone" to mark.tone.name.lowercase())
                mark.points.forEach {
                    node(
                        "circle",
                        "cx" to fixed(it.x, 1),
                        "cy" to fixed(it.y, 1),
                        "r" to num(mark.size),
                    )
                }
                add("/g")
            }
            is LineMark -> {
                val cord = if (mark.width == 1.1) "iso-line pc-cord" else "iso-line"
                if (mark.free == Free.Both)
                    node(
                        "path",
                        "class" to cord,
                        "data-tone" to mark.tone.name.lowercase(),
                        "d" to mark.shape.iso.svg(),
                        "data-free" to "",
                    )
                else path(mark.shape.iso, cord, mark.tone.name.lowercase())
            }
            else -> error("unexpected desk art ${mark.javaClass.simpleName}")
        }
    }

    private fun MutableList<String>.solid(mark: SolidMark) {
        node("g", "class" to "iso-solid")
        val p = mark.paths
        path(p.fill, "iso-fill")
        p.shades.forEachIndexed { index, shape ->
            if (!shape.isEmpty) path(shape, "iso-shade", shade = "$index")
        }
        path(p.top, "iso-top")
        mark.crease?.let { tone ->
            if (!p.crease.isEmpty) path(p.crease, "iso-line iso-crease", tone.name.lowercase())
            p.bevel?.let { if (!it.isEmpty) path(it, "iso-line iso-bevel", tone.name.lowercase()) }
        }
        path(p.outline, "iso-line iso-edge", mark.tone.name.lowercase())
        add("/g")
    }

    private fun MutableList<String>.plane(m: FaceMatrix, body: MutableList<String>.() -> Unit) {
        node("g", "transform" to matrix(m))
        body()
        add("/g")
    }

    private fun MutableList<String>.path(
        p: IsoPath,
        css: String,
        tone: String? = null,
        shade: String? = null,
    ) {
        node("path", "class" to css, "d" to p.svg(), "data-tone" to tone, "data-shade" to shade)
    }

    private fun MutableList<String>.node(tag: String, vararg attrs: Pair<String, String?>) {
        add(
            (listOf(tag) +
                    attrs
                        .filter { it.second != null }
                        .sortedBy { it.first }
                        .map { "${it.first}=${it.second}" })
                .joinToString("\t")
        )
    }

    private fun matrix(m: FaceMatrix) =
        "matrix(${listOf(m.u.x, m.u.y, m.v.x, m.v.y, m.origin.x, m.origin.y).joinToString(" ") { fixed(it, 3) }})"

    private fun fixed(value: Double, digits: Int) =
        BigDecimal.valueOf(
                kotlin.math.floor(value * Math.pow(10.0, digits.toDouble()) + 0.5) /
                    Math.pow(10.0, digits.toDouble())
            )
            .stripTrailingZeros()
            .toPlainString()

    private fun num(value: Double) = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}
