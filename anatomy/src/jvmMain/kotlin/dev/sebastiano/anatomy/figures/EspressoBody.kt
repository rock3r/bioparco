package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.figures.EspressoMachine.BASE
import dev.sebastiano.anatomy.figures.EspressoMachine.BASE_H
import dev.sebastiano.anatomy.figures.EspressoMachine.FAR_Y
import dev.sebastiano.anatomy.figures.EspressoMachine.FOOT_H
import dev.sebastiano.anatomy.figures.EspressoMachine.FRONT_X
import dev.sebastiano.anatomy.figures.EspressoMachine.P
import dev.sebastiano.anatomy.figures.EspressoMachine.PANEL
import dev.sebastiano.anatomy.figures.EspressoMachine.PUMP_Y
import dev.sebastiano.anatomy.figures.EspressoMachine.PUMP_Z
import dev.sebastiano.anatomy.figures.EspressoMachine.TOP
import dev.sebastiano.anatomy.figures.EspressoMachine.X
import dev.sebastiano.anatomy.figures.EspressoMachine.Y
import dev.sebastiano.anatomy.figures.EspressoMachine.Z
import dev.sebastiano.anatomy.kit.Free
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Knot
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Seams
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.arcOf
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.audit.ShapeMeta
import dev.sebastiano.anatomy.kit.audit.box
import dev.sebastiano.anatomy.kit.audit.cylinder
import dev.sebastiano.anatomy.kit.audit.slab
import dev.sebastiano.anatomy.kit.corners
import dev.sebastiano.anatomy.kit.cylinder
import dev.sebastiano.anatomy.kit.depthOf
import dev.sebastiano.anatomy.kit.frameOf
import dev.sebastiano.anatomy.kit.haloOf
import dev.sebastiano.anatomy.kit.insetPlan
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.pathOf
import dev.sebastiano.anatomy.kit.planOutline
import dev.sebastiano.anatomy.kit.ribsOf
import dev.sebastiano.anatomy.kit.segment
import dev.sebastiano.anatomy.kit.sideSeam
import dev.sebastiano.anatomy.kit.slabOf

/*
 * The chassis, the water tank and the pump: everything that stands on the base.
 */

/** The base pan on four feet, the far, back and front panels, and the frame the side hangs on. */
internal fun Parts.chassis() {
    val feet = corners(BASE, 26.0).sortedBy { depthOf(Point3(it.x, it.y, 0.0), P) }
    val baseShape = recorder.solid(slab(BASE, 0.0, BASE_H, ShapeMeta(name = "stand.base"), 6))
    under += art { halo(haloOf(BASE, -FOOT_H, FOOT_H + BASE_H, P)) }
    for (foot in feet) {
        val shape =
            recorder.solid(
                cylinder(foot.x, foot.y, 10.0, -FOOT_H, FOOT_H, ShapeMeta(name = "stand.foot"))
            )
        put("foot", Point3(foot.x, foot.y, -FOOT_H / 2), listOf(shape)) {
            solid(cylinder(foot.x, foot.y, 13.0, -FOOT_H, 2.0, P, 32), tone = Tone.Lo)
            solid(cylinder(foot.x, foot.y, 10.0, -FOOT_H + 2, FOOT_H - 2, P, 32), tone = Tone.Mid)
        }
    }
    put("base", Point3(40.0, 0.0, BASE_H / 2), listOf(baseShape)) {
        solid(
            slabOf(BASE, 0.0, BASE_H, P, 6, 1.2),
            tone = Tone.Lo,
            inner = art { line(sideSeam(BASE, 4.0, P, 6), tone = Tone.Faint) },
        )
        line(planOutline(insetPlan(BASE, 4.0), BASE_H, P, 6), tone = Tone.Faint)
    }
    panels()
    frame()
}

private fun Parts.panels() {
    box(
        "panel.far",
        Point3(BASE.x + PANEL, FAR_Y - PANEL, BASE_H),
        Point3(FRONT_X, FAR_Y, TOP),
        r = 0.4,
    ) {
        // Vent slots low on the far panel, and its screws.
        val slots =
            (0 until 9).map { index ->
                val x = -120.0 + index * 16
                pathOf(
                    listOf(
                            Point3(x, FAR_Y, 24.0),
                            Point3(x + 8, FAR_Y, 24.0),
                            Point3(x + 8, FAR_Y, 46.0),
                            Point3(x, FAR_Y, 46.0),
                        )
                        .map { at(it) },
                    closed = true,
                )
            }
        line(IsoPath.join(slots), tone = Tone.Faint)
        dots(
            listOf(-200.0, -20.0, 160.0).flatMap { x ->
                listOf(at(Point3(x, FAR_Y, 24.0)), at(Point3(x, FAR_Y, 340.0)))
            },
            size = 0.5,
        )
    }
    box(
        "panel.back",
        Point3(BASE.x, FAR_Y - PANEL, BASE_H),
        Point3(BASE.x + PANEL, -FAR_Y + PANEL, TOP),
        r = 0.4,
    )
    box(
        "panel.front",
        Point3(FRONT_X, FAR_Y - PANEL, BASE_H),
        Point3(FRONT_X + PANEL, -FAR_Y + PANEL, TOP),
        r = 0.4,
    )
    switches()
    rating()
}

/** The power switch and its lamp on the front, under the steam wand, and the mains inlet. */
private fun Parts.switches() {
    box(
        "switch",
        Point3(FRONT_X + PANEL, 84.0, 50.0),
        Point3(FRONT_X + PANEL + 3, 100.0, 64.0),
        tone = Tone.Lo,
        r = 1.0,
    ) {
        line(
            segment(
                Point3(FRONT_X + PANEL + 3, 92.0, 50.0),
                Point3(FRONT_X + PANEL + 3, 92.0, 64.0),
                P,
            ),
            tone = Tone.Faint,
        )
    }
    disc(
        "lamp",
        frameOf(Point3(FRONT_X + PANEL, 92.0, 76.0), X, Y, Z),
        0.0,
        2.0,
        3.2,
        tone = Tone.Hi,
        bevel = 0.3,
    )
    // The mains inlet on the back panel, by the tank.
    box(
        "inlet",
        Point3(BASE.x + PANEL, -132.0, 22.0),
        Point3(BASE.x + PANEL + 14, -112.0, 42.0),
        tone = Tone.Lo,
        r = 1.0,
    ) {
        dots(
            listOf(-128.0, -122.0, -116.0).map { at(Point3(BASE.x + PANEL + 14, it, 32.0)) },
            size = 0.6,
            tone = Tone.Hi,
        )
    }
}

/** The rating plate riveted to the far panel, its print standing for the text. */
private fun Parts.rating() {
    val y = FAR_Y + 0.8
    val shape = recorder.solid(slab(Plan(96.0, FAR_Y, 44.0, 0.8, 0.3), 30.0, 26.0))
    put("rating", Point3(118.0, y, 43.0), listOf(shape)) {
        solid(
            slabOf(Plan(96.0, FAR_Y, 44.0, 0.8, 0.3), 30.0, 26.0, P, 2),
            tone = Tone.Lo,
            crease = null,
        )
        for ((index, length) in listOf(30.0, 22.0, 26.0, 16.0).withIndex()) {
            val z = 50.0 - index * 5
            line(
                segment(Point3(100.0, y, z), Point3(100.0 + length, y, z), P),
                tone = Tone.Lo,
                free = Free.Both,
            )
        }
        dots(listOf(at(Point3(137.0, y, 52.0)), at(Point3(137.0, y, 34.0))), size = 0.45)
    }
}

/** The near-side frame the side panel screws to: two posts and two rails, with screw holes. */
private fun Parts.frame() {
    val near = -FAR_Y - 3
    val railX0 = BASE.x + PANEL
    box("rail", Point3(railX0, near, BASE_H), Point3(FRONT_X, near + 6, BASE_H + 6), r = 0.4) {
        dots(
            (0 until 6).map { at(Point3(railX0 + 30 + it * 70.0, near + 6, BASE_H + 3)) },
            size = 0.5,
            tone = Tone.Lo,
        )
    }
    for (x in listOf(railX0, FRONT_X - 6)) {
        box("post", Point3(x, near, BASE_H + 6), Point3(x + 6, near + 6, TOP - 6), r = 0.4)
    }
    box("rail.far", Point3(railX0, FAR_Y, TOP - 6), Point3(FRONT_X, FAR_Y + 6, TOP), r = 0.4)
    for (x in listOf(railX0, FRONT_X - 6)) {
        box("rail.cross", Point3(x, FAR_Y + 6, TOP - 6), Point3(x + 6, near, TOP), r = 0.4)
    }
}

/** The tank's front face, where its two fittings are. */
internal const val TANK_FACE = -163.0

/** The water tank at the back, with its level window and its two fittings. */
internal fun Parts.tank() {
    val plan = Plan(-209.0, -100.0, 46.0, 200.0, 4.0)
    val top = 290.0
    val shape = recorder.solid(slab(plan, BASE_H, top - BASE_H, ShapeMeta(name = "tank"), 4))
    put("tank", Point3(-186.0, 0.0, 150.0), listOf(shape)) {
        solid(
            slabOf(plan, BASE_H, top - BASE_H, P, 4, 0.8),
            tone = Tone.Mid,
            inner = art { line(sideSeam(plan, top - 12, P, 4), tone = Tone.Faint) },
        )
        line(planOutline(insetPlan(plan, 6.0), top, P, 4), tone = Tone.Faint)
    }
    // The level window on the near face, the water column in it, and its scale.
    val y = 100.0
    box(
        "tank.window",
        Point3(-200.0, y, 30.0),
        Point3(-188.0, y + 1.2, 270.0),
        tone = Tone.Lo,
        r = 0.3,
    )
    // The water column in the window, as dark as the boiler's water.
    val front = y + 1.8
    val column =
        listOf(
            Point3(-198.0, front, 32.0),
            Point3(-190.0, front, 32.0),
            Point3(-190.0, front, 214.0),
            Point3(-198.0, front, 214.0),
        )
    put(
        "tank.water",
        Point3(-194.0, front, 123.0),
        listOf(
            recorder.solid(
                box(
                    Point3(-198.0, y + 1.2, 32.0),
                    Point3(-190.0, front, 214.0),
                    ShapeMeta(name = "tank.water"),
                )
            )
        ),
    ) {
        solid(EspressoShell.face(column, Y, P, darker = 2), tone = Tone.Lo, crease = null)
    }
    val ticks =
        (0..12).map { index ->
            val z = 40.0 + index * 18
            val long = index % 4 == 0
            segment(Point3(-188.0, y + 1.2, z), Point3(if (long) -183.0 else -185.0, y + 1.2, z), P)
        }
    put(
        "tank.scale",
        Point3(-186.0, y + 1.3, 150.0),
        listOf(
            recorder.solid(
                box(
                    Point3(-188.0, y, 38.0),
                    Point3(-182.0, y + 1.2, 260.0),
                    ShapeMeta(name = "tank.scale"),
                )
            )
        ),
    ) {
        solid(
            slabOf(Plan(-188.0, y, 6.0, 1.2, 0.2), 38.0, 222.0, P, 2),
            tone = Tone.Lo,
            crease = null,
        )
        line(IsoPath.join(ticks), tone = Tone.Mid)
    }
    // The outlet to the pump and the return from the OPV, on the tank's front face.
    for ((name, y0, z0) in
        listOf(Triple("tank.outlet", PUMP_Y, PUMP_Z), Triple("tank.return", 62.0, 96.0))) {
        val f = frameOf(Point3(TANK_FACE, y0, z0), X, Y, Z)
        disc("$name.boss", f, 0.0, 9.0, 6.5, bevel = 0.4)
    }
}

/** The controller on the floor by the far panel, and the autofill solenoid valve. */
internal fun Parts.controls() {
    box(
        "brain",
        Point3(20.0, -128.0, BASE_H),
        Point3(90.0, -98.0, 58.0),
        r = 1.2,
        steps = 3,
        bevel = 0.5,
    ) {
        dots(
            listOf(
                    Point3(24.0, -98.0, 14.0),
                    Point3(86.0, -98.0, 14.0),
                    Point3(24.0, -98.0, 54.0),
                    Point3(86.0, -98.0, 54.0),
                )
                .map { at(it) },
            size = 0.5,
        )
        line(planOutline(Plan(26.0, -124.0, 58.0, 22.0, 1.0), 58.0, P, 2), tone = Tone.Faint)
    }
    box(
        "solenoid",
        Point3(50.0, -50.0, BASE_H),
        Point3(74.0, -30.0, 40.0),
        r = 1.0,
        steps = 3,
        bevel = 0.4,
    )
    val coil = frameOf(Point3(62.0, -40.0, 0.0), Z, X, Y)
    disc("solenoid.coil", coil, 40.0, 56.0, 8.0, tone = Tone.Hi, bevel = 0.4) {
        dots(listOf(at(Point3(62.0, -40.0, 56.0))), size = 0.6)
    }
}

/** A vibration pump on rubber mounts, its OPV block and valve. */
internal fun Parts.pump() {
    val f = frameOf(Point3(0.0, PUMP_Y, PUMP_Z), X, Y, Z)
    // The bracket and its two rubber grommets the pump's coil rests on.
    box(
        "pump.bracket",
        Point3(-80.0, PUMP_Y - 18, BASE_H),
        Point3(-46.0, PUMP_Y + 18, BASE_H + 3),
        tone = Tone.Lo,
    ) {
        dots(
            listOf(
                at(Point3(-77.0, PUMP_Y + 15, BASE_H + 3)),
                at(Point3(-49.0, PUMP_Y + 15, BASE_H + 3)),
            ),
            size = 0.5,
        )
    }
    for (x in listOf(-72.0, -54.0)) {
        val shape =
            recorder.solid(
                cylinder(x, PUMP_Y, 5.0, BASE_H + 3, 5.0, ShapeMeta(name = "pump.grommet"))
            )
        put("pump.grommet", Point3(x, PUMP_Y, BASE_H + 5.5), listOf(shape)) {
            solid(cylinder(x, PUMP_Y, 5.0, BASE_H + 3, 5.0, P, 24), tone = Tone.Lo)
        }
    }
    disc("pump.inlet", f, -100.0, -92.0, 6.5)
    disc("pump.cap", f, -92.0, -86.0, 10.0, bevel = 0.4)
    val coil =
        disc(
            "pump.coil",
            f,
            -86.0,
            -40.0,
            22.0,
            inner =
                art {
                    faded(
                        ribsOf(
                            f,
                            -80.0,
                            -46.0,
                            22.0,
                            40,
                            P,
                            fade = 0.1 to 0.55,
                            seams = Seams.Both,
                        ),
                        tone = Tone.Lo,
                    )
                },
        )
    turned(
        "pump.body",
        f,
        listOf(Knot(-40.0, 13.0), Knot(-22.0, 13.0)),
        tone = Tone.Hi,
        inner = art { line(arcOf(f, -31.0, 13.0, P), tone = Tone.Faint) },
    )
    disc("pump.hex", f, -22.0, -16.0, 8.0)
    disc("pump.nipple", f, -16.0, -10.0, 4.0)
    // The terminal box on the coil, seated on a pad cut to it.
    val padFrame = frameOf(Point3(-63.0, PUMP_Y, PUMP_Z), mul3(Y, -1.0), Z, X)
    pad("pump.pad", padFrame, 5.0, 22.0, 25.0, coil)
    box(
        "pump.terminals",
        Point3(-69.0, PUMP_Y - 30, PUMP_Z - 6),
        Point3(-57.0, PUMP_Y - 25, PUMP_Z + 6),
        r = 0.8,
    )
    opv()
}

/** The OPV block on the pump's outlet, and the valve on its front face. */
private fun Parts.opv() {
    val f = frameOf(Point3(0.0, PUMP_Y, PUMP_Z), X, Y, Z)
    box(
        "opv.block",
        Point3(-10.0, PUMP_Y - 12, PUMP_Z - 12),
        Point3(14.0, PUMP_Y + 12, PUMP_Z + 12),
        r = 1.0,
        steps = 3,
        bevel = 0.5,
    ) {
        dots(
            listOf(
                at(Point3(-6.0, PUMP_Y + 12, PUMP_Z - 8)),
                at(Point3(10.0, PUMP_Y + 12, PUMP_Z + 8)),
            ),
            size = 0.5,
        )
    }
    val body = disc("opv.body", f, 14.0, 26.0, 7.0, tone = Tone.Hi)
    disc("opv.spring", f, 26.0, 40.0, 5.5, tone = Tone.Hi)
    disc("opv.nut", f, 40.0, 44.0, 6.5)
    disc("opv.screw", f, 44.0, 50.0, 2.5, tone = Tone.Hi)
    pad("opv.pad", frameOf(Point3(20.0, PUMP_Y, PUMP_Z), Z, Y, X), 4.5, 7.0, 10.0, body)
}
