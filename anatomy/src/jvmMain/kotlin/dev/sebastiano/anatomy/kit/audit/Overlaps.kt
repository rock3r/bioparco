// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.audit

import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Projection
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.dot3
import dev.sebastiano.anatomy.kit.jsRound
import dev.sebastiano.anatomy.kit.mul3
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * Which of two drawn items is in front where they overlap on screen, measured by ray-casting both
 * shapes on a grid over the overlap, in every explode state. Each item is ray-cast once, at home;
 * in any state its raster is that one shifted on screen and in depth by its offset, which is exact
 * for a translation.
 */

/** One side of an overlapping pair: the cells where it is in front, and where it is most so. */
class FrontSide(
    /** Every cell where this side is nearer. */
    val all: Int,
    /** Cells whose four neighbours are flagged too. */
    val inner: Int,
    /** The largest closed, eroded connected piece, in px². */
    val area: Double,
    /** How much nearer it is at most. */
    val depth: Double,
    /** Where it is most in front, on screen. */
    val at: Flat?,
)

/** Items [a] and [b] (indices into the items) overlap in [state]; [front] is each side's front. */
class OverlapPair(val state: ExplodeState, val a: Int, val b: Int, val front: List<FrontSide>)

/** Every overlapping pair in every state, and the settings they were measured with. */
class Overlaps(
    val pairs: List<OverlapPair>,
    val least: Double,
    val states: List<ExplodeState>,
    val step: Double,
    val v: Point3,
)

private sealed interface Guide

private class TubeGuide(val points: List<Flat>, val widths: DoubleArray, val boxes: DoubleArray) :
    Guide

private class BodyGuide(val from: Flat, val to: Flat, val reach: Double) : Guide

private class Raster(
    val x0: Int,
    val y0: Int,
    val x1: Int,
    val y1: Int,
    val boxes: List<ScreenBox>,
    val shapes: List<AuditShape>,
    val guides: List<Guide?>,
    val uid: Int,
) {
    val w = x1 - x0 + 1
    val h = y1 - y0 + 1
    var depth: FloatArray? = null
    var done: BooleanArray? = null
}

private fun rasterOf(
    item: AuditItem,
    cam: CameraBasis,
    step: Double,
    offset: Point3,
    uid: Int,
): Raster {
    val shapes = item.shapes.map { moved(it, offset) }
    val boxes = shapes.map { screenBox(it, cam) }
    val guides = shapes.map { guideOf(it, cam, step) }
    return Raster(
        floor(boxes.minOf { it.x0 } / step).toInt(),
        floor(boxes.minOf { it.y0 } / step).toInt(),
        ceil(boxes.maxOf { it.x1 } / step).toInt(),
        ceil(boxes.maxOf { it.y1 } / step).toInt(),
        boxes,
        shapes,
        guides,
        uid,
    )
}

private fun guideOf(shape: AuditShape, cam: CameraBasis, step: Double): Guide? =
    when (shape) {
        is TubeShape -> {
            val points = shape.points.map { cam.flat(it) }
            val widths = DoubleArray(shape.radii.size) { shape.radii[it] * cam.k }
            val boxes = DoubleArray((points.size - 1) * 4)
            for (i in 0 until points.size - 1) {
                val pad = max(widths[i], widths[i + 1]) + step * 0.5
                boxes[i * 4] = min(points[i].x, points[i + 1].x) - pad
                boxes[i * 4 + 1] = max(points[i].x, points[i + 1].x) + pad
                boxes[i * 4 + 2] = min(points[i].y, points[i + 1].y) - pad
                boxes[i * 4 + 3] = max(points[i].y, points[i + 1].y) + pad
            }
            TubeGuide(points, widths, boxes)
        }
        is BodyShape -> {
            val f = shape.frame
            BodyGuide(
                cam.flat(add3(f.o, mul3(f.a, shape.poly.minOf { it.s }))),
                cam.flat(add3(f.o, mul3(f.a, shape.poly.maxOf { it.s }))),
                shape.poly.maxOf { it.r } * cam.k,
            )
        }
        else -> null
    }

private fun segmentsUnder(guide: TubeGuide, x: Double, y: Double, step: Double): IntArray {
    val boxes = guide.boxes
    val count = guide.points.size - 1
    val found = IntArray(count)
    var n = 0
    for (i in 0 until count) {
        val outside =
            x < boxes[i * 4] || x > boxes[i * 4 + 1] || y < boxes[i * 4 + 2] || y > boxes[i * 4 + 3]
        val reach = max(guide.widths[i], guide.widths[i + 1]) + step * 0.5
        if (!outside && flatDistance(x, y, guide.points[i], guide.points[i + 1]) <= reach) {
            found[n++] = i
        }
    }
    return found.copyOf(n)
}

/** The nearest surface along the camera ray through grid cell ([cx], [cy]), or NaN. */
private fun depthAt(raster: Raster, cam: CameraBasis, step: Double, cx: Int, cy: Int): Double {
    val depth = raster.depth ?: FloatArray(raster.w * raster.h).also { raster.depth = it }
    val done = raster.done ?: BooleanArray(raster.w * raster.h).also { raster.done = it }
    val cell = (cy - raster.y0) * raster.w + (cx - raster.x0)
    // The original keeps depths in a Float32Array: a cached read is rounded, a fresh one isn't.
    if (done[cell]) return depth[cell].toDouble()
    val x = cx * step
    val y = cy * step
    val c = cam.rayStart(x, y)
    var best = Double.NaN
    for (index in raster.shapes.indices) {
        if (!raster.boxes[index].contains(x, y)) continue
        val t = shapeDepth(raster.shapes[index], raster.guides[index], c, cam, x, y, step)
        if (t != null && !(t <= best)) best = t
    }
    depth[cell] = best.toFloat()
    done[cell] = true
    return best
}

private fun shapeDepth(
    shape: AuditShape,
    guide: Guide?,
    c: Point3,
    cam: CameraBasis,
    x: Double,
    y: Double,
    step: Double,
): Double? {
    var segments: IntArray? = null
    if (shape is TubeShape && guide is TubeGuide) {
        segments = segmentsUnder(guide, x, y, step)
        if (segments.isEmpty()) return null
    } else if (
        guide is BodyGuide && flatDistance(x, y, guide.from, guide.to) > guide.reach + step
    ) {
        return null
    }
    return rayDepth(shape, c, cam.v, 0.02, segments)
}

private class Region(val all: Int, val inner: Int, val area: Double)

/** One side's flagged cells closed, eroded once, and its largest connected piece measured. */
private fun regionOf(flags: ByteArray, gw: Int, gh: Int, value: Byte, step: Double): Region {
    val n = gw * gh
    val on = BooleanArray(n)
    var all = 0
    for (cell in 0 until n) {
        if (flags[cell] == value) {
            on[cell] = true
            all++
        }
    }
    if (all == 0) return Region(0, 0, 0.0)
    val inner = innerCells(on, gw, gh)
    val core = coreOf(closed(on, gw, gh), gw, gh)
    return Region(all, inner, largestPiece(core, gw, gh) * step * step)
}

private fun innerCells(on: BooleanArray, gw: Int, gh: Int): Int {
    var inner = 0
    for (y in 1 until gh - 1) {
        for (x in 1 until gw - 1) {
            val cell = y * gw + x
            if (on[cell] && on[cell - 1] && on[cell + 1] && on[cell - gw] && on[cell + gw]) inner++
        }
    }
    return inner
}

/** One dilate and one erode, so an aliased sliver becomes solid. */
private fun closed(on: BooleanArray, gw: Int, gh: Int): BooleanArray {
    val grow = dilated(on, gw, gh)
    val shut = BooleanArray(gw * gh)
    for (y in 0 until gh) {
        for (x in 0 until gw) {
            val cell = y * gw + x
            shut[cell] =
                on[cell] ||
                    (grow[cell] &&
                        (x == 0 || grow[cell - 1]) &&
                        (x == gw - 1 || grow[cell + 1]) &&
                        (y == 0 || grow[cell - gw]) &&
                        (y == gh - 1 || grow[cell + gw]))
        }
    }
    return shut
}

/** One dilation: a cell or any of its four neighbours. */
private fun dilated(on: BooleanArray, gw: Int, gh: Int): BooleanArray {
    val grow = BooleanArray(gw * gh)
    for (y in 0 until gh) {
        for (x in 0 until gw) {
            val cell = y * gw + x
            grow[cell] =
                on[cell] ||
                    (x > 0 && on[cell - 1]) ||
                    (x < gw - 1 && on[cell + 1]) ||
                    (y > 0 && on[cell - gw]) ||
                    (y < gh - 1 && on[cell + gw])
        }
    }
    return grow
}

/** One erosion, so the hairline along a real contact drops out. */
private fun coreOf(shut: BooleanArray, gw: Int, gh: Int): BooleanArray {
    val core = BooleanArray(gw * gh)
    for (y in 1 until gh - 1) {
        for (x in 1 until gw - 1) {
            val cell = y * gw + x
            core[cell] =
                shut[cell] && shut[cell - 1] && shut[cell + 1] && shut[cell - gw] && shut[cell + gw]
        }
    }
    return core
}

private fun largestPiece(core: BooleanArray, gw: Int, gh: Int): Int {
    val n = gw * gh
    val seen = BooleanArray(n)
    val stack = IntArray(n)
    var largest = 0
    for (start in 0 until n) {
        if (!core[start] || seen[start]) continue
        var top = 0
        var size = 0
        stack[top++] = start
        seen[start] = true
        while (top > 0) {
            val cell = stack[--top]
            size++
            val x = cell % gw
            val y = (cell - x) / gw
            if (x > 0) top = visit(core, seen, stack, top, cell - 1)
            if (x < gw - 1) top = visit(core, seen, stack, top, cell + 1)
            if (y > 0) top = visit(core, seen, stack, top, cell - gw)
            if (y < gh - 1) top = visit(core, seen, stack, top, cell + gw)
        }
        if (size > largest) largest = size
    }
    return largest
}

/** Pushes [next] if it is core and unseen; returns the new stack top. */
private fun visit(
    core: BooleanArray,
    seen: BooleanArray,
    stack: IntArray,
    top: Int,
    next: Int,
): Int {
    if (!core[next] || seen[next]) return top
    seen[next] = true
    stack[top] = next
    return top + 1
}

private class Shift(val gx: Int, val gy: Int, val dz: Double)

private class Measure(
    val ra: Raster,
    val rb: Raster,
    val dx: Int,
    val dy: Int,
    val dz: Double,
    val cam: CameraBasis,
    val step: Double,
    val tolerance: Double,
)

private fun measure(m: Measure, least: Double): List<FrontSide>? {
    val ra = m.ra
    val rb = m.rb
    if (
        ra.x1 < rb.x0 + m.dx || rb.x1 + m.dx < ra.x0 || ra.y1 < rb.y0 + m.dy || rb.y1 + m.dy < ra.y0
    ) {
        return null
    }
    val ix0 = max(ra.x0, rb.x0 + m.dx)
    val iy0 = max(ra.y0, rb.y0 + m.dy)
    val gw = min(ra.x1, rb.x1 + m.dx) - ix0 + 1
    val gh = min(ra.y1, rb.y1 + m.dy) - iy0 + 1
    val flags = ByteArray(gw * gh)
    val worst = doubleArrayOf(0.0, 0.0)
    val at = arrayOfNulls<Flat>(2)
    var any = false
    for (y in iy0 until iy0 + gh) {
        for (x in ix0 until ix0 + gw) {
            val gap = compare(m, x, y)
            if (gap == 0.0) continue
            val side = if (gap > 0) 0 else 1
            any = true
            flags[(y - iy0) * gw + (x - ix0)] = if (side == 0) 1 else -1
            if (abs(gap) > worst[side]) {
                worst[side] = abs(gap)
                at[side] = Flat(x * m.step, y * m.step)
            }
        }
    }
    if (!any) return null
    val front = listOf(regionOf(flags, gw, gh, 1, m.step), regionOf(flags, gw, gh, -1, m.step))
    if (front[0].area < least && front[1].area < least) return null
    return front.mapIndexed { side, region ->
        FrontSide(region.all, region.inner, region.area, worst[side], at[side])
    }
}

/**
 * How much nearer the first side is at cell ([x], [y]) (negative: the second), or 0 where neither
 * is in front by more than the tolerance. A side that is in front always is so by more than 0.
 */
private fun compare(m: Measure, x: Int, y: Int): Double {
    val ta = depthAt(m.ra, m.cam, m.step, x, y)
    if (ta.isNaN()) return 0.0
    val tb = depthAt(m.rb, m.cam, m.step, x - m.dx, y - m.dy) + m.dz
    return when {
        tb.isNaN() -> 0.0
        ta > tb + m.tolerance -> ta - tb
        tb > ta + m.tolerance -> -(tb - ta)
        else -> 0.0
    }
}

/**
 * JavaScript's `value.toFixed(digits)` as a key: the same values share a key, and only they. Away
 * from a tie the scaled double rounds the same as the exact value does.
 */
internal fun fixedKey(value: Double, digits: Int): Long {
    var scale = 1.0
    repeat(digits) { scale *= 10 }
    val scaled = abs(value) * scale
    val fraction = scaled - floor(scaled)
    val magnitude =
        if (abs(fraction - 0.5) > 1e-6) jsRound(scaled).toLong()
        else BigDecimal(abs(value)).setScale(digits, RoundingMode.HALF_UP).unscaledValue().toLong()
    return if (value < 0) -magnitude - 1 else magnitude
}

private data class MemoKey(val a: Int, val b: Int, val dx: Int, val dy: Int, val dz: Long)

private data class SlidKey(val index: Int, val x: Long, val y: Long, val z: Long)

private class OverlapPass(
    items: List<AuditItem>,
    val cam: CameraBasis,
    val step: Double,
    val tolerance: Double,
    val least: Double,
) {
    val live = items.withIndex().filter { it.value.shapes.isNotEmpty() }
    private val homes = arrayOfNulls<Raster>(live.size)
    private val sliding = live.map { (_, item) ->
        item.shapes.any { it is BodyShape && it.slide != null }
    }
    private val slid = HashMap<SlidKey, Raster>()
    private val memo = HashMap<MemoKey, List<FrontSide>?>()
    private var uid = 0

    fun rasterAt(index: Int, offset: Point3): Raster {
        if (!sliding[index]) {
            return homes[index]
                ?: rasterOf(live[index].value, cam, step, ZERO, uid++).also { homes[index] = it }
        }
        val id = SlidKey(index, fixedKey(offset.x, 2), fixedKey(offset.y, 2), fixedKey(offset.z, 2))
        return slid.getOrPut(id) { rasterOf(live[index].value, cam, step, offset, uid++) }
    }

    fun shiftOf(index: Int, offset: Point3): Shift =
        if (sliding[index]) Shift(0, 0, 0.0)
        else
            Shift(
                jsRound((dot3(offset, cam.r) * cam.k) / step).toInt(),
                jsRound((dot3(offset, cam.d) * cam.k) / step).toInt(),
                dot3(offset, cam.v),
            )

    fun measured(ra: Raster, rb: Raster, sa: Shift, sb: Shift): List<FrontSide>? {
        val dx = sb.gx - sa.gx
        val dy = sb.gy - sa.gy
        val dz = sb.dz - sa.dz
        val id = MemoKey(ra.uid, rb.uid, dx, dy, fixedKey(dz, 3))
        if (memo.containsKey(id)) return memo[id]
        val hit = measure(Measure(ra, rb, dx, dy, dz, cam, step, tolerance), least)
        memo[id] = hit
        return hit
    }
}

private fun apart(ra: Raster, sa: Shift, rb: Raster, sb: Shift): Boolean =
    ra.x1 + sa.gx < rb.x0 + sb.gx ||
        rb.x1 + sb.gx < ra.x0 + sa.gx ||
        ra.y1 + sa.gy < rb.y0 + sb.gy ||
        rb.y1 + sb.gy < ra.y0 + sa.gy

/**
 * Every pair of drawn [items] whose screen footprints overlap in each of [states], ray-cast on a
 * grid of [step] viewBox units: a cell counts when one surface is more than [tolerance] nearer, and
 * a side is in front when its largest closed, eroded piece is at least [least] px². [skip] excludes
 * pairs, such as neighbouring chunks of one pipe.
 */
fun overlapsOf(
    items: List<AuditItem>,
    projection: Projection,
    step: Double = 0.25,
    tolerance: Double = 0.3,
    least: Double = 0.25,
    states: List<ExplodeState> = listOf(Uniform(0.0)),
    skip: (AuditItem, AuditItem) -> Boolean = { _, _ -> false },
): Overlaps {
    val cam = cameraBasis(projection)
    val pass = OverlapPass(items, cam, step, tolerance, least)
    val live = pass.live
    val pairs = ArrayList<OverlapPair>()
    for (state in states) {
        val offsets = live.map { offsetOf(it.value, state) }
        val shifts = live.indices.map { pass.shiftOf(it, offsets[it]) }
        for (a in live.indices) {
            val ra = pass.rasterAt(a, offsets[a])
            val sa = shifts[a]
            for (b in a + 1 until live.size) {
                val rb = pass.rasterAt(b, offsets[b])
                val sb = shifts[b]
                val hit =
                    if (apart(ra, sa, rb, sb) || skip(live[a].value, live[b].value)) null
                    else pass.measured(ra, rb, sa, sb)
                if (hit == null) continue
                val front = hit.map { side ->
                    val moved = side.at?.let { Flat(it.x + sa.gx * step, it.y + sa.gy * step) }
                    FrontSide(side.all, side.inner, side.area, side.depth, moved)
                }
                pairs.add(OverlapPair(state, live[a].index, live[b].index, front))
            }
        }
    }
    return Overlaps(pairs, least, states, step, cam.v)
}
