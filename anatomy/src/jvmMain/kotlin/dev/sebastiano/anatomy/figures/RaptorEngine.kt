// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.audit.AuditItem
import dev.sebastiano.anatomy.kit.audit.AuditOptions
import dev.sebastiano.anatomy.kit.audit.AuditedFigure
import dev.sebastiano.anatomy.kit.audit.Recorder
import dev.sebastiano.anatomy.kit.dot3

/** One painted item of the live figure: its [key] at rest, its [lift] and screen [shift] at share 1. */
internal class RaptorNode(val item: AuditItem, val key: Double, val lift: Double, val shift: Flat)

/**
 * Fig 6, the Raptor 2 on its test stand: the original's build.mjs up to its page. Settling the
 * painter's keys measures 1.7 million overlaps, so the live figure doesn't: like the original's
 * page, it reads the keys the settle wrote, rounded to two places, from `raptor-order.txt` (the
 * items in settled order), which `RaptorParityTest` checks against a fresh settle. The audit
 * excuses what the original's does: two plumbing lines that cross the harness on purpose.
 */
internal object RaptorEngine : AuditedFigure {
    /** The build, unsettled: the live figure's art. */
    val BUILD: RaptorBuild by lazy { RaptorBuild() }

    /** A fresh build with its keys settled, for the audit and the parity tests. */
    fun settled(): RaptorBuild = RaptorBuild().also { it.recorder.settle() }

    override fun record(): Recorder = settled().recorder

    override val auditOptions: AuditOptions
        get() =
            AuditOptions(
                crowd =
                    listOf(
                        "plumbing.lox-to-frpb" to "controller.cable-block",
                        "plumbing.lox-to-frpb" to "controller.harness-frpb",
                    )
            )

    /** The items in settled order with their page keys: `index<TAB>key` lines. */
    fun bakedOrder(): List<Pair<Int, Double>> =
        javaClass.getResource("/raptor-order.txt")!!.readText().trim().lines().map { line ->
            val (index, key) = line.split('\t')
            index.toInt() to key.toDouble()
        }

    /** The live figure's items, in settled order. */
    val NODES: List<RaptorNode> by lazy {
        val items = BUILD.recorder.items
        val o = Raptor.at(Point3(0.0, 0.0, 0.0))
        bakedOrder().map { (index, key) ->
            val item = items[index]
            val m = Raptor.at(item.move)
            RaptorNode(item, key, dot3(item.move, Raptor.V), Flat(m.x - o.x, m.y - o.y))
        }
    }

    const val LABEL =
        "SpaceX Raptor 2 hanging from a vertical test stand: a deck on feet with a flame cone and " +
            "deluge ring, two columns, and a crosshead with a load cell and two gimbal actuators. " +
            "Below the gimbal sit the oxygen turbopump, the oxygen-rich preburner, the injector " +
            "and main chamber, and the bell nozzle with its cooling manifold; the methane " +
            "turbopump and fuel-rich preburner hang on the right, joined by the hot-gas manifold, " +
            "a downcomer, ducts, sense lines and the engine controller's harness."

    const val STAGE =
        "Raptor 2 on a test stand. Hover or use the arrow keys to take the engine apart part by " +
            "part; press I or the Ignite button to fire it, Escape to put it back together."

    const val CAPTION =
        "Raptor 2 is SpaceX's full-flow staged-combustion engine: liquid oxygen and methane at a " +
            "mixture ratio of 3.6, 300 bar in the main chamber, 230 tf at sea level, 1,630 kg " +
            "dry. Every drop of oxygen passes through an oxygen-rich preburner that spins the " +
            "oxygen pump, and every drop of methane through a fuel-rich preburner that spins the " +
            "methane pump; the two hot gases meet in the main chamber, which has no igniter of " +
            "its own. The load cell above the gimbal reads the thrust while it burns."
}
