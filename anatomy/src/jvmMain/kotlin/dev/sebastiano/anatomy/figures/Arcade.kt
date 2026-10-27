// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.boxCorners
import dev.sebastiano.anatomy.kit.cross3
import dev.sebastiano.anatomy.kit.fitProjection
import dev.sebastiano.anatomy.kit.frameOf
import dev.sebastiano.anatomy.kit.iso
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.pointOf
import dev.sebastiano.anatomy.kit.radialOf
import dev.sebastiano.anatomy.kit.scaleOf
import dev.sebastiano.anatomy.kit.sub3
import dev.sebastiano.anatomy.kit.unit3
import dev.sebastiano.anatomy.kit.viewOf
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.tan

/** Fig 8: the original cabinet's world, camera and mounting planes. */
internal object Arcade {
    const val W = 600.0
    const val H = 760.0
    const val XI = 60.0
    const val COIN_R = 2.426
    const val COIN_T = 0.35
    val PLATE = Plan(-98.0, -18.0, 184.0, 236.0, 10.0)
    val P =
        fitProjection(
            boxCorners(PLATE, -15.0, 0.0) +
                listOf(
                    Point3(-64.0, 0.0, 358.0),
                    Point3(64.0, 0.0, 358.0),
                    Point3(64.0, 126.0, 358.0),
                    Point3(-64.0, 126.0, 358.0),
                    Point3(-64.0, 178.0, 184.0),
                    Point3(64.0, 178.0, 184.0),
                ),
            W,
            H,
            pad = 30.0,
            azimuth = 146.0,
            elevation = 24.0,
        )
    val V = viewOf(P)
    val KS = scaleOf(P)
    val X = Point3(1.0, 0.0, 0.0)
    val Y = Point3(0.0, 1.0, 0.0)
    val Z = Point3(0.0, 0.0, 1.0)
    val XF = frameOf(Point3(0.0, 0.0, 0.0), X, Y, Z)
    val WALL =
        listOf(
            Floor(0.0, 4.0),
            Floor(156.0, 4.0),
            Floor(156.0, 168.0),
            Floor(178.0, 170.0),
            Floor(178.0, 184.0),
            Floor(136.0, 195.0),
            Floor(110.0, 306.0),
            Floor(126.0, 314.0),
            Floor(126.0, 358.0),
            Floor(0.0, 358.0),
        )
    val BD = unit3(Point3(0.0, 26.0, -111.0))
    val NB = Point3(0.0, -BD.z, BD.y)
    val C3 = Point3(0.0, 123.0, 250.5)
    val CPD = unit3(Point3(0.0, 42.0, -11.0))
    val CPN = Point3(0.0, -CPD.z, CPD.y)
    val MON_A = mul3(NB, -1.0)
    val CF = add3(C3, mul3(NB, -5.0))
    val MON = frameOf(CF, MON_A, X, BD)
    val CH = frameOf(monPoint(-44.0, 0.0, 0.0), mul3(X, -1.0), MON_A, BD)
    val JO = cpPoint(-49.0, 160.0)
    val JF = cpf(JO)
    val PIVOT = add3(JO, mul3(CPN, -4.0))
    val SWITCHES =
        listOf(
            ArcadeSwitch("left", add3(cpPoint(-53.6, 160.0), mul3(CPN, -10.4)), mul3(X, -1.0)),
            ArcadeSwitch("right", add3(cpPoint(-44.4, 160.0), mul3(CPN, -10.4)), X),
            ArcadeSwitch("up", add3(add3(JO, mul3(CPD, -4.6)), mul3(CPN, -10.4)), mul3(CPD, -1.0)),
            ArcadeSwitch("down", add3(add3(JO, mul3(CPD, 4.6)), mul3(CPN, -10.4)), CPD),
        )
    val CHUTE_SLOPE = tan(30 * PI / 180)
    val WIRE_FROM = Point3(-50.7, 124.0, chuteZ(124.0) + 1.4)
    const val WIRE_LENGTH = 13.0
    val TRAY = Point3(-74.0, 188.0, 0.0)
    val SPK_N = unit3(Point3(0.0, -8.0, 16.0))
    val SPK_C = Point3(22.0, 115.0, 313.0)
    val SPK_F = dev.sebastiano.anatomy.kit.frameAlong(SPK_C, SPK_N)
    val ANODE_AT = pointOf(MON, 30.0, funnel(30.0), -2.35)
    val ANODE_N = unit3(sub3(radialOf(MON, -2.35), mul3(MON.a, funnelSlope(30.0))))

    fun at(p: Point3) = iso(p, P)

    fun rect(u0: Double, u1: Double, v0: Double, v1: Double) =
        listOf(Floor(u0, v0), Floor(u1, v0), Floor(u1, v1), Floor(u0, v1))

    fun cpTop(y: Double) = 195 + (y - 136) * (184 - 195) / (178 - 136)

    fun cpPoint(x: Double, y: Double, s: Double = 0.0) = add3(Point3(x, y, cpTop(y)), mul3(CPN, s))

    fun cpf(o: Point3): Frame = frameOf(o, CPN, X, CPD)

    fun monPoint(u: Double, w: Double, s: Double) =
        add3(add3(add3(CF, mul3(X, u)), mul3(BD, w)), mul3(MON_A, s))

    fun chPoint(s: Double, w: Double, out: Double = 0.0) =
        add3(
            add3(add3(monPoint(-44.0, 0.0, 0.0), mul3(MON_A, s)), mul3(BD, w)),
            Point3(-out, 0.0, 0.0),
        )

    fun bezelPoint(u: Double, w: Double, off: Double = 0.0) =
        add3(add3(add3(C3, Point3(u, 0.0, 0.0)), mul3(BD, w)), mul3(NB, off))

    fun funnel(s: Double) = 3 + 31 * maxOf(0.0, (64 - s) / 55).pow(1.4)

    fun funnelSlope(s: Double) = -31 * 1.4 * maxOf(1e-6, (64 - s) / 55).pow(0.4) / 55

    fun chuteZ(y: Double) = 82 - CHUTE_SLOPE * (148 - y)
}

internal class ArcadeSwitch(val id: String, val c: Point3, val out: Point3) {
    val side = cross3(Arcade.CPN, out)
}
