// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

/*
 * Clips for the line-end check: what a group's `clip` lets show of a polyline. The renderer clips
 * with Skia; the check needs the same answer as geometry, so a line clipped away isn't checked
 * beyond the clip, and an outline or fill clipped away isn't something to land on or hide under.
 */

/** A run that shows inside the clips, and which of its ends the clip cut. */
internal class ClippedRun(val run: IsoPath.Run, val cut: Free)

/** [this] moved by a screen [offset]. */
internal fun IsoPath.moved(offset: Flat): IsoPath =
    if (offset.x == 0.0 && offset.y == 0.0) this
    else IsoPath(runs.map { run -> IsoPath.Run(run.points.map { it + offset }, run.closed) })

/** Whether [p] lies inside every one of [clips], each filled with SVG's nonzero rule. */
internal fun insideClips(p: Flat, clips: List<IsoPath>): Boolean = clips.all { clip ->
    winding(clip.runs.map { it.points }, p) != 0
}

/** Nonzero winding of [p] in [runs], each closed: SVG's default fill rule. */
internal fun winding(runs: List<List<Flat>>, p: Flat): Int {
    var winding = 0
    for (run in runs) {
        for (i in run.indices) {
            val a = run[i]
            val b = run[(i + 1) % run.size]
            val side = (b.x - a.x) * (p.y - a.y) - (p.x - a.x) * (b.y - a.y)
            if (a.y <= p.y) {
                if (b.y > p.y && side > 0) winding++
            } else if (b.y <= p.y && side < 0) {
                winding--
            }
        }
    }
    return winding
}

/** The parts of [runs] that show inside all [clips]. With no clips, every run, uncut. */
internal fun clipRuns(runs: List<IsoPath.Run>, clips: List<IsoPath>): List<ClippedRun> =
    if (clips.isEmpty()) runs.map { ClippedRun(it, Free.None) }
    else runs.flatMap { clipRun(it, clips) }

private fun clipRun(run: IsoPath.Run, clips: List<IsoPath>): List<ClippedRun> {
    val points = if (run.closed && run.points.size > 1) run.points + run.points[0] else run.points
    if (points.size < 2) {
        val shows = points.isNotEmpty() && insideClips(points[0], clips)
        return if (shows) listOf(ClippedRun(run, Free.None)) else emptyList()
    }
    // Split every segment where it crosses a clip's edge, then keep the stretches inside.
    val refined = arrayListOf(points[0])
    for (i in 0 until points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        for (t in crossings(a, b, clips).sorted()) refined.add(a + (b - a) * t)
        refined.add(b)
    }
    val inside =
        (0 until refined.size - 1).map { insideClips((refined[it] + refined[it + 1]) * 0.5, clips) }
    if (inside.all { it }) return listOf(ClippedRun(run, Free.None))
    val stretches = stretchesOf(inside)
    // A closed run that shows across its own start is one stretch, not two.
    val last = refined.size - 1
    if (
        run.closed &&
            stretches.size > 1 &&
            stretches.first().first == 0 &&
            stretches.last().last == last
    ) {
        val wrap =
            refined.subList(stretches.last().first, last + 1) +
                refined.subList(1, stretches.first().last + 1)
        val rest = stretches.subList(1, stretches.size - 1).map { pieceOf(refined, it, last) }
        return listOf(ClippedRun(IsoPath.Run(wrap, false), Free.Both)) + rest
    }
    return stretches.map { pieceOf(refined, it, last) }
}

/** The runs of segments inside, as ranges of the points they span. */
private fun stretchesOf(inside: List<Boolean>): List<IntRange> {
    val stretches = ArrayList<IntRange>()
    var start = -1
    for (i in inside.indices) {
        if (inside[i] && start < 0) start = i
        if (!inside[i] && start >= 0) {
            stretches.add(start..i)
            start = -1
        }
    }
    if (start >= 0) stretches.add(start..inside.size)
    return stretches
}

private fun pieceOf(refined: List<Flat>, stretch: IntRange, last: Int): ClippedRun {
    val startCut = stretch.first > 0
    val endCut = stretch.last < last
    val cut =
        when {
            startCut && endCut -> Free.Both
            startCut -> Free.Start
            endCut -> Free.End
            else -> Free.None
        }
    return ClippedRun(
        IsoPath.Run(refined.subList(stretch.first, stretch.last + 1).toList(), false),
        cut,
    )
}

/** Where segment [a] to [b] crosses an edge of any of [clips], as fractions strictly inside it. */
private fun crossings(a: Flat, b: Flat, clips: List<IsoPath>): List<Double> {
    val out = ArrayList<Double>()
    val d = b - a
    for (clip in clips) {
        for (run in clip.runs) {
            val ring = run.points
            for (i in ring.indices) {
                val c = ring[i]
                val e = ring[(i + 1) % ring.size] - c
                val denominator = d.x * e.y - d.y * e.x
                if (denominator == 0.0) continue
                val f = c - a
                val t = (f.x * e.y - f.y * e.x) / denominator
                val u = (f.x * d.y - f.y * d.x) / denominator
                if (t > 1e-9 && t < 1 - 1e-9 && u >= 0 && u <= 1) out.add(t)
            }
        }
    }
    return out
}
