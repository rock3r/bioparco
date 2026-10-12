package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.figures.EspressoMachine.BOILER_R
import dev.sebastiano.anatomy.figures.EspressoMachine.ELEMENT_Y
import dev.sebastiano.anatomy.figures.EspressoMachine.ELEMENT_Z
import dev.sebastiano.anatomy.figures.EspressoMachine.FAR_Y
import dev.sebastiano.anatomy.figures.EspressoMachine.FRONT_X
import dev.sebastiano.anatomy.figures.EspressoMachine.GROUP_X
import dev.sebastiano.anatomy.figures.EspressoMachine.HX_Y
import dev.sebastiano.anatomy.figures.EspressoMachine.HX_Z
import dev.sebastiano.anatomy.figures.EspressoMachine.LOWER_Z
import dev.sebastiano.anatomy.figures.EspressoMachine.P
import dev.sebastiano.anatomy.figures.EspressoMachine.PANEL
import dev.sebastiano.anatomy.figures.EspressoMachine.PORT_Y
import dev.sebastiano.anatomy.figures.EspressoMachine.PUMP_Y
import dev.sebastiano.anatomy.figures.EspressoMachine.PUMP_Z
import dev.sebastiano.anatomy.figures.EspressoMachine.STEAM_Y
import dev.sebastiano.anatomy.figures.EspressoMachine.STEAM_Z
import dev.sebastiano.anatomy.figures.EspressoMachine.TRAY_Z
import dev.sebastiano.anatomy.figures.EspressoMachine.UPPER_Z
import dev.sebastiano.anatomy.figures.EspressoMachine.X
import dev.sebastiano.anatomy.figures.EspressoMachine.Z
import dev.sebastiano.anatomy.kit.Knot
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Rings
import dev.sebastiano.anatomy.kit.Seams
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.frameAlong
import dev.sebastiano.anatomy.kit.frameOf
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.pathLength
import dev.sebastiano.anatomy.kit.radialOf
import dev.sebastiano.anatomy.kit.resample
import dev.sebastiano.anatomy.kit.ribsOf
import dev.sebastiano.anatomy.kit.tangentAlong
import kotlin.math.sqrt

/*
 * The pipework, in the order water runs through it on a shot, and the wiring.
 */

private val TOP_NORMAL = radialOf(EspressoMachine.BOILER, EspressoMachine.TOP_ANGLE)

internal fun Parts.plumbing() {
    // Tank to pump: a braided hose on two barbs, clamped.
    pipe(
        Water.FEED,
        listOf(Point3(TANK_FACE + 9, PUMP_Y, PUMP_Z), Point3(-100.0, PUMP_Y, PUMP_Z)),
        4.5,
        Tone.Mid,
        nutR = 6.0,
        nutT = 3.0,
        bore = 3.0,
        rings = Rings(pitch = 1.4, twist = 1.2, cross = true),
    )
    // Pump to the back of the heat exchanger: copper, up past the tank and in behind the boiler.
    pipe(
        Water.BREW,
        listOf(
            Point3(2.0, PUMP_Y, PUMP_Z + 12),
            Point3(2.0, PUMP_Y, 165.0),
            Point3(-150.0, PUMP_Y, 165.0),
            Point3(-150.0, HX_Y, 165.0),
            Point3(-150.0, HX_Y, HX_Z),
            Point3(
                EspressoMachine.SHELL_S0 - EspressoMachine.CAP - EspressoMachine.HEAD_T - 4,
                HX_Y,
                HX_Z,
            ),
        ),
        3.0,
        bore = 2.0,
    )
    // What the puck doesn't take goes back from the OPV to the tank.
    pipe(
        Water.RETURN,
        listOf(
            Point3(20.0, PUMP_Y, PUMP_Z + 10),
            Point3(20.0, PUMP_Y, 72.0),
            Point3(20.0, 62.0, 96.0),
            Point3(TANK_FACE + 9, 62.0, 96.0),
        ),
        2.5,
        Tone.Mid,
        nutR = 4.2,
        nutT = 2.4,
        bore = 1.75,
    )
    // The thermosyphon: hot water rises from the top of the exchanger's front head to the group,
    // and comes back to it lower down.
    val head = EspressoMachine.SHELL_S1 + EspressoMachine.CAP + EspressoMachine.HEAD_T + 4
    pipe(
        Water.UP,
        listOf(
            Point3(head, HX_Y, HX_Z + 12),
            Point3(head + 11, HX_Y, HX_Z + 12),
            Point3(FRONT_X - 13, PORT_Y, UPPER_Z),
            Point3(FRONT_X, PORT_Y, UPPER_Z),
        ),
        4.0,
        bands = listOf(0.5),
        bore = 3.0,
    )
    pipe(
        Water.DOWN,
        listOf(
            Point3(FRONT_X, PORT_Y, LOWER_Z),
            Point3(FRONT_X - 13, PORT_Y, LOWER_Z),
            Point3(head + 11, HX_Y, HX_Z - 12),
            Point3(head, HX_Y, HX_Z - 12),
        ),
        4.0,
        bore = 3.0,
    )
    steam()
    drain()
    autofill()
}

/** The autofill line: from the OPV block through the solenoid valve up into the boiler. */
private fun Parts.autofill() {
    pipe(
        "pipe.fill.in",
        listOf(Point3(2.0, PUMP_Y - 12, 34.0), Point3(2.0, -40.0, 34.0), Point3(50.0, -40.0, 34.0)),
        2.5,
        nutR = 4.2,
        nutT = 2.0,
    )
    val port = fillPoint(BOILER_R + 7)
    val out = fillPoint(BOILER_R + 15)
    pipe(
        "pipe.fill",
        listOf(
            Point3(74.0, -40.0, 34.0),
            Point3(FILL_X, -40.0, 34.0),
            Point3(FILL_X, out.y, out.z),
            out,
            port,
        ),
        2.5,
        nutR = 4.2,
        nutT = 2.0,
    )
}

/** The steam pipe from the top of the boiler to the valve on the front, and the wand. */
private fun Parts.steam() {
    val start = topPoint(STEAM_X, BOILER_R + 8)
    val up = add3(start, mul3(TOP_NORMAL, 8.0))
    pipe(
        Water.STEAM,
        listOf(
            start,
            up,
            Point3(up.x, up.y, STEAM_Z),
            Point3(140.0, up.y, STEAM_Z),
            Point3(140.0, STEAM_Y, STEAM_Z),
            Point3(FRONT_X, STEAM_Y, STEAM_Z),
        ),
        4.0,
    )
    val x0 = FRONT_X + PANEL
    box(
        "steam.valve",
        Point3(x0, STEAM_Y - 10, STEAM_Z - 12),
        Point3(x0 + 16, STEAM_Y + 10, STEAM_Z + 12),
        r = 2.0,
        steps = 3,
        bevel = 0.5,
    ) {
        dots(
            listOf(
                at(Point3(x0 + 4, STEAM_Y + 10, STEAM_Z + 8)),
                at(Point3(x0 + 12, STEAM_Y + 10, STEAM_Z - 8)),
            ),
            size = 0.5,
        )
    }
    val knob = frameOf(Point3(x0 + 16, STEAM_Y, STEAM_Z), X, EspressoMachine.Y, Z)
    disc("steam.stem", knob, 0.0, 4.0, 2.5, tone = Tone.Hi)
    disc(
        "steam.knob",
        knob,
        4.0,
        16.0,
        9.0,
        tone = Tone.Hi,
        bevel = 0.4,
        inner =
            art {
                faded(
                    ribsOf(knob, 4.0, 16.0, 9.0, 36, P, fade = 0.1 to 0.55, seams = Seams.None),
                    Tone.Lo,
                )
            },
    )
    // The wand swivels on a ball under the valve.
    val ball = Point3(x0 + 8, STEAM_Y, STEAM_Z - 12 - 5)
    ball("wand.ball", ball, 5.0, tone = Tone.Hi)
    val wand =
        pipe(
            "wand",
            listOf(
                add3(ball, mul3(Z, -sqrt(25.0 - 3.5 * 3.5))),
                Point3(ball.x, ball.y, ball.z - 20),
                Point3(ball.x + 26, ball.y + 26, 200.0),
            ),
            3.5,
            nuts = false to false,
        )
    val dense = resample(wand, 2.2)
    val tangent = tangentAlong(dense, pathLength(dense))
    turned(
        "wand.tip",
        frameAlong(wand.last(), tangent),
        listOf(Knot(0.0, 3.5), Knot(4.0, 3.5), Knot(8.0, 2.0)),
        tone = Tone.Hi,
    )
}

/** The group's three-way valve drains to the tray. */
private fun Parts.drain() {
    val out = DRAIN
    val port = Point3(GROUP_X + 36 * out.x, 36 * out.y, 215.0)
    val wide = Point3(GROUP_X + 48 * out.x, 48 * out.y, 215.0)
    pipe(
        Water.DRAIN,
        listOf(port, wide, Point3(wide.x, wide.y, TRAY_Z)),
        2.5,
        nutR = 4.0,
        nutT = 2.0,
        bore = 1.75,
    )
}

/** The pressurestat on the far panel, its sense line, and the wiring. */
internal fun Parts.wiring() {
    box(
        "pressurestat",
        Point3(SENSE_X - 20, FAR_Y, 326.0),
        Point3(SENSE_X + 20, FAR_Y + 30, 350.0),
        r = 1.6,
        steps = 3,
        bevel = 0.5,
    ) {
        dots(
            listOf(
                at(Point3(SENSE_X - 16, FAR_Y + 30, 330.0)),
                at(Point3(SENSE_X + 16, FAR_Y + 30, 346.0)),
            ),
            size = 0.5,
        )
    }
    // Its diaphragm and the adjusting screw that sets the cut-out.
    val face = frameOf(Point3(SENSE_X - 4, FAR_Y + 30, 338.0), EspressoMachine.Y, X, Z)
    disc("pressurestat.dome", face, 0.0, 3.0, 9.0, tone = Tone.Hi, bevel = 0.4)
    disc("pressurestat.screw", face, 3.0, 7.0, 2.6, tone = Tone.Hi)
    val start = topPoint(SENSE_X, BOILER_R + 7)
    val out = add3(start, mul3(TOP_NORMAL, 5.0))
    pipe(
        "pipe.sense",
        listOf(start, out, Point3(SENSE_X, -122.0, out.z), Point3(SENSE_X, -122.0, 326.0)),
        1.6,
        nutR = 3.0,
        nutT = 1.6,
    )
    cables()
}

/** The element's two wires, the level probe's, the pump's, the mains and the solenoid's. */
private fun Parts.cables() {
    val stud = EspressoMachine.SHELL_S1 + EspressoMachine.CAP + 14
    cable(
        "wire.element.a",
        listOf(
            Point3(SENSE_X + 20, -112.0, 344.0),
            Point3(150.0, -112.0, 344.0),
            Point3(150.0, -112.0, ELEMENT_Z[1]),
            Point3(150.0, ELEMENT_Y, ELEMENT_Z[1]),
            Point3(stud, ELEMENT_Y, ELEMENT_Z[1]),
        ),
    )
    cable(
        "wire.element.b",
        listOf(
            Point3(SENSE_X + 20, -120.0, 338.0),
            Point3(156.0, -120.0, 338.0),
            Point3(156.0, -120.0, ELEMENT_Z[0]),
            Point3(156.0, ELEMENT_Y, ELEMENT_Z[0]),
            Point3(stud, ELEMENT_Y, ELEMENT_Z[0]),
        ),
    )
    val probe = topPoint(PROBE_X, BOILER_R + 22)
    val lift = add3(probe, mul3(TOP_NORMAL, 4.0))
    cable(
        "wire.probe",
        listOf(
            probe,
            lift,
            Point3(PROBE_X, lift.y, 347.0),
            Point3(30.0, -124.0, 347.0),
            Point3(30.0, -124.0, 58.0),
        ),
    )
    cable(
        "wire.pump",
        listOf(
            Point3(-63.0, PUMP_Y - 30, PUMP_Z),
            Point3(-63.0, -118.0, PUMP_Z),
            Point3(20.0, -118.0, PUMP_Z),
        ),
    )
    cable(
        "wire.mains",
        listOf(
            Point3(-198.0, -122.0, 32.0),
            Point3(-150.0, -122.0, 32.0),
            Point3(-110.0, -110.0, 16.0),
            Point3(20.0, -110.0, 16.0),
        ),
        r = 1.2,
    )
    cable(
        "wire.solenoid",
        listOf(Point3(62.0, -48.0, 50.0), Point3(62.0, -98.0, 50.0)),
    )
}
