// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 * The original's `scripts/line-ends.mjs`: every texture line must end on something. It walks the
 * marks a frame really painted (see [MarkRecorder]) instead of the SVG in a browser, so a figure is
 * checked as it moves, per-frame parts included. All lengths are in viewBox units, which is what
 * the original's tolerances are in.
 *
 * Every open run of every checked line (seams, grooves, ribs, rule lines, ticks, needles, wires)
 * must end within [tolerance] of another stroke, a dot or an outline, or under a fill painted
 * after it. A short straight mark (up to 7 units: a tick) needs one end rooted within 2 units. A
 * solid's outline, crease and bevel are the kit's own and aren't checked, but lines land on them.
 * Lines fainter than 0.3 aren't checked, and nothing fainter than 0.1 is something to land on.
 * `free` ends, and the ramps of a `fadeLine`, are meant to stop and aren't checked. A tube is
 * treated as the original treats `tubeSvg`'s elements: its body and stripes are fills that hide
 * ends painted before them, and its edges (`tb-edge`) and rings are strokes to land on, but neither
 * is checked. Under a group's `clip` only what shows inside it counts: a line is checked where the
 * clip lets it show, an end the clip cuts is where it goes out of sight and isn't checked, and
 * outlines, dots and fills clipped away are nothing to land on or hide under.
 */

/**
 * One mark as painted: the art, the screen offset it was painted at, and the clips it was painted
 * inside, already placed on screen.
 */
class Drawn(val mark: Mark, val offset: Flat, val clips: List<IsoPath> = emptyList())

/** A [MarkRecorder] that keeps what the latest frame painted. */
class Drawing : MarkRecorder {
    private val list = ArrayList<Drawn>()

    /** What the latest frame painted, in painting order. */
    val marks: List<Drawn>
        get() = list.toList()

    /** Device px per viewBox unit in the latest frame. */
    var scale: Double = 1.0
        private set

    /** Where the viewBox origin was on the device in the latest frame. */
    var origin: Flat = Flat(0.0, 0.0)
        private set

    override fun begin(scale: Double, origin: Flat) {
        list.clear()
        this.scale = scale
        this.origin = origin
    }

    override fun record(mark: Mark, offset: Flat, clips: List<IsoPath>) {
        list.add(Drawn(mark, offset, clips))
    }
}

/** Which end of a line. */
enum class End {
    Start,
    End,
}

/** A line end that stops on nothing. */
class LooseEnd(
    /** Where, in screen (viewBox) units. */
    val at: Flat,
    val end: End,
    /** How far it is from the nearest stroke, dot or outline; infinite when nothing is near. */
    val gap: Double,
    /** A short straight mark (a tick), neither of whose ends is rooted. */
    val mark: Boolean,
    val length: Double,
    val alpha: Double,
    /** What the line is: its kind and tone. */
    val what: String,
    /** The start of its path as the original's SVG `d`, to find it in the figure. */
    val d: String,
)

/** What [lineEnds] found: [ends] checked on [lines] lines, and the [loose] ones. */
class LineEndReport(val ends: Int, val lines: Int, val loose: List<LooseEnd>)

/**
 * Checks that every texture line in [drawn] ends on something, within [tolerance] viewBox units.
 * [palette] decides whether halos are dense enough to hide what they cover (only in dark).
 */
fun lineEnds(
    drawn: List<Drawn>,
    palette: Palette = Palette.Light,
    tolerance: Double = 1.0,
): LineEndReport {
    val board = Board(drawn, palette)
    val slack = tolerance
    val reach = max(slack, ROOTED)
    val loose = ArrayList<LooseEnd>()
    var ends = 0
    for (line in board.lines) {
        line.runs.forEachIndexed { index, run ->
            val found = checkRun(board, line, index, run, slack, reach)
            ends += found.first
            loose += found.second
        }
    }
    return LineEndReport(ends, board.lines.size, loose.sortedByDescending { it.gap })
}

/** The ends of one run: how many were checked, and the loose ones. */
private fun checkRun(
    board: Board,
    line: CheckedLine,
    index: Int,
    run: IsoPath.Run,
    slack: Double,
    reach: Double,
): Pair<Int, List<LooseEnd>> {
    val points = run.points
    if (run.closed || points.size < 2) return 0 to emptyList()
    val first = points.first()
    val last = points.last()
    val length = lengthOf(points)
    if (distance(first, last) < 0.05 || length < 0.2) return 0 to emptyList()
    var checked = 0
    val result =
        End.entries.associateWith { end ->
            if (line.free.excuses(end) || line.cuts[index].excuses(end)) {
                0.0
            } else {
                checked++
                val p = if (end == End.Start) first else last
                if (board.hidden(line.order, p)) 0.0
                else board.clearance(line, index, end, p, reach)
            }
        }
    val straight = points.size == 2 || points.all { segmentDistance(it, first, last) < 0.15 }
    val isMark = straight && length <= MARK
    val bad =
        if (isMark) {
            if (result.values.min() <= ROOTED) emptyList()
            else End.entries.filter { result.getValue(it) > 0 }
        } else {
            End.entries.filter { result.getValue(it) > slack }
        }
    return checked to
        bad.map { end ->
            LooseEnd(
                at = if (end == End.Start) first else last,
                end = end,
                gap = result.getValue(end),
                mark = isMark,
                length = length,
                alpha = line.alpha,
                what = line.what,
                d = line.d,
            )
        }
}

private const val CELL = 6.0
private const val ROOTED = 2.0
private const val MARK = 7.0
private const val LEAST = 0.3
private const val VISIBLE = 0.1
private const val COVER = 0.5
private const val HALO_DROP = 3.0

/** A line that is checked: its runs where they were painted, and what excuses its ends. */
private class CheckedLine(
    val element: Int,
    val order: Int,
    val runs: List<IsoPath.Run>,
    /** Which ends of each run a clip cut: they go out of sight there and aren't checked. */
    val cuts: List<Free>,
    val free: Free,
    val alpha: Double,
    val what: String,
    val d: String,
)

/** Something to land on: a stroked segment, or a dot or terminal dot. */
private sealed class Landing

private class Segment(val element: Int, val run: Int, val index: Int, val a: Flat, val b: Flat) :
    Landing()

private class Round(val center: Flat, val r: Double) : Landing()

/**
 * A fill that hides a line end painted before it: a solid's silhouette, a dense dot. It hides only
 * inside the [clips] it was painted in.
 */
private class Cover(
    val order: Int,
    val runs: List<List<Flat>>?,
    val center: Flat,
    val r: Double,
    val clips: List<IsoPath>,
)

/** Everything a frame painted, sorted into lines to check, things to land on and covers. */
private class Board(drawn: List<Drawn>, private val palette: Palette) {
    val lines = ArrayList<CheckedLine>()
    private val covers = ArrayList<Cover>()
    private val grid = HashMap<Long, MutableList<Landing>>()
    private var elements = 0

    // The clips of the mark being added.
    private var clips: List<IsoPath> = emptyList()

    init {
        drawn.forEachIndexed { order, item ->
            val wire = (drawn.getOrNull(order + 1)?.mark as? WireMark)?.line === item.mark
            clips = item.clips
            add(order, item.mark, item.offset, wire)
        }
    }

    private fun cover(order: Int, fill: IsoPath, offset: Flat) {
        covers += Cover(order, fill.at(offset).map { it.points }, Flat(0.0, 0.0), 0.0, clips)
    }

    private fun add(order: Int, mark: Mark, offset: Flat, wire: Boolean) {
        when (mark) {
            is SolidMark -> addSolid(order, mark, offset)
            is LineMark ->
                addLine(
                    order,
                    mark.shape.iso.at(offset),
                    mark.free,
                    mark.alpha,
                    describe(if (wire) "wire" else "line", mark.tone, mark.stroke),
                    mark.shape.iso.svg(),
                )
            is DotsMark -> addDots(order, mark, offset)
            is WireMark ->
                for (end in mark.ends) addRound(order, end + offset, mark.size, cover = true)
            is FadeLineMark ->
                for ((points, free) in fadePieces(mark)) {
                    val path = pathOf(points)
                    addLine(
                        order,
                        path.at(offset),
                        free,
                        1.0,
                        describe("fade", mark.tone),
                        path.svg(),
                    )
                }
            is HaloMark ->
                if (palette.haloOpacity >= COVER) {
                    cover(order, mark.shape.iso, offset + Flat(0.0, HALO_DROP))
                }
            is TubeMark -> addTube(order, mark, offset)
            is VoidMark -> cover(order, mark.shape.iso, offset)
            is TextMark,
            is GroupMark -> Unit
        }
    }

    private fun addSolid(order: Int, mark: SolidMark, offset: Flat) {
        val element = elements++
        val paths = mark.paths
        cover(order, paths.fill, offset)
        addSegments(element, paths.outline.at(offset))
        if (mark.crease != null) {
            addSegments(element, paths.crease.at(offset))
            paths.bevel?.let { addSegments(element, it.at(offset)) }
        }
        if (paths.seamless && !mark.flat) {
            for (shade in paths.shades) addSegments(element, shade.at(offset))
            addSegments(element, paths.top.at(offset))
        }
    }

    /**
     * The original's line-end check sees `tubeSvg`'s body, shine and shade as opaque fills, and its
     * rings (not `iso-line`) and edges (`tb-edge`, skipped) as strokes it never checks.
     */
    private fun addTube(order: Int, mark: TubeMark, offset: Flat) {
        val element = elements++
        cover(order, mark.piece.body, offset)
        if (mark.piece.wide) {
            cover(order, mark.piece.shine, offset)
            cover(order, mark.piece.shade, offset)
        }
        for ((alpha, ring) in mark.rings) if (alpha >= VISIBLE)
            addSegments(element, ring.iso.at(offset))
        addSegments(element, mark.edges.iso.at(offset))
    }

    private fun addLine(
        order: Int,
        runs: List<IsoPath.Run>,
        free: Free,
        alpha: Double,
        what: String,
        d: String,
    ) {
        if (alpha < VISIBLE) return
        val element = elements++
        val shown = clipRuns(runs, clips)
        if (shown.isEmpty()) return
        val visible = shown.map { it.run }
        addSegmentsShown(element, visible)
        if (alpha >= LEAST) {
            val cuts = shown.map { it.cut }
            lines += CheckedLine(element, order, visible, cuts, free, alpha, what, d.take(36))
        }
    }

    private fun addDots(order: Int, mark: DotsMark, offset: Flat) {
        val alpha = palette.dotAlpha(mark.tone).toDouble()
        if (alpha < VISIBLE) return
        mark.points.forEachIndexed { index, point ->
            val each = mark.alphas?.get(index)?.coerceIn(0.0, 1.0) ?: 1.0
            if (each > 0.05) addRound(order, point + offset, mark.size, alpha * each >= COVER)
        }
    }

    private fun addRound(order: Int, center: Flat, r: Double, cover: Boolean) {
        if (r <= 0 || !insideClips(center, clips)) return
        val round = Round(center, r)
        cells(center.x - r, center.y - r, center.x + r, center.y + r) { add(it, round) }
        if (cover) covers += Cover(order, null, center, r, clips)
    }

    /** Strokes to land on, where the current clips let them show. */
    private fun addSegments(element: Int, runs: List<IsoPath.Run>) {
        addSegmentsShown(element, clipRuns(runs, clips).map { it.run })
    }

    private fun addSegmentsShown(element: Int, runs: List<IsoPath.Run>) {
        runs.forEachIndexed { index, run ->
            val points = if (run.closed) run.points + run.points.first() else run.points
            for (i in 0 until points.size - 1) {
                val a = points[i]
                val b = points[i + 1]
                val segment = Segment(element, index, i, a, b)
                cells(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y)) {
                    add(it, segment)
                }
            }
        }
    }

    private fun add(cell: Long, landing: Landing) {
        grid.getOrPut(cell) { ArrayList() }.add(landing)
    }

    private inline fun cells(x0: Double, y0: Double, x1: Double, y1: Double, each: (Long) -> Unit) {
        for (gx in floor(x0 / CELL).toInt()..floor(x1 / CELL).toInt()) {
            for (gy in floor(y0 / CELL).toInt()..floor(y1 / CELL).toInt()) {
                each((gx.toLong() shl 32) or (gy.toLong() and 0xFFFFFFFFL))
            }
        }
    }

    /** Whether [p] is under a fill painted after the mark painted at [order]. */
    fun hidden(order: Int, p: Flat): Boolean = covers.any { cover ->
        cover.order > order &&
            (if (cover.runs == null) distance(p, cover.center) <= cover.r
            else winding(cover.runs, p) != 0) &&
            insideClips(p, cover.clips)
    }

    /** How far [p], the [end] of [line]'s run [run], is from anything to land on. */
    fun clearance(line: CheckedLine, run: Int, end: End, p: Flat, reach: Double): Double {
        val own = line.runs[run].points
        val along = DoubleArray(own.size)
        for (i in 1 until own.size) along[i] = along[i - 1] + distance(own[i], own[i - 1])
        val total = along.last()
        val near = LinkedHashSet<Landing>()
        cells(p.x - reach * 4, p.y - reach * 4, p.x + reach * 4, p.y + reach * 4) {
            grid[it]?.let(near::addAll)
        }
        var best = Double.POSITIVE_INFINITY
        for (landing in near) {
            when (landing) {
                is Round -> {
                    val off = distance(p, landing.center)
                    best =
                        min(
                            best,
                            if (off <= landing.r) 0.0 else abs(off - landing.r),
                        )
                }
                is Segment -> {
                    // The stretch of its own run the end sits on is not something to land on.
                    if (landing.element == line.element && landing.run == run) {
                        val gap =
                            if (end == End.Start) along[landing.index]
                            else total - along[landing.index + 1]
                        if (gap < 2 * reach) continue
                    }
                    best = min(best, segmentDistance(p, landing.a, landing.b))
                }
            }
        }
        return best
    }
}

/** Whether a line's [Free] lets its [end] stop on a bare face. */
private fun Free.excuses(end: End): Boolean =
    this == Free.Both ||
        (this == Free.Start && end == End.Start) ||
        (this == Free.End && end == End.End)

/** A kit path where it was painted. */
private fun IsoPath.at(offset: Flat): List<IsoPath.Run> = moved(offset).runs

private fun describe(kind: String, tone: Tone, stroke: Stroke = Stroke.Solid): String =
    "$kind ${tone.name.lowercase()}" +
        if (stroke == Stroke.Solid) "" else " ${stroke.name.lowercase()}"

/**
 * The pieces `fadeLine` paints, as the original's `fadeLineSvg` writes them: a ramp in whose faded
 * end is free, the line at full strength, and a ramp out.
 */
private fun fadePieces(mark: FadeLineMark): List<Pair<List<Flat>, Free>> {
    val points = mark.points
    val lengths = DoubleArray(points.size)
    for (i in 1 until points.size) lengths[i] = lengths[i - 1] + distance(points[i], points[i - 1])
    val total = lengths.last()
    if (total < 1e-6) return emptyList()
    val share = min(1.0, total / (mark.fadeStart + mark.fadeEnd).coerceAtLeast(1e-12))
    val start = mark.fadeStart * share
    val end = mark.fadeEnd * share
    fun at(length: Double): Flat {
        var i = 1
        while (i < points.size - 1 && lengths[i] < length) i++
        val span = (lengths[i] - lengths[i - 1]).takeIf { it != 0.0 } ?: 1.0
        val t = ((length - lengths[i - 1]) / span).coerceIn(0.0, 1.0)
        return points[i - 1] + (points[i] - points[i - 1]) * t
    }
    fun piece(from: Double, to: Double): List<Flat> =
        listOf(at(from)) +
            points.filterIndexed { i, _ -> lengths[i] > from + 1e-6 && lengths[i] < to - 1e-6 } +
            at(to)
    return buildList {
        if (start > 0.05) add(piece(0.0, start) to Free.Start)
        if (total - end - start > 0.05) add(piece(start, total - end) to Free.None)
        if (end > 0.05) add(piece(total - end, total) to Free.End)
    }
}

private fun distance(a: Flat, b: Flat): Double = hypot(a.x - b.x, a.y - b.y)

private fun lengthOf(points: List<Flat>): Double =
    (1 until points.size).sumOf { distance(points[it], points[it - 1]) }

private fun segmentDistance(p: Flat, a: Flat, b: Flat): Double {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val span = dx * dx + dy * dy
    val t =
        if (span > 0) ((((p.x - a.x) * dx + (p.y - a.y) * dy) / span).coerceIn(0.0, 1.0)) else 0.0
    return hypot(p.x - a.x - dx * t, p.y - a.y - dy * t)
}
