// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Knot
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Rings
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.audit.BallFlat
import dev.sebastiano.anatomy.kit.audit.Recorder
import dev.sebastiano.anatomy.kit.audit.ShapeMeta
import dev.sebastiano.anatomy.kit.audit.cylinder
import dev.sebastiano.anatomy.kit.audit.slab
import dev.sebastiano.anatomy.kit.cross3
import dev.sebastiano.anatomy.kit.dot3
import dev.sebastiano.anatomy.kit.fillet
import dev.sebastiano.anatomy.kit.fitProjection
import dev.sebastiano.anatomy.kit.frameAlong
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.sub3
import dev.sebastiano.anatomy.kit.turn.Bolt
import dev.sebastiano.anatomy.kit.turn.DetailDot
import dev.sebastiano.anatomy.kit.turn.DetailRing
import dev.sebastiano.anatomy.kit.turn.DetailRule
import dev.sebastiano.anatomy.kit.turn.Details
import dev.sebastiano.anatomy.kit.turn.FreePose
import dev.sebastiano.anatomy.kit.turn.Material
import dev.sebastiano.anatomy.kit.turn.PartStyle
import dev.sebastiano.anatomy.kit.turn.PlaneArt
import dev.sebastiano.anatomy.kit.turn.PlaneLine
import dev.sebastiano.anatomy.kit.turn.RibSeams
import dev.sebastiano.anatomy.kit.turn.RibSpec
import dev.sebastiano.anatomy.kit.turn.RoundEnd
import dev.sebastiano.anatomy.kit.turn.StaticItem
import dev.sebastiano.anatomy.kit.turn.TubeOptions
import dev.sebastiano.anatomy.kit.turn.TurnBuild
import dev.sebastiano.anatomy.kit.turn.TurnValues
import dev.sebastiano.anatomy.kit.turn.Turning
import dev.sebastiano.anatomy.kit.turn.ball
import dev.sebastiano.anatomy.kit.turn.lathe
import dev.sebastiano.anatomy.kit.turn.round
import dev.sebastiano.anatomy.kit.turn.split
import dev.sebastiano.anatomy.kit.turn.tube
import dev.sebastiano.anatomy.kit.unit3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** The lab scene of `parity/turn.mjs`: every group kind and part kind, details and splits. */
internal object TurnLab {
    fun vert(x: Double, y: Double) =
        Frame(
            Point3(x, y, 0.0),
            Point3(0.0, 0.0, 1.0),
            Point3(1.0, 0.0, 0.0),
            Point3(0.0, 1.0, 0.0),
        )

    /** The figures' `frameOf(o, a, u)`: axis [a], `u` made square to it. */
    fun frameOf(o: Point3, a: Point3, u: Point3?): Frame {
        val axis = unit3(a)
        val uu = if (u != null) unit3(sub3(u, mul3(axis, dot3(u, axis)))) else frameAlong(o, axis).u
        return Frame(o, axis, uu, cross3(axis, uu))
    }

    private fun p(x: Double, y: Double, z: Double) = Point3(x, y, z)

    class Lab(val t: Turning, val recorder: Recorder, val built: TurnBuild)

    fun build(): Lab {
        val pp =
            fitProjection(
                listOf(
                    p(-40.0, -40.0, -8.0),
                    p(40.0, 40.0, 40.0),
                    p(-40.0, 40.0, 0.0),
                    p(40.0, -40.0, 0.0),
                ),
                560.0,
                420.0,
                pad = 26.0,
                azimuth = 38.0,
                elevation = 31.0,
            )
        val r = Recorder(pp)
        val t = Turning(pp, r)
        val table = t.turnGroup("table", Floor(4.0, -3.0))
        val arm = t.hingeGroup("arm", p(14.0, 4.0, 22.0), p(0.0, 1.0, 0.0), -12.0 to 12.0, table)
        val slider = t.slideGroup("slider", p(1.0, 0.25, 0.0), 6.0, table)
        val bob = t.freeGroup("bob", p(-10.0, 12.0, 16.0), table)
        val spinner =
            t.hingeGroup("spinner", p(-8.0, -16.0, 0.0), p(0.0, 0.0, 1.0), -30.0 to 30.0, table)
        val low = t.layer("low")
        val top = t.layer("top")
        t.stack(listOf("back", "low", "top", "front"))
        val base =
            t.round(
                "table.base",
                table,
                low,
                vert(4.0, -3.0),
                0.0,
                6.0,
                30.0,
                style = PartStyle(crease = Tone.Lo, material = Material.Steel),
                details =
                    Details(
                        ribs = RibSpec(0.8, 5.2, 90, seams = RibSeams.Both),
                        bolts = listOf(Bolt(6.0, 25.0, 8, size = 0.45)),
                    ),
            )
        t.plane(
            base,
            p(4.0, -3.0, 6.0),
            p(1.0, 0.0, 0.0),
            p(0.0, 1.0, 0.0),
            PlaneArt(listOf(PlaneLine(IsoPath.Empty))),
        )
        t.prism(
            "table.block",
            table,
            top,
            Plan(-4.0, -24.0, 14.0, 9.0, 2.0),
            6.0,
            7.0,
            bevel = 0.6,
            style = PartStyle(crease = Tone.Lo, material = Material.Gold),
            details =
                Details(
                    seams = listOf(8.0),
                    dots = listOf(DetailDot(p(13.0, -1.0, -19.5), p(0.0, 0.0, 1.0), 0.5)),
                ),
        )
        t.prism(
            "table.wedge",
            table,
            top,
            frameOf(p(18.0, -12.0, 8.0), p(0.25, 0.15, 1.0), p(1.0, 0.0, 0.0)),
            listOf(Floor(-3.0, -2.0), Floor(3.0, -2.5), Floor(3.5, 2.0), Floor(-2.0, 3.0)),
            0.0,
            4.0,
            bevel = 0.35,
            style = PartStyle(tone = Tone.Mid, material = Material.Chrome),
        )
        val bar =
            t.prism(
                "table.bar",
                table,
                top,
                Plan(12.0, 10.0, 12.0, 4.0, 0.8),
                6.0,
                3.0,
                style = PartStyle(material = Material.Gunmetal),
            )
        t.split(bar, p(1.0, 0.0, 0.0), 18.0)
        armParts(t, arm, top)
        t.ball(
            "table.knob",
            table,
            top,
            p(-16.0, 2.0, 10.0),
            3.2,
            listOf(BallFlat(p(0.0, 1.0, 0.0), 2.4), BallFlat(p(0.0, -1.0, 0.0), 2.4)),
            style = PartStyle(material = Material.Chrome),
            details = Details(dots = listOf(DetailDot(p(2.4, 0.0, 0.0), p(0.0, 1.0, 0.0), 0.4))),
        )
        t.ball(
            "table.pin",
            table,
            top,
            p(-20.0, -14.0, 8.0),
            2.0,
            style = PartStyle(tone = Tone.Mid, material = Material.Brass),
        )
        val gland = { s: Double -> 2.3 + 0.55 * sqrt(max(0.0, 1 - ((s - 0) / 1.6).pow(2))) }
        t.lathe(
            "table.gland",
            table,
            top,
            frameOf(p(-2.0, 16.0, 12.0), p(1.0, 0.0, 0.0), p(0.0, 0.0, 1.0)),
            listOf(Knot(0.0, gland(0.0)), Knot(0.8, gland(0.8)), Knot(1.6, gland(1.6))),
            radius = gland,
            style = PartStyle(material = Material.Brass),
            details = Details(bolts = listOf(Bolt(1.6, 1.7, 5, size = 0.34))),
        )
        t.prism(
            "table.port",
            table,
            top,
            Plan(-27.0, 18.0, 4.0, 4.0, 0.6),
            6.0,
            6.0,
            style = PartStyle(material = Material.Gunmetal),
        )
        val route =
            fillet(
                listOf(
                    p(-23.0, 20.0, 9.0),
                    p(-14.0, 20.0, 9.0),
                    p(-6.0, 24.0, 9.0),
                    p(6.0, 26.0, 9.0),
                ),
                6.0,
                12,
            )
        t.tube(
            "cable.lab",
            table,
            top,
            route,
            0.8,
            TubeOptions(hue = Tone.Red, gaps = listOf(0.0 to 0.6)),
            PartStyle(material = Material.Rubber),
        )
        t.tube(
            "hose.lab",
            table,
            top,
            listOf(p(8.0, -26.0, 9.0), p(8.0, -8.0, 9.0)),
            0.9,
            TubeOptions(rings = Rings(1.5, 1.6, true, 0.1 to 0.55, tone = Tone.Lo)),
            PartStyle(material = Material.Steel),
        )
        t.prism(
            "slider.carriage",
            slider,
            top,
            Plan(20.0, 14.0, 6.0, 5.0, 1.0),
            6.0,
            2.4,
            bevel = 0.3,
            style = PartStyle(material = Material.Gunmetal),
        )
        val hex =
            (0 until 6).map { j ->
                Floor(1.6 * cos(((2 * j + 1) * PI) / 6), 1.6 * sin(((2 * j + 1) * PI) / 6))
            }
        t.prism(
            "bob.nut",
            bob,
            top,
            Frame(p(-10.0, 12.0, 0.0), p(0.0, 0.0, 1.0), p(1.0, 0.0, 0.0), p(0.0, 1.0, 0.0)),
            hex,
            16.0,
            17.6,
            bevel = 0.2,
            style = PartStyle(material = Material.Brass),
        )
        t.round(
            "bob.cap",
            bob,
            top,
            vert(-10.0, 12.0),
            17.6,
            18.4,
            1.0,
            style = PartStyle(material = Material.Chrome),
            details = Details(bolts = listOf(Bolt(18.4, 0.0, 1, size = 0.4))),
        )
        t.round(
            "spinner.post",
            spinner,
            top,
            vert(-8.0, -16.0),
            6.0,
            9.0,
            1.4,
            style = PartStyle(material = Material.Steel),
        )
        t.prism(
            "spinner.blade",
            spinner,
            top,
            Plan(-7.0, -17.0, 7.0, 2.0, 0.4),
            9.0,
            1.0,
            style = PartStyle(material = Material.Chrome),
        )
        val rod =
            t.round(
                "table.rod",
                table,
                top,
                frameOf(p(22.0, -2.0, 10.0), p(0.0, 1.0, 0.0), p(0.0, 0.0, 1.0)),
                0.0,
                8.0,
                1.2,
                ends = RoundEnd.Dome to RoundEnd.Flat,
                style = PartStyle(material = Material.Steel),
            )
        t.split(rod, p(0.0, 1.0, 0.0), 2.0)
        t.billboard(rod, p(22.0, -2.0, 10.0), p(0.0, -1.0, 0.0), PlaneArt.Empty)
        val floor =
            r.solid(
                slab(
                    Plan(-44.0, -44.0, 88.0, 88.0, 4.0),
                    -6.0,
                    5.0,
                    ShapeMeta(name = "stand.floor"),
                )
            )
        r.put(null, listOf(floor), name = "stand.floor")
        val mast = r.solid(cylinder(-80.0, -80.0, 3.0, -1.0, 12.0, ShapeMeta(name = "stand.mast")))
        r.put(null, listOf(mast), name = "stand.mast")
        val statics =
            listOf(
                StaticItem("stand.floor", "back", listOf(floor)),
                StaticItem("stand.mast", "back", listOf(mast)),
            )
        val built = t.build(statics, poses(), strict = false)
        return Lab(t, r, built)
    }

    private fun armParts(
        t: Turning,
        arm: dev.sebastiano.anatomy.kit.turn.TurnGroup,
        top: dev.sebastiano.anatomy.kit.turn.LiveLayer,
    ) {
        t.round(
            "arm.link",
            arm,
            top,
            frameOf(p(14.0, 4.0, 22.0), p(1.0, 0.0, 0.15), p(0.0, 0.0, 1.0)),
            0.4,
            12.0,
            2.2,
            ends = RoundEnd.Dome to RoundEnd.Dome,
            style = PartStyle(material = Material.Gold),
            details = Details(seams = listOf(3.0, 9.2)),
        )
        t.round(
            "arm.knuckle",
            arm,
            top,
            frameOf(p(14.0, 7.0, 22.0), p(0.0, 1.0, 0.0), p(0.0, 0.0, 1.0)),
            0.0,
            1.2,
            3.0,
            style = PartStyle(material = Material.Chrome),
            details =
                Details(
                    dots = listOf(DetailDot(p(1.2, 0.0, 0.0), p(0.0, 1.0, 0.0), 0.42)),
                    rings = listOf(DetailRing(p(1.2, 0.0, 0.0), 0.9, p(0.0, 1.0, 0.0))),
                    rules =
                        listOf(DetailRule(listOf(p(1.2, -2.0, 0.0), p(1.2, 2.0, 0.0)), Tone.Faint)),
                ),
        )
        t.round(
            "arm.cone",
            arm,
            top,
            frameOf(p(14.0, 4.0, 22.0), p(-1.0, 0.0, 0.3), p(0.0, 0.0, 1.0)),
            2.0,
            6.0,
            2.6,
            1.6,
            style = PartStyle(tone = Tone.Mid, material = Material.Brass),
            details = Details(ribs = RibSpec(2.4, 5.6, 40, twist = 0.4, seams = RibSeams.First)),
        )
        t.round(
            "arm.cap",
            arm,
            top,
            frameOf(p(14.0, 4.0, 22.0), p(0.0, 0.0, 1.0), p(1.0, 0.0, 0.0)),
            3.0,
            4.0,
            1.6,
            ends = RoundEnd.Flat to RoundEnd.Dome,
            style = PartStyle(material = Material.Steel),
        )
    }

    private fun poses(): List<TurnValues> =
        (0 until 360 step 4).map { deg ->
            val d = deg.toDouble()
            val bob =
                if (deg % 40 < 20) null
                else
                    FreePose(
                        q = doubleArrayOf(0.1 * sin(d), 0.2, 0.05, 1.0),
                        t = p(0.5, -0.3, 1 + (deg % 7) / 7.0),
                    )
            TurnValues(
                mapOf(
                    "table" to d,
                    "arm" to 12 * sin(d * 0.05),
                    "slider" to 3 + 3 * sin(d * 0.03),
                    "spinner" to 30 * sin(d * 0.02),
                ),
                mapOf("bob" to bob),
            )
        }

    /** The states the parity drives the lab through. */
    fun states(): List<TurnValues> {
        val out = ArrayList<TurnValues>()
        for (i in 0 until 40) {
            val deg = i * 9.25 - 20
            val bob =
                if (i % 3 == 0) null
                else
                    FreePose(
                        q =
                            doubleArrayOf(
                                0.3 * sin(i.toDouble()),
                                0.2,
                                0.1 * cos(i.toDouble()),
                                1.0,
                            ),
                        t = p(0.4, -0.2, 1.5),
                    )
            out.add(
                TurnValues(
                    mapOf(
                        "table" to deg,
                        "arm" to -12 + (i * 24.0) / 39,
                        "slider" to (i % 7) * 0.9,
                        "spinner" to 30 * sin(i * 0.4),
                    ),
                    mapOf("bob" to bob),
                )
            )
        }
        val fixedValues = mapOf("table" to 33.0, "arm" to 5.0, "slider" to 2.0, "spinner" to 0.0)
        out.add(
            TurnValues(
                fixedValues,
                mapOf(
                    "bob" to FreePose(axis = p(1.0, 1.0, 0.0), angle = 70.0, t = p(0.0, 0.0, 3.0))
                ),
            )
        )
        out.add(
            TurnValues(
                fixedValues,
                mapOf(
                    "bob" to
                        FreePose(
                            r =
                                dev.sebastiano.anatomy.kit.turn.turnRotation(
                                    p(0.0, 1.0, 0.0),
                                    45.0,
                                ),
                            t = p(1.0, 0.0, 2.0),
                            world = true,
                        )
                ),
            )
        )
        return out
    }
}
