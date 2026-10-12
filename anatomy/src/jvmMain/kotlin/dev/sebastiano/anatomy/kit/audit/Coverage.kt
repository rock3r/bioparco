// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.audit

import dev.sebastiano.anatomy.kit.Art
import dev.sebastiano.anatomy.kit.DotsMark
import dev.sebastiano.anatomy.kit.FadeLineMark
import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.GroupMark
import dev.sebastiano.anatomy.kit.HaloMark
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.LineMark
import dev.sebastiano.anatomy.kit.Mark
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Projection
import dev.sebastiano.anatomy.kit.SolidMark
import dev.sebastiano.anatomy.kit.TextMark
import dev.sebastiano.anatomy.kit.WireMark
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.jsHypot
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.resample
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Stand-in shapes: the screen box of what an item draws against the screen box of what it records.
 * The original measures the drawing from its SVG markup (`markupBox`); here it is the item's art.
 */

private fun boxOfPoints(points: Iterable<Flat>): ScreenBox? {
    var box: ScreenBox? = null
    for ((x, y) in points) {
        box =
            box?.let { ScreenBox(min(it.x0, x), min(it.y0, y), max(it.x1, x), max(it.y1, y)) }
                ?: ScreenBox(x, y, x, y)
    }
    return box
}

private fun unionBox(a: ScreenBox?, b: ScreenBox?): ScreenBox? =
    when {
        a == null -> b
        b == null -> a
        else -> ScreenBox(min(a.x0, b.x0), min(a.y0, b.y0), max(a.x1, b.x1), max(a.y1, b.y1))
    }

private fun meetBox(a: ScreenBox?, b: ScreenBox?): ScreenBox? =
    if (a == null || b == null) a
    else ScreenBox(max(a.x0, b.x0), max(a.y0, b.y0), min(a.x1, b.x1), min(a.y1, b.y1))

private fun areaOf(box: ScreenBox?): Double =
    if (box == null) 0.0 else max(0.0, box.x1 - box.x0) * max(0.0, box.y1 - box.y0)

private fun pathBox(path: IsoPath): ScreenBox? = boxOfPoints(path.runs.flatMap { it.points })

private fun dotBox(points: List<Flat>, size: Double): ScreenBox? =
    points.fold(null as ScreenBox?) { box, (x, y) ->
        unionBox(box, ScreenBox(x - size, y - size, x + size, y + size))
    }

private fun solidBox(mark: SolidMark): ScreenBox? {
    val paths = mark.paths
    var box = pathBox(paths.fill)
    if (!mark.flat) {
        for (shade in paths.shades) box = unionBox(box, pathBox(shade))
        box = unionBox(box, pathBox(paths.top))
    }
    if (mark.crease != null) {
        box = unionBox(box, pathBox(paths.crease))
        paths.bevel?.let { box = unionBox(box, pathBox(it)) }
    }
    mark.inner?.let { box = unionBox(box, artBox(it)) }
    return unionBox(box, pathBox(paths.outline))
}

private fun markBox(mark: Mark): ScreenBox? =
    when (mark) {
        is SolidMark -> solidBox(mark)
        is LineMark -> pathBox(mark.shape.iso)
        is DotsMark -> dotBox(mark.points, mark.size)
        is WireMark -> unionBox(pathBox(mark.line.shape.iso), dotBox(mark.ends, mark.size))
        is FadeLineMark -> boxOfPoints(mark.points)
        is GroupMark -> null
        is HaloMark,
        is TextMark -> null
    }

/**
 * The screen box of what [art] draws: every path, dot and line, through group clips, without halos
 * and text. The original's `markupBox(svg)`, measured from the art's geometry.
 */
fun artBox(art: Art, clip: ScreenBox? = null): ScreenBox? {
    var box: ScreenBox? = null
    for (mark in art.marks) {
        val shape =
            if (mark is GroupMark) {
                val own = mark.clip?.let { pathBox(it.iso) }
                artBox(mark.art, if (own != null) meetBox(clip ?: own, own) else clip)
            } else {
                markBox(mark)?.let { if (clip != null) meetBox(it, clip) else it }
            }
        box = unionBox(box, shape)
    }
    return box
}

/** The screen box of [shape]'s outline, from its rings and corners. */
fun footprintOf(shape: AuditShape, cam: CameraBasis): ScreenBox =
    when (shape) {
        is BodyShape -> {
            val f = shape.frame
            val points = ArrayList<Flat>()
            for ((s, r) in shape.poly) {
                if (r <= 0) {
                    points.add(cam.flat(add3(f.o, mul3(f.a, s))))
                    continue
                }
                for (i in 0 until 16) {
                    val a = (i.toDouble() / 16) * PI * 2
                    points.add(
                        cam.flat(
                            add3(
                                add3(f.o, mul3(f.a, s)),
                                add3(mul3(f.u, r * cos(a)), mul3(f.v, r * sin(a))),
                            )
                        )
                    )
                }
            }
            boxOfPoints(points)!!
        }
        is PrismShape -> {
            val f = shape.frame
            val points = ArrayList<Flat>()
            for (s in doubleArrayOf(shape.s0, shape.s1)) {
                for ((x, y) in shape.poly) points.add(
                    cam.flat(add3(add3(f.o, mul3(f.a, s)), add3(mul3(f.u, x), mul3(f.v, y))))
                )
            }
            boxOfPoints(points)!!
        }
        else -> screenBox(shape, cam)
    }

/** An item whose recorded shapes cover only [share] of what it draws. */
class StandIn(val name: String, val share: Double, val drawn: ScreenBox, val shapes: ScreenBox)

/**
 * Every item with art and shapes whose shapes' footprint covers less than [least] of the screen box
 * of its art (both grown by [pad]). [allow] excuses items whose drawing really reaches past them.
 */
fun coverage(
    items: List<AuditItem>,
    projection: Projection,
    least: Double = 0.6,
    pad: Double = 1.0,
    allow: List<String> = emptyList(),
): List<StandIn> {
    val cam = cameraBasis(projection)
    val out = ArrayList<StandIn>()
    fun grow(box: ScreenBox) = ScreenBox(box.x0 - pad, box.y0 - pad, box.x1 + pad, box.y1 + pad)
    for (item in items) {
        val art = item.art
        val skip = art == null || item.shapes.isEmpty() || matches(allow, item.name)
        val drawn = (if (skip) null else art?.let { artBox(it) }) ?: continue
        val shapes = item.shapes.map { footprintOf(it, cam) }.reduce { a, b -> unionBox(a, b)!! }
        val share =
            areaOf(meetBox(grow(drawn), grow(shapes))) /
                areaOf(grow(drawn)).let { if (it == 0.0) 1.0 else it }
        if (share < least) out.add(StandIn(item.name, share, drawn, shapes))
    }
    return out.sortedBy { it.share }
}

/** A tube chunk whose outline folds back on screen, turning on [radius] under its [width]. */
class SelfOverlap(
    val name: String,
    val route: String?,
    val radius: Double,
    val width: Double,
    val at: Point3,
    val screen: Flat,
)

/**
 * Every drawn tube chunk whose screen centreline turns on a radius under its screen half-width by
 * at least [least] px, so its single outline crosses itself, where no other item covers the spot.
 */
fun selfOverlaps(
    items: List<AuditItem>,
    projection: Projection,
    fold: Double = 0.25,
    least: Double = 0.5,
): List<SelfOverlap> {
    val cam = cameraBasis(projection)
    val entries = entriesOf(items, cam)
    return items.mapNotNull { item ->
        val shape = item.shapes.firstOrNull()
        if (item.route.isNullOrEmpty() || shape !is TubeShape) null
        else selfOverlapOf(item, shape, cam, entries, fold, least)
    }
}

private fun selfOverlapOf(
    item: AuditItem,
    shape: TubeShape,
    cam: CameraBasis,
    entries: List<Entry>,
    fold: Double,
    least: Double,
): SelfOverlap? {
    val r = shape.radii.sum() / shape.radii.size
    val dense = resample(shape.points, max(0.1, r * 0.2))
    val screen = dense.map { cam.flat(it) }
    val n = screen.size
    if (n < 4) return null
    val width = r * cam.k
    val span = 3
    val normals =
        screen.indices.map { i ->
            val a = screen[max(0, i - span)]
            val b = screen[min(n - 1, i + span)]
            val length = jsHypot(b.x - a.x, b.y - a.y).let { if (it == 0.0) 1.0 else it }
            Flat(-(b.y - a.y) / length, (b.x - a.x) / length)
        }
    var worst: SelfOverlap? = null
    for (side in doubleArrayOf(1.0, -1.0)) {
        val edge = screen.mapIndexed { i, p -> p + normals[i] * (side * width) }
        for (i in span until n - span - 1) {
            val along = screen[i + 1] - screen[i]
            val step = edge[i + 1] - edge[i]
            val length = jsHypot(along.x, along.y)
            val forward = if (length < 1e-6) 0.0 else (step.x * along.x + step.y * along.y) / length
            if (length < 1e-6 || forward > -fold * length) continue
            val x = (screen[i].x + screen[i + 1].x) / 2
            val y = (screen[i].y + screen[i + 1].y) / 2
            val front = frontEntry(entries, cam, x, y)
            val covered = front != null && front.owner != item.route && front.item != item.name
            val bend = abs(forward / length - 1)
            val radius =
                if (bend > 1e-6) width / abs(1 - forward / length) else Double.POSITIVE_INFINITY
            val folds = !covered && width - radius >= least
            if (folds && (worst == null || radius < worst.radius)) {
                worst = SelfOverlap(item.name, item.route, radius, width, dense[i], Flat(x, y))
            }
        }
    }
    return worst
}
