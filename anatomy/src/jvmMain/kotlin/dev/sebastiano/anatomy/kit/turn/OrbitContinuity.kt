// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.audit.jsFixed
import dev.sebastiano.anatomy.kit.jsHypot
import dev.sebastiano.anatomy.kit.jsRound
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

/*
 * The orbit audit's continuity line: every group swept in steps of 0.05° (or 1/400 of a slide),
 * part by part, for a tone step over 1/16 band, a face or sheet born over 2 px², a dot or plane
 * opacity step over 0.01, a faded-line step over a level, slots overflowing, or the outline moving
 * further than the turn can carry it; then the halving test on the largest steps, and on the
 * largest steps of every clip: a jump stays large when its step is halved, a smooth change halves.
 */

/** One part's frame reduced to what continuity compares. */
private class Snap(
    val faces: List<Triple<Double, Int, Boolean>>,
    val sheets: List<Pair<Double, Int>>,
    val dots: List<Double>,
    val planes: List<Double>,
    val fade: Map<String, Map<Int, Double>>,
    val outline: List<List<Flat>>,
    val levels: Int,
)

/** How one step compared: the measures the original names. */
private class Step(val values: HashMap<String, Double> = HashMap()) {
    operator fun get(key: String) = values[key] ?: 0.0

    fun up(key: String, v: Double) {
        values[key] = max(values[key] ?: 0.0, v)
    }

    /** Whether this step breaks a bound, for a step [scale] times the audit's own. */
    fun fails(scale: Double) =
        get("q") > ceil(scale) ||
            get("birth") > 2 * scale ||
            get("dot") > 0.0105 * scale ||
            get("fade") > ceil(scale) / 32 + 0.005 ||
            get("merge") > 0 ||
            get("motion") > 0
}

private class Measure(
    val run: TurnRun,
    val x: Double,
    val dx: Double,
    val value: Double,
    val at: (Double) -> TurnValues,
)

private class Halving(
    val full: Double,
    val half: Double,
    val quarter: Double,
    val eighth: Double,
    val fails: Boolean,
)

internal class OrbitContinuity(private val o: OrbitRun) {
    private val stepSize = if (o.quick) o.options.step * 10 else o.options.step
    private val measures = ArrayList<Measure>()
    private val worst =
        linkedMapOf(
            "q" to 0.0,
            "birth" to 0.0,
            "dot" to 0.0,
            "fade" to 0.0,
            "motion" to 0.0,
            "merge" to 0.0,
        )
    private val failures = ArrayList<Finding>()
    private val k = o.scene.projection.scale * sqrt(1.6)

    private fun snap(run: TurnRun, f: TurnFrame): Snap {
        val faces = ArrayList<Triple<Double, Int, Boolean>>()
        val sheets = ArrayList<Pair<Double, Int>>()
        val dots = ArrayList<Double>()
        val planes = ArrayList<Double>()
        run.roles.forEachIndexed { r, role ->
            when {
                role.kind == RoleKind.Face ->
                    faces.add(
                        Triple(
                            if (f.d[r].isEmpty) 0.0 else areaOfPath(f.d[r]),
                            f.q[r],
                            !f.d[r].isEmpty,
                        )
                    )
                role.kind == RoleKind.Shade || role.kind == RoleKind.Top ->
                    sheets.add(areaOfPath(f.d[r]) to f.d[r].runs.size)
                role.kind == RoleKind.Dot -> dots.add(f.o[r])
                role.kind == RoleKind.Plane -> planes.add(f.o[r])
            }
        }
        val fade =
            f.fade.mapValues { (_, set) ->
                LinkedHashMap<Int, Double>().also { map ->
                    set.keys.forEachIndexed { i, key ->
                        map[key] = jsRound(set.alphas[i] * TURN_LEVELS) / TURN_LEVELS
                    }
                }
            }
        val outline =
            if (run is FixedRun) listOf(List(f.hullN) { Flat(f.hull[2 * it], f.hull[2 * it + 1]) })
            else
                polysOf(
                    run.role["fill"]?.let { f.d[it] }
                        ?: run.role["body"]?.let { f.d[it] }
                        ?: dev.sebastiano.anatomy.kit.IsoPath.Empty
                )
        return Snap(faces, sheets, dots, planes, fade, outline, f.levels)
    }

    private fun compare(run: TurnRun, a: Snap, b: Snap, dx: Double, reachPx: Double): Step {
        val m = Step()
        compareFaces(m, a, b)
        compareDetails(m, a, b)
        val slots = max(run.slots?.bevel ?: 0, run.slots?.ribs ?: 0)
        if (slots > 0 && (a.levels > slots || b.levels > slots))
            m.values["merge"] = (max(a.levels, b.levels) - slots).toDouble()
        val oa = a.outline.firstOrNull().orEmpty()
        val ob = b.outline.firstOrNull().orEmpty()
        if (oa.isNotEmpty() && ob.isNotEmpty()) {
            m.values["motion"] = max(0.0, hausdorffish(oa, ob) - (1.5 * reachPx * dx + 0.05))
        }
        return m
    }

    /** Tone steps and births of flat faces and curved sheets. */
    private fun compareFaces(m: Step, a: Snap, b: Snap) {
        for (i in 0 until minOf(a.faces.size, b.faces.size)) {
            val (areaA, qa, onA) = a.faces[i]
            val (areaB, qb, onB) = b.faces[i]
            if (onA && onB) m.up("q", (abs(qa - qb) * 16.0) / TURN_Q)
            if (onA != onB) m.up("birth", if (onA) areaA else areaB)
            m.up("area", abs(areaA - areaB))
        }
        for (i in 0 until minOf(a.sheets.size, b.sheets.size)) {
            val (areaA, countA) = a.sheets[i]
            val (areaB, countB) = b.sheets[i]
            if ((countA == 0) != (countB == 0)) m.up("birth", abs(areaA - areaB))
            m.up("area", abs(areaA - areaB))
        }
    }

    /** Opacity steps of dots, planes and faded lines. */
    private fun compareDetails(m: Step, a: Snap, b: Snap) {
        for (i in 0 until minOf(a.dots.size, b.dots.size)) m.up("dot", abs(a.dots[i] - b.dots[i]))
        for (i in 0 until minOf(a.planes.size, b.planes.size)) m.up(
            "dot",
            abs(a.planes[i] - b.planes[i]),
        )
        for (prefix in (a.fade.keys + b.fade.keys).distinct()) {
            val fa = a.fade[prefix] ?: emptyMap()
            val fb = b.fade[prefix] ?: emptyMap()
            for (key in (fa.keys + fb.keys).distinct()) m.up(
                "fade",
                abs((fa[key] ?: 0.0) - (fb[key] ?: 0.0)),
            )
        }
    }

    fun run() {
        for (g in o.t.groups) {
            if (g.loose) continue
            val range = g.range
            when (g.kind) {
                GroupKind.Turn -> sweep(g, 0.0, 360.0, "°")
                GroupKind.Hinge -> sweep(g, range!!.first, range.second, "°")
                else -> sweep(g, 0.0, g.travel, "")
            }
        }
        halvings()
    }

    private fun sweep(g: TurnGroup, from: Double, to: Double, unit: String) {
        val parts = o.runs.filter { it.group == g.index }
        if (parts.isEmpty()) return
        val span = to - from
        val slide = g.kind == GroupKind.Slide
        val delta = if (slide) max(0.01, span / 400) else stepSize
        val count = max(1, jsRound(span / delta).toInt())
        val reach = parts.map { run ->
            o.t.parts[run.index]
                .samples
                .maxOfOrNull { if (slide) 0.0 else jsHypot(it.x, it.y) }
                ?.let { max(0.0, it) } ?: 0.0
        }
        val valuesAt = { x: Double -> TurnValues(mapOf(g.name to x)) }
        fun emit(run: TurnRun, x: Double, f: TurnFrame): TurnFrame {
            run.emit(o.scene.cams(valuesAt(x))[g.index], f)
            return f
        }
        o.say(
            "continuity ${g.name}: $count steps of ${jsNumber(delta)}$unit over ${parts.size} parts"
        )
        val frames = parts.map { TurnFrame(it.roles.size) }
        val previous =
            parts.mapIndexed { i, run -> snap(run, emit(run, from, frames[i])) }.toMutableList()
        for (c in 1..count) {
            val x = from + c * delta
            parts.forEachIndexed { i, run ->
                val current = snap(run, emit(run, x, frames[i]))
                val reachPx = if (slide) k else (reach[i] * k * PI) / 180
                val m = compare(run, previous[i], current, delta, reachPx)
                for (key in worst.keys) worst[key] = max(worst.getValue(key), m[key])
                val scale = delta / o.options.step
                if (m.fails(scale)) failures.add(Finding(failure(run.name, g.name, x, m)))
                measures.add(
                    Measure(
                        run,
                        x - delta,
                        delta,
                        m["birth"] + m["motion"] * 4 + m["area"],
                        valuesAt,
                    )
                )
                previous[i] = current
            }
        }
    }

    private fun failure(part: String, group: String, at: Double, m: Step): String {
        val keys =
            listOf("q", "birth", "dot", "fade", "motion", "merge", "area").filter { m[it] > 0 }
        return "$part ($group) at ${f3(at)}: ${keys.joinToString(", ") { "$it ${f3(m[it])}" }}"
    }

    private fun halving(run: TurnRun, at: (Double) -> TurnValues, x: Double, dx: Double): Halving {
        val frames =
            (0..8).map { j ->
                val f = TurnFrame(run.roles.size)
                run.emit(o.scene.cams(at(x + (dx * j) / 8)).of(run.group), f)
                f
            }
        fun measure(scale: Int): Halving {
            val eighth = visibleChange(run, frames, scale).max()
            val quarter = visibleChange(run, listOf(0, 2, 4, 6, 8).map { frames[it] }, scale).max()
            val half = visibleChange(run, listOf(frames[0], frames[4], frames[8]), scale).max()
            val full = visibleChange(run, listOf(frames[0], frames[8]), scale)[0]
            val fails =
                full >= 2 &&
                    half > 0.6 * full &&
                    quarter > 0.6 * half &&
                    eighth > 0.6 * quarter &&
                    eighth >= 2
            return Halving(full, half, quarter, eighth, fails)
        }
        val coarse = measure(4)
        return if (coarse.fails) measure(16) else coarse
    }

    private fun halvingText(h: Halving, of: String) =
        "halving ${f2(h.full)} tone·px² step stays ${f2(h.half)}, ${f2(h.quarter)} and " +
            "${f2(h.eighth)} at a half, a quarter and an eighth of the $of"

    /**
     * The halving test on one step; records a failure under [label] and returns whether it failed.
     */
    private fun halvingFails(
        run: TurnRun,
        at: (Double) -> TurnValues,
        x: Double,
        dx: Double,
        label: String,
        of: String,
    ): Boolean {
        val h = halving(run, at, x, dx)
        if (h.fails)
            failures.add(Finding("${run.name} ($label) at ${f3(x)}: ${halvingText(h, of)}"))
        return h.fails
    }

    private fun halvings() {
        val sorted = measures.sortedWith { a, b ->
            (b.value - a.value).let { if (it < 0) -1 else if (it > 0) 1 else 0 }
        }
        var checked = 0
        var fails = 0
        val seen = HashSet<String>()
        for (entry in sorted.take(if (o.quick) 40 else 400).takeWhile { it.value > 1e-6 }) {
            if (!seen.add("${entry.run.index}|${jsFixed(entry.x, 4)}")) continue
            checked++
            val group = o.t.groups[entry.run.group].name
            if (halvingFails(entry.run, entry.at, entry.x, entry.dx, group, "step")) fails++
        }
        val clips = clipSteps()
        for (entry in clips.take(if (o.quick) 60 else 400)) {
            checked++
            if (halvingFails(entry.run, entry.at, entry.x, entry.dx, entry.family, "frame")) fails++
        }
        o.result["continuity"].addAll(failures)
        o.result.worst = worst
        o.result.halvingChecked = checked
        o.result.halvingFails = fails
        o.result.stats["measures"] = (measures.size + clips.size).toDouble()
    }

    private class ClipStep(
        val family: String,
        val run: TurnRun,
        val x: Double,
        val dx: Double,
        val value: Double,
        val at: (Double) -> TurnValues,
    )

    /** The largest area steps of each clip, frame to frame. */
    private fun clipSteps(): List<ClipStep> {
        val out = ArrayList<ClipStep>()
        fun areaRoles(run: TurnRun) =
            run.roles.indices.filter { r ->
                val role = run.roles[r]
                role.kind == RoleKind.Face ||
                    role.kind == RoleKind.Shade ||
                    role.kind == RoleKind.Top ||
                    (run is TubeRun && role.name == "body")
            }
        for (family in o.families) {
            if (family.periodic) continue
            val xs = samplesOf(family, o.frameOf(family))
            o.say("continuity ${family.name}: ${xs.size} frames over ${o.runs.size} parts")
            val frames = o.scene.frames()
            var previous: List<List<Double>>? = null
            for (x in xs) {
                val cams = o.scene.cams(family.at(x))
                val current =
                    o.runs.map { run ->
                        run.emit(cams.of(run.group), frames[run.index])
                        areaRoles(run).map { areaOfPath(frames[run.index].d[it]) }
                    }
                previous?.let { prev ->
                    o.runs.forEachIndexed { index, run ->
                        var most = 0.0
                        current[index].forEachIndexed { k, v ->
                            val d = abs(v - prev[index][k])
                            if (d > most) most = d
                        }
                        if (most >= 0.5)
                            out.add(
                                ClipStep(
                                    family.name,
                                    run,
                                    x - o.frameOf(family),
                                    o.frameOf(family),
                                    most,
                                    family.at,
                                )
                            )
                    }
                }
                previous = current
            }
        }
        return out.sortedWith { a, b ->
            (b.value - a.value).let { if (it < 0) -1 else if (it > 0) 1 else 0 }
        }
    }
}
