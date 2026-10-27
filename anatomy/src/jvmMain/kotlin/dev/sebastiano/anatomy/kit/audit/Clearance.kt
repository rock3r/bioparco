// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.audit

import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.len3
import dev.sebastiano.anatomy.kit.pathLength
import dev.sebastiano.anatomy.kit.sub3
import kotlin.math.max
import kotlin.math.min

/*
 * Clearance: every place a tube comes closer than `max(least, ratio · r)` to a solid or another
 * tube. Only an end's own mount, that mount's pipe, and the fittings of a line that shares the
 * mount or lands on this tube are excused, and only near that end.
 */

/** A tube [tube] too close to, or through, [other] (or, with [solid], two solids). */
class Clearance(
    val tube: String?,
    val other: String?,
    /** `through` or `close`. */
    val kind: String,
    val clearance: Double,
    val need: Double,
    val at: Point3,
    var from: Double,
    var to: Double,
    val length: Double,
    val state: String,
    val solid: Boolean = false,
)

/**
 * Samples every tube every [spacing] and lists every place it comes closer than `max(least, ratio ·
 * r)` to a solid or another tube (bundled cables down to [touching]). Near an excused end nothing
 * else may be entered by more than [through]. [state] poses everything; [only] keeps one kind
 * (`"through"`).
 */
fun clearances(
    tubes: List<TubeShape>,
    solids: List<AuditShape>,
    ratio: Double = 0.5,
    least: Double = 0.2,
    spacing: Double = 0.5,
    touching: Double = -0.05,
    through: Double = -0.1,
    state: ExplodeState? = null,
    only: String? = null,
    mounts: Map<TubeShape, List<Mount?>> = mountsOf(tubes, solids),
): List<Clearance> {
    val pass = ClearancePass(tubes, solids, ratio, least, through, state, only, mounts)
    for (tube in tubes) pass.againstSolids(tube, spacing)
    for (a in tubes.indices) pass.againstTubes(a, spacing, touching)
    val seen = HashSet<List<String?>>()
    return pass.problems.values
        .sortedBy { it.clearance }
        .filter { seen.add(listOf(it.tube, it.other).sortedWith(nullsLast())) }
}

private class TipEntry(val run: TubeShape, val mount: Mount)

private class ClearancePass(
    val tubes: List<TubeShape>,
    val solids: List<AuditShape>,
    val ratio: Double,
    val least: Double,
    val through: Double,
    val state: ExplodeState?,
    val only: String?,
    val mounts: Map<TubeShape, List<Mount?>>,
) {
    val problems = LinkedHashMap<Pair<String?, String?>, Clearance>()
    private val poses = HashMap<AuditShape, AuditShape>()
    private val offsets = HashMap<AuditShape, Point3>()
    private val runsBy = HashMap<String?, MutableList<TubeShape>>()
    private val label = state?.let { labelOf(it) } ?: ""

    init {
        for (shape in tubes + solids) {
            poses[shape] = placed(shape, state)
            offsets[shape] = offsetAt(shape, state)
        }
        for (tube in tubes) runsBy.getOrPut(tube.owner) { ArrayList() }.add(tube)
    }

    fun gapOf(r: Double): Double = max(least, ratio * r)

    private fun zone(r: Double): Double = r + gapOf(r) + 1

    private fun poseOf(shape: AuditShape): AuditShape = poses[shape] ?: placed(shape, state)

    private fun offsetOf(shape: AuditShape): Point3 = offsets[shape] ?: offsetAt(shape, state)

    private fun together(a: AuditShape, b: AuditShape): Boolean =
        len3(sub3(offsetOf(a), offsetOf(b))) < 0.05

    private fun tipsOf(owner: String?): List<TipEntry> =
        runsBy[owner].orEmpty().flatMap { run ->
            mounts[run].orEmpty().filterNotNull().map { TipEntry(run, it) }
        }

    private fun seatedOn(shape: AuditShape, owner: String?): Boolean =
        shape.meta.cut.any { it.owner == owner }

    private fun rimReach(run: TubeShape): Double = run.tipRims?.max() ?: 0.0

    private fun tipGap(sample: TubeSample, entry: TipEntry): Double =
        len3(sub3(sample.p, add3(entry.mount.tip, offsetOf(entry.run))))

    private fun related(tube: TubeShape, other: AuditShape, sample: TubeSample): Boolean {
        if (seatedOn(other, tube.owner)) return true
        val theirs = tipsOf(other.owner ?: other.name)
        for (entry in tipsOf(tube.owner)) {
            val near = tipGap(sample, entry) <= zone(sample.r) + rimReach(entry.run)
            if (!near || !together(tube, entry.mount.shape)) continue
            val mount = entry.mount.shape
            val host = entry.mount.host
            if (
                mount === other || (host != null && (host === other || other.owner == host.owner))
            ) {
                return true
            }
            if (theirs.any { it.mount.shape === mount }) return true
        }
        return theirs.any { entry ->
            val mount = entry.mount.shape
            val lands = mount.owner == tube.owner || seatedOn(mount, tube.owner)
            lands &&
                together(entry.run, tube) &&
                tipGap(sample, entry) <= zone(sample.r) + rimReach(entry.run) + sample.r
        }
    }

    private fun nearTip(tube: TubeShape, sample: TubeSample): Boolean {
        val at = (tube.offset ?: 0.0) + sample.along
        val total = tube.total ?: pathLength(tube.points)
        val tips = mounts[tube] ?: listOf(null, null)
        return (tips[0] != null && at <= zone(sample.r)) ||
            (tips[1] != null && total - at <= zone(sample.r))
    }

    private fun clearanceTo(other: AuditShape, sample: TubeSample): Double =
        if (sample.edge >= sample.r) sdf(other, sample.p) - sample.r
        else circleAround(sample.p, sample.t, sample.r).minOf { sdf(other, it) }

    private fun excused(tube: TubeShape, other: AuditShape, sample: TubeSample, clearance: Double) =
        related(tube, other, sample) || (nearTip(tube, sample) && clearance >= through)

    fun againstSolids(tube: TubeShape, spacing: Double) {
        val posed = poseOf(tube) as TubeShape
        val samples = samplesOf(posed, spacing)
        for (shape in solids) {
            val own = shape.owner != null && shape.owner == tube.owner
            val touching =
                matches(tube.meta.touch, shape.name) || matches(shape.meta.touch, tube.name)
            val bounds = poseOf(shape).bounds
            if (own || touching || !boundsMeet(posed.bounds, bounds, 4.0)) continue
            val other = poseOf(shape)
            for (sample in samples) {
                if (pointNear(sample.p, bounds, sample.r + 3))
                    checkSolid(tube, shape, other, sample)
            }
        }
    }

    private fun checkSolid(
        tube: TubeShape,
        shape: AuditShape,
        other: AuditShape,
        sample: TubeSample,
    ) {
        val gap = gapOf(sample.r)
        val clearance = clearanceTo(other, sample)
        if (clearance < gap && !excused(tube, shape, sample, clearance)) {
            note(tube, shape, clearance, gap, sample)
        }
    }

    fun againstTubes(a: Int, spacing: Double, touching: Double) {
        val ta = poseOf(tubes[a]) as TubeShape
        val samples = samplesOf(ta, spacing)
        for (b in tubes.indices) {
            val tb = poseOf(tubes[b]) as TubeShape
            if (a == b || skipPair(tubes[a], tubes[b]) || !boundsMeet(ta.bounds, tb.bounds, 3.0)) {
                continue
            }
            val bundle = tubes[a].meta.bundle
            val bundled = bundle != null && bundle == tubes[b].meta.bundle
            for (sample in samples) {
                if (pointNear(sample.p, tb.bounds, sample.r + 3)) {
                    checkTube(a, b, tb, sample, if (bundled) touching else null)
                }
            }
        }
    }

    /**
     * One sample of tube [a] against tube [b], posed as [tb]; bundled tubes need only [touching].
     */
    private fun checkTube(a: Int, b: Int, tb: TubeShape, sample: TubeSample, touching: Double?) {
        val clearance = clearanceTo(tb, sample)
        val r = centreline(tb, sample.p).r
        val gap = touching ?: gapOf(min(sample.r, r))
        val noted = a > b && problems.containsKey(tubes[b].name to tubes[a].name)
        if (clearance < gap && !excused(tubes[a], tubes[b], sample, clearance) && !noted) {
            note(tubes[a], tubes[b], clearance, gap, sample)
        }
    }

    private fun skipPair(a: TubeShape, b: TubeShape): Boolean =
        (a.owner != null && a.owner == b.owner && a.meta.joined) ||
            matches(a.meta.touch, b.name) ||
            matches(b.meta.touch, a.name)

    private fun note(
        tube: TubeShape,
        other: AuditShape,
        clearance: Double,
        need: Double,
        sample: TubeSample,
    ) {
        val kind = if (clearance < 0) "through" else "close"
        if (only != null && kind != only) return
        val id = tube.name to other.name
        val prior = problems[id]
        if (prior != null && prior.clearance <= clearance) {
            prior.from = min(prior.from, sample.along)
            prior.to = max(prior.to, sample.along)
            return
        }
        problems[id] =
            Clearance(
                tube.name,
                other.name,
                kind,
                clearance,
                need,
                sample.p,
                if (prior != null) min(prior.from, sample.along) else sample.along,
                if (prior != null) max(prior.to, sample.along) else sample.along,
                pathLength(tube.points),
                label,
            )
    }
}
