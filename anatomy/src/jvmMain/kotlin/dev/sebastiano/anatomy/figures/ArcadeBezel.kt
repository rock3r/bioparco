// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Art
import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Free
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.audit.ShapeMeta
import dev.sebastiano.anatomy.kit.audit.prism
import dev.sebastiano.anatomy.kit.cross3
import dev.sebastiano.anatomy.kit.dot3
import dev.sebastiano.anatomy.kit.frameOf
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.pathOf
import dev.sebastiano.anatomy.kit.roundedPlan
import dev.sebastiano.anatomy.kit.unit3

internal object ArcadeBezel {
    fun round(u0: Double, u1: Double, w0: Double, w1: Double, r: Double) =
        roundedPlan(Plan(u0, w0, u1 - u0, w1 - w0, r), 5)

    val OPEN = round(-29.5, 29.5, -37.5, 37.5, 4.0)

    fun flat(ring: List<Floor>) =
        "M" + ring.joinToString("L") { "${ArcadeScreen.n(it.x)} ${ArcadeScreen.n(it.y)}" } + "Z"

    fun path(ring: List<Floor>, off: Double) =
        pathOf(ring.map { Arcade.at(Arcade.bezelPoint(it.x, it.y, off)) }, true)

    val OPEN_CLIP = path(OPEN, -1.0)
    val FACE_CLIP = path(round(-33.0, 33.0, -41.0, 41.0, 7.0), -5.0)
    val FACE = "M-60 -57H60V57H-60Z" + flat(OPEN.reversed())
    val TRIM = flat(round(-32.5, 32.5, -40.5, 40.5, 7.0))
    val MATRIX = arcadeMatrix(Arcade.bezelPoint(0.0, 0.0, -1.0), Arcade.X, Arcade.BD)
    val SCREEN_MATRIX = arcadeMatrix(Arcade.bezelPoint(0.0, 0.0, -5.0), Arcade.X, Arcade.BD)
    val RECESS: List<Pair<Int, IsoPath>> =
        OPEN.indices.mapNotNull { i ->
            val a = OPEN[i]
            val b = OPEN[(i + 1) % OPEN.size]
            val inward =
                unit3(add3(mul3(Arcade.X, -(a.x + b.x) / 2), mul3(Arcade.BD, -(a.y + b.y) / 2)))
            val along = unit3(add3(mul3(Arcade.X, b.x - a.x), mul3(Arcade.BD, b.y - a.y)))
            var normal = cross3(along, Arcade.NB)
            if (dot3(normal, inward) < 0) normal = mul3(normal, -1.0)
            if (dot3(normal, Arcade.V) <= 0) null
            else
                minOf(3, dev.sebastiano.anatomy.kit.toneOf(normal, Arcade.P)) to
                    pathOf(
                        listOf(
                                Arcade.bezelPoint(a.x, a.y, -1.0),
                                Arcade.bezelPoint(b.x, b.y, -1.0),
                                Arcade.bezelPoint(b.x, b.y, -5.0),
                                Arcade.bezelPoint(a.x, a.y, -5.0),
                            )
                            .map(Arcade::at),
                        true,
                    )
        }
    val GLARE =
        pathOf(
            listOf(
                    Arcade.bezelPoint(-24.0, -50.0, 0.05),
                    Arcade.bezelPoint(-6.0, -50.0, 0.05),
                    Arcade.bezelPoint(26.0, 50.0, 0.05),
                    Arcade.bezelPoint(8.0, 50.0, 0.05),
                )
                .map(Arcade::at),
            true,
        )
    val LINES: Art = art {
        line(path(OPEN, -1.0), Tone.Lo)
        line(path(round(-32.5, 32.5, -40.5, 40.5, 7.0), -1.0), Tone.Faint)
        line(
            IsoPath.join(
                listOf(
                        Triple(-18.0, 18.0, 46.0),
                        Triple(-12.0, 12.0, 49.6),
                        Triple(-15.0, 15.0, 53.2),
                    )
                    .map { (a, b, w) ->
                        pathOf(
                            listOf(
                                Arcade.at(Arcade.bezelPoint(a, w, -1.0)),
                                Arcade.at(Arcade.bezelPoint(b, w, -1.0)),
                            )
                        )
                    }
            ),
            Tone.Lo,
            free = Free.Both,
        )
    }

    fun paths(): List<String> =
        listOf(OPEN_CLIP.svg(), FACE_CLIP.svg(), FACE_CLIP.svg()) +
            ArcadeScreen.paths() +
            RECESS.map { it.second.svg() } +
            listOf(FACE, flat(OPEN), TRIM, "M-18 46H18M-12 49.6H12M-15 53.2H15", GLARE.svg())
}

internal fun ArcadeBuild.bezel() {
    val f = frameOf(Arcade.bezelPoint(0.0, 0.0), Arcade.X, Arcade.NB, Arcade.BD)
    val shape =
        recorder.solid(
            prism(f, Arcade.rect(-1.6, 0.0, -57.0, 57.0), -60.0, 60.0, ShapeMeta(name = "bezel"))
        )
    put("bezel", Arcade.bezelPoint(0.0, 0.0), listOf(shape), 1.0) {
        solid(
            arcadePrism(
                f,
                listOf(Floor(0.0, -57.0), Floor(0.0, 57.0), Floor(-1.6, 57.0), Floor(-1.6, -57.0)),
                -60.0,
                60.0,
            ),
            Tone.Lo,
            flat = true,
        )
    }
}
