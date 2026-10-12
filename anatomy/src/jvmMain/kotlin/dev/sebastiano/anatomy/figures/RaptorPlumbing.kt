// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.figures.Raptor.FTP
import dev.sebastiano.anatomy.figures.Raptor.FTP_DEG
import dev.sebastiano.anatomy.figures.Raptor.P
import dev.sebastiano.anatomy.figures.Raptor.TOP
import dev.sebastiano.anatomy.figures.Raptor.Z_EXIT
import dev.sebastiano.anatomy.figures.Raptor.dir
import dev.sebastiano.anatomy.figures.Raptor.ftp
import dev.sebastiano.anatomy.figures.Raptor.out
import dev.sebastiano.anatomy.figures.Raptor.polar
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.audit.AuditShape
import dev.sebastiano.anatomy.kit.audit.BossOptions
import dev.sebastiano.anatomy.kit.audit.PipeOptions
import dev.sebastiano.anatomy.kit.audit.PipeRoute
import dev.sebastiano.anatomy.kit.audit.ShapeMeta
import dev.sebastiano.anatomy.kit.audit.disc
import dev.sebastiano.anatomy.kit.audit.pipe
import dev.sebastiano.anatomy.kit.audit.sdf
import dev.sebastiano.anatomy.kit.audit.valveOn
import dev.sebastiano.anatomy.kit.disc
import dev.sebastiano.anatomy.kit.dotsOf
import dev.sebastiano.anatomy.kit.frameAlong
import dev.sebastiano.anatomy.kit.len3
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.sub3
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

/*
 * The ducts (hot-gas manifold, downcomer with the main fuel valve, oxygen duct with the main
 * oxygen valve) and the feed, purge, pressurisation and igniter lines between the assemblies.
 */

internal const val LOX_S = 233.75
internal const val LOX_DEG = 150.0
private const val HOT_S = 199.4

/** The original's `line`: a plumbing pipe that moves by [move], counted among the lines. */
internal fun RaptorBuild.line(points: List<Point3>, r: Double, move: Point3, name: String, options: PipeOptions = PipeOptions()): PipeRoute {
    lines += points
    val flangeR = if (options.flangeR == PipeOptions().flangeR) listOf(1.7) else options.flangeR
    return parts.pipe("plumbing", points, r, name, options.copy(move = move, flangeR = flangeR, maxLength = 16.0))
}

/** Points on a ring of radius [r] at height [s], from [from] to [to] degrees. */
internal fun ringAt(s: Double, r: Double, from: Double, to: Double, steps: Int = 0): List<Point3> {
    val count = if (steps > 0) steps else max(2, ceil(abs(to - from) / 12).toInt())
    return List(count + 1) { index -> polar(from + (to - from) * index / count, r, s) }
}

/** The run of a gapped tube nearest [point]: the host a pad on the tube sits on. */
internal fun tubeAt(runs: List<AuditShape>, point: Point3): AuditShape =
    runs.minBy { abs(sdf(it, point)) }

internal fun RaptorBuild.ducts() {
    val hotA = parts.boss("ftp", ftp(160.0, 18.4, HOT_S), dir(160.0), 5.2, 1.0, BossOptions(host = listOf(ftpTurbine, ftpRing), ring = false, name = "ftp.hot-pad"))
    val hotB = parts.boss("chamber", polar(FTP_DEG, 33.4, HOT_S), dir(FTP_DEG), 5.2, 1.0, BossOptions(host = listOf(head), ring = false, name = "chamber.hot-pad"))
    parts.pipe("hotgas", listOf(hotA, hotB), 3.6, "hotgas.duct", PipeOptions(flangeR = listOf(1.3), bands = listOf(0.5), maxLength = 12.0, free = true to true))
    downcomer()
    val loxFace = parts.boss("orpb", polar(LOX_DEG, 22.2, LOX_S), dir(LOX_DEG), 7.0, 1.2, BossOptions(host = listOf(orpbBody), ring = false, name = "orpb.lox-pad"))
    val route =
        listOf(
            loxStart,
            add3(loxStart, mul3(voluteTan, 6.0)),
            polar(140.0, 52.0, 270.0),
            polar(140.0, 52.0, LOX_S),
            polar(LOX_DEG, 40.0, LOX_S),
            loxFace,
        )
    lox = parts.pipe("lox", route, 5.6, "lox.duct", PipeOptions(bend = 1.5, flangeR = listOf(OTP_FLANGE_R / 5.6, 1.2), valve = 0.5 to 4.2, maxLength = 14.0, free = true to true))
    loxRun = recorder.tubes.filter { it.owner == lox.owner }
    mov = parts.valveOn("lox", lox, 0.5, 4.2, side = dir(110.0))
}

private fun RaptorBuild.downcomer() {
    downElbow = add3(fuelStart, mul3(fvTan, 30.0))
    val stub = listOf(MANIFOLD_S + sqrt(TEE * TEE - DOWN_R * DOWN_R), MANIFOLD_S + 7.6, MANIFOLD_S + 8.8)
    val f = frameAlong(polar(PORTS[0], manifoldR, 0.0), Point3(0.0, 0.0, 1.0))
    val owner = "nozzle.manifold"
    val stubShape = recorder.solid(disc(f, stub[0], stub[1], DOWN_R, ShapeMeta(name = "nozzle.stub", owner = owner)))
    parts.put("nozzle", polar(PORTS[0], manifoldR, (stub[0] + stub[1]) / 2), listOf(stubShape), name = "nozzle.stub") {
        solid(disc(stub[0], stub[1], DOWN_R, f, P), Tone.Hi)
    }
    val flange = recorder.solid(disc(f, stub[1], stub[2], DOWN_R * 1.4, ShapeMeta(name = "nozzle.port-flange", owner = owner)))
    parts.put("nozzle", polar(PORTS[0], manifoldR, (stub[1] + stub[2]) / 2), listOf(flange), name = "nozzle.port-flange") {
        solid(disc(stub[1], stub[2], DOWN_R * 1.4, f, P), Tone.Hi)
        dots(dotsOf(f, stub[2], DOWN_R * 1.2, 8, P, all = true), 0.38, Tone.Mid)
    }
    val route =
        listOf(
            fuelStart,
            downElbow,
            Point3(downElbow.x, downElbow.y, Z_EXIT + 196),
            polar(12.0, 60.0, 152.0),
            polar(12.0, manifoldR, 130.0),
            polar(12.0, manifoldR, stub[2]),
        )
    val options =
        PipeOptions(
            bend = 1.6,
            flangeR = listOf(FTP_FLANGE_R / DOWN_R, 1.4),
            bands = listOf(0.5, 0.58, 0.66),
            bellows = Triple(0.24, 0.3, 5),
            valve = 12.4 to 3.6,
            maxLength = 18.0,
            free = true to true,
        )
    val down = parts.pipe("downcomer", route, DOWN_R, "downcomer.duct", options)
    mfv = parts.valveOn("downcomer", down, 12.4, 3.6)
}

internal fun RaptorBuild.feedLines() {
    jacketLines()
    val tapAt = polar(LOX_DEG, 34.0, LOX_S - 5.6)
    val loxTap = parts.boss("lox", tapAt, Point3(0.0, 0.0, -1.0), 3.1, 1.0, BossOptions(host = listOf(tubeAt(loxRun, tapAt)), ring = false, name = "lox.tap-pad"))
    val feedAt = Raptor.onBody(FTP, RaptorBuild.FTP_LOW_PROFILE, 112.0, 172.6)
    val feed = parts.boss("ftp", feedAt.at, feedAt.normal, 3.1, 0.8, BossOptions(host = listOf(ftpLow), ring = false, name = "ftp.feed-pad"))
    val tap =
        listOf(loxTap, polar(LOX_DEG, 34.0, 222.0), polar(LOX_DEG - 4, 41.5, 210.5)) +
            ringAt(199.5, 41.5, LOX_DEG - 8, 2.0) +
            listOf(polar(-6.0, 50.0, 192.0), add3(feed, mul3(feedAt.normal, 11.0)), feed)
    line(tap, 2.1, out(86.0, 50.0, -16.0), "plumbing.lox-to-frpb", PipeOptions(bands = listOf(0.18, 0.4, 0.62, 0.84), flangeR = listOf(1.4), free = true to true))
    val pressLoxRoute =
        listOf(pressLox, polar(200.0, 33.0, 252.5), polar(198.0, 36.0, 262.0), polar(188.0, 37.0, 285.0), polar(150.0, 46.0, 310.0), polar(135.0, 50.0, TOP + 13), polar(135.0, 50.0, TOP + 21))
    line(pressLoxRoute, 1.5, out(205.0, 30.0, -8.0), "plumbing.press-lox", PipeOptions(bands = listOf(0.25, 0.5, 0.75), coupling = true, free = true to true))
    val pressCh4 =
        listOf(polar(-62.0, 29.4, 182.5), polar(-62.0, 49.5, 182.5), polar(-58.0, 49.5, 222.0), polar(-52.0, 50.0, 270.0), polar(-46.0, 49.0, 300.0), polar(-45.0, 46.0, TOP + 13), polar(-45.0, 46.0, TOP + 21))
    line(pressCh4, 1.5, out(-70.0, 46.0, -30.0), "plumbing.press-ch4", PipeOptions(bands = listOf(0.3, 0.6), coupling = true, flangeR = listOf(1.5, 1.7), free = true to true))
    val dome = domeAt(-150.0, 212.0)
    val purgeChamber =
        listOf(purgeC, polar(-120.0, 34.0, 252.5), polar(-120.0, 38.0, 250.0), polar(-140.0, 40.0, 228.0), add3(dome.at, mul3(dome.normal, 4.0)), dome.at)
    line(purgeChamber, 1.1, out(-140.0, 45.0, -10.0), "plumbing.purge-chamber", PipeOptions(bands = listOf(0.5), free = true to true))
    val purgeFtp =
        listOf(purgeF, polar(-100.0, 40.0, 252.5), polar(-72.0, 44.0, 238.0), polar(-52.0, 44.5, 229.0), polar(-40.0, 52.0, 222.0), add3(ftp(200.0, 18.4, 210.0), mul3(dir(200.0), 8.0)), ftp(200.0, 18.4, 210.0))
    line(purgeFtp, 1.1, out(-80.0, 35.0, -15.0), "plumbing.purge-ftp", PipeOptions(bands = listOf(0.35, 0.7), free = true to true))
    igniterLines()
}

private fun RaptorBuild.jacketLines() {
    val move = out(-120.0, 50.0, -10.0)
    val s = 199.0
    val from = parts.boss("chamber", polar(-48.0, 33.4, s), dir(-48.0), 4.4, 1.0, BossOptions(host = listOf(head), ring = false, name = "chamber.jacket-pad"))
    val to = parts.boss("ftp", ftp(168.0, 11.6, 181.0), dir(168.0), 4.4, 1.0, BossOptions(host = listOf(ftpLow), ring = false, name = "ftp.jacket-pad"))
    val tee = polar(-48.0, 41.0, s)
    val ball = 4.2
    val d = len3(sub3(tee, from))
    val g = sqrt(ball * ball - 9)
    val jacket =
        line(
            listOf(from, polar(-48.0, 50.0, s), add3(to, mul3(dir(168.0), 13.0)), to),
            3.0,
            move,
            "plumbing.jacket-to-frpb",
            PipeOptions(bands = listOf(0.55), flangeR = listOf(1.3, 1.4), free = true to true, gaps = listOf(d - g to d + g)),
        )
    parts.ballAt("plumbing", tee, ball, Tone.Hi, move, name = "plumbing.jacket-tee", owner = jacket.owner)
    val orpbFeed = parts.boss("orpb", polar(-96.0, 22.2, 236.0), dir(-96.0), 3.2, 0.8, BossOptions(host = listOf(orpbBody), ring = false, name = "orpb.jacket-pad"))
    val route =
        listOf(
            add3(tee, Point3(0.0, 0.0, sqrt(ball * ball - 2.1 * 2.1))),
            add3(tee, Point3(0.0, 0.0, 6.0)),
            polar(-52.0, 41.0, 209.0),
            polar(-70.0, 42.0, 216.0),
            polar(-92.0, 36.0, 230.0),
            polar(-96.0, 31.0, 236.0),
            orpbFeed,
        )
    line(route, 2.1, move, "plumbing.jacket-to-orpb", PipeOptions(bands = listOf(0.5), flanges = false to true, flangeR = listOf(1.4), free = false to true))
}

private fun RaptorBuild.igniterLines() {
    val inletAt = add3(polar(LOX_DEG, 30.0, LOX_S), mul3(dir(LOX_DEG + 90), 5.6))
    val inlet = parts.boss("lox", inletAt, dir(LOX_DEG + 90), 2.0, 0.8, BossOptions(host = listOf(tubeAt(loxRun, inletAt)), ring = false, name = "lox.inlet-pad"))
    val orpb = listOf(orpbPlug, add3(orpbPlug, mul3(dir(IGNITER_DEG), 3.0)), add3(inlet, mul3(dir(LOX_DEG + 90), 3.0)), inlet)
    line(orpb, 0.9, out(160.0, 40.0, -40.0), "plumbing.igniter-orpb", PipeOptions(flangeR = listOf(2.0), bend = 3.0, free = true to true))
    val turn = ftp(62.0, 26.5, 189.0)
    val tip = parts.elbow("ftp", frpbPlug, dir(70.0), 1.9, turn, 2.3, 0.9 * 1.3, "ftp.igniter-elbow", "ftp.igniter-plug")
    val frpb = listOf(tip, turn, ftp(52.0, 24.0, 196.0), ftp(52.0, 15.0, 196.0))
    line(frpb, 0.9, pieces.getValue("ftp").move, "plumbing.igniter-frpb", PipeOptions(flangeR = listOf(1.3, 2.0), bend = 4.0))
}
