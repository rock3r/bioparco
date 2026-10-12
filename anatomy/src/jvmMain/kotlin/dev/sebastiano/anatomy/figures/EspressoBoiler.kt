package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.figures.EspressoMachine.BOILER
import dev.sebastiano.anatomy.figures.EspressoMachine.BOILER_R
import dev.sebastiano.anatomy.figures.EspressoMachine.BOILER_Y
import dev.sebastiano.anatomy.figures.EspressoMachine.BOILER_Z
import dev.sebastiano.anatomy.figures.EspressoMachine.CAP
import dev.sebastiano.anatomy.figures.EspressoMachine.ELEMENT_Y
import dev.sebastiano.anatomy.figures.EspressoMachine.ELEMENT_Z
import dev.sebastiano.anatomy.figures.EspressoMachine.FAR_Y
import dev.sebastiano.anatomy.figures.EspressoMachine.HEAD_R
import dev.sebastiano.anatomy.figures.EspressoMachine.HEAD_T
import dev.sebastiano.anatomy.figures.EspressoMachine.HX
import dev.sebastiano.anatomy.figures.EspressoMachine.HX_R
import dev.sebastiano.anatomy.figures.EspressoMachine.HX_Y
import dev.sebastiano.anatomy.figures.EspressoMachine.HX_Z
import dev.sebastiano.anatomy.figures.EspressoMachine.P
import dev.sebastiano.anatomy.figures.EspressoMachine.SHELL_S0
import dev.sebastiano.anatomy.figures.EspressoMachine.SHELL_S1
import dev.sebastiano.anatomy.figures.EspressoMachine.TOP_ANGLE
import dev.sebastiano.anatomy.figures.EspressoMachine.WALL
import dev.sebastiano.anatomy.figures.EspressoMachine.WATER_ANGLE
import dev.sebastiano.anatomy.figures.EspressoMachine.WATER_Z
import dev.sebastiano.anatomy.figures.EspressoMachine.X
import dev.sebastiano.anatomy.figures.EspressoMachine.Y
import dev.sebastiano.anatomy.figures.EspressoMachine.Z
import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.Knot
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Seams
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.audit.AuditShape
import dev.sebastiano.anatomy.kit.audit.ShapeMeta
import dev.sebastiano.anatomy.kit.audit.box
import dev.sebastiano.anatomy.kit.audit.prism
import dev.sebastiano.anatomy.kit.dotsOf
import dev.sebastiano.anatomy.kit.frameOf
import dev.sebastiano.anatomy.kit.pointOf
import dev.sebastiano.anatomy.kit.prismOf
import dev.sebastiano.anatomy.kit.radialOf
import dev.sebastiano.anatomy.kit.ribsOf
import dev.sebastiano.anatomy.kit.segment
import dev.sebastiano.anatomy.kit.tangentOf
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The boiler in section: the back half of its shell, its two end caps, the heat exchanger that
 * runs through it from cap to cap, the element's U in the water under it, and the fittings on top.
 */

private const val OWNER = "boiler"

/** The cut half-shell's cross-section: the back half of the ring, as one concave polygon. */
private fun halfRing(rOut: Double, rIn: Double, steps: Int = 48): List<Floor> {
    val outer =
        (0..steps).map { PI / 2 + PI * it / steps }.map { Floor(rOut * cos(it), rOut * sin(it)) }
    val inner =
        (steps downTo 0)
            .map { PI / 2 + PI * it / steps }
            .map { Floor(rIn * cos(it), rIn * sin(it)) }
    return outer + inner
}

private fun halfDisc(r: Double, steps: Int = 48): List<Floor> =
    (0..steps).map { PI / 2 + PI * it / steps }.map { Floor(r * cos(it), r * sin(it)) }

/** The shell, the caps, the heat exchanger and the element. Returns the shell's shape. */
internal fun Parts.boiler() {
    val f = BOILER
    val rIn = BOILER_R - WALL
    val shell =
        recorder.solid(
            prism(
                f,
                halfRing(BOILER_R, rIn),
                SHELL_S0,
                SHELL_S1,
                ShapeMeta(name = "boiler.shell", owner = OWNER),
            )
        )
    shellShape = shell
    put("boiler.shell", Point3(15.0, BOILER_Y - 30, BOILER_Z), listOf(shell)) {
        val dry = EspressoShell.patch(f, SHELL_S0, SHELL_S1, rIn, PI / 2, WATER_ANGLE, true, P)
        val wet =
            EspressoShell.patch(
                f,
                SHELL_S0,
                SHELL_S1,
                rIn,
                WATER_ANGLE,
                3 * PI / 2,
                true,
                P,
                darker = 2,
            )
        solid(dry, tone = Tone.Hi)
        solid(wet, tone = Tone.Hi)
        val outer =
            EspressoShell.patch(f, SHELL_S0, SHELL_S1, BOILER_R, PI / 2, 3 * PI / 2, false, P)
        // The shell's long weld seam runs along the top, end cap to end cap.
        solid(
            outer,
            tone = Tone.Hi,
            inner =
                art {
                    line(
                        segment(
                            pointOf(f, SHELL_S0, BOILER_R, 1.9),
                            pointOf(f, SHELL_S1, BOILER_R, 1.9),
                            P,
                        ),
                        tone = Tone.Faint,
                    )
                },
        )
        solid(
            EspressoShell.cut(f, SHELL_S0, SHELL_S1, rIn, BOILER_R, PI / 2, -1.0, P),
            tone = Tone.Hi,
        )
        solid(
            EspressoShell.cut(f, SHELL_S0, SHELL_S1, rIn, BOILER_R, 3 * PI / 2, 1.0, P),
            tone = Tone.Hi,
        )
    }
    caps()
    exchanger()
    element()
    water()
    brackets()
}

/** The water's level across the cut, in front of the exchanger; the live figure tints it. */
private fun Parts.water() {
    val rIn = BOILER_R - WALL
    val floor = BOILER_Z - rIn
    val water =
        recorder.solid(
            box(
                Point3(SHELL_S0, BOILER_Y - 0.3, floor),
                Point3(SHELL_S1, BOILER_Y, WATER_Z),
                ShapeMeta(name = "boiler.water", owner = OWNER),
            )
        )
    put("boiler.water", Point3(15.0, BOILER_Y, (floor + WATER_Z) / 2), listOf(water)) {
        line(
            segment(Point3(SHELL_S0, BOILER_Y, WATER_Z), Point3(SHELL_S1, BOILER_Y, WATER_Z), P),
            tone = Tone.Lo,
        )
    }
}

/** Two brackets from the far panel hold the boiler by its back. */
private fun Parts.brackets() {
    for (x in listOf(-70.0, 40.0)) {
        box(
            "boiler.bracket",
            Point3(x, FAR_Y, BOILER_Z - 10),
            Point3(x + 20, BOILER_Y - BOILER_R + 0.3, BOILER_Z + 10),
            tone = Tone.Lo,
        ) {
            dots(
                listOf(
                    at(Point3(x + 10, FAR_Y + 6, BOILER_Z + 10)),
                    at(Point3(x + 10, BOILER_Y - BOILER_R - 8, BOILER_Z + 10)),
                ),
                size = 0.55,
            )
        }
    }
}

/** The back cap, seen from inside with the waterline across it, and the front cap. */
private fun Parts.caps() {
    val f = BOILER
    val rIn = BOILER_R - WALL
    val disc = halfDisc(BOILER_R)
    recorder
        .solid(
            prism(
                f,
                disc,
                SHELL_S0 - CAP,
                SHELL_S0,
                ShapeMeta(name = "boiler.cap.back", owner = OWNER),
            )
        )
        .also { shape ->
            put(
                "boiler.cap.back",
                Point3(SHELL_S0 - CAP / 2, BOILER_Y - 25, BOILER_Z),
                listOf(shape),
            ) {
                solid(prismOf(f, disc, SHELL_S0 - CAP, SHELL_S0, P), tone = Tone.Hi)
                // The wet half of the cap's inside face, below the waterline.
                val half = sqrt(rIn * rIn - (WATER_Z - BOILER_Z) * (WATER_Z - BOILER_Z))
                val wet =
                    listOf(
                        Point3(SHELL_S0, BOILER_Y, WATER_Z),
                        Point3(SHELL_S0, BOILER_Y - half, WATER_Z),
                    ) +
                        (0..24).map {
                            pointOf(
                                f,
                                SHELL_S0,
                                rIn,
                                WATER_ANGLE + (3 * PI / 2 - WATER_ANGLE) * it / 24,
                            )
                        }
                solid(EspressoShell.face(wet, X, P, darker = 2), tone = Tone.Hi, crease = null)
            }
        }
    recorder
        .solid(
            prism(
                f,
                disc,
                SHELL_S1,
                SHELL_S1 + CAP,
                ShapeMeta(name = "boiler.cap.front", owner = OWNER),
            )
        )
        .also { shape ->
            put(
                "boiler.cap.front",
                Point3(SHELL_S1 + CAP / 2, BOILER_Y - 25, BOILER_Z),
                listOf(shape),
            ) {
                solid(prismOf(f, disc, SHELL_S1, SHELL_S1 + CAP, P), tone = Tone.Hi)
            }
        }
}

/** The heat exchanger: the tube through the boiler, its heads outside the caps and their ports. */
private fun Parts.exchanger() {
    val f = HX
    val s0 = SHELL_S0
    val s1 = SHELL_S1
    turned(
        "hx.tube",
        f,
        listOf(Knot(s0, HX_R), Knot(s1, HX_R)),
        tone = Tone.Hi,
        crease = Tone.Lo,
        lit = true,
        owner = OWNER,
        inner =
            art {
                line(
                    dev.sebastiano.anatomy.kit.arcOf(f, s0 + 4, HX_R, P) +
                        dev.sebastiano.anatomy.kit.arcOf(f, s1 - 4, HX_R, P),
                    tone = Tone.Lo,
                )
            },
    )
    val front = s1 + CAP
    val back = s0 - CAP
    disc("hx.head.front", f, front, front + HEAD_T, HEAD_R, bevel = 0.5, owner = OWNER) {
        dots(dotsOf(f, front + HEAD_T, HEAD_R - 2.6, 8, P, phase = PI / 8, all = true), size = 0.45)
    }
    disc("hx.head.back", f, back - HEAD_T, back, HEAD_R, bevel = 0.5, owner = OWNER)
    // The ports: hot water out at the top of the front head, the return low on it, and the
    // fresh-water inlet in the middle of the back head.
    for ((name, dz) in listOf("hx.port.out" to 12.0, "hx.port.return" to -12.0)) {
        val port = frameOf(Point3(front + HEAD_T, HX_Y, HX_Z + dz), X, Y, Z)
        disc(name, port, 0.0, 4.0, 7.0, owner = OWNER)
    }
    disc(
        "hx.port.in",
        frameOf(Point3(back - HEAD_T, HX_Y, HX_Z), X, Y, Z),
        -4.0,
        0.0,
        6.0,
        owner = OWNER,
    )
}

/** The element: its flange on the front cap, its terminals, and its U in the water. */
private fun Parts.element() {
    val front = SHELL_S1 + CAP
    val zMid = (ELEMENT_Z[0] + ELEMENT_Z[1]) / 2
    val flange = frameOf(Point3(front, ELEMENT_Y, zMid), X, Y, Z)
    disc("element.flange", flange, 0.0, 4.0, 15.0, bevel = 0.4, owner = OWNER) {
        dots(dotsOf(flange, 4.0, 12.6, 4, P, phase = PI / 4, all = true), size = 0.45)
    }
    for (z in ELEMENT_Z) {
        val stud = frameOf(Point3(front + 4, ELEMENT_Y, z), X, Y, Z)
        disc("element.nut", stud, 0.0, 2.4, 3.4, owner = OWNER)
        disc("element.stud", stud, 2.4, 10.0, 1.6, tone = Tone.Hi, owner = OWNER)
    }
    val u =
        listOf(
            Point3(SHELL_S1, ELEMENT_Y, ELEMENT_Z[0]),
            Point3(-75.0, ELEMENT_Y, ELEMENT_Z[0]),
            Point3(-75.0, ELEMENT_Y, ELEMENT_Z[1]),
            Point3(SHELL_S1, ELEMENT_Y, ELEMENT_Z[1]),
        )
    pipe(
        "element",
        u,
        4.0,
        Tone.Mid,
        nuts = false to false,
        bend = 2.6,
        maxLength = 30.0,
        touch = listOf(OWNER),
    )
}

/** Where the fittings on top stand along the boiler. */
internal const val SENSE_X = -85.0
internal const val PROBE_X = -45.0
internal const val STEAM_X = 80.0

/** Where the autofill line comes into the boiler: under it, behind the cut. */
internal const val FILL_X = 90.0
internal const val FILL_ANGLE = 250 * PI / 180

/** Where the fill line leaves the boiler's inlet, [s] out from the axis. */
internal fun fillPoint(s: Double): Point3 = pointOf(BOILER, FILL_X, s, FILL_ANGLE)

/** A fitting's frame on top of the boiler at [x]: s out along the radius from the axis. */
private fun topFrame(x: Double): Frame =
    frameOf(
        Point3(x, BOILER_Y, BOILER_Z),
        radialOf(BOILER, TOP_ANGLE),
        tangentOf(BOILER, TOP_ANGLE),
        X,
    )

/** Where a fitting at [x] is [s] out from the boiler's axis. */
internal fun topPoint(x: Double, s: Double): Point3 = pointOf(topFrame(x), s, 0.0, 0.0)

/** The fittings on top of the boiler, each on a saddle pad cut to the shell. */
internal fun Parts.fittings() {
    val shell: AuditShape = shellShape!!
    val top = BOILER_R + 3
    fun seat(name: String, x: Double, a: Double = 6.0): Frame {
        val f = topFrame(x)
        pad("$name.pad", f, a, BOILER_R, top, shell)
        return f
    }
    // The pressurestat's sense line, the level probe, the vacuum breaker, the safety valve and
    // the steam outlet.
    seat("sense", SENSE_X).let { disc("sense.nut", it, top, top + 4, 4.6, owner = OWNER) }
    seat("probe", PROBE_X).let {
        disc("probe.hex", it, top, top + 6, 5.5, owner = OWNER)
        disc("probe.insulator", it, top + 6, top + 16, 3.4, tone = Tone.Hi, owner = OWNER)
        disc("probe.terminal", it, top + 16, top + 19, 2.0, owner = OWNER)
    }
    seat("breaker", -10.0).let {
        disc("breaker.hex", it, top, top + 5, 6.0, owner = OWNER)
        turned(
            "breaker.cap",
            it,
            (0..8).map { k ->
                Knot(top + 5 + k, 4.6 * sqrt(1 - (k / 8.5) * (k / 8.5)) + 0.4, k in 1..7)
            },
            owner = OWNER,
        )
    }
    seat("safety", 25.0, 7.0).let {
        disc("safety.hex", it, top, top + 5, 7.0, owner = OWNER)
        disc("safety.body", it, top + 5, top + 19, 5.5, tone = Tone.Hi, owner = OWNER)
        disc(
            "safety.cap",
            it,
            top + 19,
            top + 25,
            6.5,
            owner = OWNER,
            inner =
                art {
                    faded(
                        ribsOf(
                            it,
                            top + 19,
                            top + 25,
                            6.5,
                            28,
                            P,
                            fade = 0.1 to 0.55,
                            seams = Seams.None,
                        ),
                        tone = Tone.Lo,
                    )
                },
        )
    }
    // The autofill inlet, under the boiler toward the back.
    val fill =
        frameOf(
            Point3(FILL_X, BOILER_Y, BOILER_Z),
            radialOf(BOILER, FILL_ANGLE),
            tangentOf(BOILER, FILL_ANGLE),
            X,
        )
    pad("fill.pad", fill, 6.0, BOILER_R, top, shell)
    disc("fill.nut", fill, top, top + 4, 6.0, owner = OWNER)
    seat("steam", STEAM_X, 7.5).let { disc("steam.nut", it, top, top + 5, 7.2, owner = OWNER) }
}
