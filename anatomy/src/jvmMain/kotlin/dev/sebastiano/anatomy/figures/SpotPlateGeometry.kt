// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.insetPlan
import dev.sebastiano.anatomy.kit.onTop
import dev.sebastiano.anatomy.kit.pathOf
import dev.sebastiano.anatomy.kit.planOutline
import dev.sebastiano.anatomy.kit.ringOf

object SpotBase {
    private val g = SpotGeometry
    private val r = SpotRig
    private val feetAt = g.corners(r.BASE, 18.0)
    private val screwsAt = g.corners(r.BASE, 6.0)
    val halo = g.solid(insetPlan(r.BASE, 6.0), -r.BASE_H - r.FOOT_H, 0.4, 8).fill
    val feet = feetAt.map { (x, y) ->
        listOf(
            g.cyl(x, y, 3.6, -r.BASE_H - r.FOOT_H, r.FOOT_H - 1.2, 24),
            g.cyl(x, y, 4.8, -r.BASE_H - 1.2, 1.2, 28),
        )
    }
    val slab = g.solid(r.BASE, -r.BASE_H, r.BASE_H, 10, 2.2)
    val groove = g.visibleRun(r.BASE, -r.BASE_H * 0.45, 10)
    val inlay = planOutline(insetPlan(r.BASE, 4.0), 0.0, r.P, 8)
    val screws = screwsAt.map { r.at(Point3(it.x, it.y, 0.0)) }
    val screwRings = IsoPath.join(screwsAt.map { g.ring(it.x, it.y, 1.6, 0.0, 18) })
}

data class SpotWell(val opening: IsoPath, val wall: IsoPath, val foot: IsoPath)

object SpotTray {
    private val g = SpotGeometry
    private val r = SpotRig
    private val screwsAt = g.corners(r.TRAY, 7.0)
    val screws = screwsAt.map { r.at(Point3(it.x, it.y, r.TRAY_H)) }
    val screwRings = IsoPath.join(screwsAt.map { g.ring(it.x, it.y, 1.7, r.TRAY_H, 18) })
    val slab = g.solid(r.TRAY, 0.0, r.TRAY_H, 10, 2.4)
    val groove = g.visibleRun(r.TRAY, r.TRAY_H * 0.42, 10)
    val inlay = planOutline(insetPlan(r.TRAY, 4.2), r.TRAY_H, r.P, 10)
    val wells = r.WELLS.map(::wellOf)
    private val rulers = r.GAPS.indices.map(::rulerOf)
    val minor = g.lines(rulers.flatMap { it.first })
    val major = g.lines(rulers.flatMap { it.second })
    val datum =
        g.lines(
            r.GAPS.indices.map { row ->
                listOf(
                    Point3(r.rowX(row) - r.WELL_HALF - 3.4, r.rowY(row) + r.DATUM_Y, r.TRAY_H),
                    Point3(r.rowX(row) - r.WELL_HALF - 0.8, r.rowY(row) + r.DATUM_Y, r.TRAY_H),
                )
            }
        )
    val gapMarks =
        r.GAPS.mapIndexed { row, gap ->
            r.at(
                Point3(
                    r.rowX(row) + r.WELL_HALF + 2.2 + 4.6 + 2.4,
                    r.rowY(row) + r.DATUM_Y + gap,
                    r.TRAY_H,
                )
            )
        }
    val counts =
        r.GAPS.indices.flatMap { row ->
            (0..row).map { dot ->
                r.at(Point3(r.rowX(row) + (dot - row / 2.0) * 3.2, countY(row), r.TRAY_H))
            }
        }

    private fun countY(row: Int) =
        r.rowY(row) + r.WELL_D + (if (row < 3) r.ROW_RIM else r.BORDER) / 2

    private fun wellOf(plan: Plan): SpotWell {
        val points = g.clean(ringOf(plan, 12))
        val back = g.runOf(points, false)
        val wall =
            if (back.size > 1)
                pathOf(
                    back.map { r.at(Point3(it.x, it.y, r.TRAY_H)) } +
                        back.reversed().map { r.at(Point3(it.x, it.y, r.FLOOR)) },
                    true,
                )
            else IsoPath.Empty
        return SpotWell(onTop(points, r.TRAY_H, r.P, true), wall, onTop(back, r.FLOOR, r.P))
    }

    private fun rulerOf(row: Int): Pair<List<List<Point3>>, List<List<Point3>>> {
        val x0 = r.rowX(row) + r.WELL_HALF + 2.2
        val minor = ArrayList<List<Point3>>()
        val major = ArrayList<List<Point3>>()
        for (step in 0..48 step 2) {
            val y = r.rowY(row) + r.DATUM_Y + step
            (if (step % 8 == 0) major else minor).add(
                listOf(
                    Point3(x0, y, r.TRAY_H),
                    Point3(x0 + if (step % 8 == 0) 4.6 else 2.2, y, r.TRAY_H),
                )
            )
        }
        return minor to major
    }
}
