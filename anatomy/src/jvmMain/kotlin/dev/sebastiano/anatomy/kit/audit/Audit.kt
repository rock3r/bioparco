// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.audit

import dev.sebastiano.anatomy.kit.Projection

/*
 * The whole audit on a recorder: every check on the recorded shapes, at rest, apart and in every
 * replayed frame of the explode, as one result for `report` and `failures`. The original's
 * `auditOrExit` reads `--audit` from the command line; here `auditReport` returns the text and the
 * count, and `capture --audit` prints it and exits non-zero (see `AuditedFigure`).
 */

/** Every check's findings. */
class AuditResult(
    val clearance: List<Clearance> = emptyList(),
    val depth: DepthReport = DepthReport(emptyList()),
    val terminals: List<Terminal>? = null,
    val folds: List<ScreenFold>? = null,
    val supports: Support? = null,
    val loose: List<LooseEnd>? = null,
    val apart: List<Clearance>? = null,
    val flight: List<Clearance>? = null,
    val crowding: List<Crowding>? = null,
    val self: List<SelfOverlap>? = null,
    val wobble: List<Wobble>? = null,
    val seats: List<OffSeat>? = null,
    val cover: List<StandIn>? = null,
)

/**
 * What the audit excuses and how it poses the figure: [allow] depth pairs, [crowd] crowding pairs
 * and [cover] stand-in items you have looked at; the [ground] (the recorder's by default); the
 * clearance [frames] (every replayed and prefix frame by default); and whether the figure comes
 * [apart] (when it has a stagger order, by default).
 */
class AuditOptions(
    val allow: List<Pair<String, String>> = emptyList(),
    val crowd: List<Pair<String, String>> = emptyList(),
    val cover: List<String> = emptyList(),
    val ground: ((AuditShape) -> Boolean)? = null,
    val frames: List<ExplodeState>? = null,
    val apart: Boolean? = null,
)

/** A figure that records itself for the audit. `capture <figure> --audit` runs it. */
interface AuditedFigure {
    /** Builds the figure, at the projection it draws with, into a fresh recorder. */
    fun record(): Recorder

    /** What the audit excuses for this figure. */
    val auditOptions: AuditOptions
        get() = AuditOptions()
}

private fun worstOf(lists: List<List<Clearance>>): List<Clearance> {
    val all = LinkedHashMap<Pair<String?, String?>, Clearance>()
    for (c in lists.flatten()) {
        val id = c.tube to c.other
        val prior = all[id]
        if (prior == null || prior.clearance > c.clearance) all[id] = c
    }
    return all.values.sortedBy { it.clearance }
}

/** Runs every check on [recorder], settling it first if it hasn't been. */
fun audit(
    recorder: Recorder,
    projection: Projection = recorder.projection,
    options: AuditOptions = AuditOptions(),
): AuditResult {
    val r = recorder
    val settled = r.settled ?: r.settle(projection = projection)
    val items = settled.items
    val apart = options.apart ?: r.order.isNotEmpty()
    val frames = options.frames ?: explodeStates(r.order, r.spring).frames
    val mounts = mountsOf(r.tubes, r.solids)
    val links = linksOf(r.tubes, mounts)
    val one = Uniform(1.0)
    fun through(state: ExplodeState) =
        clearances(r.tubes, r.solids, state = state, mounts = mounts, only = "through") +
            solidClearances(r.solids, state)
    val rods = slenderOf(r.solids)
    return AuditResult(
        clearance = clearances(r.tubes, r.solids, mounts = mounts),
        apart = if (apart) through(one) else emptyList(),
        flight = worstOf(frames.map { through(it) }),
        crowding =
            (if (apart) listOf(Uniform(0.0), one) else listOf(Uniform(0.0))).flatMap { state ->
                crowding(
                    r.routes,
                    projection,
                    links = links,
                    items = items,
                    allow = options.crowd,
                    state = state,
                    rods = rods,
                )
            },
        cover = coverage(items, projection, allow = options.cover),
        terminals = terminals(r.tubes, r.solids, mounts = mounts),
        loose = looseEnds(r.tubes, r.solids, one),
        folds = folds(r.routes, projection, items = items),
        self = selfOverlaps(items, projection),
        wobble = wobble(r.solids),
        seats = seats(r.solids),
        supports = supports(r.solids, r.tubes, options.ground ?: r.ground),
        depth =
            depthOrder(items, projection, settled.overlaps, settled.solved, allow = options.allow),
    )
}

/** An audit's [result], its printed [text] (report, summary and verdict), and its [failures]. */
class AuditRun(val result: AuditResult, val text: String, val failures: Int)

/**
 * Runs [audit] and formats it as the original's `auditOrExit` prints it: the report, a summary line
 * with the counts and the time taken, and `audit passed` or `audit failed: n problems`.
 */
fun auditReport(
    recorder: Recorder,
    projection: Projection = recorder.projection,
    options: AuditOptions = AuditOptions(),
): AuditRun {
    val started = System.nanoTime()
    val result = audit(recorder, projection, options)
    val settled = recorder.settled!!
    val seconds = (System.nanoTime() - started) / 1e9
    val count = failures(result)
    val text = buildString {
        appendLine(report(result))
        appendLine(
            "audit · ${recorder.tubes.size} tubes · ${recorder.solids.size} solids · ${recorder.items.size} items · " +
                "${settled.states.size} depth states · ${settled.overlaps.pairs.size} overlaps · " +
                "${settled.solved.rules} order rules · ${jsFixed(seconds, 1)} s"
        )
        appendLine(
            if (count > 0) "audit failed: $count problem${if (count == 1) "" else "s"}"
            else "audit passed"
        )
    }
    return AuditRun(result, text, count)
}
