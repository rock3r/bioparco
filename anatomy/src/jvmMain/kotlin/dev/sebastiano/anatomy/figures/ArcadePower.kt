// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Free
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Seams
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.audit.ShapeMeta
import dev.sebastiano.anatomy.kit.audit.box as recordedBox
import dev.sebastiano.anatomy.kit.dotGrid
import dev.sebastiano.anatomy.kit.frameAlong
import dev.sebastiano.anatomy.kit.pathOf
import dev.sebastiano.anatomy.kit.ribsOf
import dev.sebastiano.anatomy.kit.sideSeam

internal fun ArcadeBuild.power() {
    supply()
    relayPlate()
    service()
    cabinetElectrics()
}

private fun ArcadeBuild.supply() {
    box("psu.base", Point3(-48.0, 10.0, 8.0), Point3(-20.0, 40.0, 9.4), Tone.Lo) {
        dots(
            listOf(
                    Point3(-46.0, 12.0, 9.4),
                    Point3(-22.0, 12.0, 9.4),
                    Point3(-46.0, 38.0, 9.4),
                    Point3(-22.0, 38.0, 9.4),
                )
                .map(Arcade::at),
            0.45,
        )
    }
    box(
        "xfmr.core",
        Point3(-46.0, 16.0, 9.4),
        Point3(-22.0, 34.0, 30.0),
        inner =
            art {
                line(
                    IsoPath.join(
                        listOf(12.0, 15.0, 18.0, 21.0, 24.0, 27.0).map {
                            sideSeam(Plan(-46.0, 16.0, 24.0, 18.0, 0.4), it, Arcade.P, 2)
                        }
                    ),
                    Tone.Faint,
                )
            },
    )
    box(
        "xfmr.coil",
        Point3(-42.0, 34.0, 13.0),
        Point3(-26.0, 37.0, 27.0),
        Tone.Hi,
        r = 1.4,
        steps = 3,
    )
    box(
        "xfmr.coil.back",
        Point3(-42.0, 13.0, 13.0),
        Point3(-26.0, 16.0, 27.0),
        Tone.Hi,
        r = 1.4,
        steps = 3,
    )
    box("xfmr.terminals", Point3(-40.0, 22.0, 30.0), Point3(-28.0, 28.0, 32.0), Tone.Lo) {
        dots(
            listOf(-38.0, -35.0, -32.0, -29.0).map { Arcade.at(Point3(it, 25.0, 32.0)) },
            0.45,
            Tone.Hi,
        )
    }
    box("psu.case", Point3(-12.0, 10.0, 8.0), Point3(30.0, 40.0, 22.0), bevel = 0.4) {
        dots(dotGrid(-8.0, 13.0, 14, 8, 2.6, 22.0, Arcade.P), 0.35, Tone.Lo)
    }
    box("psu.strip", Point3(-8.0, 40.0, 9.0), Point3(26.0, 43.0, 14.0), Tone.Lo) {
        dots(
            listOf(-5.0, -1.0, 3.0, 7.0, 11.0, 15.0, 19.0, 23.0).map {
                Arcade.at(Point3(it, 43.0, 12.4))
            },
            0.45,
            Tone.Hi,
        )
    }
}

private fun ArcadeBuild.relayPlate() {
    box("pw.plate", Point3(58.6, 14.0, 28.0), Point3(60.0, 58.0, 70.0), Tone.Lo) {
        dots(
            listOf(16.0 to 30.0, 56.0 to 30.0, 16.0 to 68.0, 56.0 to 68.0).map { (y, z) ->
                Arcade.at(Point3(58.6, y, z))
            },
            0.45,
            Tone.Hi,
        )
    }
    box("relay", Point3(51.6, 18.0, 52.0), Point3(58.6, 30.0, 64.0), r = 0.6) {
        dots(listOf(Arcade.at(Point3(51.6, 27.4, 61.0))), 0.7, Tone.Hi)
    }
    for (z in listOf(56.0, 62.0)) {
        box(
            "fuse.holder",
            Point3(56.0, 36.0, z - 1.6),
            Point3(58.6, 50.0, z + 1.6),
            r = 1.0,
            steps = 3,
        )
        disc("fuse.cap", frameAlong(Point3(57.3, 50.0, z), Arcade.Y), 0.0, 2.0, 1.3, Tone.Hi)
    }
    box("pw.strip", Point3(55.6, 18.0, 34.0), Point3(58.6, 54.0, 39.0)) {
        dots(
            listOf(21.0, 25.0, 29.0, 33.0, 37.0, 41.0, 45.0, 49.0).map {
                Arcade.at(Point3(55.6, it, 36.5))
            },
            0.45,
            Tone.Hi,
        )
    }
    box("pw.filter", Point3(52.6, 18.0, 42.0), Point3(58.6, 34.0, 49.0), Tone.Hi, r = 0.8)
}

private fun ArcadeBuild.cabinetElectrics() {
    disc("cord.grip", frameAlong(Point3(-30.0, 4.0, 22.0), Arcade.Y), 0.0, 2.0, 2.6)
    box("outlet", Point3(-40.0, 4.0, 330.0), Point3(-10.0, 8.0, 338.0)) {
        dots(listOf(-34.0, -25.0, -16.0).map { Arcade.at(Point3(it, 8.0, 334.0)) }, 0.5, Tone.Lo)
    }
    val shape =
        recorder.solid(
            recordedBox(
                Point3(8.0, 4.0, 290.0),
                Point3(50.0, 4.1, 314.0),
                ShapeMeta(name = "back.vents"),
            )
        )
    put("back.vents", Point3(20.0, 4.1, 300.0), listOf(shape), 0.1) {
        line(
            IsoPath.join(
                (0..4).map { i ->
                    pathOf(
                        listOf(
                                Point3(8.0 + i * 9, 4.05, 290.0),
                                Point3(13.0 + i * 9, 4.05, 290.0),
                                Point3(13.0 + i * 9, 4.05, 314.0),
                                Point3(8.0 + i * 9, 4.05, 314.0),
                            )
                            .map(Arcade::at),
                        true,
                    )
                }
            ),
            Tone.Faint,
        )
    }
}

private fun ArcadeBuild.service() {
    box("service.plate", Point3(58.6, 16.0, 98.0), Point3(60.0, 52.0, 120.0), Tone.Lo) {
        dots(
            listOf(18.0 to 100.0, 50.0 to 100.0, 18.0 to 118.0, 50.0 to 118.0).map { (y, z) ->
                Arcade.at(Point3(58.6, y, z))
            },
            0.45,
            Tone.Hi,
        )
    }
    box("service.toggle", Point3(55.6, 20.0, 108.0), Point3(58.6, 26.0, 114.0), r = 0.5) {
        line(
            pathOf(listOf(Point3(55.6, 23.0, 111.0), Point3(52.6, 23.0, 113.4)).map(Arcade::at)),
            Tone.Hi,
            free = Free.End,
        )
    }
    val axis = Point3(-1.0, 0.0, 0.0)
    disc("service.button.ring", frameAlong(Point3(58.6, 33.0, 111.0), axis), 0.0, 1.0, 3.0)
    disc("service.button", frameAlong(Point3(57.6, 33.0, 111.0), axis), 0.0, 1.6, 2.1, Tone.Hi)
    disc("service.pot", frameAlong(Point3(58.6, 44.0, 111.0), axis), 0.0, 1.6, 2.6)
    val f = frameAlong(Point3(57.0, 44.0, 111.0), axis)
    disc("service.knob", f, 0.0, 2.6, 2.2, Tone.Hi) {
        faded(
            ribsOf(f, 0.0, 2.6, 2.2, 24, Arcade.P, fade = 0.1 to 0.55, seams = Seams.Both),
            Tone.Lo,
        )
        line(
            pathOf(listOf(Point3(54.35, 44.0, 111.0), Point3(54.35, 45.6, 112.2)).map(Arcade::at)),
            Tone.Hi,
            free = Free.End,
        )
    }
}
