package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.figures.ArcadeBezel
import dev.sebastiano.anatomy.figures.ArcadeCabinet
import dev.sebastiano.anatomy.kit.Point3
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class ArcadeParityTest {
    @Test
    fun cabinetMatchesEveryOriginalPathAndRoute() {
        val expected =
            GZIPInputStream(javaClass.getResourceAsStream("/goldens/arcade.txt.gz")!!)
                .bufferedReader()
                .readLines()
        val actual = lines()
        val wrong = expected.indices.filter { expected[it] != actual.getOrNull(it) }
        val report =
            wrong.take(6).joinToString("\n") {
                "line ${it + 1}\n want ${expected[it].take(180)}\n got ${actual.getOrNull(it)?.take(180)}"
            }
        assertEquals(0, wrong.size, "${wrong.size} of ${expected.size} lines differ\n$report")
        assertEquals(expected.size, actual.size)
    }

    @Test
    fun bakedOrderMatchesFreshSettle() {
        val r = SETTLED.recorder
        val actual =
            r.painted().map {
                r.items.indexOf(it) to
                    String.format(java.util.Locale.ROOT, "%.2f", it.key).toDouble()
            }
        assertEquals(actual, ArcadeCabinet.ORDER)
    }

    private fun lines(): List<String> = buildList {
        val b = SETTLED
        add("back")
        arcadeArt(b.back)
        for (item in b.recorder.painted()) {
            add(
                listOf(
                        "item",
                        item.name,
                        item.piece,
                        ParityLines.fixed(item.key, 6),
                        ParityLines.p3(item.move),
                    )
                    .joinToString("\t")
            )
            arcadeArt(item.art!!)
            when (item.name) {
                "marquee.plexi" -> {
                    b.surfaces.getValue(item.name).paths.forEach { add("path\t$it") }
                    add("rect\t-56\t-13.6\t112\t27.2\t2")
                }
                "meter.face" -> {
                    add("rect\t0\t0\t6.8\t4.8\t0.4")
                    add("text\t0.35\t3.55\t3\t6.1\t004127")
                }
                "fan.rotor" -> {
                    val paths = b.surfaces.getValue(item.name).paths
                    add("dot\t0\t0\t12\t-")
                    add("path\t${paths[0]}")
                    add("dot\t0\t0\t3.4\t-")
                    add("path\t${paths[1]}")
                }
                "bezel" -> {
                    val paths = ArcadeBezel.paths()
                    val scanAt = 3 + dev.sebastiano.anatomy.figures.ArcadeScreen.paths().size
                    paths.forEachIndexed { i, path ->
                        if (i == scanAt) add("ellipse\t0\t0\t1.2\t1.2")
                        add("path\t$path")
                    }
                }
            }
        }
        for (r in b.recorder.routes) add(
            listOf(
                    "route",
                    r.name,
                    r.piece,
                    ParityLines.p3(r.move ?: Point3(0.0, 0.0, 0.0)),
                    ParityLines.num(r.r),
                    r.points.joinToString(" ") { ParityLines.p3(it) },
                )
                .joinToString("\t")
        )
        for (name in listOf("marquee.plexi", "meter.face", "fan.rotor")) add(
            arcadeMatrixLine(b.surfaces.getValue(name).matrix)
        )
        add(arcadeMatrixLine(ArcadeBezel.SCREEN_MATRIX))
        add(arcadeMatrixLine(ArcadeBezel.MATRIX))
    }

    private companion object {
        val SETTLED by lazy { ArcadeCabinet.settled() }
    }
}
