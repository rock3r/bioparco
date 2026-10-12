// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.audit.ShapeMeta
import dev.sebastiano.anatomy.kit.audit.ball
import dev.sebastiano.anatomy.kit.audit.box as recordedBox
import dev.sebastiano.anatomy.kit.frameAlong
import dev.sebastiano.anatomy.kit.frameOf
import dev.sebastiano.anatomy.kit.pathOf

private fun px(x: Double) = -50 + x

private fun pz(z: Double) = 116 + z

internal fun ArcadeBuild.board() {
    for ((x, z) in listOf(3.0 to 3.0, 57.0 to 3.0, 3.0 to 51.0, 57.0 to 51.0)) disc(
        "pcb.standoff",
        frameAlong(Point3(px(x), 4.0, pz(z)), Arcade.Y),
        0.0,
        3.2,
        1.0,
        Tone.Lo,
    )
    box("pcb.board", Point3(-50.0, 7.2, 116.0), Point3(10.0, 8.0, 170.0), Tone.Lo, r = 0.6)
    chip("pcb.cpu", 4.0, 40.0, 10.4, 3.0)
    repeat(4) { chip("pcb.rom", 18 + it * 8.6, 45.0, 7.2, 3.0) }
    repeat(3) { chip("pcb.ram", 4.0 + it * 6, 33.0, 4.6, 1.6) }
    repeat(3) { r -> repeat(6) { c -> chip("pcb.logic", 24 + c * 5.6, 12 + r * 7.4, 4.0, 1.6) } }
    chip("pcb.sound", 4.0, 22.0, 5.4, 3.0)
    chip("pcb.video", 4.0, 12.0, 7.2, 3.0, 1.6)
    box(
        "pcb.crystal",
        Point3(px(17.0), 8.0, pz(41.0)),
        Point3(px(20.0), 9.4, pz(42.6)),
        Tone.Hi,
        r = 0.6,
        steps = 3,
    )
    box("pcb.dips", Point3(px(16.0), 8.0, pz(33.0)), Point3(px(24.0), 8.8, pz(35.0))) {
        dots((0..7).map { Arcade.at(Point3(px(16.5 + it), 8.8, pz(34.0))) }, 0.25, Tone.Hi)
    }
    val bf = frameOf(Point3(0.0, 8.0, 0.0), Arcade.Y, Arcade.X, Arcade.Z)
    prism("pcb.heatsink", bf, Arcade.rect(px(4.0), px(12.0), pz(3.0), pz(8.0)), 0.0, 4.2) {
        line(
            IsoPath.join(
                listOf(5.6, 7.2, 8.8, 10.4).map { x ->
                    pathOf(
                        listOf(Point3(px(x), 12.2, pz(3.0)), Point3(px(x), 12.2, pz(8.0)))
                            .map(Arcade::at)
                    )
                }
            ),
            Tone.Lo,
        )
    }
    for (c in
        listOf(
            listOf(50.0, 37.4, 1.6, 3.6),
            listOf(56.0, 37.4, 1.2, 2.8),
            listOf(16.0, 26.0, 1.0, 2.4),
            listOf(18.0, 16.0, 1.0, 2.4),
        )) disc(
        "pcb.cap",
        frameAlong(Point3(px(c[0]), 8.0, pz(c[1])), Arcade.Y),
        0.0,
        c[3],
        c[2],
        Tone.Hi,
    )
    val led = Point3(px(54.0), 8.4, pz(48.0))
    put("pcb.led", led, listOf(recorder.solid(ball(led, 0.6, ShapeMeta(name = "pcb.led")))), 0.4) {
        dots(listOf(Arcade.at(led)), 0.9, Tone.Hi)
    }
    traces()
    box("pcb.edge", Point3(10.0, 4.6, 124.0), Point3(14.6, 11.0, 162.0), r = 0.5) {
        line(
            pathOf(listOf(Point3(11.0, 11.0, 124.0), Point3(11.0, 11.0, 162.0)).map(Arcade::at)),
            Tone.Lo,
        )
    }
    box("pcb.vplug", Point3(px(6.0), 5.4, 170.0), Point3(px(12.0), 10.4, 172.4))
    fan()
}

private fun ArcadeBuild.chip(
    name: String,
    x: Double,
    z: Double,
    len: Double,
    wid: Double,
    tall: Double = 1.2,
) {
    val pins = ArrayList<Point3>()
    var i = 0.6
    while (i < len - 0.3) {
        pins.add(Point3(px(x + i), 8.0, pz(z) - 0.25))
        pins.add(Point3(px(x + i), 8.0, pz(z + wid) + 0.25))
        i += 1.27
    }
    box(
        name,
        Point3(px(x), 8.0, pz(z)),
        Point3(px(x + len), 8 + tall, pz(z + wid)),
        Tone.Hi,
        r = 0.15,
        steps = 1,
    ) {
        dots(pins.map(Arcade::at), 0.22, Tone.Lo)
        dots(listOf(Arcade.at(Point3(px(x + 0.8), 8 + tall, pz(z + wid / 2)))), 0.35, Tone.Lo)
    }
}

private fun ArcadeBuild.traces() {
    val traces = ArrayList<IsoPath>()
    val pads = ArrayList<Point3>()
    fun lane(x0: Double, z0: Double, x1: Double, z1: Double, via: Double? = null) {
        val pts =
            if (via != null) listOf(x0 to z0, via to z0, via to z1, x1 to z1)
            else listOf(x0 to z0, x1 to z0, x1 to z1)
        traces.add(pathOf(pts.map { (x, z) -> Arcade.at(Point3(px(x), 8.02, pz(z))) }))
        pads.add(Point3(px(x0), 8.02, pz(z0)))
        pads.add(Point3(px(x1), 8.02, pz(z1)))
    }
    lane(15.0, 41.4, 18.0, 39.0, 16.6)
    lane(26.6, 45.0, 29.0, 37.0)
    lane(35.2, 45.0, 37.6, 37.0)
    lane(9.0, 39.0, 9.0, 35.0)
    lane(15.0, 34.6, 24.0, 27.6, 20.0)
    lane(9.6, 25.0, 24.0, 23.2, 16.0)
    lane(11.4, 15.0, 24.0, 11.4, 14.0)
    lane(34.0, 9.4, 44.0, 4.0, 39.0)
    lane(50.0, 18.0, 54.0, 26.0)
    traces.add(
        pathOf(
            listOf(Point3(px(58.0), 8.02, pz(2.0)), Point3(px(58.0), 8.02, pz(52.0)))
                .map(Arcade::at)
        )
    )
    pads.add(Point3(px(58.0), 8.02, pz(2.0)))
    pads.add(Point3(px(58.0), 8.02, pz(52.0)))
    val shape =
        recorder.solid(
            recordedBox(
                Point3(-50.0, 8.0, 116.0),
                Point3(10.0, 8.05, 170.0),
                ShapeMeta(name = "pcb.traces"),
            )
        )
    put("pcb.traces", Point3(px(30.0), 8.05, pz(27.0)), listOf(shape), 0.05) {
        line(IsoPath.join(traces), Tone.Faint)
        dots(pads.map(Arcade::at), 0.3, Tone.Lo)
    }
}

private fun ArcadeBuild.fan() {
    val f = frameOf(Point3(-18.0, 4.0, 262.0), Arcade.Y, Arcade.X, Arcade.Z)
    prism("fan.frame", f, Arcade.rect(-13.0, 13.0, -13.0, 13.0), 0.0, 3.4) {
        dots(
            listOf(-11.0 to -11.0, 11.0 to -11.0, -11.0 to 11.0, 11.0 to 11.0).map { (u, v) ->
                Arcade.at(Point3(-18 + u, 7.4, 262 + v))
            },
            0.5,
            Tone.Hi,
        )
    }
    val shape =
        recorder.solid(
            dev.sebastiano.anatomy.kit.audit.disc(f, 3.4, 3.5, 12.0, ShapeMeta(name = "fan.rotor"))
        )
    put("fan.rotor", Point3(-18.0, 7.5, 262.0), listOf(shape), 0.2) {}
    surfaces["fan.rotor"] = ArcadeSurface.fan()
}
