// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.extrude
import dev.sebastiano.anatomy.kit.insetPlan
import dev.sebastiano.anatomy.kit.onTop
import dev.sebastiano.anatomy.kit.planOutline
import dev.sebastiano.anatomy.kit.topMatrix
import kotlin.math.PI
import kotlin.math.atan2

object SpotDeck {
    private val g = SpotGeometry
    private val r = SpotRig
    private val bandIn = r.SCALE_R - 2
    private val bandOut = r.SCALE_R + 10
    private val bandSplit = atan2(r.VIEW.y, r.VIEW.x) * 180 / PI
    private val screwsAt = g.corners(r.DECK, 6.0)
    private val stopsAt = r.GAPS.map { g.polar(r.STOP_R, r.angleOf(it), r.DECK_H) }
    val slab = g.solid(r.DECK, 0.0, r.DECK_H, 8, 1.6)
    val inlay = planOutline(insetPlan(r.DECK, 3.4), r.DECK_H, r.P, 8)
    val screws = screwsAt.map { r.at(Point3(it.x, it.y, r.DECK_H)) }
    val screwRings = IsoPath.join(screwsAt.map { g.ring(it.x, it.y, 1.5, r.DECK_H, 18) })
    val guide =
        onTop(
            (0..60).map {
                val p = g.polar(r.STOP_R + 3, r.SWEEP * it / 60, r.DECK_H)
                Floor(p.x, p.y)
            },
            r.DECK_H,
            r.P,
        )
    val stops = stopsAt.map { g.cyl(it.x, it.y, 1.3, r.DECK_H, 1.8, 16) }
    val stopTops = stopsAt.map { r.at(Point3(it.x, it.y, r.DECK_H + 1.8)) }
    val bandBack =
        extrude(
            g.sector(bandIn, bandOut, -7.0, bandSplit, 24),
            r.DECK_H,
            r.STACK_TOP - r.DECK_H,
            r.P,
            convex = false,
        )
    val bandFront =
        extrude(
            g.sector(bandIn, bandOut, bandSplit, r.SWEEP + 7, 64),
            r.DECK_H,
            r.STACK_TOP - r.DECK_H,
            r.P,
            convex = false,
        )
    val seam = r.at(g.polar((bandIn + bandOut) / 2, bandSplit, r.STACK_TOP))
    val bandLine =
        onTop(
            g.sector(bandOut - 1.6, bandOut - 1.6, -7 + 1.2, r.SWEEP + 7 - 1.2, 96).take(97),
            r.STACK_TOP,
            r.P,
        )
    private val ticks = scaleTicks()
    val minor = ticks.first
    val major = ticks.second
    val gapDots = r.GAPS.map { r.at(g.polar(bandOut - 3.6, r.angleOf(it), r.STACK_TOP)) }
    val ends = listOf(0.0, r.SWEEP).map { r.at(g.polar(bandOut - 3.6, it, r.STACK_TOP)) }
    val label = topMatrix(Floor(r.PIVOT.x - 13, r.PIVOT.y + 150), r.DECK_H, r.P)
    val washer = g.cyl(r.PIVOT.x, r.PIVOT.y, 9.4, r.DECK_H, r.WASHER_H, 32)
    val cap = g.cyl(r.PIVOT.x, r.PIVOT.y, 8.2, r.STACK_TOP, 0.8, 32)
    val nut = g.cyl(r.PIVOT.x, r.PIVOT.y, 5.8, r.STACK_TOP + 0.8, 4.2, 28)
    val nutKnurl = g.knurl(r.PIVOT.x, r.PIVOT.y, 5.8, r.STACK_TOP + 0.8, r.STACK_TOP + 5, 13)
    val nutRing = g.ring(r.PIVOT.x, r.PIVOT.y, 3.2, r.STACK_TOP + 5, 20)
    val pin = r.at(Point3(r.PIVOT.x, r.PIVOT.y, r.STACK_TOP + 5))

    private fun scaleTicks(): Pair<IsoPath, IsoPath> {
        val minor = ArrayList<List<Point3>>()
        val major = ArrayList<List<Point3>>()
        for (merge in 0..48 step 2) {
            val angle = r.angleOf(merge.toDouble())
            val long = merge % 8 == 0
            (if (long) major else minor).add(
                listOf(
                    g.polar(bandIn + 1.2, angle, r.STACK_TOP),
                    g.polar(bandIn + if (long) 6.0 else 3.6, angle, r.STACK_TOP),
                )
            )
        }
        return g.lines(minor) to g.lines(major)
    }
}
