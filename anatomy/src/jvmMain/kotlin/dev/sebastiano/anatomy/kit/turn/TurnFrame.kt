// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.jsRound
import kotlin.math.min

/*
 * One part's frame, `t3Frame`: what the runtime computed for it this frame, role by role, plus its
 * screen box and hull for ordering. The original writes these into SVG attributes; here the
 * renderer paints them.
 */

/** An affine screen transform: `matrix(a b c d e f)`, or `translate(e f)` when [translate]. */
class Affine(
    val a: Double,
    val b: Double,
    val c: Double,
    val d: Double,
    val e: Double,
    val f: Double,
    val translate: Boolean = false,
) {
    /** Where a local point lands. */
    fun x(x: Double, y: Double): Double = a * x + c * y + e

    fun y(x: Double, y: Double): Double = b * x + d * y + f

    /** The transform as the original writes it. */
    fun svg(): String =
        if (translate) "translate(${turnNumber(e)} ${turnNumber(f)})"
        else
            "matrix(${turnFix(a, 1e5)} ${turnFix(b, 1e5)} ${turnFix(c, 1e5)} ${turnFix(d, 1e5)} " +
                "${turnFix(e, 100.0)} ${turnFix(f, 100.0)})"
}

/** One quad or cap ring of a tube's footprint: [n] points in [buf]. */
class QuadBuf(var buf: DoubleArray, var n: Int)

/** The faded lines of one prefix (`v` bevels, `r` ribs and rings) by key, for the audit. */
class FadeSet(val keys: IntArray, val alphas: DoubleArray)

/** Faded lines as an emit collects them: the path, its opacity, and a key stable across frames. */
internal class FadedLines {
    val d = ArrayList<IsoPath>()
    val alpha = ArrayList<Double>()
    val key = ArrayList<Int>()

    fun clear() {
        d.clear()
        alpha.clear()
        key.clear()
    }

    fun add(path: IsoPath, a: Double, k: Int) {
        d.add(path)
        alpha.add(a)
        key.add(k)
    }
}

/** A part's frame: per role its path [d], face level [q], stroke width [w], transform [t]… */
class TurnFrame(count: Int) {
    val d: Array<IsoPath> = Array(count) { IsoPath.Empty }
    val q = IntArray(count)
    val w = DoubleArray(count) { -1.0 }
    val t: Array<Affine?> = arrayOfNulls(count)
    val o = DoubleArray(count) { 1.0 }
    val cx = DoubleArray(count)
    val cy = DoubleArray(count)

    /** The screen box `x0, y0, x1, y1`. */
    val box = DoubleArray(4)

    /** The outline's hull, `x, y` pairs, [hullN] of them. */
    var hull = DoubleArray(64)
    var hullN = 0

    /**
     * A tube's quads and cap rings: the first `n` points (`x, y` pairs) of each buffer, the first
     * [quadN] of them. The buffers are reused from frame to frame, as the original reuses them.
     */
    var quadList: MutableList<QuadBuf>? = null
    var quadN = 0

    /** How many opacity levels the faded lines needed this frame. */
    var levels = 0

    /** The faded lines by prefix, before they were merged into slots. */
    val fade = HashMap<String, FadeSet>()

    /** A fixed part's translation this frame. */
    var shiftX = 0.0
    var shiftY = 0.0

    internal fun setHull(buf: DoubleArray, count: Int) {
        if (hull.size < 2 * count) hull = DoubleArray(maxOf(2 * count, hull.size * 2))
        for (i in 0 until 2 * count) hull[i] = buf[i]
        hullN = count
    }
}

/**
 * The original's `t3Faded`: groups faded lines into at most the run's slots by opacity level (1/32
 * steps), merging the two closest levels until they fit, and writes each slot's path and opacity.
 * Returns how many levels the lines needed.
 */
internal fun t3Faded(f: TurnFrame, run: TurnRun, prefix: String, lines: FadedLines): Int {
    val detail = TurnState.detail
    var count = lines.d.size
    if (detail < 1) for (i in 0 until count) lines.alpha[i] = lines.alpha[i] * detail
    if (detail < 0.01) count = 0
    f.fade[prefix] =
        FadeSet(IntArray(count) { lines.key[it] }, DoubleArray(count) { lines.alpha[it] })
    val slots = (if (prefix == "v") run.slots?.bevel else run.slots?.ribs) ?: 6
    val present = levelsOf(lines, count)
    mergeLevels(present, slots)
    for (j in 0 until slots) {
        val role = run.role["$prefix$j"] ?: continue
        if (j < present.size) {
            f.d[role] = present[j].second
            f.o[role] = present[j].first / TURN_LEVELS
        } else {
            f.d[role] = IsoPath.Empty
            f.o[role] = 0.0
        }
    }
    return present.size
}

/** The first [count] faded lines grouped by opacity level, most opaque first. */
private fun levelsOf(lines: FadedLines, count: Int): MutableList<Pair<Double, IsoPath>> {
    val levels = arrayOfNulls<IsoPath>(TURN_LEVELS + 1)
    for (i in 0 until count) {
        val level = min(TURN_LEVELS, jsRound(lines.alpha[i] * TURN_LEVELS).toInt())
        if (level > 0) levels[level] = (levels[level] ?: IsoPath.Empty) + lines.d[i]
    }
    val present = ArrayList<Pair<Double, IsoPath>>()
    for (l in TURN_LEVELS downTo 1) {
        val path = levels[l]
        if (path != null && !path.isEmpty) present.add(l.toDouble() to path)
    }
    return present
}

/** Merges the two closest levels of [present] until they fit in [slots]. */
private fun mergeLevels(present: MutableList<Pair<Double, IsoPath>>, slots: Int) {
    while (present.size > slots) {
        var best = 1
        var gap = Double.POSITIVE_INFINITY
        for (i in 1 until present.size) {
            val g = present[i - 1].first - present[i].first
            if (g < gap) {
                gap = g
                best = i
            }
        }
        val (a, da) = present[best - 1]
        val (b, db) = present[best]
        present.removeAt(best)
        present[best - 1] = (a + b) / 2 to (da + db)
    }
}
