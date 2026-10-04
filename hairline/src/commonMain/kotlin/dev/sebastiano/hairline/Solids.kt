// Ported from hairline by Lucas Marques (https://github.com/lucasmarkes/hairline), MIT licensed.
// See hairline/LICENSE.
package dev.sebastiano.hairline

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/* Rounded solids: sampled outlines with their normals, hulls, prisms, fillets and reflections. */

/**
 * A rounded rectangle, sampled. Each sample carries its outward normal, so a caller can keep just
 * the run that faces the camera. [n] samples per corner.
 */
fun rrect(u0: Double, v0: Double, u1: Double, v1: Double, r: Double, n: Int = 4): Ring {
    val rr = max(0.0, min(r, min((u1 - u0) / 2, (v1 - v0) / 2)))
    val out = ArrayList<Sample>(4 * (n + 1))
    val corners =
        arrayOf(
            doubleArrayOf(u1 - rr, v1 - rr, 0.0),
            doubleArrayOf(u0 + rr, v1 - rr, 90.0),
            doubleArrayOf(u0 + rr, v0 + rr, 180.0),
            doubleArrayOf(u1 - rr, v0 + rr, 270.0),
        )
    for ((cu, cv, a0) in corners) {
        for (k in 0..n) {
            val a = rad(a0 + 90.0 * k / n)
            val ca = cos(a)
            val sa = sin(a)
            out.add(Sample(cu + rr * ca, cv + rr * sa, ca, sa))
        }
    }
    return out
}

/** A circle of radius [r] about the origin, sampled the same way. */
fun circ(r: Double, n: Int = 96): Ring =
    List(n) { k ->
        val a = k.toDouble() / n * PI * 2
        val ca = cos(a)
        val sa = sin(a)
        Sample(r * ca, r * sa, ca, sa)
    }

private fun cross(o: Vec2, a: Vec2, b: Vec2): Double =
    (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

/** Convex hull (monotone chain), counter-clockwise in screen space. */
fun hull(input: List<Vec2>): List<Vec2> {
    val pts = input.sortedWith(compareBy<Vec2> { it.x }.thenBy { it.y })
    val lo = ArrayList<Vec2>()
    val up = ArrayList<Vec2>()
    for (p in pts) {
        while (lo.size > 1 && cross(lo[lo.size - 2], lo[lo.size - 1], p) <= 0) lo.removeAt(
            lo.size - 1
        )
        lo.add(p)
    }
    for (i in pts.indices.reversed()) {
        val p = pts[i]
        while (up.size > 1 && cross(up[up.size - 2], up[up.size - 1], p) <= 0) up.removeAt(
            up.size - 1
        )
        up.add(p)
    }
    if (lo.isNotEmpty()) lo.removeAt(lo.size - 1)
    if (up.isNotEmpty()) up.removeAt(up.size - 1)
    return lo + up
}

/** A ring laid flat at height z, projected. */
fun ringAt(p: Projector, ring: List<Sample>, z: Double): List<Vec2> = ring.map { p(it.u, it.v, z) }

/**
 * The samples whose normal faces the camera. The world direction of screen-down is (sin az, cos
 * az), so facing is a dot product with it.
 */
fun facing(c: Camera): (Sample) -> Boolean {
    val s = sin(c.az)
    val co = cos(c.az)
    return { q -> q.nu * s + q.nv * co >= -1e-6 }
}

/** The one cyclic run of samples that pass [keep], in ring order. */
fun run(ring: List<Sample>, keep: (Sample) -> Boolean): Ring {
    val n = ring.size
    var s = -1
    for (i in 0 until n) {
        if (keep(ring[i]) && !keep(ring[(i + n - 1) % n])) {
            s = i
            break
        }
    }
    if (s < 0) return if (n > 0 && keep(ring[0])) ring.toList() else emptyList()
    val out = ArrayList<Sample>()
    var k = 0
    while (k < n && keep(ring[(s + k) % n])) {
        out.add(ring[(s + k) % n])
        k++
    }
    return out
}

/**
 * A prism standing from z0 to z1. Its silhouette is the hull of the two rings, so the vertical
 * corners are never drawn; its only inner line is the crease, the front run of an inset ring on the
 * lid, which reads as a bevel.
 */
fun prism(
    p: Projector,
    front: (Sample) -> Boolean,
    ring: List<Sample>,
    inner: List<Sample>?,
    z0: Double,
    z1: Double,
): PrismPaths =
    PrismPaths(
        sil = poly(hull(ringAt(p, ring, z1) + ringAt(p, ring, z0))),
        crease = if (inner != null) open(ringAt(p, run(inner, front), z1)) else PathData.EMPTY,
    )

/** A rounded footprint and its crease ring, inset by b. */
fun rings(x0: Double, y0: Double, x1: Double, y1: Double, r: Double, b: Double): Pair<Ring, Ring> =
    rrect(x0, y0, x1, y1, r) to rrect(x0 + b, y0 + b, x1 - b, y1 - b, max(0.3, r - b))

/** The leftmost, rightmost and nearest samples of a ring: where construction lines drop from. */
fun extremes(p: Projector, ring: List<Sample>): Triple<Sample, Sample, Sample> {
    val pr = ring.map { p(it.u, it.v, 0.0) }
    var a = 0
    var b = 0
    var c = 0
    pr.forEachIndexed { k, q ->
        if (q.x < pr[a].x) a = k
        if (q.x > pr[b].x) b = k
        if (q.y > pr[c].y) c = k
    }
    return Triple(ring[a], ring[b], ring[c])
}

/** Rounds every vertex of a closed polygon with a quadratic through the vertex; r per vertex. */
fun fillet(pts: List<Vec2>, rs: List<Double>, n: Int = 4): List<Vec2> {
    val m = pts.size
    val out = ArrayList<Vec2>(m * (n + 1))
    for (i in 0 until m) {
        val a = pts[(i + m - 1) % m]
        val p = pts[i]
        val b = pts[(i + 1) % m]
        val la = hypot(a.x - p.x, a.y - p.y)
        val lb = hypot(b.x - p.x, b.y - p.y)
        val t = min(rs[i], min(la / 2, lb / 2))
        val p1 = Vec2(p.x + (a.x - p.x) / la * t, p.y + (a.y - p.y) / la * t)
        val p2 = Vec2(p.x + (b.x - p.x) / lb * t, p.y + (b.y - p.y) / lb * t)
        for (k in 0..n) {
            val s = k.toDouble() / n
            val w = 1 - s
            out.add(
                Vec2(
                    w * w * p1.x + 2 * w * s * p.x + s * s * p2.x,
                    w * w * p1.y + 2 * w * s * p.y + s * s * p2.y,
                )
            )
        }
    }
    return out
}

/** A prism's mirror under its floor: the far edge of the reflection and its two sides. */
class Ghost(val d: PathData, val y0: Double, val y1: Double)

/**
 * A prism's mirror under its floor, as a path. [Ghost.y0]/[Ghost.y1] are where its fade starts (the
 * floor's top) and ends (just past the reflection's lowest point).
 */
fun ghost(
    p: Projector,
    front: (Sample) -> Boolean,
    ring: List<Sample>,
    z0: Double,
    depth: Double,
): Ghost {
    val f = run(ring, front)
    val lowP = ringAt(p, f, z0 - depth)
    val sides =
        listOf(f.first(), f.last()).map { q -> seg(p(q.u, q.v, z0), p(q.u, q.v, z0 - depth)) }
    return Ghost(
        d = open(lowP) + sides.join(),
        y0 = ringAt(p, f, z0).minOf { it.y },
        y1 = lowP.maxOf { it.y } + 2,
    )
}
