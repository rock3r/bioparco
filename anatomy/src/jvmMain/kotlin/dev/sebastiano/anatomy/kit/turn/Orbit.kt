// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Projection
import dev.sebastiano.anatomy.kit.audit.Recorder
import dev.sebastiano.anatomy.kit.audit.jsFixed
import dev.sebastiano.anatomy.kit.jsRound
import kotlin.math.max

/*
 * The orbit audit, `kit/turn-audit.mjs`: proves a turning figure over every turn angle and motion
 * clip. Each check is its own file: the painter's order (OrbitOrder.kt), the kit's rigid checks,
 * swept envelopes and posed clearances (OrbitChecks.kt), live routes (OrbitRoutes.kt), stand-in
 * shapes and the depth oracle (OrbitDepth.kt), and continuity (OrbitContinuity.kt). `orbitReport`
 * writes the original's report, ending `turn audit passed` only for a full run with no problems.
 */

/** A motion the audit samples: a turn sweep (periodic) or a clip, by [at] from [from] to [to]. */
class OrbitFamily(
    val name: String,
    val at: (Double) -> TurnValues,
    val from: Double,
    val to: Double,
    val frame: Double,
    val periodic: Boolean = false,
    val unit: String = if (periodic) "°" else "s",
    val delta: Double? = null,
)

/** What to sample and what to excuse; see `references/3d.md`, "The orbit audit". */
class OrbitOptions(
    /** The turn, `0…360` (or more) in steps of [OrbitFamily.frame] degrees. */
    val sweep: OrbitFamily? = null,
    /** Gestures and the break-apart, every frame at 60 fps. */
    val clips: List<OrbitFamily> = emptyList(),
    /** Extra states. */
    val poses: List<TurnValues> = emptyList(),
    val random: Int = 2000,
    val step: Double = 0.05,
    /** Route × part pairs exempt from `crowding`, after you have looked. */
    val crowd: List<Pair<String, String>> = emptyList(),
    val cover: List<String> = emptyList(),
    /** Stretches of a route inside a fitting, exempt from `endon`. */
    val fittings: List<Triple<String, Double, Double>> = emptyList(),
    val depthAngles: Int = 36,
    val depthEvents: Int = 36,
    /** About ten times fewer states, for iteration: only a full run is the proof. */
    val quick: Boolean = false,
    val log: ((String) -> Unit)? = null,
    val routeStates: Int = 240,
    val posedStep: Double? = null,
    /**
     * Run only these checks: `order`, `rigid`, `swept`, `posed`, `routes`, `coverage`,
     * `continuity`, `depth`.
     */
    val only: List<String>? = null,
)

/** One finding: its line, the text after it, and the state it was found in (for grouping). */
class Finding(
    val text: String,
    val state: String = "",
    val layer: String = "",
    val parts: List<String> = emptyList(),
)

/** A reorder between two neighbouring states: where, how many pairs, which. */
class OrderChange(val family: OrbitFamily, val x: Double, val count: Int, val pairs: List<String>)

/** What the orbit audit found, line by line, and what it measured. */
class OrbitResult(val quick: Boolean, val skipped: List<String>) {
    val lines: Map<String, MutableList<Finding>> = ORBIT_LINES.keys.associateWith { ArrayList() }
    val stats = HashMap<String, Double>()
    var changes: List<OrderChange> = emptyList()
    var worst: Map<String, Double> = emptyMap()
    var halvingChecked = 0
    var halvingFails = 0

    operator fun get(line: String): MutableList<Finding> = lines.getValue(line)

    /** How many problems fail the audit. */
    val failures: Int
        get() = lines.values.sumOf { it.size }
}

/** Each report line and the check that runs it (`separation` always runs). */
internal val ORBIT_LINES =
    linkedMapOf(
        "separation" to null,
        "unseparated" to "order",
        "cycles" to "order",
        "forced" to "order",
        "swaps" to "order",
        "layers" to "depth",
        "rigid" to "rigid",
        "swept" to "swept",
        "posed" to "posed",
        "folds" to "routes",
        "self" to "routes",
        "crowding" to "routes",
        "endon" to "routes",
        "coverage" to "coverage",
        "depth" to "depth",
        "continuity" to "continuity",
    )

internal val ORBIT_CHECKS =
    listOf("order", "rigid", "swept", "posed", "routes", "coverage", "continuity", "depth")

/** `f2`: a number to two decimals, as the report prints it. */
internal fun f2(v: Double) = jsFixed(jsRound(v * 100) / 100, 2)

/** `f3`: a number to three decimals. */
internal fun f3(v: Double) = jsFixed(jsRound(v * 1000) / 1000, 3)

/** The samples of [family] at [frame] apart: a periodic one stops short of its end. */
internal fun samplesOf(family: OrbitFamily, frame: Double): List<Double> {
    val count = max(1, jsRound((family.to - family.from) / frame).toInt())
    return (0 until count + if (family.periodic) 0 else 1).map { family.from + it * frame }
}

/** Everything the checks share: the figure, its prepared scene, the families and the result. */
internal class OrbitRun(
    val t: Turning,
    val r: Recorder,
    val p: Projection,
    val statics: List<StaticItem>,
    val options: OrbitOptions,
) {
    val built: TurnBuild = t.built ?: error("orbit: build the figure first")
    val scene: TurnScene = prepareTurn(built.data, 1.0)
    val runs: List<TurnRun> = scene.runs
    val quick = options.quick
    val result =
        OrbitResult(
            quick,
            ORBIT_LINES.filter { (_, check) -> check != null && !wants(check) }.keys.toList(),
        )
    val families: List<OrbitFamily> = familiesOf()
    val extra = options.poses

    fun wants(check: String) = options.only == null || check in options.only

    fun say(text: String) = options.log?.invoke(text)

    fun frameOf(family: OrbitFamily) =
        if (quick) family.frame * (if (family.periodic) max(1.0, 1 / family.frame) else 3.0)
        else family.frame

    fun label(family: OrbitFamily, x: Double) = "${family.name} ${jsFixed(x, 3)}${family.unit}"

    /** Every part emitted for [values] into fresh frames. */
    fun emitAll(values: TurnValues): Pair<TurnCams, List<TurnFrame>> {
        val cams = scene.cams(values)
        val frames = scene.frames()
        for (run in runs) run.emit(cams.of(run.group), frames[run.index])
        return cams to frames
    }

    private fun familiesOf(): List<OrbitFamily> {
        val out = ArrayList<OrbitFamily>()
        val roots = t.groups.filter { it.kind == GroupKind.Turn && it.parent == null }
        val sweep = options.sweep
        if (sweep != null) out.add(sweep)
        else if (roots.isNotEmpty()) {
            out.add(
                OrbitFamily(
                    "sweep",
                    { x -> TurnValues(roots.associate { it.name to x }) },
                    0.0,
                    360.0,
                    0.3,
                    periodic = true,
                )
            )
        }
        out.addAll(options.clips)
        return out
    }
}

/**
 * Runs the orbit audit on [t] (built) with its recorder [r], camera [p] and [statics]: every line
 * of `references/3d.md`'s table, as the original's `orbit`.
 */
fun orbit(
    t: Turning,
    r: Recorder,
    p: Projection,
    statics: List<StaticItem>,
    options: OrbitOptions = OrbitOptions(),
): OrbitResult {
    val unknown = options.only.orEmpty().filter { it !in ORBIT_CHECKS }
    require(unknown.isEmpty()) {
        "orbit: unknown check ${unknown.joinToString(", ")} in only (the checks are ${ORBIT_CHECKS.joinToString(", ")})"
    }
    val started = System.nanoTime()
    val run = OrbitRun(t, r, p, statics, options)
    run.say("separation")
    val margins = run.built.report.margins
    for (m in margins) if (m.margin < -0.08)
        run.result["separation"].add(
            Finding("interlock ${m.a} × ${m.b} ${f2(m.margin)}: split, flats or a flush contact")
        )
    run.result.stats["planes"] = margins.size.toDouble()
    run.result.stats["tightest"] = if (margins.isEmpty()) 0.0 else margins.minOf { it.margin }
    OrbitOrder(run).scan()
    OrbitChecks(run).run()
    OrbitRoutes(run).run()
    OrbitDepth(run).coverage()
    if (run.wants("continuity")) {
        TurnState.exact = true
        try {
            OrbitContinuity(run).run()
        } finally {
            TurnState.exact = false
        }
    }
    OrbitDepth(run).depth()
    run.result.stats["seconds"] = (System.nanoTime() - started) / 1e9
    return run.result
}
