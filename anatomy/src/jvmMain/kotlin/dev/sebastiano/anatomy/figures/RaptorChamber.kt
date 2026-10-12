// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.figures.Raptor.DEG
import dev.sebastiano.anatomy.figures.Raptor.ENGINE
import dev.sebastiano.anatomy.figures.Raptor.FADE
import dev.sebastiano.anatomy.figures.Raptor.KS
import dev.sebastiano.anatomy.figures.Raptor.P
import dev.sebastiano.anatomy.figures.Raptor.dir
import dev.sebastiano.anatomy.figures.Raptor.ex
import dev.sebastiano.anatomy.figures.Raptor.polar
import dev.sebastiano.anatomy.kit.Knot
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Seams
import dev.sebastiano.anatomy.kit.Smooth
import dev.sebastiano.anatomy.kit.SolidPaths
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.arcOf
import dev.sebastiano.anatomy.kit.arcPoints
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.audit.BossOptions
import dev.sebastiano.anatomy.kit.audit.ShapeMeta
import dev.sebastiano.anatomy.kit.audit.TubeOptions
import dev.sebastiano.anatomy.kit.audit.TurnedOptions
import dev.sebastiano.anatomy.kit.audit.disc
import dev.sebastiano.anatomy.kit.audit.ring
import dev.sebastiano.anatomy.kit.audit.solid
import dev.sebastiano.anatomy.kit.audit.tube
import dev.sebastiano.anatomy.kit.bandsOf
import dev.sebastiano.anatomy.kit.circleOf
import dev.sebastiano.anatomy.kit.disc
import dev.sebastiano.anatomy.kit.dotsOf
import dev.sebastiano.anatomy.kit.frameAlong
import dev.sebastiano.anatomy.kit.jsHypot
import dev.sebastiano.anatomy.kit.pathLength
import dev.sebastiano.anatomy.kit.pointOf
import dev.sebastiano.anatomy.kit.ringBand
import dev.sebastiano.anatomy.kit.sub3
import dev.sebastiano.anatomy.kit.unit3
import kotlin.math.pow
import kotlin.math.sqrt

/*
 * The nozzle with its cooling manifold, the throat and main chamber with their jackets, rings,
 * injector and dome, and the oxygen-rich preburner above them.
 */

internal fun bell(s: Double) = 36 + 29 * (1 - (s / 120).pow(1.6))

internal fun bellSlope(s: Double) = -29 * 1.6 * (s / 120).pow(0.6) / 120

private val BELL_AT = listOf(3.2, 14.0, 28.0, 44.0, 60.0, 76.0, 92.0, 106.0, 116.0)
private const val BELL_STEPS = 96
private const val BELL_SKIRT = 7
internal const val MANIFOLD_S = 109.0
internal const val MANIFOLD_TUBE = 4.6
internal const val TEE = 6.6
internal const val DOWN_R = 4.6
internal val PORTS = listOf(12.0, 132.0, 252.0)

internal fun RaptorBuild.nozzle() {
    parts.turned(
        "nozzle",
        ENGINE,
        listOf(Knot(0.0, 66.4), Knot(3.2, 66.4)),
        options = TurnedOptions(bevel = 0.6, steps = BELL_STEPS),
    )
    bellBands()
    parts.put(
        "nozzle",
        ex(ENGINE, 118.0),
        listOf(
            recorder.solid(ring(ENGINE, 116.0, 120.0, 30.5, 38.0, ShapeMeta(name = "nozzle.lip")))
        ),
        name = "nozzle.lip",
    ) {
        solid(ringBand(116.0, 120.0, 30.5, 38.0, ENGINE, P), Tone.Hi)
        void(circleOf(ENGINE, 120.0, 30.5, P))
        line(arcOf(ENGINE, 120.0, 30.5, P, least = -1.0, inward = true), Tone.Lo)
        dots(dotsOf(ENGINE, 120.0, 36.4, 40, P, all = true), 0.38, Tone.Mid)
    }
    manifold()
    for (deg in listOf(60.0, 200.0)) parts.boss(
        "nozzle",
        polar(deg, bell(40.0), 40.0),
        dir(deg),
        1.8,
        2.6,
    )
    parts.boss("nozzle", polar(30.0, bell(70.0), 70.0), dir(30.0), 1.6, 2.2)
    lip = arcOf(ENGINE, 0.0, 66.4, P, least = -0.05)
    for (deg in listOf(96.0, 156.0)) {
        val normal = unit3(sub3(dir(deg), Point3(0.0, 0.0, bellSlope(88.0))))
        parts.boss(
            "nozzle",
            polar(deg, bell(88.0) - 0.15, 88.0),
            normal,
            2.4,
            3.2,
            BossOptions(name = "nozzle.pad"),
        )
    }
}

private fun RaptorBuild.bellBands() {
    val ends =
        listOf(
            Knot(BELL_AT.first(), bell(BELL_AT.first())),
            Knot(BELL_AT.last(), bell(BELL_AT.last())),
        )
    val bands =
        bandsOf(
            ends,
            BELL_AT.subList(1, BELL_AT.size - 1),
            ENGINE,
            P,
            ::bellSlope,
            ::bell,
            BELL_STEPS,
            rows = 8,
        )
    bands.bands.forEachIndexed { index, band ->
        val name = "nozzle.bell$index"
        val p = band.paths
        val paths =
            if (index < BELL_SKIRT) p
            else SolidPaths(p.fill, band.outline, p.crease, p.top, p.shades, p.bevel, p.seamless)
        val shape = recorder.solid(solid(ENGINE, band.profile, ::bell, ShapeMeta(name = name)))
        parts.put("nozzle", ex(ENGINE, (band.s0 + band.s1) / 2), listOf(shape), name = name) {
            val seam = if (index == 2 || index == 5) art { line(band.seam, Tone.Faint) } else null
            solid(paths, Tone.Hi, inner = seam)
        }
    }
    val sides = bands.sidesOf(BELL_AT.first(), BELL_AT[BELL_SKIRT], Seams.First, 72)
    val shapes = sides.points.map { tube(it, 0.4 / KS) }
    parts.put("nozzle", ex(ENGINE, BELL_AT[BELL_SKIRT] - 1), shapes, 0.2, name = "nozzle.sides") {
        line(sides.d, Tone.Hi)
    }
}

private fun bellGap(r: Double) =
    (0..240).minOf { jsHypot(it / 2.0 - MANIFOLD_S, bell(it / 2.0) - r) }

private fun RaptorBuild.manifold() {
    while (bellGap(manifoldR) < TEE + 0.3) manifoldR += 0.05
    val mr = manifoldR
    val route = arcPoints(ENGINE, MANIFOLD_S, mr, PORTS[0] * DEG, (PORTS[0] + 360) * DEG, 120)
    val length = pathLength(route)
    val teeGap = sqrt(TEE * TEE - MANIFOLD_TUBE * MANIFOLD_TUBE)
    fun portAt(deg: Double) = (deg - PORTS[0]) / 360 * length
    val gaps =
        listOf(0.0 to teeGap, length - teeGap to length) +
            PORTS.drop(1).map { portAt(it) - teeGap to portAt(it) + teeGap }
    val options =
        TubeOptions(
            maxLength = 22.0,
            spacing = 1.2,
            closed = true,
            touch = listOf("nozzle.bell*"),
            gaps = gaps,
        )
    parts.tubeItems("nozzle", route, MANIFOLD_TUBE, "nozzle.manifold", options)
    for (deg in PORTS) {
        parts.ballAt(
            "nozzle",
            polar(deg, mr, MANIFOLD_S),
            TEE,
            Tone.Hi,
            name = "nozzle.tee",
            owner = "nozzle.manifold",
        )
    }
    for (deg in PORTS.drop(1)) {
        val f = frameAlong(polar(deg, mr, MANIFOLD_S), dir(deg))
        val s0 = sqrt(TEE * TEE - 2.6 * 2.6)
        val stub =
            recorder.solid(
                disc(f, s0, 7.6, 2.6, ShapeMeta(name = "nozzle.stub", owner = "nozzle.manifold"))
            )
        parts.put("nozzle", pointOf(f, 7.0, 0.0, 0.0), listOf(stub), name = "nozzle.stub") {
            solid(disc(s0, 7.6, 2.6, f, P), Tone.Hi)
        }
        val blind = recorder.solid(disc(f, 7.6, 9.4, 4.4, ShapeMeta(name = "nozzle.blind")))
        parts.put("nozzle", pointOf(f, 8.5, 0.0, 0.0), listOf(blind), name = "nozzle.blind") {
            solid(disc(7.6, 9.4, 4.4, f, P), Tone.Hi)
            dots(dotsOf(f, 9.4, 3.4, 6, P, all = true), 0.38, Tone.Mid)
        }
    }
    for (deg in listOf(72.0, 192.0, 312.0)) {
        val f = frameAlong(polar(deg, bell(MANIFOLD_S) - 0.1, MANIFOLD_S), dir(deg))
        val reach = mr - MANIFOLD_TUBE + 0.12 - (bell(MANIFOLD_S) - 0.1)
        val pad =
            recorder.solid(
                disc(f, 0.0, reach, 0.9, ShapeMeta(name = "nozzle.pad", owner = "nozzle.manifold"))
            )
        parts.put("nozzle", pointOf(f, reach / 2, 0.0, 0.0), listOf(pad), name = "nozzle.pad") {
            solid(disc(0.0, reach, 0.9, f, P), Tone.Mid)
        }
    }
}

private fun throat(s: Double): Double {
    val k = (if (s < 146) 0.997 else 0.95) * (s - 146)
    return sqrt(24.2 * 24.2 + k * k)
}

private fun throatSlope(s: Double) = (if (s < 146) 0.994 else 0.9025) * (s - 146) / throat(s)

private fun ringDots(
    s: Double,
    r: Double,
    count: Int,
    size: Double,
): dev.sebastiano.anatomy.kit.ArtBuilder.() -> Unit = {
    dots(dotsOf(ENGINE, s, r, count, P, all = true), size, Tone.Mid)
}

internal fun RaptorBuild.chamber() {
    parts.turned(
        "chamber",
        ENGINE,
        listOf(Knot(120.0, 38.0), Knot(123.5, 38.0)),
        options = TurnedOptions(extra = ringDots(123.5, 36.4, 40, 0.38), bevel = 0.5),
    )
    val ss = List(15) { 123.5 + 22.5 * it / 15 } + List(11) { 146 + 16.0 * it / 10 }
    val profile = ss.mapIndexed { index, s -> Knot(s, throat(s), index > 0 && index < ss.size - 1) }
    parts.turned(
        "chamber",
        ENGINE,
        profile,
        options =
            TurnedOptions(
                smooth = Smooth(rows = 5, slope = ::throatSlope),
                inner = arcOf(ENGINE, 146.0, throat(146.0), P),
            ),
    )
    jackets =
        listOf(
                Triple(162.0, 171.0, 167.0),
                Triple(174.2, 186.0, 180.0),
                Triple(189.2, 194.0, 191.6),
            )
            .map { (s0, s1, seam) ->
                parts.band(
                    "chamber",
                    ENGINE,
                    s0,
                    s1,
                    29.4,
                    options =
                        TurnedOptions(name = "chamber.jacket", inner = arcOf(ENGINE, seam, 29.4, P)),
                )
            }
    rings =
        listOf(171.0, 186.0).map { s ->
            val extra: dev.sebastiano.anatomy.kit.ArtBuilder.() -> Unit = {
                dots(dotsOf(ENGINE, s + 3.2, 30.4, 24, P, least = 0.0, all = true), 0.34, Tone.Lo)
            }
            parts.band(
                "chamber",
                ENGINE,
                s,
                s + 3.2,
                31.6,
                options = TurnedOptions(name = "chamber.ring", extra = extra),
            )
        }
    head =
        parts.band(
            "chamber",
            ENGINE,
            194.0,
            205.0,
            33.4,
            options =
                TurnedOptions(
                    bias = 0.4,
                    extra = {
                        val faded = dotsOf(ENGINE, 200.5, 33.4, 30, P, fade = FADE)
                        dots(faded.points, 0.4, Tone.Mid, faded.alphas)
                    },
                ),
        )
    injector =
        parts.turned(
            "chamber",
            ENGINE,
            listOf(Knot(205.0, 37.5), Knot(210.0, 37.5)),
            options = TurnedOptions(bevel = 0.6, extra = ringDots(210.0, 35.6, 36, 0.42)),
        )
    dome =
        parts.turned(
            "chamber",
            ENGINE,
            RaptorBuild.DOME_PROFILE,
            options = TurnedOptions(extra = { line(circleOf(ENGINE, 222.0, 23.5, P), Tone.Faint) }),
        )
    for (deg in listOf(70, 110, 250)) {
        jacketBoss[deg] =
            parts.boss(
                "chamber",
                polar(deg.toDouble(), 29.4, 180.0),
                dir(deg.toDouble()),
                1.7,
                3.0,
                BossOptions(host = listOf(jackets[1])),
            )
    }
    parts.boss(
        "chamber",
        polar(-150.0, 33.4, 200.0),
        dir(-150.0),
        2.4,
        3.6,
        BossOptions(host = listOf(head)),
    )
    for (deg in listOf(-100, 175)) {
        val on = domeAt(deg.toDouble(), 216.0)
        domeBoss[deg] =
            parts.boss("chamber", on.at, on.normal, 1.5, 2.6, BossOptions(host = listOf(dome))) to
                on.normal
    }
    val on = domeAt(28.0, 219.0)
    parts.boss("chamber", on.at, on.normal, 1.5, 2.4, BossOptions(host = listOf(dome)))
}

internal fun domeAt(deg: Double, s: Double) =
    Raptor.onBody(ENGINE, RaptorBuild.DOME_PROFILE, deg, s)

internal const val IGNITER_DEG = 200.0

internal fun RaptorBuild.preburner() {
    parts.turned(
        "orpb",
        ENGINE,
        listOf(Knot(222.0, 25.5), Knot(225.5, 25.5)),
        options = TurnedOptions(extra = ringDots(225.5, 24.0, 24, 0.4)),
    )
    orpbBody =
        parts.band(
            "orpb",
            ENGINE,
            225.5,
            242.0,
            22.2,
            options =
                TurnedOptions(inner = arcOf(ENGINE, 230.0, 22.2, P) + arcOf(ENGINE, 236.0, 22.2, P)),
        )
    parts.turned(
        "orpb",
        ENGINE,
        listOf(Knot(242.0, 25.5), Knot(246.0, 25.5)),
        options = TurnedOptions(extra = ringDots(246.0, 24.0, 24, 0.4)),
    )
    val host = listOf(orpbBody)
    val igniter =
        parts.boss(
            "orpb",
            polar(IGNITER_DEG, 22.2, 233.0),
            dir(IGNITER_DEG),
            2.6,
            4.4,
            BossOptions(host = host, ring = false),
        )
    orpbPlug = parts.boss("orpb", igniter, dir(IGNITER_DEG), 2.0, 4.0)
    orpbExciter =
        parts.boss(
            "orpb",
            polar(100.0, 22.2, 228.5),
            dir(100.0),
            1.3,
            2.4,
            BossOptions(host = host, name = "orpb.exciter"),
        )
    orpbSense =
        parts.boss(
            "orpb",
            polar(20.0, 22.2, 236.0),
            dir(20.0),
            2.0,
            3.2,
            BossOptions(host = host, name = "orpb.sense-boss"),
        )
    parts.boss("orpb", polar(70.0, 22.2, 238.0), dir(70.0), 1.5, 2.6, BossOptions(host = host))
}
