// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.figures.Raptor.BEAM_Z
import dev.sebastiano.anatomy.figures.Raptor.DEG
import dev.sebastiano.anatomy.figures.Raptor.KS
import dev.sebastiano.anatomy.figures.Raptor.P
import dev.sebastiano.anatomy.figures.Raptor.Z_EXIT
import dev.sebastiano.anatomy.figures.Raptor.at
import dev.sebastiano.anatomy.figures.Raptor.dir
import dev.sebastiano.anatomy.figures.Raptor.ftp
import dev.sebastiano.anatomy.figures.Raptor.polar
import dev.sebastiano.anatomy.figures.Raptor.radiusOn
import dev.sebastiano.anatomy.kit.ArtBuilder
import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Free
import dev.sebastiano.anatomy.kit.Knot
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Smooth
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.audit.BossOptions
import dev.sebastiano.anatomy.kit.audit.BoxOptions
import dev.sebastiano.anatomy.kit.audit.CableOptions
import dev.sebastiano.anatomy.kit.audit.Clamp
import dev.sebastiano.anatomy.kit.audit.PipeOptions
import dev.sebastiano.anatomy.kit.audit.ShapeMeta
import dev.sebastiano.anatomy.kit.audit.Style
import dev.sebastiano.anatomy.kit.audit.ball
import dev.sebastiano.anatomy.kit.audit.body
import dev.sebastiano.anatomy.kit.audit.cable
import dev.sebastiano.anatomy.kit.audit.clampAt
import dev.sebastiano.anatomy.kit.audit.disc
import dev.sebastiano.anatomy.kit.circleOf
import dev.sebastiano.anatomy.kit.corners
import dev.sebastiano.anatomy.kit.cross3
import dev.sebastiano.anatomy.kit.disc
import dev.sebastiano.anatomy.kit.frameAlong
import dev.sebastiano.anatomy.kit.frameOf
import dev.sebastiano.anatomy.kit.insetPlan
import dev.sebastiano.anatomy.kit.jsHypot
import dev.sebastiano.anatomy.kit.jsRound
import dev.sebastiano.anatomy.kit.lathe
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.onTop
import dev.sebastiano.anatomy.kit.planOutline
import dev.sebastiano.anatomy.kit.sideSeam
import dev.sebastiano.anatomy.kit.unit3
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.sqrt

/*
 * The sense lines into the plumbing block, the pressure transducers and their pigtails, and the
 * engine controller with its connectors, its harness through four clamps, and its cables.
 */

private const val SENSE_DEG = 40.0

internal fun RaptorBuild.senseLines() {
    val c = polar(52.0, 45.0, 228.0)
    block = Plan(c.x - 6, c.y - 4, 12.0, 8.0, 1.0)
    blockZ = c.z
    val b = block
    val move = pieces.getValue("controller").move
    val extra: ArtBuilder.() -> Unit = {
        line(planOutline(insetPlan(b, 1.2), c.z + 6, P, 2), Tone.Faint)
        dots(List(4) { at(Point3(b.x + 2 + it * 2.6, b.y + b.d, c.z + 2)) }, 0.5, Tone.Hi)
    }
    parts.boxAt(
        "plumbing",
        b,
        c.z - 6,
        12.0,
        options = BoxOptions(0.5, move, name = "plumbing.block", extra = extra),
    )
    val routes = senseRoutes()
    senses = routes.size
    routes.forEachIndexed { index, points ->
        val flangeR = if (index in listOf(0, 2, 5)) listOf(1.3, 2.2) else listOf(2.2)
        line(
            points,
            0.75,
            move,
            "plumbing.sense$index",
            PipeOptions(
                flangeR = flangeR,
                bend = 4.0,
                flanges = true to false,
                free = true to false,
            ),
        )
        val end = points.last()
        if (end.z > c.z) {
            parts.put(
                "plumbing",
                end,
                listOf(ball(end, 0.5 / KS)),
                0.4,
                move,
                name = "plumbing.port$index",
            ) {
                dots(listOf(at(end)), 0.5, Tone.Hi)
            }
        }
    }
    transducers()
}

private fun RaptorBuild.senseRoutes(): List<List<Point3>> {
    val b = block
    val low = blockZ - 6
    val top = blockZ + 6
    fun below(x: Double) =
        listOf(
            Point3(x, b.y + 10.5, low - 15),
            Point3(x, b.y + 6, low - 7),
            Point3(x, b.y + 6, low),
        )
    fun above(x: Double) = listOf(Point3(x, b.y + 4, top + 6), Point3(x, b.y + 4, top))
    val domeSense = domeAt(SENSE_DEG, 219.0)
    val senseBoss =
        parts.boss(
            "chamber",
            domeSense.at,
            domeSense.normal,
            1.7,
            1.2,
            BossOptions(name = "chamber.sense-boss", host = listOf(dome)),
        )
    val manifoldRuns = recorder.tubes.filter { it.owner == "nozzle.manifold" }
    val manifoldTop = polar(64.0, manifoldR, MANIFOLD_S + MANIFOLD_TUBE)
    val manifoldSense =
        parts.boss(
            "nozzle",
            manifoldTop,
            Point3(0.0, 0.0, 1.0),
            1.8,
            0.8,
            BossOptions(
                host = listOf(tubeAt(manifoldRuns, manifoldTop)),
                ring = false,
                name = "nozzle.sense-pad",
            ),
        )
    val face =
        parts.boss(
            "chamber",
            polar(56.0, 29.4, 180.0),
            dir(56.0),
            1.3,
            1.4,
            BossOptions(host = listOf(jackets[1]), name = "chamber.sense-port"),
        )
    val tee = add3(face, mul3(dir(56.0), sqrt(1.7 * 1.7 - 1.3 * 1.3)))
    parts.ballAt(
        "chamber",
        tee,
        1.7,
        Tone.Hi,
        name = "chamber.sense-tee",
        owner = "chamber.sense-port",
    )
    val outward = unit3(add3(dir(56.0), Point3(0.0, 0.0, 1.0)))
    val tip = add3(tee, mul3(outward, sqrt(1.7 * 1.7 - (0.75 * 1.3) * (0.75 * 1.3))))
    val toBlock = Point3(b.x + 6, b.y - 6, low + 5)
    val chamberElbow =
        parts.elbow(
            "chamber",
            senseBoss,
            domeSense.normal,
            1.7,
            toBlock,
            2.1,
            0.75 * 1.3,
            "chamber.sense-elbow",
            "chamber.sense-boss",
        )
    val orpbTurn = polar(34.0, 36.0, 239.0)
    val orpbElbow =
        parts.elbow(
            "orpb",
            orpbSense,
            dir(20.0),
            2.0,
            orpbTurn,
            2.4,
            0.75 * 1.3,
            "orpb.sense-elbow",
            "orpb.sense-boss",
        )
    return listOf(
        listOf(tip, add3(tip, mul3(outward, 20.0))) + below(b.x + 4),
        listOf(
            manifoldSense,
            polar(64.0, manifoldR, 124.0),
            polar(64.0, 50.0, 150.0),
            polar(62.0, 50.0, 190.0),
        ) + below(b.x + 1.5),
        listOf(chamberElbow, toBlock, Point3(b.x + 6, b.y, low + 5)),
        listOf(ftpSense, add3(ftpSense, mul3(dir(100.0), 6.0)), polar(20.0, 62.0, 205.0)) +
            below(b.x + 9),
        listOf(otpSense, add3(otpSense, mul3(dir(80.0), 3.0)), polar(78.0, 44.0, 258.0)) +
            above(b.x + 2.5),
        listOf(orpbElbow, orpbTurn) + above(b.x + 6),
        listOf(
            ftpPumpSense,
            add3(ftpPumpSense, mul3(dir(110.0), 4.0)),
            polar(-5.0, 58.0, 258.0),
            polar(40.0, 44.0, 256.0),
        ) + above(b.x + 9.5),
    )
}

private class Transducer(
    val face: Point3,
    val axis: Point3,
    val host: dev.sebastiano.anatomy.kit.audit.AuditShape,
    val deg: Double,
    val s: Double,
    val r: Double,
)

private fun RaptorBuild.transducers() {
    val list =
        listOf(
            Transducer(jacketBoss.getValue(110), dir(110.0), rings[0], 110.0, 172.6, 31.6),
            Transducer(jacketBoss.getValue(250), dir(250.0), rings[0], 250.0, 172.6, 31.6),
            Transducer(
                domeBoss.getValue(-100).first,
                domeBoss.getValue(-100).second,
                injector,
                -100.0,
                207.5,
                37.5,
            ),
        )
    val move = pieces.getValue("chamber").move
    val meridian =
        listOf(
            Knot(0.0, 0.0),
            Knot(0.0, 1.9),
            Knot(1.2, 2.2),
            Knot(5.2, 2.2),
            Knot(6.0, 1.5),
            Knot(6.0, 0.0),
        )
    for (t in list) {
        val f = frameAlong(t.face, t.axis)
        val id = recorder.nameFor("plumbing.transducer")
        val shape = recorder.solid(body(f, meridian, ShapeMeta(name = id)))
        parts.put(
            "plumbing",
            add3(t.face, mul3(t.axis, 3.0)),
            listOf(shape),
            0.3,
            move,
            name = id,
        ) {
            solid(lathe(meridian, f, P, 20, smooth = Smooth(rows = 3)), Tone.Hi)
        }
        val tip = add3(t.face, mul3(t.axis, 6.0))
        val socket =
            parts.boss(
                "plumbing",
                polar(t.deg, t.r, t.s),
                dir(t.deg),
                1.2,
                2.0,
                BossOptions(move, host = listOf(t.host), name = "plumbing.socket"),
            )
        val out1 = add3(tip, mul3(t.axis, 3.0))
        val near = add3(socket, mul3(dir(t.deg), 3.0))
        val reach = max(jsHypot(out1.x, out1.y), jsHypot(near.x, near.y)) + 2
        val route =
            listOf(
                tip,
                out1,
                polar(t.deg, reach, out1.z - Z_EXIT),
                polar(t.deg, reach, t.s),
                near,
                socket,
            )
        parts.cable(
            "plumbing",
            route,
            "plumbing.pigtail",
            CableOptions(move, r = 0.5, bend = 1.6, shells = true to false),
        )
    }
}

private val BOX = Plan(-24.0, 46.0, 26.0, 12.0, 1.6)
private const val BOX_Z = Z_EXIT + 230
private const val BOX_H = 28.0
private val JBOX = Plan(-38.0, 46.0, 10.0, 10.0, 1.2)
private const val CONNECT_Y = 46.0 + 12.0
private const val HARNESS_S = 214.6
private const val UNDER = Z_EXIT + 222
private const val DROP_Y = CONNECT_Y + 8.5
private val CONNECTORS =
    listOf(
        Floor(BOX.x + 4, BOX_Z + 6),
        Floor(BOX.x + 10, BOX_Z + 6),
        Floor(BOX.x + 16, BOX_Z + 6),
        Floor(BOX.x + 22, BOX_Z + 6),
        Floor(BOX.x + 8, BOX_Z + 17),
        Floor(BOX.x + 18, BOX_Z + 17),
    )

private fun plug(index: Int) = Point3(CONNECTORS[index].x, CONNECT_Y + 4, CONNECTORS[index].y)

private fun laneR(lane: Int) = 37.6 + lane * 1.4

private fun laneS(lane: Int) = HARNESS_S + lane * 0.2

internal fun RaptorBuild.controller() {
    val move = pieces.getValue("controller").move
    val lid = BOX_Z + BOX_H
    val extra: ArtBuilder.() -> Unit = {
        line(planOutline(insetPlan(BOX, 2.0), lid, P, 3), Tone.Faint)
        val rules =
            List(3) { row ->
                val y = BOX.y + 3.4 + row * 2.4
                onTop(
                    listOf(Floor(BOX.x + 4, y), Floor(BOX.x + if (row == 1) 14 else 19, y)),
                    lid,
                    P,
                )
            }
        line(rules[0] + rules[1] + rules[2], Tone.Lo, free = Free.Both)
        dots(corners(BOX, 2.2).map { at(Point3(it.x, it.y, lid)) }, 0.45)
    }
    parts.boxAt(
        "controller",
        BOX,
        BOX_Z,
        BOX_H,
        Style(inner = sideSeam(BOX, lid - 6, P, 3)),
        BoxOptions(name = "controller.box", bevel = 0.8, extra = extra),
    )
    val armY = sqrt(25.5 * 25.5 - 2.0 * 2.0)
    parts.boxAt(
        "controller",
        Plan(-2.0, armY, 4.0, BOX.y - armY, 0.4),
        Z_EXIT + 242.5,
        3.0,
        options = BoxOptions(-1.0, bevel = 0.3, name = "controller.bracket"),
    )
    val jboxExtra: ArtBuilder.() -> Unit = {
        line(planOutline(insetPlan(JBOX, 1.4), Z_EXIT + 256, P, 2), Tone.Faint)
        dots(corners(JBOX, 1.8).map { at(Point3(it.x, it.y, Z_EXIT + 256)) }, 0.4)
    }
    parts.boxAt(
        "controller",
        JBOX,
        Z_EXIT + 244,
        12.0,
        options = BoxOptions(name = "controller.jbox", extra = jboxExtra),
    )
    val bracket = Plan(JBOX.x + JBOX.w, JBOX.y + 3, BOX.x - JBOX.x - JBOX.w, 4.0, 0.3)
    parts.boxAt(
        "controller",
        bracket,
        Z_EXIT + 248,
        4.0,
        options = BoxOptions(-0.5, bevel = 0.2, name = "controller.jbox-bracket"),
    )
    for ((x, z) in CONNECTORS) {
        val f =
            frameOf(
                Point3(x, CONNECT_Y, z),
                Point3(0.0, 1.0, 0.0),
                Point3(1.0, 0.0, 0.0),
                Point3(0.0, 0.0, 1.0),
            )
        val id = recorder.nameFor("controller.connector")
        val shape = recorder.solid(disc(f, 0.0, 4.0, 2.4, ShapeMeta(name = id)))
        parts.put("controller", Point3(x, CONNECT_Y + 2, z), listOf(shape), 0.4, name = id) {
            solid(disc(0.0, 4.0, 2.4, f, P), Tone.Hi)
            line(circleOf(f, 4.0, 1.2, P, 14), Tone.Faint)
        }
    }
    harness(move)
    cables(move)
}

private class Harness(
    val name: String,
    val index: Int,
    val lane: Int,
    val end: Double,
    val points: List<Point3>,
)

private fun harnessRoute(h: Harness): List<Point3> {
    val start = plug(h.index)
    val x = start.x
    val r = laneR(h.lane)
    val entry = acos(x / r) / DEG
    val count = max(2, jsRound((entry - 5 - h.end) / 10).toInt())
    val ring = ringAt(laneS(h.lane), r, entry - 5, h.end, count)
    return listOf(
        start,
        Point3(x, DROP_Y, start.z),
        Point3(x, DROP_Y, UNDER),
        Point3(x, sqrt(r * r - x * x), UNDER),
    ) + ring + h.points
}

private fun RaptorBuild.harness(move: Point3) {
    val pickup = dir(PICKUP_DEG)
    val pickupRoute =
        listOf(
            polar(0.0, 58.0, 228.0),
            add3(ftpPickup, add3(mul3(pickup, 8.0), mul3(parts.sideOn(pickup), 9.0))),
            add3(ftpPickup, mul3(pickup, 8.0)),
            ftpPickup,
        )
    val list =
        listOf(
            Harness(
                "harness-frpb",
                3,
                0,
                8.0,
                listOf(
                    polar(5.0, 50.0, 212.0),
                    polar(2.0, 66.0, 196.0),
                    ftp(96.0, 28.0, 186.0),
                    ftp(56.0, 22.0, 174.0),
                    add3(frpbExciter, mul3(frpbExciterAt.normal, 7.0)),
                    frpbExciter,
                ),
            ),
            Harness("harness-pickup", 2, 1, 12.0, pickupRoute),
            Harness(
                "harness-mfv",
                1,
                2,
                16.0,
                listOf(
                    polar(16.0, laneR(2), 268.0),
                    Point3(mfv.approach.x, mfv.approach.y, Z_EXIT + 268),
                    mfv.approach,
                    mfv.port,
                ),
            ),
            Harness(
                "cable-block",
                0,
                3,
                55.0,
                listOf(Point3(block.x + 2, block.y + 2.5, blockZ - 6)),
            ),
        )
    val degs = listOf(88.0, 70.0, 34.0, 24.0)
    val clamps = degs.map { deg ->
        val lanes = if (deg > 55) 4 else 3
        val centre =
            polar(deg, (laneR(0) + laneR(lanes - 1)) / 2, (laneS(0) + laneS(lanes - 1)) / 2)
        Clamp(
            centre,
            unit3(cross3(dir(deg), Point3(0.0, 0.0, 1.0))),
            (laneR(lanes - 1) - laneR(0)) / 2 + 1.25,
        )
    }
    for (h in list) {
        val options =
            CableOptions(
                move,
                bend = 4.0,
                bundle = "harness",
                touch = listOf("controller.clamp*"),
                clamps = clamps,
                shells = false to true,
                free = false to (h.name != "cable-block"),
            )
        parts.cable("controller", harnessRoute(h), "controller.${h.name}", options)
    }
    clamps.forEachIndexed { index, c ->
        val deg = degs[index]
        parts.clampAt("controller", c.centre, c.axis, c.radius, move, bundle = "harness")
        val s = c.centre.z - Z_EXIT
        val inner = jsHypot(c.centre.x, c.centre.y) - c.radius
        val r = radiusOn(RaptorBuild.DOME_PROFILE, s)
        parts.boss(
            "chamber",
            polar(deg, r, s),
            dir(deg),
            0.9,
            inner - r,
            BossOptions(host = listOf(dome), ring = false, name = "chamber.clamp-stud"),
        )
    }
}

private fun RaptorBuild.cables(move: Point3) {
    val over = BOX_Z + BOX_H + 4
    val p5 = plug(5)
    val orpb =
        listOf(
            p5,
            Point3(p5.x, DROP_Y, p5.z),
            Point3(p5.x, DROP_Y, over),
            Point3(p5.x, BOX.y - 4, over),
            polar(104.0, 36.0, 240.0),
            polar(100.0, 30.0, 228.5),
            orpbExciter,
        )
    parts.cable(
        "controller",
        orpb,
        "controller.cable-orpb",
        CableOptions(move, shells = false to true, free = false to true),
    )
    val movIn = add3(mov.port, mul3(dir(110.0), 9.0))
    val p4 = plug(4)
    val movRoute =
        listOf(p4, Point3(p4.x, DROP_Y, p4.z), Point3(movIn.x, DROP_Y, movIn.z), movIn, mov.port)
    parts.cable(
        "controller",
        movRoute,
        "controller.cable-mov",
        CableOptions(move, shells = false to true, free = false to true),
    )
    val jx = JBOX.x + JBOX.w / 2
    val jy = JBOX.y + JBOX.d / 2
    val jbox =
        listOf(
            Point3(jx, jy, Z_EXIT + 256),
            Point3(jx, jy, Z_EXIT + 265),
            Point3(BOX.x + 3, BOX.y + BOX.d / 2, Z_EXIT + 265),
            Point3(BOX.x + 3, BOX.y + BOX.d / 2, BOX_Z + BOX_H),
        )
    parts.cable("controller", jbox, "controller.cable-jbox", CableOptions(move, bend = 3.0))
    val press =
        listOf(Point3(JBOX.x + 3, JBOX.y + 3, Z_EXIT + 256), Point3(JBOX.x + 3, JBOX.y + 3, BEAM_Z))
    parts.cable(
        "stand",
        press,
        "stand.cable-press",
        CableOptions(shells = true to true, free = true to false),
    )
}
