// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.figures.TurningDial.AZIMUTH
import dev.sebastiano.anatomy.figures.TurningDial.BASE
import dev.sebastiano.anatomy.figures.TurningDial.BASE_H
import dev.sebastiano.anatomy.figures.TurningDial.DIAL_R
import dev.sebastiano.anatomy.figures.TurningDial.FOOT_H
import dev.sebastiano.anatomy.figures.TurningDial.INDEX_AT
import dev.sebastiano.anatomy.figures.TurningDial.LOCK_Z0
import dev.sebastiano.anatomy.figures.TurningDial.PLATE
import dev.sebastiano.anatomy.figures.TurningDial.PLATE_Z0
import dev.sebastiano.anatomy.figures.TurningDial.PLATE_Z1
import dev.sebastiano.anatomy.figures.TurningDial.WHEEL_AT
import dev.sebastiano.anatomy.figures.TurningDial.WINDOW
import dev.sebastiano.anatomy.kit.Art
import dev.sebastiano.anatomy.kit.ArtBuilder
import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Projection
import dev.sebastiano.anatomy.kit.SolidPaths
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.audit.AuditShape
import dev.sebastiano.anatomy.kit.audit.Recorder
import dev.sebastiano.anatomy.kit.audit.ShapeMeta
import dev.sebastiano.anatomy.kit.audit.cylinder as cylinderShape
import dev.sebastiano.anatomy.kit.audit.extruded
import dev.sebastiano.anatomy.kit.audit.slab
import dev.sebastiano.anatomy.kit.corners
import dev.sebastiano.anatomy.kit.cylinder
import dev.sebastiano.anatomy.kit.depthOf
import dev.sebastiano.anatomy.kit.extrude
import dev.sebastiano.anatomy.kit.haloOf
import dev.sebastiano.anatomy.kit.insetPlan
import dev.sebastiano.anatomy.kit.iso
import dev.sebastiano.anatomy.kit.onTop
import dev.sebastiano.anatomy.kit.pathOf
import dev.sebastiano.anatomy.kit.planOutline
import dev.sebastiano.anatomy.kit.ring
import dev.sebastiano.anatomy.kit.roundedPlan
import dev.sebastiano.anatomy.kit.sideSeam
import dev.sebastiano.anatomy.kit.slabOf
import dev.sebastiano.anatomy.kit.turn.StaticItem
import dev.sebastiano.anatomy.kit.turn.jsCos
import dev.sebastiano.anatomy.kit.turn.jsNumber
import dev.sebastiano.anatomy.kit.turn.jsSin
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * The turning dial's static art: the base on its feet, the posts and washers under the door plate,
 * the plate with its window onto the index wheel, and the brass index at the back of the dial.
 */
internal class TurningDialStatic(private val p: Projection, private val r: Recorder) {
    val items = ArrayList<StaticItem>()

    private fun at(x: Double, y: Double, z: Double) = iso(Point3(x, y, z), p)

    private fun byDepth(points: List<Floor>) = points.sortedBy {
        depthOf(Point3(it.x, it.y, 0.0), p)
    }

    private fun put(
        name: String,
        layer: String,
        at: Point3,
        art: Art,
        shapes: List<AuditShape>,
        bias: Double = 0.0,
    ) {
        val solids = shapes.map { r.solid(it) }
        r.put(art, solids, at = at, bias = bias, name = name)
        items.add(StaticItem(name, layer, solids))
    }

    private fun meta(name: String, index: Int, ground: Boolean = false) =
        ShapeMeta(name = if (index > 0) "$name#${index + 1}" else name, ground = ground)

    fun build() {
        base()
        backPosts()
        midStatic()
    }

    private fun base() {
        val foot = -BASE_H - FOOT_H
        for ((x, y) in corners(BASE, 22.0)) {
            val name = "stand.foot.${jsNumber(x)}.${jsNumber(y)}"
            val art = art {
                solid(cylinder(x, y, 7.0, foot, 1.6, p, 28), Tone.Lo)
                solid(cylinder(x, y, 5.4, foot + 1.6, FOOT_H - 1.6, p, 28), Tone.Mid)
            }
            put(
                name,
                "back",
                Point3(x, y, foot),
                art,
                listOf(
                    cylinderShape(x, y, 7.0, foot, 1.6, meta(name, 0, ground = true)),
                    cylinderShape(x, y, 5.4, foot + 1.6, FOOT_H - 1.6, meta(name, 1)),
                ),
            )
        }
        val screws = corners(BASE, 7.0)
        val art = art {
            solid(
                slabOf(BASE, -BASE_H, BASE_H, p, 8, 1.6),
                Tone.Mid,
                inner = art { line(sideSeam(BASE, -BASE_H + 4, p, 8), Tone.Faint) },
            )
            line(IsoPath.join(screws.map { (x, y) -> ring(x, y, 2.1, 0.0, p, 16) }), Tone.Lo)
            dots(screws.map { (x, y) -> at(x, y, 0.0) }, 0.5)
        }
        put(
            "stand.base",
            "back",
            Point3(0.0, 0.0, -BASE_H),
            art,
            listOf(slab(BASE, -BASE_H, BASE_H, meta("stand.base", 0, ground = true))),
            bias = 4.0,
        )
    }

    private fun backPosts() {
        val v = Floor(jsCos((AZIMUTH * PI) / 180), jsSin((AZIMUTH * PI) / 180))
        for ((x, y) in byDepth(corners(PLATE, 12.0))) {
            val front = x * v.x + y * v.y > 0
            val name = "stand.post.${jsNumber(x)}.${jsNumber(y)}"
            val art = art {
                solid(cylinder(x, y, 3.4, 0.0, PLATE_Z0, p, 28), Tone.Mid, crease = null)
            }
            put(
                name,
                if (front) "mid" else "back",
                Point3(x, y, 0.0),
                art,
                listOf(cylinderShape(x, y, 3.4, 0.0, PLATE_Z0, meta(name, 0))),
            )
        }
        for ((name, centre, radius) in
            listOf(
                Triple("stand.washer.dial", Floor(0.0, 0.0), 4.2),
                Triple("stand.washer.wheel", WHEEL_AT, 6.4),
            )) {
            val art = art {
                solid(cylinder(centre.x, centre.y, radius, 0.0, LOCK_Z0, p, 32), Tone.Lo)
            }
            put(
                name,
                "back",
                Point3(centre.x, centre.y, 0.0),
                art,
                listOf(cylinderShape(centre.x, centre.y, radius, 0.0, LOCK_Z0, meta(name, 0))),
            )
        }
    }

    /** The door plate with its window: a slab with a hole, the window's walls seen through it. */
    private fun windowed(): Art {
        val slab = slabOf(PLATE, PLATE_Z0, PLATE_Z1 - PLATE_Z0, p, 8, 1.0)
        val ring = roundedPlan(WINDOW, 4)
        val hole = onTop(ring, PLATE_Z1, p, true)
        val v = Floor(jsCos((AZIMUTH * PI) / 180), jsSin((AZIMUTH * PI) / 180))
        val lx = -jsSin((AZIMUTH * PI) / 180)
        val ly = jsCos((AZIMUTH * PI) / 180)
        val walls = List(4) { ArrayList<IsoPath>() }
        val bottoms = ArrayList<Pair<Floor, Floor>>()
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[(i + 1) % ring.size]
            val len =
                dev.sebastiano.anatomy.kit.jsHypot(b.x - a.x, b.y - a.y).let {
                    if (it == 0.0) 1.0 else it
                }
            val nx = -(b.y - a.y) / len
            val ny = (b.x - a.x) / len
            if (nx * v.x + ny * v.y <= 1e-9) continue
            val shade = min(3, max(0, floor(((nx * lx + ny * ly + 1) / 2) * 4).toInt()))
            walls[shade].add(
                pathOf(
                    listOf(
                        at(a.x, a.y, PLATE_Z1),
                        at(b.x, b.y, PLATE_Z1),
                        at(b.x, b.y, PLATE_Z0),
                        at(a.x, a.y, PLATE_Z0),
                    ),
                    true,
                )
            )
            bottoms.add(a to b)
        }
        val under =
            IsoPath.join(
                bottoms.map { (a, b) ->
                    pathOf(listOf(at(a.x, a.y, PLATE_Z0), at(b.x, b.y, PLATE_Z0)))
                }
            )
        val screws = byDepth(corners(PLATE, 12.0))
        val inner = art {
            group("td-window", clip = hole) {
                walls.forEachIndexed { shade, list ->
                    if (list.isNotEmpty()) wall(IsoPath.join(list), shade)
                }
                line(under, Tone.Lo)
            }
            line(planOutline(insetPlan(PLATE, 4.0), PLATE_Z1, p), Tone.Faint)
            line(IsoPath.join(screws.map { (x, y) -> ring(x, y, 2.4, PLATE_Z1, p, 18) }), Tone.Lo)
            line(planOutline(insetPlan(WINDOW, -1.4), PLATE_Z1, p, 4), Tone.Faint)
        }
        val cut = holeFor(slab.fill, hole)
        val plate =
            SolidPaths(
                slab.fill + cut,
                slab.outline + hole,
                slab.crease,
                slab.top + holeFor(slab.top, hole),
                slab.shades,
                slab.bevel,
            )
        return art {
            halo(haloOf(insetPlan(PLATE, 4.0), PLATE_Z0, 0.1, p), 5.0)
            solid(plate, Tone.Mid, inner = inner)
            dots(screws.map { (x, y) -> at(x, y, PLATE_Z1) }, 0.5)
        }
    }

    /** A window's walls in one side tone, seen through the hole. */
    private fun ArtBuilder.wall(path: IsoPath, shade: Int) {
        val shades = List(4) { if (it == shade) path else IsoPath.Empty }
        solid(SolidPaths(path, IsoPath.Empty, IsoPath.Empty, IsoPath.Empty, shades), crease = null)
    }

    /** [hole] wound against [fill], so a non-zero fill leaves it open as SVG's even-odd does. */
    private fun holeFor(fill: IsoPath, hole: IsoPath): IsoPath {
        fun area(path: IsoPath): Double {
            val pts = path.runs.first().points
            var s = 0.0
            for (i in pts.indices) {
                val a = pts[i]
                val b = pts[(i + 1) % pts.size]
                s += a.x * b.y - b.x * a.y
            }
            return s
        }
        if ((area(fill) > 0) != (area(hole) > 0)) return hole
        return IsoPath(hole.runs.map { IsoPath.Run(it.points.reversed(), it.closed) })
    }

    private fun midStatic() {
        put(
            "stand.plate",
            "mid",
            Point3(0.0, 0.0, PLATE_Z0),
            windowed(),
            listOf(slab(PLATE, PLATE_Z0, PLATE_Z1 - PLATE_Z0, meta("stand.plate", 0))),
        )
        val c = jsCos(INDEX_AT)
        val s = jsSin(INDEX_AT)
        val wedge =
            listOf(
                    Floor(DIAL_R + 1.2, -0.7),
                    Floor(DIAL_R + 1.2, 0.7),
                    Floor(DIAL_R + 7.4, 2.6),
                    Floor(DIAL_R + 7.4, -2.6),
                )
                .map { (rr, t) -> Floor(c * rr - s * t, s * rr + c * t) }
        val art = art { solid(extrude(wedge, PLATE_Z1, 3.0, p, bevel = 0.35), Tone.Hi, lit = true) }
        put(
            "stand.index",
            "mid",
            Point3(c * 48, s * 48, PLATE_Z1),
            art,
            listOf(extruded(wedge, PLATE_Z1, 3.0, meta("stand.index", 0))),
        )
    }

    /** The soft shadow under the base, painted first. */
    fun baseHalo(): IsoPath = haloOf(BASE, -BASE_H - FOOT_H, BASE_H, p)
}
