package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.figures.EspressoMachine.BASE_H
import dev.sebastiano.anatomy.figures.EspressoMachine.FRONT_X
import dev.sebastiano.anatomy.figures.EspressoMachine.GROUP
import dev.sebastiano.anatomy.figures.EspressoMachine.GROUP_X
import dev.sebastiano.anatomy.figures.EspressoMachine.P
import dev.sebastiano.anatomy.figures.EspressoMachine.PANEL
import dev.sebastiano.anatomy.figures.EspressoMachine.SPOUT_Z
import dev.sebastiano.anatomy.figures.EspressoMachine.TRAY_Z
import dev.sebastiano.anatomy.figures.EspressoMachine.X
import dev.sebastiano.anatomy.figures.EspressoMachine.Y
import dev.sebastiano.anatomy.figures.EspressoMachine.Z
import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Knot
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Seams
import dev.sebastiano.anatomy.kit.Smooth
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.audit.ShapeMeta
import dev.sebastiano.anatomy.kit.cross3
import dev.sebastiano.anatomy.kit.frameAlong
import dev.sebastiano.anatomy.kit.frameOf
import dev.sebastiano.anatomy.kit.insetPlan
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.pathOf
import dev.sebastiano.anatomy.kit.planOutline
import dev.sebastiano.anatomy.kit.pointOf
import dev.sebastiano.anatomy.kit.ribsOf
import dev.sebastiano.anatomy.kit.unit3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The E61 group on the front panel with its lever and gauge, the portafilter locked into it, the
 * cup on the drip tray under it.
 */

private const val GROUP_OWNER = "group"
private const val LEVER = "lever"
private const val GAUGE_OWNER = "gauge"
private const val PORTAFILTER = "portafilter"

/** The group body's bulge, in mm from its axis, and its slope. */
internal fun groupRadius(s: Double): Double = 31.5 + 2.5 * sin(PI * (s - 218) / 52)

internal fun groupSlope(s: Double): Double = 2.5 * PI / 52 * cos(PI * (s - 218) / 52)

/** The cup's outside, from its foot to its rim. */
internal fun cupRadius(z: Double): Double =
    15 + 13 * sin(PI / 2 * (z - CUP_FOOT) / (CUP_RIM - CUP_FOOT))

internal fun cupSlope(z: Double): Double =
    13 * PI / 2 / (CUP_RIM - CUP_FOOT) * cos(PI / 2 * (z - CUP_FOOT) / (CUP_RIM - CUP_FOOT))

internal const val CUP_FOOT = 48.0
internal const val CUP_RIM = 94.0

/** The lever's pivot, and the gauge's frame on the top of the group. */
internal val PIVOT = Point3(GROUP_X, 31.5, 287.0)
internal const val HUB_R = 4.5
internal const val ARM_R = 2.6
internal val GAUGE_BALL = Point3(GROUP_X, 0.0, 314.0)
internal val GAUGE_NORMAL = unit3(Point3(0.0, sin(40 * PI / 180), cos(40 * PI / 180)))
internal val GAUGE: Frame =
    frameOf(GAUGE_BALL, GAUGE_NORMAL, X, mul3(cross3(GAUGE_NORMAL, X), -1.0))
internal const val DIAL_S = 22.5
internal const val DIAL_R = 17.5

/** The gauge's needle angle for [bar], from 0 at lower left clockwise to 16 at lower right. */
internal fun dialAngle(bar: Double): Double = (225 - 270 * bar / 16) * PI / 180

internal fun Parts.group() {
    box(
        "group.block",
        Point3(FRONT_X + PANEL, -22.0, 222.0),
        Point3(GROUP_X - 34.0, 22.0, 302.0),
        r = 1.0,
        steps = 3,
        bevel = 0.5,
        meta = ShapeMeta(owner = GROUP_OWNER),
    ) {
        dots(listOf(at(Point3(190.0, 22.0, 228.0)), at(Point3(190.0, 22.0, 296.0))), size = 0.55)
    }
    val g = GROUP
    disc(
        "group.collar",
        g,
        196.0,
        206.0,
        28.0,
        owner = GROUP_OWNER,
        inner =
            art {
                faded(
                    ribsOf(g, 196.0, 206.0, 28.0, 48, P, fade = 0.1 to 0.55, seams = Seams.None),
                    Tone.Lo,
                )
            },
    )
    disc("group.neck", g, 206.0, 212.0, 22.0, owner = GROUP_OWNER)
    named["group.ring"] =
        disc("group.ring", g, 212.0, 218.0, 32.0, bevel = 0.5, owner = GROUP_OWNER)
    turned(
        "group.body",
        g,
        (0..26).map { Knot(218 + 2.0 * it, groupRadius(218 + 2.0 * it), it in 1..25) },
        tone = Tone.Hi,
        smooth = Smooth(slope = ::groupSlope, radius = ::groupRadius),
        radius = ::groupRadius,
        owner = GROUP_OWNER,
    )
    disc("group.shoulder", g, 270.0, 278.0, 25.0, owner = GROUP_OWNER)
    val housing = disc("group.housing", g, 278.0, 296.0, 19.0, owner = GROUP_OWNER)
    disc("group.cap", g, 296.0, 302.0, 21.0, bevel = 0.5, owner = GROUP_OWNER)
    // The lever's pivot on a pad cut to the cam housing.
    pad("lever.pad", frameOf(Point3(GROUP_X, 0.0, PIVOT.z), Y, X, Z), 6.0, 19.0, 24.0, housing)
    val axle = frameOf(Point3(GROUP_X, 0.0, PIVOT.z), Y, X, Z)
    disc("lever.pin", axle, 24.0, 29.0, 3.0, tone = Tone.Hi, owner = LEVER)
    disc("lever.hub", axle, 29.0, 34.0, HUB_R, tone = Tone.Hi, bevel = 0.3, owner = LEVER)
    lever(LEVER_DOWN)
    // The drain port low on the ring, toward the open side.
    val drain = DRAIN
    val ring = named.getValue("group.ring")
    pad(
        "group.drain",
        frameOf(Point3(GROUP_X, 0.0, 215.0), drain, cross3(Z, drain), Z),
        4.2,
        32.0,
        36.0,
        ring,
    )
    gauge()
}

/** The way the drain port faces, on the group's lower ring: behind it, away from the wand. */
internal val DRAIN = unit3(Point3(-0.6, -0.8, 0.0))

/** The lever's angle up from the floor toward the front: down at rest, raised for a shot. */
internal const val LEVER_DOWN = -38 * PI / 180
internal const val LEVER_UP = 42 * PI / 180
internal const val LEVER_ARM = 50.0
internal val ARM_START = sqrt(HUB_R * HUB_R - ARM_R * ARM_R)
internal const val KNOB_R = 6.5

/** The lever's direction at [angle]. */
internal fun leverDirection(angle: Double): Point3 = Point3(cos(angle), 0.0, sin(angle))

/** The knob's centre with the lever at [angle]: the arm's rim lands on the ball. */
internal fun knobAt(angle: Double): Point3 =
    add3(PIVOT, mul3(leverDirection(angle), LEVER_ARM + sqrt(KNOB_R * KNOB_R - ARM_R * ARM_R)))

/** The lever at rest, recorded; the live figure draws it at its angle in the same slot. */
private fun Parts.lever(angle: Double) {
    val d = leverDirection(angle)
    val arm = frameAlong(PIVOT, d)
    disc("lever.arm", arm, ARM_START, LEVER_ARM, ARM_R, tone = Tone.Hi, owner = LEVER)
    ball("lever.knob", knobAt(angle), KNOB_R, tone = Tone.Lo, owner = LEVER)
}

/** The brew gauge on its swivel on the group's top, its face toward the reader. */
private fun Parts.gauge() {
    val g = GROUP
    val reach = sqrt(4.6 * 4.6 - 3.4 * 3.4)
    disc("gauge.stem", g, 302.0, GAUGE_BALL.z - reach, 3.4, owner = GAUGE_OWNER)
    ball("gauge.ball", GAUGE_BALL, 4.6, tone = Tone.Hi, owner = GAUGE_OWNER)
    val f = GAUGE
    disc("gauge.neck", f, reach, 9.0, 3.4, owner = GAUGE_OWNER)
    disc("gauge.case", f, 9.0, 19.0, 19.0, owner = GAUGE_OWNER)
    disc(
        "gauge.bezel",
        f,
        19.0,
        22.0,
        20.5,
        bevel = 0.4,
        inner =
            art {
                faded(
                    ribsOf(f, 19.0, 22.0, 20.5, 60, P, fade = 0.1 to 0.55, seams = Seams.None),
                    Tone.Lo,
                )
            },
    )
    disc("gauge.face", f, 22.0, DIAL_S, DIAL_R, tone = Tone.Hi) {
        val minor = ArrayList<IsoPath>()
        val major = ArrayList<IsoPath>()
        for (step in 0..32) {
            val a = dialAngle(step * 0.5)
            val long = step % 4 == 0
            val inner = if (long) 13.6 else 15.2
            val path =
                pathOf(listOf(at(pointOf(f, DIAL_S, 17.0, a)), at(pointOf(f, DIAL_S, inner, a))))
            if (long) major.add(path) else minor.add(path)
        }
        line(IsoPath.join(minor), tone = Tone.Lo)
        line(IsoPath.join(major), tone = Tone.Mid)
    }
}

/** The portafilter locked into the collar: its basket holder, spouts and handle. */
internal fun Parts.portafilter() {
    val g = GROUP
    disc("portafilter.body", g, 182.0, 196.0, 29.0, bevel = 0.4, owner = PORTAFILTER)
    disc("portafilter.spout", g, 172.0, 182.0, 9.0)
    for (y in listOf(-6.0, 6.0)) {
        val f = frameOf(Point3(GROUP_X, y, 172.0), mul3(Z, -1.0), X, Y)
        disc("portafilter.nozzle", f, 0.0, 172.0 - SPOUT_Z, 2.2)
    }
    val h = unit3(Point3(1.0, 0.0, -0.1))
    val handle = frameAlong(Point3(GROUP_X, 0.0, 189.0), h)
    disc("portafilter.neck", handle, sqrt(29.0 * 29.0 - 5.0 * 5.0), 58.0, 5.0, owner = PORTAFILTER)
    fun grip(s: Double) = 7.5 + 2 * sin(PI * (s - 58) / 80)
    fun gripSlope(s: Double) = 2 * PI / 80 * cos(PI * (s - 58) / 80)
    turned(
        "portafilter.grip",
        handle,
        (0..20).map { Knot(58 + 4.0 * it, grip(58 + 4.0 * it), it in 1..19) },
        tone = Tone.Lo,
        smooth = Smooth(slope = ::gripSlope, radius = ::grip),
        radius = ::grip,
        owner = PORTAFILTER,
    )
}

/** The drip tray and its grate, the saucer and the cup, and the cup's handle. */
internal fun Parts.cup() {
    val tray = Plan(FRONT_X + PANEL, -110.0, 112.0, 220.0, 4.0)
    box(
        "tray",
        Point3(tray.x, tray.y, BASE_H),
        Point3(tray.x + tray.w, tray.y + tray.d, TRAY_Z - 2),
        r = 4.0,
        steps = 4,
        bevel = 0.6,
    )
    val grate = insetPlan(tray, 3.0)
    box(
        "tray.grate",
        Point3(grate.x, grate.y, TRAY_Z - 2),
        Point3(grate.x + grate.w, grate.y + grate.d, TRAY_Z),
        tone = Tone.Lo,
        r = 2.0,
        steps = 3,
    ) {
        val slots =
            (0 until 8).map { index ->
                planOutline(
                    Plan(grate.x + 8 + index * 12.5, grate.y + 8, 6.0, grate.d - 16, 3.0),
                    TRAY_Z,
                    P,
                    3,
                )
            }
        line(IsoPath.join(slots), tone = Tone.Faint)
    }
    val g = GROUP
    disc("saucer", g, TRAY_Z, TRAY_Z + 3, 38.0, tone = Tone.Hi, bevel = 0.6)
    disc("cup.foot", g, TRAY_Z + 3, CUP_FOOT, 14.0, tone = Tone.Hi)
    turned(
        "cup.body",
        g,
        (0..23).map { Knot(CUP_FOOT + 2.0 * it, cupRadius(CUP_FOOT + 2.0 * it), it in 1..22) },
        tone = Tone.Hi,
        smooth = Smooth(slope = ::cupSlope, radius = ::cupRadius),
        radius = ::cupRadius,
    )
    fun onCup(z: Double) = Point3(GROUP_X + cupRadius(z), 0.0, z)
    fun outOf(z: Double, by: Double) =
        add3(onCup(z), mul3(unit3(Point3(1.0, 0.0, -cupSlope(z))), by))
    val handle = listOf(onCup(86.0), outOf(86.0, 12.0), outOf(62.0, 12.0), onCup(62.0))
    pipe("cup.handle", handle, 2.2, Tone.Hi, nuts = false to false, bend = 2.4, maxLength = 10.0)
}
