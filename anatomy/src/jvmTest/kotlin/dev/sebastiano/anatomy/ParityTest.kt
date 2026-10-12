package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.figures.DialIndicator
import dev.sebastiano.anatomy.kit.Along
import dev.sebastiano.anatomy.kit.Art
import dev.sebastiano.anatomy.kit.Axis
import dev.sebastiano.anatomy.kit.DotsMark
import dev.sebastiano.anatomy.kit.Face
import dev.sebastiano.anatomy.kit.FaceMatrix
import dev.sebastiano.anatomy.kit.FadeLineMark
import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.GroupMark
import dev.sebastiano.anatomy.kit.HaloMark
import dev.sebastiano.anatomy.kit.LineMark
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.SolidMark
import dev.sebastiano.anatomy.kit.SolidPaths
import dev.sebastiano.anatomy.kit.TextMark
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.TubeMark
import dev.sebastiano.anatomy.kit.VoidMark
import dev.sebastiano.anatomy.kit.WireMark
import dev.sebastiano.anatomy.kit.axisVector
import dev.sebastiano.anatomy.kit.boxCorners
import dev.sebastiano.anatomy.kit.coil
import dev.sebastiano.anatomy.kit.cylinder
import dev.sebastiano.anatomy.kit.depthOf
import dev.sebastiano.anatomy.kit.dotGrid
import dev.sebastiano.anatomy.kit.extrude
import dev.sebastiano.anatomy.kit.fitProjection
import dev.sebastiano.anatomy.kit.haloOf
import dev.sebastiano.anatomy.kit.insetPlan
import dev.sebastiano.anatomy.kit.iso
import dev.sebastiano.anatomy.kit.knurl
import dev.sebastiano.anatomy.kit.lineOnTop
import dev.sebastiano.anatomy.kit.planOutline
import dev.sebastiano.anatomy.kit.radialTicks
import dev.sebastiano.anatomy.kit.ring
import dev.sebastiano.anatomy.kit.ringSeam
import dev.sebastiano.anatomy.kit.rise
import dev.sebastiano.anatomy.kit.screenRise
import dev.sebastiano.anatomy.kit.segment
import dev.sebastiano.anatomy.kit.sideArc
import dev.sebastiano.anatomy.kit.sideMatrix
import dev.sebastiano.anatomy.kit.sideRing
import dev.sebastiano.anatomy.kit.sideSeam
import dev.sebastiano.anatomy.kit.sideTicks
import dev.sebastiano.anatomy.kit.slabOf
import dev.sebastiano.anatomy.kit.topMatrix
import dev.sebastiano.anatomy.kit.topTicks
import dev.sebastiano.anatomy.kit.translateAlong
import java.math.BigDecimal
import kotlin.math.PI
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Holds the Kotlin kit to the original. The goldens under `resources/goldens/` are output of the
 * original's `kit/iso-kit.mjs` and its built dial-indicator page, captured by
 * `anatomy/parity/capture.mjs` (see the note at its top). Every path must match to the 0.1 the
 * original writes, every number to the digits it prints.
 */
class ParityTest {
    @Test
    fun kitMatchesTheOriginal() {
        assertLines(golden("kit.txt"), kitCases())
    }

    @Test
    fun dialIndicatorMatchesTheOriginalPage() {
        val d = DialIndicator
        val offsets =
            mapOf(
                "carriage" to translateAlong(d.P, Point3(d.STOPS.first(), 0.0, 0.0)),
                "plunger" to
                    translateAlong(d.P, Point3(0.0, 0.0, d.CARRIAGE_TOP + d.BLOCKS.first())),
            )
        val lines = ArrayList<String>()
        lines.serialize(d.ART, offsets)
        val needle = d.needleAt(d.BLOCKS.first() / d.PER_TURN)
        lines.add(listOf("path", "needle:iso-line", "lit", "-", needle.svg()).joinToString("\t"))
        assertLines(golden("dial-indicator.txt"), lines)
    }

    private fun assertLines(expected: List<String>, actual: List<String>) {
        expected.zip(actual).forEachIndexed { index, (want, got) ->
            assertEquals(want, got, "line ${index + 1} differs")
        }
        assertEquals(expected.size, actual.size, "line count")
    }

    private fun golden(name: String): List<String> =
        javaClass.getResource("/goldens/$name")!!.readText().trimEnd('\n').split('\n')

    private fun MutableList<String>.serialize(art: Art, offsets: Map<String, Flat>) {
        fun path(kind: String, tone: Tone?, shade: Int?, d: String) =
            add(
                listOf("path", kind, tone?.css ?: "-", shade?.toString() ?: "-", d)
                    .joinToString("\t")
            )
        for (mark in art.marks) {
            when (mark) {
                is HaloMark -> path("iso-halo", null, null, mark.shape.iso.svg())
                is SolidMark -> solid(mark, ::path)
                is LineMark -> path("iso-line", mark.tone, null, mark.shape.iso.svg())
                is DotsMark ->
                    mark.points.forEach {
                        add("dot\t${fixed(it.x, 1)}\t${fixed(it.y, 1)}\t${fixed(mark.size, 6)}")
                    }
                is TextMark ->
                    add(
                        "text\t${matrix(mark.matrix)}\t${fixed(mark.size, 6)}\t" +
                            "${mark.anchor.name.lowercase()}\t${mark.text}"
                    )
                is GroupMark -> {
                    val at = offsets.getValue(mark.part)
                    add("group\t${mark.part}\ttranslate(${fixed(at.x, 2)} ${fixed(at.y, 2)})")
                    serialize(mark.art, offsets)
                }
                is WireMark,
                is dev.sebastiano.anatomy.kit.FillMark,
                is VoidMark,
                is FadeLineMark,
                is TubeMark -> error("not in this figure")
            }
        }
    }

    private fun solid(mark: SolidMark, path: (String, Tone?, Int?, String) -> Unit) {
        val p = mark.paths
        path("iso-fill", null, null, p.fill.svg())
        if (!mark.flat) {
            p.shades.forEachIndexed { index, shade ->
                if (!shade.isEmpty) path("iso-shade", null, index, shade.svg())
            }
            path("iso-top", null, null, p.top.svg())
        }
        if (mark.crease != null && !p.crease.isEmpty)
            path("iso-line iso-crease", mark.crease, null, p.crease.svg())
        if (mark.crease != null && p.bevel?.isEmpty == false)
            path("iso-line iso-bevel", mark.crease, null, p.bevel.svg())
        path("iso-line iso-edge", mark.tone, null, p.outline.svg())
    }

    private val Tone.css: String
        get() = name.lowercase()

    @Suppress("LongMethod")
    private fun kitCases(): List<String> {
        val lines = ArrayList<String>()
        fun put(name: String, value: String) = lines.add("$name\t$value")
        fun flat(p: Flat) = "${fixed(p.x, 4)},${fixed(p.y, 4)}"
        fun solid(name: String, paths: SolidPaths) {
            put("$name.fill", paths.fill.svg())
            put("$name.outline", paths.outline.svg())
            put("$name.crease", paths.crease.svg())
            put("$name.top", paths.top.svg())
            paths.shades.forEachIndexed { index, shade -> put("$name.shade$index", shade.svg()) }
            put("$name.bevel", paths.bevel?.svg() ?: "")
        }
        val box = Plan(-40.0, -30.0, 80.0, 60.0, 6.0)
        val cameras =
            listOf(
                Triple("a45", 45.0, 30.0),
                Triple("a57", 57.0, 30.0),
                Triple("a30e44", 30.0, 44.0),
            )
        for ((name, azimuth, elevation) in cameras) {
            val p =
                fitProjection(
                    boxCorners(box, -10.0, 40.0),
                    600.0,
                    360.0,
                    pad = 28.0,
                    headroom = 12.0,
                    azimuth = azimuth,
                    elevation = elevation,
                )
            put(
                "$name.projection",
                "${fixed(p.origin.x, 4)},${fixed(p.origin.y, 4)},${fixed(p.scale, 6)}",
            )
            put("$name.iso", flat(iso(Point3(12.0, -7.0, 5.0), p)))
            put("$name.depth", fixed(depthOf(Point3(12.0, -7.0, 5.0), p), 6))
            put("$name.screenRise", fixed(screenRise(10.0, p), 6))
            put("$name.axis", flat(axisVector(p, Point3(1.0, 2.0, 3.0))))
            solid("$name.slab", slabOf(box, 0.0, 12.0, p, 8, 1.2))
            solid("$name.cylinder", cylinder(10.0, -5.0, 9.0, 2.0, 6.0, p, 24, 0.8))
            solid("$name.flatSlab", slabOf(Plan(0.0, 0.0, 10.0, 4.0, 0.0), 0.0, 0.0, p, 2))
            val ell =
                listOf(
                        0.0 to 0.0,
                        30.0 to 0.0,
                        30.0 to 10.0,
                        10.0 to 10.0,
                        10.0 to 30.0,
                        0.0 to 30.0,
                    )
                    .map { Floor(it.first, it.second) }
            solid("$name.concave", extrude(ell, 0.0, 8.0, p, convex = false, bevel = 0.6))
            put("$name.planOutline", planOutline(insetPlan(box, 4.0), 12.0, p, 6).svg())
            put("$name.ring", ring(5.0, 5.0, 3.0, 12.0, p, 16).svg())
            put("$name.lineOnTop", lineOnTop(Floor(0.0, 0.0), Floor(10.0, 4.0), 3.0, p).svg())
            put("$name.segment", segment(Point3(0.0, 0.0, 0.0), Point3(3.0, 4.0, 5.0), p).svg())
            put("$name.rise", rise(Floor(2.0, 3.0), 1.0, 9.0, p).svg())
            put("$name.knurl", knurl(0.0, 0.0, 6.0, 0.0, 4.0, 36, p).svg())
            put("$name.sideArc", sideArc(0.0, 0.0, 6.0, 2.0, p, 12).svg())
            put("$name.sideSeam", sideSeam(box, 6.0, p, 6).svg())
            put("$name.ringSeam", ringSeam(ell, 4.0, p).svg())
            put("$name.sideRingX", sideRing(Point3(10.0, 2.0, 8.0), 3.0, Axis.X, p, 12).svg())
            put("$name.sideRingY", sideRing(Point3(10.0, 2.0, 8.0), 3.0, Axis.Y, p, 12).svg())
            for (axis in listOf(Axis.X, Axis.Y)) {
                val c = coil(-10.0, 20.0, Floor(2.0, 6.0), 4.2, 5.5, axis, p)
                val key = axis.name.lowercase()
                put("$name.coil$key.back", c.back.svg())
                put("$name.coil$key.front", c.front.svg())
            }
            val top = topTicks(-31.0, 29.0, 4.0, 5, 20.0, 1.0, 2.0 to 3.6, p)
            put("$name.topTicks.minor", top.minor.svg())
            put("$name.topTicks.major", top.major.svg())
            val topY = topTicks(-12.0, 12.0, 3.0, 4, -20.0, 1.0, 1.5 to 3.0, p, Axis.Y)
            put("$name.topTicksY.minor", topY.minor.svg())
            put("$name.topTicksY.major", topY.major.svg())
            val side = sideTicks(-20.0, 20.0, 5.0, 2, 30.0, 10.0, 2.0 to 4.0, p)
            put("$name.sideTicks.minor", side.minor.svg())
            put("$name.sideTicks.major", side.major.svg())
            val sideY = sideTicks(-9.0, 9.0, 3.0, 3, 40.0, 8.0, 1.0 to 2.0, p, Axis.Y)
            put("$name.sideTicksY.minor", sideY.minor.svg())
            put("$name.sideTicksY.major", sideY.major.svg())
            val dial = radialTicks(0.0, 0.0, 18.0, 50, 5, 10.0, 1.3 to 2.8, p)
            put("$name.radial.minor", dial.minor.svg())
            put("$name.radial.major", dial.major.svg())
            val arc = radialTicks(0.0, 0.0, 18.0, 12, 3, 10.0, 1.0 to 2.0, p, -PI / 3, PI * 2 / 3)
            put("$name.radialArc.minor", arc.minor.svg())
            put("$name.radialArc.major", arc.major.svg())
            put(
                "$name.dotGrid",
                dotGrid(-6.0, -4.0, 4, 3, 2.5, 7.0, p).joinToString(" ") { flat(it) },
            )
            put("$name.halo", haloOf(box, -2.0, 4.0, p).svg())
            val by = translateAlong(p, Point3(12.0, -4.0, 3.0))
            put("$name.translate", "translate(${fixed(by.x, 2)} ${fixed(by.y, 2)})")
            put("$name.topMatrix", matrix(topMatrix(Floor(4.0, 5.0), 6.0, p, Along.X)))
            put("$name.topMatrixY", matrix(topMatrix(Floor(4.0, 5.0), 6.0, p, Along.Y)))
            put("$name.sideLeft", matrix(sideMatrix(Floor(4.0, 5.0), 6.0, p, Face.Left)))
            put("$name.sideRight", matrix(sideMatrix(Floor(4.0, 5.0), 6.0, p, Face.Right)))
        }
        return lines
    }

    private fun matrix(m: FaceMatrix): String =
        listOf(m.u.x, m.u.y, m.v.x, m.v.y, m.origin.x, m.origin.y).joinToString(
            " ",
            "matrix(",
            ")",
        ) {
            fixed(it, 3)
        }

    /** JavaScript's `String(Math.round(value * 10^digits) / 10^digits)`. */
    private fun fixed(value: Double, digits: Int): String {
        var scale = 1.0
        repeat(digits) { scale *= 10 }
        val rounded = floor(value * scale + 0.5) / scale
        if (rounded == 0.0) return "0"
        return BigDecimal(rounded.toString()).stripTrailingZeros().toPlainString()
    }
}
