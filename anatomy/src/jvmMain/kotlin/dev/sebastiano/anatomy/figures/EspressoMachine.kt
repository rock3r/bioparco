package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.audit.AuditedFigure
import dev.sebastiano.anatomy.kit.audit.Recorder
import dev.sebastiano.anatomy.kit.boxCorners
import dev.sebastiano.anatomy.kit.fitProjection
import dev.sebastiano.anatomy.kit.frameOf
import kotlin.math.PI
import kotlin.math.asin

/*
 * A heat-exchanger espresso machine with its side panel off, in world millimetres: x runs from
 * the back (the water tank) to the front (the group), y from the far side panel to the open side,
 * z up from the drip tray's floor. The boiler lies along x with its near half cut away, so the
 * heat exchanger tube that runs through it shows, with the element under it in the water.
 */
object EspressoMachine : AuditedFigure {
    const val WIDTH = 600.0
    const val HEIGHT = 400.0

    val BASE = Plan(-215.0, -140.0, 510.0, 280.0, 6.0)
    const val BASE_H = 10.0
    const val FOOT_H = 16.0
    const val TOP = 356.0

    /** The panels: far side, back and front, 3 mm steel. */
    const val FAR_Y = -137.0
    const val FRONT_X = 177.0
    const val PANEL = 3.0

    /**
     * The boiler: 116 mm across, 220 long between its end caps (2.2 L inside), cut along y =
     * [BOILER_Y].
     */
    const val BOILER_Y = -15.0
    const val BOILER_Z = 250.0
    const val BOILER_R = 58.0
    const val WALL = 2.0
    const val SHELL_S0 = -120.0
    const val SHELL_S1 = 100.0
    const val CAP = 2.0
    const val WATER_Z = 272.0

    /** The heat exchanger: a 34 mm copper tube through the boiler, behind the cut. */
    const val HX_Y = -37.0
    const val HX_Z = 257.0
    const val HX_R = 17.0
    const val HEAD_R = 20.0
    const val HEAD_T = 10.0

    /** The element's U under the heat exchanger. */
    const val ELEMENT_Y = -33.0
    val ELEMENT_Z = listOf(208.0, 230.0)

    /** The pump, lying along x by the tank. */
    const val PUMP_Y = 40.0
    const val PUMP_Z = 40.0

    /** The group's axis, and its parts' heights. */
    const val GROUP_X = 228.0
    const val PORT_Y = -12.0
    const val UPPER_Z = 296.0
    const val LOWER_Z = 236.0
    const val SPOUT_Z = 164.0
    const val TRAY_Z = 42.0

    /** The steam valve's height and where it goes through the front panel. */
    const val STEAM_Y = 60.0
    const val STEAM_Z = 343.0

    val X = Point3(1.0, 0.0, 0.0)
    val Y = Point3(0.0, 1.0, 0.0)
    val Z = Point3(0.0, 0.0, 1.0)

    /** The boiler's frame: s along x, angle 0 toward the open side, 90° up. */
    val BOILER: Frame = frameOf(Point3(0.0, BOILER_Y, BOILER_Z), X, Y, Z)
    val HX: Frame = frameOf(Point3(0.0, HX_Y, HX_Z), X, Y, Z)

    /** The group's frame: s up, about its axis. */
    val GROUP: Frame = frameOf(Point3(GROUP_X, 0.0, 0.0), Z, X, Y)

    /** The angle of the waterline on the boiler's inside wall. */
    val WATER_ANGLE = PI - asin((WATER_Z - BOILER_Z) / (BOILER_R - WALL))

    /** The angle the fittings on top of the boiler stand at, a little behind the cut. */
    const val TOP_ANGLE = 100 * PI / 180

    val P =
        fitProjection(
            boxCorners(BASE, -FOOT_H, TOP) +
                listOf(Point3(372.0, 0.0, 190.0), Point3(GROUP_X, 20.0, 345.0)),
            WIDTH,
            HEIGHT,
            pad = 30.0,
            azimuth = 84.0,
            elevation = 30.0,
        )

    const val LABEL =
        "A heat-exchanger espresso machine with its side panel off. A copper boiler lies along " +
            "the machine, drawn cut in half so the heat-exchanger tube that runs through it shows, " +
            "with the heating element under it in the water. A vibration pump by the water tank " +
            "pushes fresh water up into the back of the heat exchanger; it comes out of the front " +
            "hot and runs through the upper thermosyphon pipe into the E61 group, and back from " +
            "the group to the exchanger through the lower one. An over-pressure valve returns " +
            "what the puck does not take to the tank. A steam pipe from the top of the boiler " +
            "feeds the steam wand, and a gauge on the group reads the brew pressure."

    /** Everything, recorded and settled: built once. */
    internal val PARTS: Parts by lazy { build().also { it.recorder.settle() } }

    internal fun build(): Parts =
        Parts(P).apply {
            chassis()
            tank()
            pump()
            controls()
            boiler()
            fittings()
            group()
            portafilter()
            cup()
            plumbing()
            wiring()
        }

    override fun record(): Recorder = PARTS.recorder
}

/** Water routes, by name: what the live figure marks the flow on. */
internal object Water {
    const val FEED = "hose.feed"
    const val BREW = "pipe.brew"
    const val RETURN = "hose.return"
    const val UP = "pipe.thermo.up"
    const val DOWN = "pipe.thermo.down"
    const val DRAIN = "pipe.drain"
    const val STEAM = "pipe.steam"
}
