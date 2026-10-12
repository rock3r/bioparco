// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.ParityLines.Companion.num
import dev.sebastiano.anatomy.figures.TurningDial
import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Projection
import dev.sebastiano.anatomy.kit.turn.Material
import dev.sebastiano.anatomy.kit.turn.TurnValues
import dev.sebastiano.anatomy.kit.turn.Turning
import dev.sebastiano.anatomy.kit.turn.jsAcos
import dev.sebastiano.anatomy.kit.turn.jsAtan2
import dev.sebastiano.anatomy.kit.turn.jsCos
import dev.sebastiano.anatomy.kit.turn.jsSin
import dev.sebastiano.anatomy.kit.turn.materialCss
import dev.sebastiano.anatomy.kit.turn.turnFloorOf
import dev.sebastiano.anatomy.kit.turn.turnRotation
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Holds the Kotlin 3D mode to the original's `kit/turn-build.mjs` and `kit/turn.mjs`. The golden
 * `resources/goldens/turn.txt.gz` is captured by `anatomy/parity/turn.mjs` (see the note at its
 * top), which builds the same two scenes (a lab scene with every group and part kind, and the
 * turning dial) and drives the runtime through the same states. Every path must match to the 0.01
 * the runtime writes (one state of each scene at the 0.000001 of `TURN.exact`), every order and
 * every number to the digits printed.
 */
class TurnParityTest {
    @Test
    fun turnMatchesTheOriginal() {
        val out = ParityLines()
        out.lab()
        out.broken()
        out.dial()
        out.math()
        out.put("materials", Material.entries.joinToString(" ") { it.id })
        out.put(
            "materials.css",
            materialCss()
                .split("\n")
                .filter { it.startsWith(".iso[data-theme=\"") }
                .joinToString(" "),
        )
        compare(out.lines)
    }

    private fun ParityLines.lab() {
        val lab = TurnLab.build()
        report("lab", lab.built)
        data("lab", lab.built.data)
        val states = TurnLab.states()
        val driven = drive("lab", lab.built.data, states, { it % 6 == 0 || it >= 40 }, 6)
        extras(
            "lab",
            driven,
            states.last(),
            listOf(listOf(listOf("arm.link")), listOf(listOf("arm.cone"), listOf("table.knob"))),
            listOf(
                Triple("arm", Point3(26.0, 4.0, 24.0), Point3(1.0, 0.0, 0.0)),
                Triple("bob", Point3(-10.0, 12.0, 18.0), null),
                Triple(null, Point3(0.0, 0.0, 0.0), Point3(0.0, 0.0, 1.0)),
            ),
            listOf("table", "arm", "bob"),
        )
        put(
            "lab.floor",
            "${num(turnFloorOf(driven.scene, "bob"))} ${num(turnFloorOf(driven.scene, "bob", turnRotation(Point3(1.0, 0.0, 0.0), 90.0)))}",
        )
    }

    private fun ParityLines.broken() {
        val t = Turning(Projection(Flat(200.0, 150.0), 2.0, 45.0, 30.0))
        val g = t.turnGroup("g", Floor(0.0, 0.0))
        val live = t.layer("live")
        t.prism("a", g, live, Plan(0.0, 0.0, 10.0, 10.0, 1.0), 0.0, 4.0)
        t.prism("b", g, live, Plan(5.0, 5.0, 10.0, 10.0, 1.0), 2.0, 4.0)
        val message = runCatching {
            t.build(poses = listOf(TurnValues(mapOf("g" to 0.0)), TurnValues(mapOf("g" to 90.0))))
        }.fold({ "built" }, { it.message!!.replace("\n", " / ") })
        put("broken.build", message)
    }

    private fun ParityLines.dial() {
        val d = TurningDial
        val scene = d.scene
        report("dial", scene.built)
        data("dial", scene.built.data)
        val states = ArrayList<TurnValues>()
        for (i in 0 until 24) states.add(
            d.follow(TurnValues(mapOf("dial" to i * 15.5, "lift" to d.tapAt(i / 6.0))))
        )
        for (i in 0 until 12) {
            states.add(
                d.follow(
                    TurnValues(
                        mapOf("dial" to 100 + 12 * (i / 5.0), "lift" to 0.3),
                        mapOf("pod" to d.breakAt(i / 5.0)),
                    )
                )
            )
        }
        for (i in 0 until 8) states.add(
            d.follow(TurnValues(mapOf("dial" to -37 + i * 0.3, "lift" to d.tapAt(1 + i / 30.0))))
        )
        val driven = drive("dial", scene.built.data, states, { it in listOf(0, 3, 17, 30, 41) }, 3)
        val led = scene.led
        extras(
            "dial",
            driven,
            states.last(),
            listOf(
                listOf(listOf("hand.f2.p3")),
                listOf(listOf("hand.f2.p3"), listOf("hand.pod.socket")),
            ),
            listOf(Triple(led.group, led.at, led.normal), Triple("dial", scene.podAt, null)),
            listOf("dial", "f2.dip", "pod"),
        )
        put("dial.floor", num(turnFloorOf(driven.scene, "pod")))
    }

    /** The runtime's trigonometry, bit for bit, on the arguments the script drew. */
    private fun ParityLines.math() {
        var seed = 4242L
        fun rand(): Double {
            seed = (seed * 16807) % 2147483647
            return seed.toDouble() / 2147483647
        }
        fun bits(v: Double) = java.lang.Long.toHexString(v.toRawBits())
        val scales = doubleArrayOf(0.8, 3.2, 7.0, 60.0, 400.0)
        for (i in 0 until 400) {
            val x = (rand() * 2 - 1) * scales[i % 5]
            val y = rand() * 2 - 1
            val z = rand() * 2 - 1
            put(
                "math.$i",
                listOf(x, y, z, jsSin(x), jsCos(x), jsAcos(y), jsAtan2(y, z)).joinToString(" ") {
                    bits(it)
                },
            )
        }
    }

    private fun compare(lines: List<String>) {
        val expected =
            GZIPInputStream(javaClass.getResourceAsStream("/goldens/turn.txt.gz")!!)
                .bufferedReader()
                .readText()
                .trimEnd('\n')
                .split('\n')
        val wrong = ArrayList<String>()
        for (index in 0 until minOf(expected.size, lines.size)) {
            if (expected[index] != lines[index]) {
                wrong.add(
                    "line ${index + 1}:\n  want ${expected[index].take(600)}\n  got  ${lines[index].take(600)}"
                )
                if (wrong.size >= 8) break
            }
        }
        if (wrong.isNotEmpty()) fail("${wrong.size}+ lines differ\n${wrong.joinToString("\n")}")
        assertEquals(expected.size, lines.size, "line count")
    }
}
