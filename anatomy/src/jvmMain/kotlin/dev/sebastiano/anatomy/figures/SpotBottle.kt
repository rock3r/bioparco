// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.circleRing
import dev.sebastiano.anatomy.kit.onTop
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object SpotBottle {
    private val g = SpotGeometry
    private val r = SpotRig
    private val bx = r.BOTTLE.x
    private val by = r.BOTTLE.y
    private const val COASTER_TOP = SpotRig.DECK_H + 2
    private const val BODY_TOP = COASTER_TOP + 32
    private const val SHOULDER_TOP = BODY_TOP + 2.2
    private const val NECK_TOP = SHOULDER_TOP + 2.2
    private const val COLLAR_TOP = NECK_TOP + 2.6
    private const val CAP_TOP = COLLAR_TOP + 11
    private const val BULB_TOP = CAP_TOP + 10
    val coaster = g.cyl(bx, by, 25.0, r.DECK_H, COASTER_TOP - r.DECK_H, 44)
    val coasterRing = g.ring(bx, by, 22.6, COASTER_TOP, 40)
    val coasterDots =
        (0..3).map {
            val angle = PI / 4 + PI / 2 * it
            r.at(Point3(bx + 23.8 * cos(angle), by + 23.8 * sin(angle), COASTER_TOP))
        }
    val body = g.cyl(bx, by, 19.0, COASTER_TOP, BODY_TOP - COASTER_TOP, 44)
    val level = onTop(g.runOf(g.clean(circleRing(bx, by, 19.0, 44)), true), COASTER_TOP + 21, r.P)
    val foot = onTop(g.runOf(g.clean(circleRing(bx, by, 19.0, 44)), true), COASTER_TOP + 2.4, r.P)
    val shine = shineAt(19.0, COASTER_TOP + 5, BODY_TOP - 4)
    val shoulder = g.cyl(bx, by, 15.5, BODY_TOP, SHOULDER_TOP - BODY_TOP, 40)
    val neck = g.cyl(bx, by, 11.0, SHOULDER_TOP, NECK_TOP - SHOULDER_TOP, 36)
    val collar = g.cyl(bx, by, 9.4, NECK_TOP, COLLAR_TOP - NECK_TOP, 36)
    val cap = g.cyl(bx, by, 10.0, COLLAR_TOP, CAP_TOP - COLLAR_TOP, 36)
    val capKnurl = g.knurl(bx, by, 10.0, COLLAR_TOP + 1, CAP_TOP - 1, 15)
    val capRing = g.ring(bx, by, 7.2, CAP_TOP, 32)
    val bulb = g.cyl(bx, by, 6.0, CAP_TOP, BULB_TOP - CAP_TOP, 32)
    val bulbShine = shineAt(6.0, CAP_TOP + 2, BULB_TOP - 2)
    val bulbRing = g.ring(bx, by, 3.4, BULB_TOP, 24)
    val tip = r.at(Point3(bx, by, BULB_TOP))

    private fun shineAt(radius: Double, z0: Double, z1: Double) =
        g.line(
            listOf(z0, z1).map {
                Point3(
                    bx + radius * cos(g.viewAngle + 0.95),
                    by + radius * sin(g.viewAngle + 0.95),
                    it,
                )
            }
        )
}
