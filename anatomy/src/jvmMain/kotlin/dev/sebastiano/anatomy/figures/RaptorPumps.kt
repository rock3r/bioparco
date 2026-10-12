// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.figures.Raptor.AZIMUTH
import dev.sebastiano.anatomy.figures.Raptor.DEG
import dev.sebastiano.anatomy.figures.Raptor.ENGINE
import dev.sebastiano.anatomy.figures.Raptor.FADE
import dev.sebastiano.anatomy.figures.Raptor.FTP
import dev.sebastiano.anatomy.figures.Raptor.LUGS
import dev.sebastiano.anatomy.figures.Raptor.P
import dev.sebastiano.anatomy.figures.Raptor.dir
import dev.sebastiano.anatomy.figures.Raptor.ftp
import dev.sebastiano.anatomy.figures.Raptor.polar
import dev.sebastiano.anatomy.kit.ArtBuilder
import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.Knot
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Smooth
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.arcOf
import dev.sebastiano.anatomy.kit.audit.BossOptions
import dev.sebastiano.anatomy.kit.audit.FlangeOptions
import dev.sebastiano.anatomy.kit.audit.PipeOptions
import dev.sebastiano.anatomy.kit.audit.ShapeMeta
import dev.sebastiano.anatomy.kit.audit.StrutEnd
import dev.sebastiano.anatomy.kit.audit.TubeOptions
import dev.sebastiano.anatomy.kit.audit.TurnedOptions
import dev.sebastiano.anatomy.kit.audit.disc
import dev.sebastiano.anatomy.kit.audit.pipe
import dev.sebastiano.anatomy.kit.audit.strut
import dev.sebastiano.anatomy.kit.circleOf
import dev.sebastiano.anatomy.kit.disc
import dev.sebastiano.anatomy.kit.dotsOf
import dev.sebastiano.anatomy.kit.frameAlong
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.ribsOf
import dev.sebastiano.anatomy.kit.spiral
import dev.sebastiano.anatomy.kit.sub3
import dev.sebastiano.anatomy.kit.unit3
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/*
 * The oxygen turbopump with its volute, the gimbal and thrust puck on top, the methane turbopump
 * and fuel-rich preburner beside the chamber, and the three struts that tie the methane pump to
 * the engine.
 */

internal const val OTP_FLANGE_R = 9.4
internal const val FTP_FLANGE_R = 8.6
internal const val LUG_BALL = 3.6

internal fun lugBall(foot: Double) = polar(foot, 29.6 + LUG_BALL, 252.5)

private fun ringDots(frame: Frame, s: Double, r: Double, count: Int, size: Double): ArtBuilder.() -> Unit = {
    dots(dotsOf(frame, s, r, count, P, all = true), size, Tone.Mid)
}

private fun fadedDots(frame: Frame, s: Double, r: Double, count: Int): ArtBuilder.() -> Unit = {
    val faded = dotsOf(frame, s, r, count, P, fade = FADE)
    dots(faded.points, 0.4, Tone.Mid, faded.alphas)
}

private fun arcs(frame: Frame, r: Double, vararg at: Double) =
    at.map { arcOf(frame, it, r, P) }.reduce { a, b -> a + b }

/** A volute's end, its outlet's direction, and where the outlet flange's far face is. */
private class Outlet(val tangent: Point3, val start: Point3)

private fun RaptorBuild.volute(
    piece: String,
    frame: Frame,
    spiral: List<Point3>,
    radius: (Double) -> Double,
    reach: Double,
    flange: Pair<Double, Double>,
    touch: String,
    maxLength: Double,
): Outlet {
    val end = spiral.last()
    val tangent = unit3(sub3(end, spiral[spiral.size - 2]))
    val outlet = add3(end, mul3(tangent, reach))
    val options = TubeOptions(bias = 0.3, maxLength = maxLength, spacing = 1.0, touch = listOf(touch), ends = false to true)
    parts.tubeItems(piece, spiral + outlet, radius, "$piece.volute", options)
    val (r, thick) = flange
    parts.flangeAt(piece, add3(outlet, mul3(tangent, thick / 2)), tangent, r, thick, FlangeOptions(8, bias = 0.4, name = "$piece.outlet"))
    return Outlet(tangent, add3(outlet, mul3(tangent, thick)))
}

internal fun RaptorBuild.oxygenPump() {
    val ring = listOf(Knot(246.0, 25.0), Knot(248.0, 27.4), Knot(257.0, 27.4), Knot(259.0, 24.5))
    otpRing = parts.turned("otp", ENGINE, ring, options = TurnedOptions(extra = fadedDots(ENGINE, 257.0, 27.4, 36), inner = arcOf(ENGINE, 252.5, 27.4, P)))
    parts.band("otp", ENGINE, 259.0, 283.0, 20.6, options = TurnedOptions(name = "otp.housing", inner = arcs(ENGINE, 20.6, 263.0, 279.0)))
    val spiral = spiral(ENGINE, 271.0, 20.6 + 2.4 + 0.3, 20.6 + 6.6 + 0.3, -160 * DEG, 112 * DEG, 64)
    val outlet = volute("otp", ENGINE, spiral, { 2.4 + 4.2 * it }, 7.0, OTP_FLANGE_R to 2.6, "otp.housing", 18.0)
    voluteTan = outlet.tangent
    loxStart = outlet.start
    parts.turned("otp", ENGINE, listOf(Knot(283.0, 16.5), Knot(286.0, 16.5)), options = TurnedOptions(extra = ringDots(ENGINE, 286.0, 15.0, 16, 0.38)))
    parts.band("otp", ENGINE, 286.0, 292.0, 12.6)
    for ((_, deg) in LUGS) {
        val f = frameAlong(polar(deg, 0.0, 252.5), dir(deg))
        val start = sqrt(27.4 * 27.4 - 2.0 * 2.0)
        val meta = ShapeMeta(name = "otp.lug", seated = true, cut = listOf(otpRing))
        val lug = recorder.solid(disc(f, start, 29.6, 2.0, meta))
        parts.put("otp", polar(deg, 28.5, 252.5), listOf(lug), 0.1, name = "otp.lug") {
            solid(disc(start, 29.6, 2.0, f, P), Tone.Mid)
        }
    }
    val host = BossOptions(host = listOf(otpRing))
    otpSense = parts.boss("otp", polar(80.0, 27.4, 252.5), dir(80.0), 1.7, 2.4, host)
    purgeC = parts.boss("otp", polar(-120.0, 27.4, 252.5), dir(-120.0), 2.2, 3.0, host)
    pressLox = parts.boss("otp", polar(200.0, 27.4, 252.5), dir(200.0), 2.7, 2.0, host)
    purgeF = parts.boss("otp", polar(-100.0, 27.4, 252.5), dir(-100.0), 2.0, 2.4, host)
}

private fun puck(s: Double) = 18.4 - (if (s < 297.5) 5.0 else 3.4) * (abs(s - 297.5) / 5.5).pow(4)

private fun puckSlope(s: Double) = (if (s < 297.5) 5.0 else -3.4) * 4 * abs(s - 297.5).pow(3) / 5.5.pow(4)

internal fun RaptorBuild.gimbal() {
    val profile = List(23) { index ->
        val s = 292 + 11.0 * index / 22
        Knot(s, puck(s), index in 1 until 22)
    }
    val gimbal =
        parts.turned("gimbal", ENGINE, profile, options = TurnedOptions(smooth = Smooth(rows = 3, slope = ::puckSlope), inner = arcOf(ENGINE, 297.5, 18.4, P)))
    for (deg in listOf(AZIMUTH - 45, AZIMUTH + 45, AZIMUTH + 135, AZIMUTH - 135)) {
        parts.boss("gimbal", polar(deg, 18.4, 297.5), dir(deg), 1.9, 2.8, BossOptions(bias = 0.3, host = listOf(gimbal)))
    }
    val extra: ArtBuilder.() -> Unit = {
        dots(dotsOf(ENGINE, 310.0, 21.2, 18, P, all = true), 0.45, Tone.Mid)
        line(circleOf(ENGINE, 310.0, 15.0, P), Tone.Faint)
    }
    parts.turned("gimbal", ENGINE, listOf(Knot(303.0, 24.0), Knot(310.0, 24.0)), options = TurnedOptions(bevel = 0.8, extra = extra))
}

internal fun RaptorBuild.methanePump() {
    ftpLow = parts.turned("ftp", FTP, RaptorBuild.FTP_LOW_PROFILE, options = TurnedOptions(inner = arcs(FTP, 11.6, 180.0, 188.0)))
    ftpRing = parts.turned("ftp", FTP, listOf(Knot(194.0, 15.0), Knot(198.0, 15.0)), options = TurnedOptions(extra = ringDots(FTP, 198.0, 13.6, 18, 0.4)))
    val turbine = listOf(Knot(198.0, 16.0), Knot(200.0, 18.4), Knot(214.0, 18.4), Knot(216.0, 16.0))
    ftpTurbine = parts.turned("ftp", FTP, turbine, options = TurnedOptions(extra = fadedDots(FTP, 200.0, 18.4, 28), inner = arcOf(FTP, 207.0, 18.4, P)))
    val ribs: ArtBuilder.() -> Unit = { faded(ribsOf(FTP, 216.0, 226.0, 11.6, 18, P, fade = 0.1 to 0.55), Tone.Faint) }
    ftpNeck = parts.band("ftp", FTP, 216.0, 226.0, 11.6, options = TurnedOptions(extra = ribs))
    ftpHousing = parts.band("ftp", FTP, 226.0, 250.0, 15.2, options = TurnedOptions(name = "ftp.housing", inner = arcs(FTP, 15.2, 230.0, 246.0)))
    val spiral = spiral(FTP, 238.0, 15.2 + 2 + 0.3, 15.2 + 5.6 + 0.3, -140 * DEG, 36 * DEG, 48)
    val outlet = volute("ftp", FTP, spiral, { 2 + 3.6 * it }, 7.5, FTP_FLANGE_R to 2.4, "ftp.housing", 16.0)
    fvTan = outlet.tangent
    fuelStart = outlet.start
    parts.band("ftp", FTP, 250.0, 262.0, 10.2, options = TurnedOptions(inner = arcOf(FTP, 256.0, 10.2, P)))
    parts.turned("ftp", FTP, listOf(Knot(262.0, 13.6), Knot(265.0, 13.6)), options = TurnedOptions(bevel = 0.5, extra = ringDots(FTP, 265.0, 12.0, 12, 0.4)))
    methaneBosses()
}

private fun RaptorBuild.methaneBosses() {
    val igniter = parts.boss("ftp", ftp(70.0, 11.6, 182.0), dir(70.0), 2.4, 4.0, BossOptions(host = listOf(ftpLow), ring = false))
    frpbPlug = parts.boss("ftp", igniter, dir(70.0), 1.9, 3.6, BossOptions(name = "ftp.igniter-plug"))
    frpbExciterAt = Raptor.onBody(FTP, RaptorBuild.FTP_LOW_PROFILE, 40.0, 173.0)
    frpbExciter = parts.boss("ftp", frpbExciterAt.at, frpbExciterAt.normal, 1.3, 2.4, BossOptions(host = listOf(ftpLow), name = "ftp.exciter"))
    ftpSense = parts.boss("ftp", ftp(100.0, 18.4, 207.0), dir(100.0), 1.7, 2.6, BossOptions(host = listOf(ftpTurbine)))
    ftpPumpSense = parts.boss("ftp", ftp(110.0, 15.2, 233.0), dir(110.0), 1.7, 2.4, BossOptions(host = listOf(ftpHousing)))
    ftpPickup = parts.boss("ftp", ftp(PICKUP_DEG, 11.6, 221.0), dir(PICKUP_DEG), 1.4, 2.4, BossOptions(name = "ftp.pickup", host = listOf(ftpNeck)))
    val balance =
        listOf(
            ftp(250.0, 15.2, 229.0),
            ftp(250.0, 19.0, 229.0),
            ftp(250.0, 21.0, 221.0),
            ftp(250.0, 22.0, 212.0),
            ftp(250.0, 18.4, 212.0),
        )
    parts.pipe("ftp", balance, 1.2, "ftp.balance", PipeOptions(flangeR = listOf(1.6), bend = 3.0))
}

internal const val PICKUP_DEG = 60.0

internal fun RaptorBuild.struts() {
    val low = Raptor.onBody(FTP, RaptorBuild.FTP_LOW_PROFILE, 214.0, 172.4)
    parts.strut(
        "ftp",
        StrutEnd(ftpHousing, ftp(196.0, 15.2, 244.0), dir(196.0)),
        StrutEnd(otpRing, polar(-24.0, 27.4, 250.6), dir(-24.0)),
    )
    parts.strut(
        "ftp",
        StrutEnd(ftpTurbine, ftp(116.0, 18.4, 205.0), dir(116.0)),
        StrutEnd(injector, polar(8.0, 37.5, 207.5), dir(8.0)),
    )
    parts.strut(
        "ftp",
        StrutEnd(ftpLow, low.at, low.normal),
        StrutEnd(rings[0], polar(-56.0, 31.6, 172.6), dir(-56.0)),
    )
}
