// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.Knot
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Projection
import dev.sebastiano.anatomy.kit.audit.Piece
import dev.sebastiano.anatomy.kit.audit.SpringSpec
import dev.sebastiano.anatomy.kit.boxCorners
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Fig 6, after the original's examples/raptor-engine: a Raptor 2 hanging in a vertical test stand.
 * This file holds the build's constants, camera and assemblies; RaptorStand.kt draws the stand
 * behind the engine, RaptorChamber.kt, RaptorPumps.kt, RaptorPlumbing.kt and RaptorHarness.kt the
 * engine itself, in the original's order, through the kit's recording helpers (`PartRecorder`).
 */
internal object Raptor {
    const val DEG = PI / 180
    const val W = 932.0
    const val H = 900.0
    const val AZIMUTH = 38.0
    const val ELEVATION = 27.0
    const val Z_EXIT = 172.0
    const val TOP = 310.0
    const val BEAM_Z = Z_EXIT + TOP + 23
    const val BEAM_H = 28.0
    val DECK = Plan(-150.0, -150.0, 300.0, 300.0, 10.0)
    const val DECK_H = 15.0
    const val FOOT_H = 8.0
    const val COLUMN = 24.0
    val COLUMN_AT = listOf(Floor(-124.0, 124.0), Floor(124.0, -124.0))
    const val CONE_TIP = 32.0
    const val CHAMBER_BAR = 300.0
    const val SEA_LEVEL_TF = 230.0
    const val EXIT_M = 1.3
    val BACK_TF = 101325 * PI * ((EXIT_M / 2) * (EXIT_M / 2)) / 9806.65

    private val SQRT2 = sqrt(2.0)

    val P: Projection =
        fitProjection(
            boxCorners(DECK, -DECK_H - FOOT_H, 0.0) +
                listOf(
                    Point3(COLUMN_AT[0].x - COLUMN, COLUMN_AT[0].y + COLUMN, BEAM_Z + BEAM_H),
                    Point3(COLUMN_AT[1].x + COLUMN, COLUMN_AT[1].y - COLUMN, BEAM_Z + BEAM_H),
                    Point3(-190 / SQRT2, 190 / SQRT2, BEAM_Z + BEAM_H),
                    Point3(190 / SQRT2, -190 / SQRT2, BEAM_Z + BEAM_H),
                ),
            W,
            H,
            pad = 26.0,
            azimuth = AZIMUTH,
            elevation = ELEVATION,
        )
    val V: Point3 = viewOf(P)
    val KS: Double = scaleOf(P)
    val FADE = 0.04 to 0.42

    private val UP = Point3(0.0, 0.0, 1.0)
    private val EAST = Point3(1.0, 0.0, 0.0)
    private val NORTH = Point3(0.0, 1.0, 0.0)
    val ENGINE: Frame = frameOf(Point3(0.0, 0.0, Z_EXIT), UP, EAST, NORTH)
    const val FTP_DEG = -20.0
    val FTP_AT = Floor(72 * cos(FTP_DEG * DEG), 72 * sin(FTP_DEG * DEG))
    val FTP: Frame = frameOf(Point3(FTP_AT.x, FTP_AT.y, Z_EXIT), UP, EAST, NORTH)
    val DECKF: Frame = frameOf(Point3(0.0, 0.0, 0.0), UP, EAST, NORTH)
    /** The gimbal actuators' arms and the lug angle each lands on. */
    val LUGS = listOf(135.0 to 185.0, -45.0 to -45.0)

    val STAGGER =
        listOf(
            "nozzle",
            "downcomer",
            "controller",
            "plumbing",
            "lox",
            "ftp",
            "hotgas",
            "chamber",
            "orpb",
            "otp",
            "gimbal",
            "stand",
        )
    val SPRING = SpringSpec(stagger = 0.035, stiffness = 64.0, damping = 14.2, substeps = 4)
    /** The legend's order, which the arrow keys walk. */
    val ORDER =
        listOf(
            "gimbal",
            "otp",
            "orpb",
            "lox",
            "controller",
            "chamber",
            "hotgas",
            "ftp",
            "downcomer",
            "plumbing",
            "nozzle",
        )

    fun polar(deg: Double, r: Double, s: Double, centre: Floor = Floor(0.0, 0.0)) =
        Point3(centre.x + r * cos(deg * DEG), centre.y + r * sin(deg * DEG), Z_EXIT + s)

    fun dir(deg: Double) = Point3(cos(deg * DEG), sin(deg * DEG), 0.0)

    fun out(deg: Double, r: Double, z: Double) = Point3(r * cos(deg * DEG), r * sin(deg * DEG), z)

    fun ftp(deg: Double, r: Double, s: Double) = pointOf(FTP, s, r, deg * DEG)

    fun ex(frame: Frame, s: Double) = Point3(frame.o.x, frame.o.y, frame.o.z + s)

    fun at(point: Point3) = iso(point, P)

    /** The assemblies, fresh for each build: the build fills in two of their readouts. */
    fun pieces(): Map<String, Piece> =
        linkedMapOf(
            "gimbal" to
                Piece("Gimbal and thrust puck", "gimbal · carries 230 tf into the stand", Point3(0.0, 0.0, 0.0)),
            "otp" to Piece("Oxygen turbopump", "oxygen turbopump · spun by oxygen-rich gas", Point3(0.0, 0.0, -26.0)),
            "orpb" to Piece("Oxygen-rich preburner", "oxygen-rich preburner · all the oxygen", Point3(0.0, 0.0, -52.0)),
            "lox" to Piece("Oxygen duct and main valve", "oxygen duct · pump to preburner", out(166.0, 52.0, -12.0)),
            "chamber" to
                Piece("Injector and main chamber", "main chamber · 300 bar · no igniter", Point3(0.0, 0.0, -80.0)),
            "nozzle" to Piece("Nozzle", "nozzle · 1.3 m exit · methane cooled", Point3(0.0, 0.0, -120.0)),
            "ftp" to
                Piece(
                    "Methane turbopump and fuel-rich preburner",
                    "methane turbopump · spun by fuel-rich gas",
                    out(FTP_DEG, 70.0, -46.0),
                ),
            "hotgas" to
                Piece("Hot-gas manifold", "hot-gas manifold · fuel-rich gas to the injector", out(FTP_DEG, 36.0, -62.0)),
            "downcomer" to
                Piece("Downcomer and main fuel valve", "downcomer · methane to the cooling jacket", out(40.0, 66.0, -84.0)),
            "plumbing" to Piece("Plumbing", "plumbing", Point3(0.0, 0.0, 0.0)),
            "controller" to Piece("Engine controller and harness", "engine controller", out(70.0, 70.0, -28.0)),
            "stand" to Piece("Stand", "", Point3(0.0, 0.0, 0.0)),
        )

    /** A profile's radius at [s], linear between its knots. */
    fun radiusOn(profile: List<Knot>, s: Double): Double {
        var index = 1
        while (index < profile.size - 1 && profile[index].s < s) index++
        val (s0, r0) = profile[index - 1]
        val (s1, r1) = profile[index]
        val span = (s1 - s0).let { if (it == 0.0) 1.0 else it }
        return r0 + (r1 - r0) * (s - s0) / span
    }

    fun slopeOn(profile: List<Knot>, s: Double) =
        (radiusOn(profile, s + 0.01) - radiusOn(profile, s - 0.01)) / 0.02

    /** A point on a turned body and its outward normal there. */
    class OnBody(val at: Point3, val normal: Point3)

    fun onBody(frame: Frame, profile: List<Knot>, deg: Double, s: Double) =
        OnBody(
            pointOf(frame, s, radiusOn(profile, s), deg * DEG),
            unit3(sub3(radialOf(frame, deg * DEG), mul3(frame.a, slopeOn(profile, s)))),
        )
}
