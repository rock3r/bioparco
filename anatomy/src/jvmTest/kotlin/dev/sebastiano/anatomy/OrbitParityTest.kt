// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.audit.Recorder
import dev.sebastiano.anatomy.kit.audit.ShapeMeta
import dev.sebastiano.anatomy.kit.audit.slab
import dev.sebastiano.anatomy.kit.fitProjection
import dev.sebastiano.anatomy.kit.turn.Details
import dev.sebastiano.anatomy.kit.turn.LiveLayer
import dev.sebastiano.anatomy.kit.turn.Material
import dev.sebastiano.anatomy.kit.turn.OrbitFamily
import dev.sebastiano.anatomy.kit.turn.OrbitOptions
import dev.sebastiano.anatomy.kit.turn.PartStyle
import dev.sebastiano.anatomy.kit.turn.RibSeams
import dev.sebastiano.anatomy.kit.turn.RibSpec
import dev.sebastiano.anatomy.kit.turn.StaticItem
import dev.sebastiano.anatomy.kit.turn.TubeOptions
import dev.sebastiano.anatomy.kit.turn.TurnGroup
import dev.sebastiano.anatomy.kit.turn.TurnValues
import dev.sebastiano.anatomy.kit.turn.Turning
import dev.sebastiano.anatomy.kit.turn.jsCos
import dev.sebastiano.anatomy.kit.turn.jsSin
import dev.sebastiano.anatomy.kit.turn.orbit
import dev.sebastiano.anatomy.kit.turn.orbitReport
import dev.sebastiano.anatomy.kit.turn.round
import dev.sebastiano.anatomy.kit.turn.tube
import java.util.zip.GZIPInputStream
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Holds the Kotlin orbit audit to the original's `kit/turn-audit.mjs`. The golden
 * `resources/goldens/orbit.txt.gz` is captured by `anatomy/parity/orbit.mjs` (see the note at its
 * top): the report on `spin` of `parity/turn.mjs`, clean and with three planted faults, in a quick
 * run with a wiggle clip. Every line must match as the original prints it, but the run time.
 */
class OrbitParityTest {
    @Test
    fun orbitReportsMatchTheOriginal() {
        val out = ArrayList<String>()
        for (broken in listOf(false, true)) {
            val name = if (broken) "broken" else "clean"
            val spin = Spin.build(broken)
            val wiggle = OrbitFamily("wiggle", Spin::wiggle, 0.0, 1.0, 1 / 60.0)
            val result =
                orbit(
                    spin.t,
                    spin.r,
                    spin.t.projection,
                    spin.statics,
                    OrbitOptions(quick = true, clips = listOf(wiggle)),
                )
            val report = orbitReport(result).split('\n')
            for (line in report.dropLast(1)) out.add("$name| $line")
            out.add("$name| failures ${result.failures}")
        }
        val expected =
            GZIPInputStream(javaClass.getResourceAsStream("/goldens/orbit.txt.gz")!!)
                .bufferedReader()
                .readText()
                .trimEnd('\n')
                .split('\n')
        expected.zip(out).forEachIndexed { index, (want, got) ->
            assertEquals(want, got, "line ${index + 1} differs")
        }
        assertEquals(expected.size, out.size, "line count")
    }
}

/** `spin(broken)` of `parity/turn.mjs`: a turntable with a block, a post and a hinged pawl. */
private object Spin {
    class Scene(val t: Turning, val r: Recorder, val statics: List<StaticItem>)

    private fun p(x: Double, y: Double, z: Double) = Point3(x, y, z)

    private val UP = Frame(p(0.0, 0.0, 0.0), p(0.0, 0.0, 1.0), p(1.0, 0.0, 0.0), p(0.0, 1.0, 0.0))

    fun wiggle(t: Double) =
        TurnValues(mapOf("table" to 20 + 10 * t, "pawl" to 20 * jsSin(2 * PI * t)))

    fun build(broken: Boolean): Scene {
        val pp =
            fitProjection(
                listOf(
                    p(-30.0, -30.0, -4.0),
                    p(30.0, 30.0, 12.0),
                    p(-30.0, 30.0, 0.0),
                    p(30.0, -30.0, 0.0),
                ),
                360.0,
                280.0,
                pad = 20.0,
                azimuth = 40.0,
                elevation = 31.0,
            )
        val r = Recorder(pp)
        val t = Turning(pp, r)
        val table = t.turnGroup("table", Floor(0.0, 0.0))
        val pawl = t.hingeGroup("pawl", p(10.0, 6.0, 0.0), p(0.0, 0.0, 1.0), -20.0 to 20.0, table)
        val live = t.layer("live")
        t.stack(listOf("back", "live", "front"))
        parts(t, table, pawl, live)
        if (broken) faults(t, table, pawl, live)
        val floor =
            r.solid(
                slab(
                    Plan(-30.0, -30.0, 60.0, 60.0, 3.0),
                    -4.0,
                    4.0,
                    ShapeMeta(name = "stand.floor", ground = true),
                )
            )
        r.put(null, listOf(floor), name = "stand.floor")
        val statics = listOf(StaticItem("stand.floor", "back", listOf(floor)))
        val poses =
            (0 until 180).map { i ->
                TurnValues(mapOf("table" to i * 2.0, "pawl" to 20 * jsSin(i * 0.3)))
            }
        t.build(statics, poses, strict = !broken)
        return Scene(t, r, statics)
    }

    private fun parts(t: Turning, table: TurnGroup, pawl: TurnGroup, live: LiveLayer) {
        t.round(
            "table.disc",
            table,
            live,
            TurnLab.vert(0.0, 0.0),
            0.0,
            3.0,
            20.0,
            style = PartStyle(crease = Tone.Lo, material = Material.Steel),
            details = Details(ribs = RibSpec(0.6, 2.4, 60, seams = RibSeams.Both)),
        )
        t.prism(
            "table.block",
            table,
            live,
            Plan(-9.0, -7.0, 10.0, 8.0, 1.0),
            3.0,
            5.0,
            bevel = 0.4,
            style = PartStyle(material = Material.Gold),
            details = Details(seams = listOf(5.0)),
        )
        t.round(
            "table.post",
            table,
            live,
            TurnLab.vert(10.0, 6.0),
            3.0,
            9.0,
            1.5,
            style = PartStyle(material = Material.Chrome),
        )
        t.prism(
            "pawl.arm",
            pawl,
            live,
            UP,
            listOf(Floor(8.5, 4.5), Floor(14.0, 5.0), Floor(14.0, 7.0), Floor(8.5, 7.5)),
            9.0,
            10.0,
            bevel = 0.2,
            style = PartStyle(material = Material.Brass),
        )
    }

    /** The planted faults: a sunk lug, a blade that swings through the block, an end-on cable. */
    private fun faults(t: Turning, table: TurnGroup, pawl: TurnGroup, live: LiveLayer) {
        t.prism(
            "table.lug",
            table,
            live,
            Plan(-2.0, -4.0, 4.0, 3.0, 0.5),
            6.0,
            4.0,
            style = PartStyle(material = Material.Gunmetal),
        )
        t.prism(
            "pawl.blade",
            pawl,
            live,
            UP,
            listOf(Floor(-2.0, 3.0), Floor(9.0, 3.3), Floor(9.0, 4.4), Floor(-2.0, 4.4)),
            6.0,
            7.0,
            style = PartStyle(material = Material.Chrome),
        )
        val end = p(-6 + 8 * jsCos(0.7), -3 + 8 * jsSin(0.7) * 0.6, 8 + 8 * jsSin(0.54))
        t.tube(
            "cable.bad",
            table,
            live,
            listOf(p(-6.0, -3.0, 8.0), end),
            0.6,
            TubeOptions(),
            PartStyle(material = Material.Rubber),
        )
    }
}
