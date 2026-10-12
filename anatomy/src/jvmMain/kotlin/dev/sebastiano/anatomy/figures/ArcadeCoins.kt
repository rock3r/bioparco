// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Free
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.audit.ShapeMeta
import dev.sebastiano.anatomy.kit.audit.box as recordedBox
import dev.sebastiano.anatomy.kit.frameAlong
import dev.sebastiano.anatomy.kit.pathOf
import dev.sebastiano.anatomy.kit.slabOf
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal fun ArcadeBuild.coinDoor() {
    box(
        "door.frame",
        Point3(-57.0, 156.0, 38.0),
        Point3(-3.0, 157.4, 120.0),
        Tone.Lo,
        r = 1.2,
        steps = 3,
    )
    box(
        "door.plate",
        Point3(-55.0, 157.4, 40.0),
        Point3(-5.0, 158.4, 118.0),
        r = 1.0,
        steps = 3,
        bevel = 0.4,
    )
    coinEntries()
    doorHardware()
    mechs()
}

private fun ArcadeBuild.coinEntries() {
    for (cx in listOf(-42.0, -18.0)) {
        box(
            "door.entry",
            Point3(cx - 8, 158.4, 88.0),
            Point3(cx + 8, 159.8, 114.0),
            r = 1.2,
            steps = 3,
            bevel = 0.3,
        )
        val shape =
            recorder.solid(
                recordedBox(
                    Point3(cx - 5, 159.8, 90.0),
                    Point3(cx + 5, 161.0, 98.0),
                    ShapeMeta(name = "door.reject"),
                )
            )
        put("door.slot", Point3(cx, 159.9, 104.0), listOf(shape), 1.0) {
            val slot =
                pathOf(
                    listOf(
                            Point3(cx - 0.5, 159.82, 100.5),
                            Point3(cx + 0.5, 159.82, 100.5),
                            Point3(cx + 0.5, 159.82, 107.5),
                            Point3(cx - 0.5, 159.82, 107.5),
                        )
                        .map(Arcade::at),
                    true,
                )
            void(slot)
            line(slot, Tone.Lo, width = 0.5)
            solid(slabOf(Plan(cx - 5, 159.8, 10.0, 1.2, 0.6), 90.0, 8.0, Arcade.P, 2, 0.3), Tone.Hi)
        }
    }
}

private fun ArcadeBuild.doorHardware() {
    disc("door.lock", frameAlong(Point3(-30.0, 158.4, 72.0), Arcade.Y), 0.0, 1.6, 2.4, Tone.Hi) {
        line(
            pathOf(
                listOf(Point3(-30.0, 160.02, 70.8), Point3(-30.0, 160.02, 73.2)).map(Arcade::at)
            ),
            Tone.Lo,
            free = Free.Both,
        )
    }
    box("door.return", Point3(-39.0, 158.4, 46.0), Point3(-21.0, 160.4, 60.0), r = 1.4, steps = 3)
    val mouth =
        recorder.solid(
            recordedBox(
                Point3(-36.0, 160.4, 49.0),
                Point3(-24.0, 160.5, 55.0),
                ShapeMeta(name = "door.return.mouth"),
            )
        )
    put("door.return.mouth", Point3(-30.0, 160.5, 52.0), listOf(mouth), 1.0) {
        val path =
            pathOf(
                listOf(
                        Point3(-36.0, 160.42, 49.0),
                        Point3(-24.0, 160.42, 49.0),
                        Point3(-24.0, 160.42, 55.0),
                        Point3(-36.0, 160.42, 55.0),
                    )
                    .map(Arcade::at),
                true,
            )
        void(path)
        line(path, Tone.Lo, width = 0.5)
    }
    for (z in listOf(48.0, 110.0)) disc(
        "door.hinge",
        frameAlong(Point3(-2.1, 156.9, z), Arcade.Z),
        -3.0,
        3.0,
        0.9,
    )
    box("door.price", Point3(-36.0, 158.4, 80.0), Point3(-24.0, 158.9, 85.0), Tone.Lo) {
        line(
            pathOf(
                listOf(Point3(-34.0, 158.92, 82.5), Point3(-28.0, 158.92, 82.5)).map(Arcade::at)
            ),
            Tone.Lo,
            free = Free.Both,
        )
    }
}

private fun ArcadeBuild.mechs() {
    box("door.back", Point3(-57.0, 150.0, 38.0), Point3(-3.0, 152.0, 120.0), Tone.Lo)
    for (cx in listOf(-42.0, -18.0)) {
        val side = cx - 6.5
        box(
            "mech",
            Point3(side, 141.0, 84.0),
            Point3(cx + 6.5, 150.0, 106.0),
            r = 0.6,
            bevel = 0.4,
        ) {
            line(
                pathOf(
                    listOf(
                            Point3(side, 143.0, 88.0),
                            Point3(side, 148.0, 88.0),
                            Point3(side, 148.0, 102.0),
                            Point3(side, 143.0, 102.0),
                        )
                        .map(Arcade::at),
                    true,
                ),
                Tone.Faint,
            )
            dots(
                listOf(
                        Point3(side, 143.4, 103.6),
                        Point3(side, 147.6, 103.6),
                        Point3(side, 143.4, 86.4),
                        Point3(side, 147.6, 86.4),
                    )
                    .map(Arcade::at),
                0.45,
            )
        }
        disc(
            "mech.magnet",
            frameAlong(Point3(side, 145.5, 96.0), Point3(-1.0, 0.0, 0.0)),
            0.0,
            1.4,
            2.4,
            Tone.Hi,
        )
        disc(
            "mech.pivot",
            frameAlong(Point3(side, 146.0, 89.0), Point3(-1.0, 0.0, 0.0)),
            0.0,
            0.8,
            0.9,
            Tone.Lo,
        )
        box("mech.lever", Point3(cx - 1, 141.5, 106.0), Point3(cx + 1, 150.0, 107.6), Tone.Lo)
        disc(
            "door.socket",
            frameAlong(Point3(cx, 150.0, 112.0), Point3(0.0, -1.0, 0.0)),
            0.0,
            3.4,
            1.6,
        )
    }
}

internal fun ArcadeBuild.chute() {
    val t = 0.8
    val cs = cos(30 * PI / 180)
    val sn = sin(30 * PI / 180)
    val floor =
        listOf(
            Floor(148.0, Arcade.chuteZ(148.0)),
            Floor(104.0, Arcade.chuteZ(104.0)),
            Floor(104 + t * sn, Arcade.chuteZ(104.0) - t * cs),
            Floor(148 + t * sn, Arcade.chuteZ(148.0) - t * cs),
        )
    board("chute.floor", floor, -50.0, -10.0)
    fun wall(h: Double) =
        listOf(
            Floor(148.0, Arcade.chuteZ(148.0)),
            Floor(148.0, Arcade.chuteZ(148.0) + h),
            Floor(104.0, Arcade.chuteZ(104.0) + h),
            Floor(104.0, Arcade.chuteZ(104.0)),
        )
    board("chute.far", wall(6.0), -10.0, -9.3)
    board("chute.lip", wall(1.2), -50.7, -50.0, Tone.Hi)
    box("chute.tab", Point3(-48.0, 148.0, 79.4), Point3(-12.0, 150.0, 82 - t * cs), Tone.Lo)
    for (x in listOf(-49.0, -13.0)) box(
        "chute.leg",
        Point3(x, 109.0, 8.0),
        Point3(x + 1.4, 111.0, Arcade.chuteZ(109 - t * sn) - t * cs),
        Tone.Lo,
    )
    val z0 = Arcade.chuteZ(124.0) - 2.4
    val z1 = Arcade.chuteZ(124.0) + 3
    box("coin.switch", Point3(-54.4, 121.4, z0), Point3(-50.7, 126.6, z1)) {
        dots(
            listOf(Point3(-54.4, 122.6, z0 + 1), Point3(-54.4, 125.4, z1 - 1)).map(Arcade::at),
            0.4,
        )
    }
    box("coin.switch.bracket", Point3(-50.7, 121.8, z0), Point3(-50.7, 126.2, z0 + 1), Tone.Lo)
}

internal fun ArcadeBuild.cashBox() {
    box("cash.floor", Point3(-58.0, 62.0, 8.0), Point3(-16.0, 104.0, 8.7), Tone.Lo)
    box("cash.back", Point3(-58.0, 62.0, 8.7), Point3(-16.0, 62.7, 30.0))
    box("cash.far", Point3(-16.7, 62.7, 8.7), Point3(-16.0, 104.0, 30.0))
    box("cash.near", Point3(-58.0, 62.7, 8.7), Point3(-57.3, 104.0, 30.0))
    box("cash.front", Point3(-57.3, 103.3, 8.7), Point3(-16.7, 104.0, 30.0))
    box("cash.handle", Point3(-59.6, 78.0, 22.0), Point3(-58.0, 88.0, 24.0), Tone.Hi)
    box("meter.foot", Point3(-58.0, 106.0, 8.0), Point3(-50.0, 120.0, 12.0), Tone.Lo)
    box("meter", Point3(-58.0, 108.0, 12.0), Point3(-52.0, 118.0, 20.0), r = 0.6)
    val shape =
        recorder.solid(
            recordedBox(
                Point3(-58.05, 109.6, 13.6),
                Point3(-58.0, 116.4, 18.4),
                ShapeMeta(name = "meter.face"),
            )
        )
    put("meter.face", Point3(-58.0, 113.0, 16.0), listOf(shape), 0.3) {}
    surfaces["meter.face"] = ArcadeSurface.meter()
}
