// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Free
import dev.sebastiano.anatomy.kit.Knot
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.arcOf
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.audit.AuditedFigure
import dev.sebastiano.anatomy.kit.audit.Recorder
import dev.sebastiano.anatomy.kit.audit.ShapeMeta
import dev.sebastiano.anatomy.kit.audit.disc as recordedDisc
import dev.sebastiano.anatomy.kit.audit.slab
import dev.sebastiano.anatomy.kit.corners
import dev.sebastiano.anatomy.kit.cylinder
import dev.sebastiano.anatomy.kit.depthOf
import dev.sebastiano.anatomy.kit.disc
import dev.sebastiano.anatomy.kit.dot3
import dev.sebastiano.anatomy.kit.frameOf
import dev.sebastiano.anatomy.kit.haloOf
import dev.sebastiano.anatomy.kit.insetPlan
import dev.sebastiano.anatomy.kit.lineOnTop
import dev.sebastiano.anatomy.kit.pathOf
import dev.sebastiano.anatomy.kit.planOutline
import dev.sebastiano.anatomy.kit.ring
import dev.sebastiano.anatomy.kit.sideSeam
import dev.sebastiano.anatomy.kit.slabOf
import dev.sebastiano.anatomy.kit.topTicks
import dev.sebastiano.anatomy.kit.unit3

internal object ArcadeCabinet : AuditedFigure {
    val BUILD by lazy { ArcadeBuild() }

    fun settled() = ArcadeBuild().also { it.recorder.settle() }

    override fun record(): Recorder = settled().recorder

    val ORDER by lazy {
        javaClass.getResource("/arcade-order.txt")!!.readText().trim().lines().map { line ->
            val fields = line.split('\t')
            fields[0].toInt() to fields[1].toDouble()
        }
    }
    val ITEMS by lazy { ORDER.map { BUILD.recorder.items[it.first] } }
}

internal fun ArcadeBuild.stand() {
    val p = Arcade.P
    val plate = Arcade.PLATE
    back = art {
        halo(haloOf(plate, -15.0, 10.0, p))
        for (c in corners(plate, 22.0).sortedBy { depthOf(Point3(it.x, it.y, 0.0), p) }) {
            solid(cylinder(c.x, c.y, 7.0, -15.0, 1.6, p, 28), Tone.Lo)
            solid(cylinder(c.x, c.y, 5.4, -13.4, 3.4, p, 28), Tone.Mid)
        }
        solid(
            slabOf(plate, -10.0, 10.0, p, 8, 1.6),
            Tone.Mid,
            inner = art { line(sideSeam(plate, -4.0, p, 8), Tone.Faint) },
        )
        line(planOutline(insetPlan(plate, 5.0), 0.0, p), Tone.Lo)
        val screws = corners(plate, 10.0)
        line(
            dev.sebastiano.anatomy.kit.IsoPath.join(
                screws.map { ring(it.x, it.y, 2.2, 0.0, p, 16) }
            ),
            Tone.Lo,
        )
        dots(screws.map { Arcade.at(Point3(it.x, it.y, 0.0)) }, 0.5)
        val strip = Plan(-84.0, 205.0, 156.0, 6.0, 1.0)
        solid(slabOf(strip, 0.0, 0.6, p, 3), Tone.Lo, crease = null)
        val t = topTicks(-80.0, 68.0, 4.0, 5, strip.y, 0.6, 1.8 to 3.4, p)
        line(t.minor, Tone.Lo)
        line(t.major, Tone.Mid)
        val label = Plan(48.0, 184.0, 30.0, 13.0, 1.2)
        solid(slabOf(label, 0.0, 0.8, p, 3), Tone.Lo, crease = null)
        line(planOutline(insetPlan(label, 1.4), 0.8, p, 3), Tone.Faint)
        line(
            dev.sebastiano.anatomy.kit.IsoPath.join(
                listOf(20.0, 14.0, 17.0).mapIndexed { i, w ->
                    lineOnTop(Floor(52.0, 187.4 + i * 3), Floor(52 + w, 187.4 + i * 3), 0.8, p)
                }
            ),
            Tone.Lo,
            free = Free.Both,
        )
        dots(listOf(Point3(75.4, 186.4, 0.8), Point3(75.4, 194.6, 0.8)).map(Arcade::at), 0.45)
        halo(haloOf(Plan(-83.0, 179.0, 18.0, 18.0, 9.0), 0.0, 2.0, p))
    }
    recorder.solid(slab(plate, -10.0, 10.0, ShapeMeta(name = "stand.plate", ground = true), 8))
    val feet =
        listOf(Floor(-54.0, 12.0), Floor(54.0, 12.0), Floor(-54.0, 144.0), Floor(54.0, 144.0))
    for (c in feet.sortedBy { depthOf(Point3(it.x, it.y, 0.0), p) }) {
        val f = frameOf(Point3(c.x, c.y, 0.0), Arcade.Z, Arcade.X, Arcade.Y)
        val shapes =
            listOf(
                recorder.solid(recordedDisc(f, 0.0, 1.2, 3.4, ShapeMeta(name = "leveler.pad"))),
                recorder.solid(recordedDisc(f, 1.2, 2.4, 2.4, ShapeMeta(name = "leveler.nut"))),
                recorder.solid(recordedDisc(f, 2.4, 4.0, 1.0, ShapeMeta(name = "leveler.stem"))),
            )
        put("leveler", Point3(c.x, c.y, 2.0), shapes) {
            solid(disc(0.0, 1.2, 3.4, f, p, steps = 32), Tone.Lo)
            solid(disc(1.2, 2.4, 2.4, f, p, steps = 6), Tone.Mid)
            solid(disc(2.4, 4.0, 1.0, f, p, steps = 16), Tone.Mid)
        }
    }
}

internal fun ArcadeBuild.cabinet() {
    val ring = if (arcadeSigned(Arcade.WALL) > 0) Arcade.WALL else Arcade.WALL.reversed()
    val facing =
        ring.indices.map { i ->
            val a = ring[i]
            val b = ring[(i + 1) % ring.size]
            dot3(unit3(Point3(0.0, b.y - a.y, -(b.x - a.x))), Arcade.V) > 1e-9
        }
    val bead =
        dev.sebastiano.anatomy.kit.IsoPath.join(
            arcadeRuns(facing, true).map { run ->
                pathOf(run.map { Arcade.at(Point3(62.0, ring[it].x, ring[it].y)) })
            }
        )
    prism(
        "wall",
        Arcade.XF,
        Arcade.WALL,
        60.0,
        64.0,
        bias = -400.0,
        ref = Point3(64.0, 80.0, 180.0),
        inner = art { line(bead, Tone.Lo) },
    )
    box("cleat.floor", Point3(56.0, 8.0, 8.0), Point3(60.0, 148.0, 12.0), Tone.Lo)
    box("cleat.back", Point3(56.0, 4.0, 12.0), Point3(60.0, 8.0, 350.0), Tone.Lo)
    box("cleat.top", Point3(56.0, 8.0, 350.0), Point3(60.0, 122.0, 354.0), Tone.Lo)
    box("cleat.front", Point3(56.0, 148.0, 12.0), Point3(60.0, 152.0, 164.0), Tone.Lo)
    cabinetBoards()
    marquee()
}

private fun ArcadeBuild.cabinetBoards() {
    board(
        "floor",
        Arcade.rect(4.0, 152.0, 4.0, 8.0),
        tone = Tone.Lo,
        holes = listOf(Floor(24.0, 6.0), Floor(130.0, 6.0)),
    )
    board(
        "back",
        Arcade.rect(0.0, 4.0, 4.0, 354.0),
        bias = -300.0,
        holes = listOf(Floor(2.0, 40.0), Floor(2.0, 180.0), Floor(2.0, 320.0)),
    )
    board(
        "top",
        Arcade.rect(0.0, 126.0, 354.0, 358.0),
        holes = listOf(Floor(30.0, 356.0), Floor(100.0, 356.0)),
    ) {
        line(
            dev.sebastiano.anatomy.kit.IsoPath.join(
                (0..8).map {
                    planOutline(Plan(-36.0 + it * 8, 14.0, 3.0, 30.0, 1.5), 358.0, Arcade.P, 3)
                }
            ),
            Tone.Faint,
        )
    }
    board(
        "front",
        Arcade.rect(152.0, 156.0, 4.0, 168.0),
        holes = listOf(Floor(154.0, 20.0), Floor(154.0, 150.0)),
    )
    box("kick", Point3(-60.0, 156.0, 6.0), Point3(60.0, 156.8, 24.0), Tone.Lo, r = 0.3) {
        dots(
            listOf(-54.0, -18.0, 18.0, 54.0).flatMap { x ->
                listOf(Arcade.at(Point3(x, 156.8, 9.0)), Arcade.at(Point3(x, 156.8, 21.0)))
            },
            0.45,
            Tone.Hi,
        )
    }
    board("marquee.shelf", Arcade.rect(98.0, 126.0, 318.0, 322.0), tone = Tone.Lo)
    board(
        "speaker.board",
        listOf(Floor(110.0, 306.0), Floor(126.0, 314.0), Floor(124.2, 317.6), Floor(108.2, 309.6)),
    )
    board(
        "cp.top",
        listOf(
            Floor(136.0, 195.0),
            Floor(178.0, 184.0),
            Floor(177.24, 181.1),
            Floor(135.24, 192.1),
        ),
        holes = listOf(Floor(157.0, 188.0)),
    )
    board(
        "cp.front",
        listOf(
            Floor(174.0, 170.0),
            Floor(178.0, 170.0),
            Floor(178.0, 180.89),
            Floor(174.0, 181.94),
        ),
    )
    board(
        "cp.bottom",
        listOf(Floor(152.0, 168.0), Floor(174.0, 170.0), Floor(174.0, 174.0), Floor(152.0, 172.0)),
        tone = Tone.Lo,
    )
}

private fun ArcadeBuild.marquee() {
    board("marquee.plexi", Arcade.rect(123.0, 125.0, 322.0, 354.0), tone = Tone.Faint)
    surfaces["marquee.plexi"] = ArcadeSurface.marquee()
    val f = frameOf(Point3(-46.0, 110.0, 338.0), Arcade.X, Arcade.Y, Arcade.Z)
    turned(
        "marquee.tube",
        f,
        listOf(Knot(0.0, 3.6), Knot(92.0, 3.6)),
        Tone.Hi,
        inner =
            art { line(arcOf(f, 4.0, 3.6, Arcade.P) + arcOf(f, 88.0, 3.6, Arcade.P), Tone.Faint) },
    )
    box("marquee.socket", Point3(-51.0, 106.0, 333.0), Point3(-46.0, 114.0, 342.0))
    box("marquee.socket", Point3(46.0, 106.0, 333.0), Point3(51.0, 114.0, 342.0))
    box("marquee.bracket", Point3(-51.0, 106.0, 342.0), Point3(-49.0, 114.0, 354.0), Tone.Lo)
    box("marquee.bracket", Point3(49.0, 106.0, 342.0), Point3(51.0, 114.0, 354.0), Tone.Lo)
    box(
        "marquee.ballast",
        Point3(8.0, 99.0, 322.0),
        Point3(40.0, 105.0, 327.0),
        inner =
            art { line(sideSeam(Plan(8.0, 99.0, 32.0, 6.0, 0.4), 324.5, Arcade.P, 2), Tone.Faint) },
    ) {
        dots(listOf(Point3(10.0, 105.0, 327.0), Point3(38.0, 105.0, 327.0)).map(Arcade::at), 0.45)
    }
    turned(
        "marquee.starter",
        frameOf(Point3(44.0, 103.0, 322.0), Arcade.Z, Arcade.X, Arcade.Y),
        listOf(Knot(0.0, 1.8), Knot(5.0, 1.8), Knot(5.6, 1.3, true), Knot(6.0, 0.6, true)),
    )
    turned(
        "speaker.basket",
        Arcade.SPK_F,
        listOf(
            Knot(0.0, 5.6),
            Knot(0.8, 5.6),
            Knot(0.8, 4.8),
            Knot(2.4, 2.8, true),
            Knot(2.8, 2.4),
        ),
    )
    disc("speaker.magnet", Arcade.SPK_F, 2.8, 3.8, 2.6, Tone.Lo)
}
