// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Knot
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Seams
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.arcOf
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.audit.ShapeMeta
import dev.sebastiano.anatomy.kit.audit.solid
import dev.sebastiano.anatomy.kit.bandsOf
import dev.sebastiano.anatomy.kit.frameAlong
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.pathOf

internal fun ArcadeBuild.monitor() {
    val m = Arcade.MON
    val p = Arcade.P
    prism("crt.face", m, Arcade.rect(-34.0, 34.0, -42.0, 42.0), 0.0, 3.0, Tone.Hi)
    prism("crt.band", m, Arcade.rect(-35.0, 35.0, -43.0, 43.0), 3.0, 9.0)
    for (signs in listOf(-1.0 to -1.0, 1.0 to -1.0, -1.0 to 1.0, 1.0 to 1.0)) {
        val (su, sv) = signs
        prism("crt.ear", m, Arcade.rect(su * 35, su * 41, sv * 37, sv * 43), 5.0, 6.0) {
            dots(listOf(Arcade.at(Arcade.monPoint(su * 38.6, sv * 40, 5.0))), 0.5, Tone.Hi)
        }
    }
    val funnel =
        bandsOf(
            listOf(Knot(9.0, Arcade.funnel(9.0)), Knot(62.0, Arcade.funnel(62.0))),
            listOf(48.0),
            m,
            p,
            radius = Arcade::funnel,
            slope = Arcade::funnelSlope,
            rows = 6,
        )
    val front = funnel.bands[0]
    val shape =
        recorder.solid(
            solid(m, front.profile, radius = Arcade::funnel, meta = ShapeMeta(name = "crt.funnel"))
        )
    put("crt.funnel", add3(Arcade.CF, mul3(Arcade.MON_A, 26.0)), listOf(shape)) {
        solid(front.paths, Tone.Hi)
        line(funnel.sidesOf(9.0, 48.0, ends = Seams.None).d, Tone.Hi)
    }
    turned(
        "crt.yoke",
        m,
        listOf(Knot(48.0, 15.5), Knot(52.0, 15.5), Knot(60.0, 11.0, true), Knot(64.0, 8.0)),
        inner = art { line(arcOf(m, 52.0, 15.5, p), Tone.Faint) },
    )
    for ((s0, s1) in listOf(64.0 to 69.0, 70.4 to 72.4, 73.8 to 88.4)) disc(
        "crt.neck",
        m,
        s0,
        s1,
        3.0,
        Tone.Hi,
    )
    disc("crt.ring", m, 69.0, 70.4, 4.2)
    disc("crt.ring", m, 72.4, 73.8, 4.2)
    prism("crt.neckboard", m, Arcade.rect(-7.0, 7.0, -7.0, 7.0), 90.0, 91.0)
    disc("crt.socket", m, 88.4, 90.0, 4.4, Tone.Lo)
    turned(
        "crt.anode",
        frameAlong(add3(Arcade.ANODE_AT, mul3(Arcade.ANODE_N, -0.4)), Arcade.ANODE_N),
        listOf(
            Knot(0.0, 3.6),
            Knot(0.8, 3.4, true),
            Knot(1.8, 2.4, true),
            Knot(2.4, 1.2, true),
            Knot(2.6, 0.6),
        ),
        Tone.Lo,
    )
    monitorMounts()
    chassis()
}

private fun ArcadeBuild.monitorMounts() {
    val m = Arcade.MON
    for (su in listOf(-1.0, 1.0)) prism(
        "mon.angle",
        m,
        Arcade.rect(su * 41, su * 44, -46.0, 46.0),
        4.0,
        8.0,
    )
    for (su in listOf(-1.0, 1.0)) prism(
        "mon.rail",
        m,
        Arcade.rect(su * 36, su * 44, 46.0, 48.0),
        4.0,
        66.0,
    ) {
        dots(
            listOf(Arcade.monPoint(su * 40, 46.0, 20.0), Arcade.monPoint(su * 40, 46.0, 50.0))
                .map(Arcade::at),
            0.45,
        )
    }
    prism("mon.crossbar", m, Arcade.rect(-36.0, 36.0, 46.0, 48.0), 62.0, 66.0)
    prism("mon.shelf", m, Arcade.rect(-60.0, 60.0, 48.0, 52.0), -2.0, 70.0, Tone.Lo)
    prism("mon.cleat", m, Arcade.rect(56.0, 60.0, 52.0, 56.0), 2.0, 66.0, Tone.Lo)
    board(
        "mon.post",
        listOf(Floor(130.0, 174.0), Floor(134.0, 174.0), Floor(134.0, 194.6), Floor(130.0, 195.6)),
        tone = Tone.Lo,
    )
}

private fun ArcadeBuild.chassis() {
    val f = Arcade.CH
    prism("chassis.board", f, Arcade.rect(22.0, 64.0, 14.0, 46.0), 0.0, 1.0, Tone.Lo)
    prism("chassis.flyback", f, Arcade.rect(46.0, 56.0, 18.0, 29.0), 1.0, 7.0)
    disc(
        "chassis.flyback.coil",
        frameAlong(Arcade.chPoint(51.0, 23.5, 7.0), Point3(-1.0, 0.0, 0.0)),
        0.0,
        2.6,
        4.0,
        Tone.Hi,
    )
    for (s in listOf(48.0, 53.0)) disc(
        "chassis.pot",
        frameAlong(Arcade.chPoint(s, 31.0, 1.0), Point3(-1.0, 0.0, 0.0)),
        0.0,
        1.6,
        1.1,
    ) {
        line(
            pathOf(
                listOf(Arcade.chPoint(s - 0.8, 31.0, 2.7), Arcade.chPoint(s + 0.8, 31.0, 2.7))
                    .map(Arcade::at)
            ),
            Tone.Hi,
        )
    }
    prism("chassis.heatsink", f, Arcade.rect(26.0, 38.0, 17.0, 27.0), 1.0, 6.0) {
        line(
            IsoPath.join(
                listOf(28.0, 30.0, 32.0, 34.0, 36.0).map { s ->
                    pathOf(
                        listOf(Arcade.chPoint(s, 17.0, 6.0), Arcade.chPoint(s, 27.0, 6.0))
                            .map(Arcade::at)
                    )
                }
            ),
            Tone.Lo,
        )
    }
    val caps =
        listOf(
            listOf(30.0, 34.0, 1.8, 5.0),
            listOf(35.0, 38.0, 1.4, 4.0),
            listOf(47.0, 40.0, 1.6, 4.4),
            listOf(42.0, 42.0, 1.2, 3.4),
        )
    for (c in caps) disc(
        "chassis.cap",
        frameAlong(Arcade.chPoint(c[0], c[1], 1.0), Point3(-1.0, 0.0, 0.0)),
        0.0,
        c[3],
        c[2],
        Tone.Hi,
    )
    prism("chassis.plug.video", f, Arcade.rect(56.0, 62.0, 36.0, 42.0), 1.0, 3.0)
    prism("chassis.plug.neck", f, Arcade.rect(57.0, 62.0, 18.0, 23.0), 1.0, 3.0)
}
