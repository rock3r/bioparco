// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Rings
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.audit.RouteOptions
import dev.sebastiano.anatomy.kit.audit.ShapeMeta
import dev.sebastiano.anatomy.kit.audit.ball
import dev.sebastiano.anatomy.kit.audit.disc as recordedDisc
import dev.sebastiano.anatomy.kit.audit.tube
import dev.sebastiano.anatomy.kit.disc
import dev.sebastiano.anatomy.kit.fillet
import dev.sebastiano.anatomy.kit.frameAlong
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.pathLength
import dev.sebastiano.anatomy.kit.tangentAlong
import dev.sebastiano.anatomy.kit.tubePieces

internal class ArcadeCable(
    val tone: Tone = Tone.Mid,
    val bend: Double = 3.0,
    val shells: Pair<Boolean, Boolean> = false to false,
    val rings: Rings? = null,
)

internal fun ArcadeBuild.cable(
    name: String,
    points: List<Point3>,
    r: Double = 0.5,
    o: ArcadeCable = ArcadeCable(),
) {
    val route = fillet(points, maxOf(o.bend, r * 2.6), 10, projection = Arcade.P, tube = r)
    val total = pathLength(route)
    val shell = maxOf(1.6, r * 2.4)
    val gaps = buildList {
        if (o.shells.first) add(0.0 to shell)
        if (o.shells.second) add(total - shell to total)
    }
    recorder.route(
        name,
        route,
        r,
        RouteOptions(
            gaps = gaps,
            limp = true,
            rims = listOf(if (o.shells.first) r * 1.9 else r, if (o.shells.second) r * 1.9 else r),
            piece = "cab",
        ),
    )
    tubePieces(
            route,
            r,
            Arcade.P,
            maxLength = 16.0,
            spacing = maxOf(0.6, minOf(1.6, r)),
            gaps = gaps,
            rings = o.rings,
        )
        .forEachIndexed { index, chunk ->
            put(
                "$name:$index",
                chunk.mid,
                listOf(tube(chunk.points, chunk.radii)),
                0.05,
                name,
                index,
            ) {
                tube(chunk, o.tone)
            }
        }
    for (index in 0..1) {
        val end = if (index == 1) route.last() else route.first()
        val hasShell = if (index == 1) o.shells.second else o.shells.first
        if (hasShell) {
            val tangent = tangentAlong(route, if (index == 1) total else 0.0)
            val f = frameAlong(end, tangent)
            val s0 = if (index == 1) -shell else 0.0
            val s1 = if (index == 1) 0.0 else shell
            val shape =
                recorder.solid(
                    recordedDisc(
                        f,
                        s0,
                        s1,
                        r * 1.9,
                        ShapeMeta(name = "$name.shell$index", owner = name),
                    )
                )
            put("$name.shell$index", add3(end, mul3(tangent, (s0 + s1) / 2)), listOf(shape), 0.15) {
                solid(disc(s0, s1, r * 1.9, f, Arcade.P, steps = 20, bevel = 0.2), Tone.Mid)
            }
        } else {
            val size = maxOf(0.5, r * Arcade.KS * 0.9)
            put(
                "$name.end$index",
                end,
                listOf(
                    ball(end, size / Arcade.KS, ShapeMeta(name = "$name.end$index", owner = name))
                ),
                0.2,
            ) {
                dots(listOf(Arcade.at(end)), size, Tone.Hi)
            }
        }
    }
}

internal fun ArcadeBuild.harness() {
    controlHarness()
    coinHarness()
    utilityHarness()
    monitorCables()
}

private fun ArcadeBuild.controlHarness() {
    box("loom.plug", Point3(-55.0, 147.2, 173.2), Point3(-43.0, 150.4, 176.8))
    val top = 192.1 - (148 - 135.24) * ((192.1 - 181.1) / (177.24 - 135.24))
    box("loom.plug.bracket", Point3(-51.0, 148.0, 176.8), Point3(-47.6, 149.0, top), Tone.Lo)
    cable(
        "loom",
        listOf(
            Point3(-43.0, 148.8, 175.0),
            Point3(46.0, 148.8, 175.0),
            Point3(52.0, 146.0, 168.0),
            Point3(52.0, 146.0, 146.0),
            Point3(52.0, 20.0, 146.0),
            Point3(28.0, 7.8, 146.0),
            plug(146.0),
        ),
        1.3,
        ArcadeCable(
            bend = 6.0,
            shells = false to true,
            rings = Rings(pitch = 1.1, twist = 0.8, cross = true, fade = 0.1 to 0.55),
        ),
    )
    val lanes =
        mapOf(
            "up" to (-49.0 to 175.8),
            "left" to (-53.6 to 175.0),
            "right" to (-44.4 to 175.0),
            "down" to (-50.8 to 174.2),
        )
    for (sw in Arcade.SWITCHES) {
        val tab = add3(sw.c, mul3(Arcade.CPN, -2.0))
        val (x, z) = lanes.getValue(sw.id)
        val route =
            if (sw.id == "down")
                listOf(tab, Point3(tab.x, tab.y, z), Point3(x, tab.y - 3, z), Point3(x, 150.4, z))
            else listOf(tab, Point3(tab.x, tab.y, z), Point3(x, 150.4, z))
        cable("wire.stick.${sw.id}", route, 0.25, ArcadeCable(Tone.Lo, 1.2))
    }
}

private fun ArcadeBuild.coinHarness() {
    val swx = (-54.4 - 50.7) / 2
    val swz = Arcade.chuteZ(124.0) - 2.4
    cable(
        "wire.coin",
        listOf(
            Point3(swx + 0.9, 124.0, swz),
            Point3(swx + 0.9, 124.0, 12.6),
            Point3(50.0, 124.0, 12.6),
            Point3(50.0, 66.0, 12.6),
            Point3(50.0, 66.0, 128.0),
            Point3(50.0, 20.0, 128.0),
            Point3(28.0, 7.8, 128.0),
            plug(128.0),
        ),
        o = ArcadeCable(shells = false to true),
    )
    cable(
        "wire.meter",
        listOf(
            Point3(-55.0, 113.0, 20.0),
            Point3(-55.0, 113.0, 40.0),
            Point3(swx - 0.9, 123.0, 52.0),
            Point3(swx - 0.9, 124.0, swz),
        ),
        0.4,
        ArcadeCable(Tone.Lo),
    )
    cable(
        "cable.power",
        listOf(
            Point3(-1.0, 43.0, 12.4),
            Point3(-1.0, 50.0, 12.4),
            Point3(44.0, 50.0, 12.4),
            Point3(54.0, 62.0, 12.4),
            Point3(54.0, 62.0, 136.0),
            Point3(54.0, 20.0, 136.0),
            Point3(28.0, 7.8, 136.0),
            plug(136.0),
        ),
        0.7,
        ArcadeCable(bend = 5.0, shells = false to true),
    )
}

private fun ArcadeBuild.utilityHarness() {
    cable(
        "wire.service",
        listOf(
            Point3(58.6, 30.0, 99.6),
            Point3(56.6, 30.0, 99.6),
            Point3(56.6, 13.0, 99.6),
            Point3(56.6, 13.0, 141.4),
            Point3(30.0, 7.8, 141.4),
            plug(141.4),
        ),
        0.4,
        ArcadeCable(Tone.Lo, shells = false to true),
    )
    cable(
        "cable.mains",
        listOf(
            Point3(52.6, 26.0, 45.5),
            Point3(46.0, 26.0, 45.5),
            Point3(46.0, 26.0, 15.0),
            Point3(30.0, 26.0, 15.0),
        ),
        0.6,
        ArcadeCable(Tone.Lo, 4.0),
    )
    cable("cord.ac", listOf(Point3(-30.0, 6.0, 22.0), Point3(-30.0, 13.0, 22.0)), 0.9)
}

private fun ArcadeBuild.monitorCables() {
    val back = add3(Arcade.SPK_C, mul3(Arcade.SPK_N, 3.8))
    cable(
        "cable.speaker",
        listOf(
            back,
            add3(back, mul3(Arcade.SPK_N, 0.6)),
            Point3(22.0, 104.0, 316.6),
            Point3(22.0, 7.8, 316.6),
            Point3(22.0, 7.8, 158.0),
            plug(158.0),
        ),
        0.4,
        ArcadeCable(Tone.Lo),
    )
    val vp = Arcade.chPoint(59.0, 39.0, 3.0)
    cable(
        "cable.video",
        listOf(
            Point3(-41.0, 7.9, 172.4),
            Point3(-41.0, 7.9, 184.0),
            Point3(-52.0, 36.0, 192.0),
            Point3(-52.0, vp.y, vp.z),
            vp,
        ),
        0.6,
        ArcadeCable(bend = 5.0),
    )
    val fly = Arcade.chPoint(51.0, 18.0, 4.0)
    val cap = add3(Arcade.ANODE_AT, mul3(Arcade.ANODE_N, 2.2))
    cable(
        "lead.anode",
        listOf(fly, add3(fly, mul3(Arcade.BD, -6.0)), add3(cap, mul3(Arcade.ANODE_N, 6.0)), cap),
        0.8,
        ArcadeCable(Tone.Hi, 5.0),
    )
    val np = Arcade.chPoint(59.5, 20.5, 3.0)
    cable(
        "cable.neck",
        listOf(
            Arcade.monPoint(-4.0, 4.0, 91.0),
            Arcade.monPoint(-4.0, 4.0, 94.0),
            Arcade.monPoint(-30.0, 16.0, 94.0),
            add3(np, Point3(-5.0, 0.0, 0.0)),
            np,
        ),
        0.45,
        ArcadeCable(Tone.Lo),
    )
}

private fun plug(z: Double) = Point3(14.6, 7.8, z)
