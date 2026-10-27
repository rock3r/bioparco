// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.audit

import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Projection
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.dot3
import dev.sebastiano.anatomy.kit.jsHypot
import dev.sebastiano.anatomy.kit.jsRound
import dev.sebastiano.anatomy.kit.len3
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.resample
import dev.sebastiano.anatomy.kit.sub3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min

/*
 * Routes on screen: lines that double back or pinch to a point, which drawn shape is in front at a
 * screen point, which lines really meet, and slender solids treated as lines.
 */

/**
 * A pipe or cable route (`{ name, points, r }`), for `folds` and `crowding`. A [rod] is a slender
 * solid seen as a line: [solid] is its shape's name and [owners] the names that count as it.
 */
class AuditRoute(
    val name: String?,
    val points: List<Point3>,
    val r: Double,
    val bundle: String? = null,
    override val move: Point3? = null,
    override val ride: String? = null,
    override val piece: String? = null,
    val solid: String? = null,
    val owners: Set<String> = emptySet(),
    val rod: Boolean = false,
) : Mover

/** A drawn shape with its grown screen box and who it counts as. */
internal class Entry(
    val shape: AuditShape,
    val box: ScreenBox,
    val owner: String?,
    val bundle: String?,
    val item: String,
)

internal fun entriesOf(items: List<AuditItem>, cam: CameraBasis): List<Entry> =
    items.flatMap { item ->
        item.shapes.map { shape ->
            val box = screenBox(shape, cam)
            Entry(
                shape,
                ScreenBox(box.x0 - 1, box.y0 - 1, box.x1 + 1, box.y1 + 1),
                item.route ?: shape.owner ?: item.name,
                shape.meta.bundle,
                item.name,
            )
        }
    }

/** The drawn shape nearest the viewer at screen point ([x], [y]), or null. */
internal fun frontEntry(entries: List<Entry>, cam: CameraBasis, x: Double, y: Double): Entry? {
    val c = cam.rayStart(x, y)
    var best = -BIG
    var who: Entry? = null
    for (entry in entries) {
        if (!entry.box.contains(x, y)) continue
        val t = rayDepth(entry.shape, c, cam.v, 0.02, null)
        if (t != null && t > best) {
            best = t
            who = entry
        }
    }
    return who
}

internal fun belongs(entry: Entry?, name: String?, bundle: String?): Boolean =
    entry != null &&
        (entry.owner == name || entry.item == name || (bundle != null && entry.bundle == bundle))

/** A route that doubles back ([kind] `fold`) or pinches to a point (`pinch`) on screen. */
class ScreenFold(
    val name: String?,
    val kind: String,
    val flatTurn: Double,
    val at: Point3,
    val realTurn: Double = 0.0,
    val radius: Double = 0.0,
    val width: Double = 0.0,
)

private fun angle(a: Flat, b: Flat): Double {
    val length =
        (jsHypot(a.x, a.y) * jsHypot(b.x, b.y)).let { if (it == 0.0 || it.isNaN()) 1.0 else it }
    return acos(max(-1.0, min(1.0, (a.x * b.x + a.y * b.y) / length))) * 180 / PI
}

internal fun turnOf(a: Point3, b: Point3): Double {
    val length = (len3(a) * len3(b)).let { if (it == 0.0 || it.isNaN()) 1.0 else it }
    return acos(max(-1.0, min(1.0, dot3(a, b) / length))) * 180 / PI
}

/**
 * Every route whose screen direction turns more than [fold] degrees within four radii while its
 * real turn is under [turn] (a cusp), and every bend over 90° on screen round a radius under
 * [pinch] × its screen radius. With [items], only bends you can see count.
 */
fun folds(
    routes: List<AuditRoute>,
    projection: Projection,
    fold: Double = 140.0,
    turn: Double = 110.0,
    pinch: Double = 2.0,
    items: List<AuditItem>? = null,
): List<ScreenFold> {
    val cam = cameraBasis(projection)
    val entries = items?.let { entriesOf(it, cam) }
    fun shown(name: String?, p: Point3): Boolean {
        if (entries == null) return true
        val at = cam.flat(p)
        val front = frontEntry(entries, cam, at.x, at.y) ?: return false
        return front.owner == name
    }
    val out = ArrayList<ScreenFold>()
    for (route in routes) {
        val step = max(0.25, route.r * 0.5)
        val dense = resample(route.points, step)
        val screen = dense.map { Flat(dot3(it, cam.r) * cam.k, dot3(it, cam.d) * cam.k) }
        pinchOf(route, dense, screen, step, cam.k * route.r, pinch, ::shown)?.let { out.add(it) }
        val reach = max(2, jsRound((4 * route.r) / step).toInt())
        var i = reach
        while (i < dense.size - reach) {
            val a = screen[i] - screen[i - reach]
            val b = screen[i + reach] - screen[i]
            if (jsHypot(a.x, a.y) >= 1e-6 && jsHypot(b.x, b.y) >= 1e-6) {
                val flatTurn = angle(a, b)
                val realTurn =
                    turnOf(sub3(dense[i], dense[i - reach]), sub3(dense[i + reach], dense[i]))
                if (flatTurn > fold && realTurn < turn && shown(route.name, dense[i])) {
                    out.add(ScreenFold(route.name, "fold", flatTurn, dense[i], realTurn = realTurn))
                    i += 2 * reach
                }
            }
            i++
        }
    }
    return out
}

private fun pinchOf(
    route: AuditRoute,
    dense: List<Point3>,
    screen: List<Flat>,
    step: Double,
    width: Double,
    pinch: Double,
    shown: (String?, Point3) -> Boolean,
): ScreenFold? {
    var worst: ScreenFold? = null
    var worstIndex = 0
    for (span in doubleArrayOf(1.5, 3.0, 6.0)) {
        val near = max(1, jsRound((span * route.r) / step).toInt())
        for (i in near until dense.size - near) {
            val a = screen[i] - screen[i - near]
            val b = screen[i + near] - screen[i]
            val c = screen[i + near] - screen[i - near]
            val la = jsHypot(a.x, a.y)
            val lb = jsHypot(b.x, b.y)
            if (la < width * 0.3 || lb < width * 0.3) continue
            val bend = angle(a, b)
            val area = abs(a.x * b.y - a.y * b.x) / 2
            val radius =
                if (area > 1e-9) (la * lb * jsHypot(c.x, c.y)) / (4 * area)
                else Double.POSITIVE_INFINITY
            val worse = worst == null || radius < worst.radius
            if (bend > 90 && radius < pinch * width && worse && shown(route.name, dense[i])) {
                worst =
                    ScreenFold(route.name, "pinch", bend, dense[i], radius = radius, width = width)
                worstIndex = i
            }
        }
    }
    if (worst == null) return null
    var toStart = 0.0
    for (i in 1..worstIndex) toStart +=
        jsHypot(screen[i].x - screen[i - 1].x, screen[i].y - screen[i - 1].y)
    var toEnd = 0.0
    for (i in worstIndex + 1 until screen.size) {
        toEnd += jsHypot(screen[i].x - screen[i - 1].x, screen[i].y - screen[i - 1].y)
    }
    return if (min(toStart, toEnd) > 3 * width) worst else null
}

/**
 * The lines that really meet, by owner: a tube and its mount's owner (and the owners of what the
 * mount is cut by), and every pair of tubes that share a mount.
 */
fun linksOf(
    tubes: List<TubeShape>,
    mounts: Map<TubeShape, List<Mount?>>,
): Map<String, Set<String>> {
    val links = LinkedHashMap<String, MutableSet<String>>()
    fun link(a: String?, b: String?) {
        if (a.isNullOrEmpty() || b.isNullOrEmpty() || a == b) return
        links.getOrPut(a) { LinkedHashSet() }.add(b)
        links.getOrPut(b) { LinkedHashSet() }.add(a)
    }
    fun ownerOf(shape: AuditShape) = shape.owner ?: shape.name
    val seats = LinkedHashMap<String?, MutableList<String?>>()
    for (tube in tubes) {
        for (mount in mounts[tube].orEmpty()) {
            if (mount == null) continue
            val host = ownerOf(mount.shape)
            link(tube.owner, host)
            seats.getOrPut(host) { ArrayList() }.add(tube.owner)
            for (cut in mount.shape.meta.cut) link(tube.owner, ownerOf(cut))
        }
    }
    for (owners in seats.values) {
        for (a in owners) {
            for (b in owners) link(a, b)
        }
    }
    return links
}

/**
 * Every slender recorded solid (a turned part, prism or box longer than [ratio] × its radius or
 * half-width, and at least [least] thick) as a pseudo-route, which `crowding` takes as `rods`.
 */
fun slenderOf(
    solids: List<AuditShape>,
    ratio: Double = 4.0,
    least: Double = 0.3,
): List<AuditRoute> {
    val out = ArrayList<AuditRoute>()
    fun axisLine(centre: Point3, axis: Point3, half: Double, r: Double, shape: AuditShape) {
        out.add(
            AuditRoute(
                shape.owner ?: shape.name,
                listOf(add3(centre, mul3(axis, -half)), add3(centre, mul3(axis, half))),
                r,
                move = shape.move,
                ride = shape.ride,
                piece = shape.piece,
                solid = shape.name,
                owners = listOfNotNull(shape.name, shape.owner).filter { it.isNotEmpty() }.toSet(),
                rod = true,
            )
        )
    }
    for (shape in solids.filter { !it.meta.fitting }) {
        if (shape is BodyShape) {
            val f = shape.frame
            val s0 = shape.poly.minOf { it.s }
            val s1 = shape.poly.maxOf { it.s }
            val r = shape.poly.maxOf { it.r }
            if (r >= least && s1 - s0 >= ratio * r) {
                axisLine(add3(f.o, mul3(f.a, (s0 + s1) / 2)), f.a, (s1 - s0) / 2, r, shape)
            }
        }
        val (centre, axes, half) =
            when (shape) {
                is PrismShape -> prismBlock(shape)
                is BoxShape -> Triple(shape.o, shape.axes, shape.half)
                else -> continue
            }
        val order = listOf(0, 1, 2).sortedByDescending { half[it] }
        val r = half[order[1]]
        if (r >= least && half[order[0]] >= ratio * r) {
            axisLine(centre, axes[order[0]], half[order[0]], r, shape)
        }
    }
    return out
}

private fun prismBlock(shape: PrismShape): Triple<Point3, List<Point3>, List<Double>> {
    val f = shape.frame
    val x0 = shape.poly.minOf { it.x }
    val x1 = shape.poly.maxOf { it.x }
    val y0 = shape.poly.minOf { it.y }
    val y1 = shape.poly.maxOf { it.y }
    val centre =
        add3(
            f.o,
            add3(
                add3(mul3(f.u, (x0 + x1) / 2), mul3(f.v, (y0 + y1) / 2)),
                mul3(f.a, (shape.s0 + shape.s1) / 2),
            ),
        )
    return Triple(
        centre,
        listOf(f.u, f.v, f.a),
        listOf((x1 - x0) / 2, (y1 - y0) / 2, (shape.s1 - shape.s0) / 2),
    )
}
