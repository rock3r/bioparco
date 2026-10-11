package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.ParityLines.Companion.fixed
import dev.sebastiano.anatomy.ParityLines.Companion.num
import dev.sebastiano.anatomy.ParityLines.Companion.p2
import dev.sebastiano.anatomy.ParityLines.Companion.p3
import dev.sebastiano.anatomy.ParityLines.Companion.points3
import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.Knot
import dev.sebastiano.anatomy.kit.LatheFill
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Projection
import dev.sebastiano.anatomy.kit.Rims
import dev.sebastiano.anatomy.kit.Smooth
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.angleToward
import dev.sebastiano.anatomy.kit.arcPoints
import dev.sebastiano.anatomy.kit.bandOf
import dev.sebastiano.anatomy.kit.bandOfProfile
import dev.sebastiano.anatomy.kit.bandsOf
import dev.sebastiano.anatomy.kit.cross3
import dev.sebastiano.anatomy.kit.curveRuns
import dev.sebastiano.anatomy.kit.cut
import dev.sebastiano.anatomy.kit.disc
import dev.sebastiano.anatomy.kit.dot3
import dev.sebastiano.anatomy.kit.facingOf
import dev.sebastiano.anatomy.kit.fadeOf
import dev.sebastiano.anatomy.kit.fillet
import dev.sebastiano.anatomy.kit.frameAlong
import dev.sebastiano.anatomy.kit.frameOf
import dev.sebastiano.anatomy.kit.hullOf
import dev.sebastiano.anatomy.kit.lathe
import dev.sebastiano.anatomy.kit.len3
import dev.sebastiano.anatomy.kit.lerp3
import dev.sebastiano.anatomy.kit.lightOf
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.outlineOf
import dev.sebastiano.anatomy.kit.pathLength
import dev.sebastiano.anatomy.kit.pointAlong
import dev.sebastiano.anatomy.kit.pointOf
import dev.sebastiano.anatomy.kit.prismOf
import dev.sebastiano.anatomy.kit.profileCurve
import dev.sebastiano.anatomy.kit.radialOf
import dev.sebastiano.anatomy.kit.resample
import dev.sebastiano.anatomy.kit.ringBand
import dev.sebastiano.anatomy.kit.rodOut
import dev.sebastiano.anatomy.kit.runsOf
import dev.sebastiano.anatomy.kit.saddleOf
import dev.sebastiano.anatomy.kit.saddleStart
import dev.sebastiano.anatomy.kit.scaleOf
import dev.sebastiano.anatomy.kit.silhouetteOf
import dev.sebastiano.anatomy.kit.silhouettePoints
import dev.sebastiano.anatomy.kit.softOf
import dev.sebastiano.anatomy.kit.solidOf
import dev.sebastiano.anatomy.kit.sphereOf
import dev.sebastiano.anatomy.kit.spiral
import dev.sebastiano.anatomy.kit.stadium
import dev.sebastiano.anatomy.kit.stepsFor
import dev.sebastiano.anatomy.kit.sub3
import dev.sebastiano.anatomy.kit.tangentAlong
import dev.sebastiano.anatomy.kit.tangentOf
import dev.sebastiano.anatomy.kit.toneOf
import dev.sebastiano.anatomy.kit.unit3
import dev.sebastiano.anatomy.kit.viewOf
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Holds the Kotlin round parts to the original's `kit/lathe.mjs`. The golden
 * `resources/goldens/lathe.txt` is captured by `anatomy/parity/lathe.mjs` (see the note at its
 * top), which calls the original with the same cases in the same order. Every path must match to
 * the 0.1 the original writes, every number to the digits it prints.
 */
class LatheParityTest {
    @Test
    fun latheMatchesTheOriginal() {
        val out = ParityLines()
        out.vectors()
        out.routes()
        out.profiles()
        for ((name, projection) in CAMERAS) {
            out.framesOf(projection, name)
            out.solidsOf(projection, name)
            out.lathesOf(projection, name)
            out.shapesOf(projection, name)
            LatheLineCases.bandsOf(out, projection, name)
            LatheLineCases.linesOf(out, projection, name)
        }
        val expected =
            javaClass.getResource("/goldens/lathe.txt")!!.readText().trimEnd('\n').split('\n')
        expected.zip(out.lines).forEachIndexed { index, (want, got) ->
            assertEquals(want, got, "line ${index + 1} differs")
        }
        assertEquals(expected.size, out.lines.size, "line count")
    }

    private fun ParityLines.vectors() {
        val a = Point3(1.5, -2.0, 0.75)
        val b = Point3(-0.4, 3.2, 2.5)
        put("dot3", num(dot3(a, b)))
        put("add3", p3(add3(a, b)))
        put("sub3", p3(sub3(a, b)))
        put("mul3", p3(mul3(a, -1.7)))
        put("len3", num(len3(b)))
        put("unit3", p3(unit3(b)))
        put("unit3.zero", p3(unit3(Point3(0.0, 0.0, 0.0))))
        put("cross3", p3(cross3(a, b)))
        put("lerp3", p3(lerp3(a, b, 0.3)))
        for ((score, label) in SCORES) put("bandOf.$label", bandOf(score))
        put("stepsFor.3.undefined.undefined.undefined", stepsFor(3.0))
        put("stepsFor.10.undefined.undefined.undefined", stepsFor(10.0))
        put("stepsFor.40.undefined.undefined.undefined", stepsFor(40.0))
        put("stepsFor.200.undefined.undefined.undefined", stepsFor(200.0))
        put("stepsFor.6.32.160.2", stepsFor(6.0, 32, 160, 2.0))
        put("stepsFor.30.32.160.2.4", stepsFor(30.0, 32, 160, 2.4))
        put("stepsFor.2.24.undefined.undefined", stepsFor(2.0, 24))
        val hull = listOf(0 to 0, 4 to 1, 2 to 2, 5 to 5, 1 to 4, 3 to 3, -1 to 3, 2 to -1)
        put(
            "hullOf",
            hullOf(hull.map { Flat(it.first.toDouble(), it.second.toDouble()) }).joinToString(" ") {
                p2(it)
            },
        )
        put("runsOf.mixed", json(runsOf(listOf(true, true, false, true, false, false, true))))
        put("runsOf.all", json(runsOf(listOf(true, true, true))))
        put("runsOf.none", json(runsOf(listOf(false, false))))
        put("softOf", "${softOf(BELL)} ${softOf(STEPPED)}")
        val fades = listOf(-0.2, 0.04, 0.1, 0.25, 0.42, 0.8)
        put("fadeOf", fades.joinToString(" ") { fixed(fadeOf(it, 0.04 to 0.42), 9) })
        put(
            "saddleStart",
            listOf(0.0, 0.7, 1.6, 3.0).joinToString(" ") { num(saddleStart(10.0, 4.0, it)) },
        )
    }

    private fun json(runs: List<List<Int>>) =
        runs.joinToString(",", "[", "]") { it.joinToString(",", "[", "]") }

    private fun ParityLines.routes() {
        val legs =
            listOf(
                Point3(0.0, 0.0, 0.0),
                Point3(20.0, 0.0, 0.0),
                Point3(20.0, 15.0, 5.0),
                Point3(5.0, 25.0, 5.0),
                Point3(5.0, 25.0, 30.0),
            )
        val projection = Projection(Flat(300.0, 200.0), 2.0)
        put("fillet", points3(fillet(legs, 4.0)))
        put("fillet.tube", points3(fillet(legs, 3.0, 5, projection, tube = 2.0)))
        put("fillet.short", points3(fillet(legs.subList(0, 2), 4.0)))
        put("pathLength", num(pathLength(legs)))
        put("resample", points3(resample(legs, 6.5)))
        put("cut", points3(cut(legs, 12.0, 41.5)))
        put("pointAlong", "${p3(pointAlong(legs, 27.3))} ${p3(pointAlong(legs, 500.0))}")
        put(
            "tangentAlong",
            listOf(0.0, 20.0, 63.0).joinToString(" ") { p3(tangentAlong(legs, it)) },
        )
        val frame = frameAlong(Point3(1.0, 2.0, 3.0), Point3(0.3, 0.2, 0.9))
        put("spiral", points3(spiral(frame, 2.0, 5.0, 9.0, 0.2, 4.0)))
        put("spiral.steps", points3(spiral(frame, 2.0, 5.0, 9.0, 0.2, 4.0, 7)))
        put("arcPoints", points3(arcPoints(frame, 1.0, 6.0, -0.5, 2.5)))
        put("arcPoints.steps", points3(arcPoints(frame, 1.0, 6.0, -0.5, 2.5, 5)))
    }

    private fun ParityLines.profiles() {
        val meridian =
            BELL +
                listOf(
                    Knot(26.0, 0.0),
                    Knot(30.0, 0.0, true),
                    Knot(34.0, 3.0, true),
                    Knot(38.0, 3.0),
                )
        curveRuns(meridian).forEachIndexed { index, curve ->
            fun at(t: Double) = meridian[index].s + (meridian[index + 1].s - meridian[index].s) * t
            put(
                "curveRuns.$index",
                if (curve == null) "-"
                else
                    listOf(0.1, 0.5, 0.9).map(::at).joinToString(" ") {
                        "${num(curve.radius(it))}/${num(curve.slope(it))}"
                    },
            )
        }
        val stepped =
            listOf(
                Knot(0.0, 10.0),
                Knot(5.0, 10.0),
                Knot(8.0, 7.0),
                Knot(18.0, 8.5, true),
                Knot(24.0, 6.0, true),
                Knot(30.0, 6.0),
            )
        val curves =
            listOf(
                "bell" to profileCurve(BELL),
                "stepped" to profileCurve(stepped),
                "analytic" to profileCurve(THROAT, slope = ::throatSlope, radius = ::throat),
            )
        val at = listOf(-2.0, 0.0, 1.3, 4.9, 7.7, 12.2, 19.5, 26.0, 40.0)
        for ((name, curve) in curves) {
            val values = at.joinToString(" ") { "${num(curve.radius(it))}/${num(curve.slope(it))}" }
            put("profileCurve.$name", "${num(curve.s0)} ${num(curve.s1)} ${curve.analytic} $values")
        }
        val error = runCatching { profileCurve(STEPPED) }.exceptionOrNull()
        put("profileCurve.throws", error?.message ?: "")
    }

    private fun ParityLines.framesOf(projection: Projection, name: String) {
        put("$name.view", p3(viewOf(projection)))
        put("$name.light", p3(lightOf(projection)))
        put("$name.scale", num(scaleOf(projection)))
        val normals =
            listOf(
                Point3(1.0, 0.0, 0.0),
                Point3(0.0, 1.0, 0.0),
                Point3(0.0, 0.0, 1.0),
                Point3(-1.0, -1.0, 0.2),
                Point3(0.3, -0.8, 0.5),
            )
        put("$name.toneOf", normals.joinToString(" ") { toneOf(it, projection).toString() })
        for ((frameName, frame) in FRAMES) {
            val at = "$name.$frameName"
            put(
                "$at.frame",
                listOf(frame.o, frame.a, frame.u, frame.v).joinToString(" ") { p3(it) },
            )
            put("$at.pointOf", p3(pointOf(frame, 3.0, 7.0, 1.1)))
            put("$at.radialOf", p3(radialOf(frame, -2.3)))
            put("$at.tangentOf", p3(tangentOf(frame, 0.4)))
            put("$at.angleToward", num(angleToward(frame, viewOf(projection))))
            put(
                "$at.facingOf",
                listOf(0.0, 1.0, 2.5, 4.0).joinToString(" ") {
                    num(facingOf(frame, it, projection, 0.3))
                },
            )
        }
    }

    private fun ParityLines.solidsOf(projection: Projection, name: String) {
        for ((frameName, frame) in FRAMES) {
            val at = "$name.$frameName"
            solid("$at.disc", disc(0.0, 6.0, 10.0, frame, projection, bevel = 1.0))
            solid(
                "$at.ringBand",
                ringBand(0.0, 4.0, 6.0, 14.0, frame, projection, rims = Rims.Ends),
            )
            solid("$at.bell", solidOf(BELL, frame, projection))
            solid(
                "$at.stepped",
                solidOf(STEPPED, frame, projection, bevel = 0.8, rims = Rims.First),
            )
            solid("$at.waist", solidOf(WAIST, frame, projection, rims = Rims.Last))
        }
    }

    private fun ParityLines.lathesOf(projection: Projection, name: String) {
        val f = FRAMES[2].second
        val d = FRAMES[3].second
        val p = projection
        val ring = listOf(Knot(0.0, 6.0), Knot(0.0, 10.0), Knot(12.0, 10.0), Knot(12.0, 6.0))
        solid("$name.hole", lathe(ring, f, p, hole = Knot(12.0, 6.0), rims = Rims.None))
        val cup = listOf(Knot(0.0, 9.0), Knot(6.0, 8.0), Knot(10.0, 5.0), Knot(10.0, 0.0))
        solid("$name.facets", lathe(cup, d, p, fill = LatheFill.Facets, steps = 40))
        solid("$name.hull", solidOf(WAIST, f, p, fill = LatheFill.Hull, steps = 48))
        solid("$name.surface", solidOf(BELL, d, p, fill = LatheFill.Surface))
        val analytic = Smooth(rows = 4, slope = ::throatSlope, radius = ::throat)
        solid("$name.analytic", lathe(THROAT, f, p, smooth = analytic))
        val slopeOnly = Smooth(slope = { -0.3 })
        solid("$name.slopeOnly", lathe(CONE, d, p, smooth = slopeOnly, rims = Rims.First))
        solid("$name.forcedOff", solidOf(BELL, f, p, smooth = null, steps = 36))
        solid("$name.forcedOn", solidOf(STEPPED, d, p, smooth = Smooth()))
        val rows = Smooth(rows = 2, error = 0.6, depth = 5, fineness = 0.3, steps = 30)
        solid("$name.rows", solidOf(BELL, d, p, smooth = rows))
        val open =
            listOf(Knot(2.0, 9.0), Knot(6.0, 8.2, true), Knot(10.0, 7.4, true), Knot(14.0, 7.0))
        solid("$name.bandOfProfile", bandOfProfile(open, f, p, rims = Rims.None))
        val outline = outlineOf(WAIST, f, p, rims = Rims.Ends)
        put("$name.outlineOf.fill", outline.fill)
        put("$name.outlineOf.outline", outline.outline)
        put("$name.silhouetteOf", silhouetteOf(WAIST, f, p))
        put(
            "$name.silhouetteOf.slope",
            silhouetteOf(THROAT, d, p, samples = 30, slope = ::throatSlope, radius = ::throat),
        )
        put(
            "$name.silhouettePoints",
            silhouettePoints(CONE, f, p, samples = 12).joinToString(" | ") { points3(it) },
        )
    }

    private fun ParityLines.shapesOf(projection: Projection, name: String) {
        val f = FRAMES[2].second
        val x = FRAMES[1].second
        val p = projection
        solid("$name.sphere", sphereOf(Point3(4.0, -3.0, 10.0), 7.0, p))
        solid("$name.sphere.steps", sphereOf(Point3(-2.0, 6.0, 3.0), 3.5, p, steps = 24))
        solid("$name.saddle", saddleOf(x, 4.0, 10.0, 14.0, p))
        solid("$name.saddle.start", saddleOf(f, 3.0, { 9 + 0.5 * cos(it) }, 13.0, p, 30))
        val plate = stadium(8.0, 5.0, 18.0)
        put("$name.stadium", plate.joinToString(" ") { p2(Flat(it.x, it.y)) })
        put(
            "$name.stadium.steps",
            stadium(3.0, 2.0, 6.0, 12).joinToString(" ") { p2(Flat(it.x, it.y)) },
        )
        solid("$name.prism.stadium", prismOf(f, plate, 0.0, 4.0, p))
        val square = listOf(Floor(-5.0, -3.0), Floor(5.0, -3.0), Floor(5.0, 3.0), Floor(-5.0, 3.0))
        solid("$name.prism.square", prismOf(x, square, -2.0, 3.0, p))
        val rod = rodOut(f, 0.0, 10.0, 2.5, p, stroke = 6.0)
        solid("$name.rod", rod.paths)
        put("$name.rod.clip", rod.clip)
        put(
            "$name.rod.numbers",
            listOf(rod.from, rod.to, rod.stroke, rod.reach).joinToString(" ") { num(it) },
        )
        solid("$name.rod.still", rodOut(x, 2.0, 9.0, 1.5, p, bevel = 0.4).paths)
    }

    internal companion object {
        val CAMERAS =
            listOf(
                "a45" to Projection(Flat(300.0, 200.0), 2.0),
                "a57" to Projection(Flat(310.0, 190.0), 2.4, azimuth = 57.0),
                "a30e44" to Projection(Flat(290.0, 210.0), 1.8, azimuth = 30.0, elevation = 44.0),
            )
        val FRAMES: List<Pair<String, Frame>> =
            listOf(
                "up" to
                    frameOf(
                        Point3(0.0, 0.0, 0.0),
                        Point3(0.0, 0.0, 1.0),
                        Point3(1.0, 0.0, 0.0),
                        Point3(0.0, 1.0, 0.0),
                    ),
                "x" to frameAlong(Point3(10.0, -5.0, 8.0), Point3(1.0, 0.0, 0.0)),
                "tilt" to frameAlong(Point3(-6.0, 4.0, 12.0), Point3(0.4, -0.7, 0.6)),
                "down" to frameAlong(Point3(3.0, 2.0, 20.0), Point3(0.2, 0.3, -1.0)),
            )
        val SCORES =
            listOf(
                -0.5 to "-0.5",
                0.0 to "0",
                0.1 to "0.1",
                0.2 to "0.2",
                0.5 to "0.5",
                0.62 to "0.62",
                0.9 to "0.9",
            )
        val BELL =
            listOf(
                Knot(0.0, 12.0),
                Knot(3.0, 11.6, true),
                Knot(8.0, 9.8, true),
                Knot(14.0, 7.2, true),
                Knot(20.0, 5.4, true),
                Knot(26.0, 4.8),
            )
        val STEPPED =
            listOf(
                Knot(0.0, 10.0),
                Knot(5.0, 10.0),
                Knot(5.0, 7.0),
                Knot(8.0, 7.0),
                Knot(8.0, 8.5),
                Knot(18.0, 8.5),
            )
        val WAIST =
            listOf(
                Knot(0.0, 10.0),
                Knot(5.0, 7.4, true),
                Knot(10.0, 6.0, true),
                Knot(15.0, 7.4, true),
                Knot(20.0, 10.0),
            )
        val THROAT = listOf(0.0, 4.0, 8.0, 12.0, 16.0, 20.0).map { Knot(it, throat(it)) }
        val CONE = listOf(0.0, 5.0, 10.0, 15.0, 20.0).map { Knot(it, 9 - 0.3 * it) }

        fun throat(s: Double): Double {
            val x = 0.5 * (s - 10)
            return sqrt(36 + x * x)
        }

        fun throatSlope(s: Double): Double = 0.25 * (s - 10) / throat(s)

        fun bell(s: Double): Double {
            val y = 1 - s / 30
            return 4 + 8 * (y * y)
        }

        fun bellSlope(s: Double): Double = -16.0 / 30 * (1 - s / 30)
    }
}
