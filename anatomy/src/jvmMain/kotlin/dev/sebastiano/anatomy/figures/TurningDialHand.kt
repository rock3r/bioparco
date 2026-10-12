// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.figures.TurningDial.COVER_TOP
import dev.sebastiano.anatomy.figures.TurningDial.FACE_Z
import dev.sebastiano.anatomy.figures.TurningDial.PALM
import dev.sebastiano.anatomy.figures.TurningDial.frameOf
import dev.sebastiano.anatomy.figures.TurningDialParts.Companion.CABLE_Z
import dev.sebastiano.anatomy.figures.TurningDialParts.Companion.CLAMP_T
import dev.sebastiano.anatomy.figures.TurningDialParts.Companion.CLAMP_X
import dev.sebastiano.anatomy.figures.TurningDialParts.Companion.FZ
import dev.sebastiano.anatomy.figures.TurningDialParts.Companion.PORTS
import dev.sebastiano.anatomy.figures.TurningDialParts.Companion.PORT_X
import dev.sebastiano.anatomy.figures.TurningDialParts.Companion.TERM
import dev.sebastiano.anatomy.figures.TurningDialParts.Companion.UP
import dev.sebastiano.anatomy.figures.TurningDialParts.Companion.X_AXIS
import dev.sebastiano.anatomy.figures.TurningDialParts.Companion.Z_AXIS
import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Rings
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.cross3
import dev.sebastiano.anatomy.kit.fillet
import dev.sebastiano.anatomy.kit.len3
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.pathLength
import dev.sebastiano.anatomy.kit.sub3
import dev.sebastiano.anatomy.kit.turn.DetailDot
import dev.sebastiano.anatomy.kit.turn.DetailRing
import dev.sebastiano.anatomy.kit.turn.Details
import dev.sebastiano.anatomy.kit.turn.Material
import dev.sebastiano.anatomy.kit.turn.PartStyle
import dev.sebastiano.anatomy.kit.turn.RoundEnd
import dev.sebastiano.anatomy.kit.turn.TubeOptions
import dev.sebastiano.anatomy.kit.turn.TurnGroup
import dev.sebastiano.anatomy.kit.turn.jsCos
import dev.sebastiano.anatomy.kit.turn.jsSin
import dev.sebastiano.anatomy.kit.turn.round
import dev.sebastiano.anatomy.kit.turn.tube
import dev.sebastiano.anatomy.kit.unit3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.tan

/** The hand's three fingers (one of three hinges, which lifts), its cables and its hose. */
internal class TurningDialHand(private val parts: TurningDialParts) {
    private val t = parts.t

    private class Finger(
        val id: Int,
        val y: Double,
        val psi: Double,
        val port: Boolean = false,
        val cable: String? = null,
        val guide: Boolean = false,
        val flex: Boolean = false,
    )

    private class Tip(val c3: Point3, val e3: Point3, val j2: Point3)

    fun fingers() {
        for (finger in FINGERS) finger(finger)
    }

    private fun finger(finger: Finger) {
        val psi = finger.psi * D2R
        val d1 = Point3(jsCos(psi), jsSin(psi), 0.0)
        val w = Point3(-jsSin(psi), jsCos(psi), 0.0)
        fun pitch(a: Double) = Point3(jsCos(a) * jsCos(psi), jsCos(a) * jsSin(psi), -jsSin(a))
        val b = Point3(PALM.x + PALM.w, finger.y, FZ)
        val r0 = 3.7
        val e0 = r0 + abs(tan(psi)) * (RL + DISC_T)
        val c1 = add3(b, mul3(d1, e0))
        val e1 = add3(c1, mul3(d1, LENGTHS[0]))
        val d2 = pitch(PITCH)
        val c2 = add3(e1, mul3(unit3(add3(d1, d2)), 2 * RL + GAP))
        val j1 = mul3(add3(e1, c2), 0.5)
        val e2 = add3(c2, mul3(d2, LENGTHS[1]))
        fun tipOf(a3: Double): Tip {
            val d3 = pitch(a3)
            val c3 = add3(e2, mul3(unit3(add3(d2, d3)), 2 * RL + GAP))
            return Tip(c3, add3(c3, mul3(d3, LENGTHS[2])), mul3(add3(e2, c3), 0.5))
        }
        var lo = 0.0
        var hi = 70 * D2R
        repeat(60) {
            val mid = (lo + hi) / 2
            if (tipOf(mid).e3.z - RL > FACE_Z) lo = mid else hi = mid
        }
        val (c3, e3, j2) = tipOf(hi).let { Triple(it.c3, it.e3, it.j2) }
        val tag = "hand.f${finger.id}"
        val dial = parts.dial
        val g1 =
            if (finger.flex) t.hingeGroup("f${finger.id}.mcp", c1, w, -22.0 to 0.0, dial) else dial
        val g2 =
            if (finger.flex) t.hingeGroup("f${finger.id}.pip", j1, w, -14.0 to 0.0, g1) else dial
        val g3 =
            if (finger.flex) t.hingeGroup("f${finger.id}.dip", j2, w, -9.0 to 0.0, g2) else dial
        fun link(name: String, c: Point3, e: Point3, seams: List<Double>, group: TurnGroup) =
            t.round(
                "$tag.$name",
                group,
                parts.hand,
                frameOf(c, sub3(e, c), Z_AXIS),
                0.0,
                len3(sub3(e, c)),
                RL,
                ends = RoundEnd.Dome to RoundEnd.Dome,
                style = PartStyle(tone = Tone.Hi, crease = Tone.Lo, material = Material.Gold),
                details = Details(seams = seams),
            )
        link("p1", c1, e1, listOf(1.2, LENGTHS[0] - 1.2), g1)
        link("p2", c2, e2, listOf(1.0, LENGTHS[1] - 1.0), g2)
        val tipLink = link("p3", c3, e3, listOf(0.9), g3)
        if (finger.flex) {
            val d3 = unit3(sub3(e3, c3))
            val led = add3(e3, mul3(d3, RL))
            parts.led = TurningDial.Led(led, d3, tipLink.name, g3.name)
            t.billboard(tipLink, led, d3, parts.ledArt(), 0.02 to 0.3)
        }
        fun discs(name: String, j: Point3, rDisc: Double, group: TurnGroup) {
            for (side in listOf(-1.0, 1.0)) {
                val centre = add3(j, mul3(w, side * (RL + DISC_T / 2)))
                val normal = mul3(w, side)
                t.round(
                    "$tag.$name${if (side > 0) "+" else "-"}",
                    group,
                    parts.hand,
                    frameOf(centre, normal, Z_AXIS),
                    -DISC_T / 2,
                    DISC_T / 2,
                    rDisc,
                    style = PartStyle(tone = Tone.Mid, material = Material.Chrome),
                    details =
                        Details(
                            dots = listOf(DetailDot(Point3(DISC_T / 2, 0.0, 0.0), normal, 0.42)),
                            rings =
                                listOf(
                                    DetailRing(Point3(DISC_T / 2, 0.0, 0.0), 0.85, normal, Tone.Lo)
                                ),
                        ),
                )
            }
        }
        discs("k0", c1, r0, dial)
        discs("k1", j1, 3.5, g1)
        discs("k2", j2, 3.3, g2)
        if (finger.port) junction(finger, tag, c1, e1, d1, w)
        if (finger.guide) {
            val n = unit3(cross3(d2, w))
            val up = if (n.z > 0) n else mul3(n, -1.0)
            t.prism(
                "$tag.guide",
                g2,
                parts.hand,
                dev.sebastiano.anatomy.kit.Frame(c2, d2, w, up),
                listOf(
                    Floor(-1.5, RL),
                    Floor(1.5, RL),
                    Floor(1.15, RL + 0.9),
                    Floor(-1.15, RL + 0.9),
                ),
                1.1,
                LENGTHS[1] - 1.1,
                bevel = 0.25,
                style = PartStyle(tone = Tone.Mid, material = Material.Gunmetal),
            )
        }
    }

    private fun junction(
        finger: Finger,
        tag: String,
        c1: Point3,
        e1: Point3,
        d1: Point3,
        w: Point3,
    ) {
        val m = mul3(add3(c1, e1), 0.5)
        val ring =
            listOf(Floor(-2.1, -1.7), Floor(2.1, -1.7), Floor(2.1, 1.7), Floor(-2.1, 1.7)).map {
                (a, b) ->
                Floor(m.x + d1.x * a + w.x * b, m.y + d1.y * a + w.y * b)
            }
        val z0 = m.z + RL
        t.prism(
            "$tag.junction",
            parts.dial,
            parts.hand,
            UP,
            ring,
            z0,
            z0 + JUNCTION_H,
            bevel = 0.3,
            style = PartStyle(tone = Tone.Mid, material = Material.Gunmetal),
            details = Details(seams = listOf(z0 + 1)),
        )
        val back = Point3(m.x - d1.x * 2.1, m.y - d1.y * 2.1, z0 + JUNCTION_H * 0.62)
        parts.along(
            "$tag.port",
            frameOf(back, mul3(d1, -1.0), Z_AXIS),
            0.0,
            0.6,
            0.95,
            Material.Brass,
            owner = "cable.${finger.cable}",
        )
        parts.fingerPorts[finger.id] = back to d1
    }

    private fun cable(
        colour: String,
        waypoints: List<Point3>,
        ends: Pair<Double, Double> = 0.7 to 0.0,
    ) {
        val points = fillet(waypoints, BEND, 12)
        fun at(x: Double): Double? {
            var walked = 0.0
            for (i in 1 until points.size) {
                val a = points[i - 1]
                val b = points[i]
                val length = len3(sub3(b, a))
                if ((a.x - x) * (b.x - x) <= 0 && abs(b.x - a.x) > 1e-9)
                    return walked + (length * (x - a.x)) / (b.x - a.x)
                walked += length
            }
            return null
        }
        val hit = at(CLAMP_X)
        val total = pathLength(points)
        val gaps = ArrayList<Pair<Double, Double>>()
        gaps.add(0.0 to ends.first)
        if (hit != null) gaps.add(hit - CLAMP_T / 2 to hit + CLAMP_T / 2)
        if (ends.second != 0.0) gaps.add(total - ends.second to total)
        val hue =
            when (colour) {
                "red" -> Tone.Red
                "green" -> Tone.Green
                else -> Tone.Blue
            }
        t.tube(
            "cable.$colour",
            parts.dial,
            parts.hand,
            points,
            CABLE_R,
            TubeOptions(chunk = 6.0, gaps = gaps, hue = hue),
            PartStyle(tone = Tone.Hi, material = Material.Rubber),
        )
    }

    fun cables() {
        fun portStart(colour: String) = Point3(PORT_X, PORTS.getValue(colour), CABLE_Z)
        fun approach(port: Pair<Point3, Point3>, length: Double, rise: Double) =
            add3(add3(port.first, mul3(port.second, -length)), Point3(0.0, 0.0, rise))
        val p1 = parts.fingerPorts.getValue(1)
        val p3 = parts.fingerPorts.getValue(3)
        cable(
            "red",
            listOf(
                portStart("red"),
                Point3(-0.4, PORTS.getValue("red"), CABLE_Z),
                approach(p1, 7.6, 0.3),
                p1.first,
            ),
            0.7 to 0.6,
        )
        cable("green", listOf(portStart("green"), Point3(TERM.x, PORTS.getValue("green"), CABLE_Z)))
        cable(
            "blue",
            listOf(
                portStart("blue"),
                Point3(-0.4, PORTS.getValue("blue"), CABLE_Z),
                approach(p3, 7.6, 0.3),
                p3.first,
            ),
            0.7 to 0.6,
        )
        val hoseZ = COVER_TOP + 3.3
        val valve = Plan(3.4, HOSE_Y - 1.8, 3.4, 3.6, 0.6)
        val screws =
            listOf(
                Floor(valve.x + 0.8, valve.y + 0.8),
                Floor(valve.x + valve.w - 0.8, valve.y + valve.d - 0.8),
            )
        parts.plan(
            "hand.valve",
            valve,
            COVER_TOP,
            hoseZ + 1.9 - COVER_TOP,
            Material.Gunmetal,
            bevel = 0.3,
            details =
                Details(
                    seams = listOf(COVER_TOP + 1.2),
                    dots = screws.map { DetailDot(Point3(hoseZ + 1.9, it.x, it.y), Z_AXIS, 0.4) },
                ),
        )
        val from = Point3(PORT_X, HOSE_Y, hoseZ)
        val to = Point3(valve.x, HOSE_Y, hoseZ)
        parts.along(
            "hand.hose.ferrule.0",
            frameOf(from, X_AXIS, Z_AXIS),
            0.0,
            FERRULE,
            1.25,
            Material.Brass,
            owner = "hand.hose",
            details = Details(seams = listOf(0.45)),
        )
        parts.along(
            "hand.hose.ferrule.1",
            frameOf(to, Point3(-1.0, 0.0, 0.0), Z_AXIS),
            0.0,
            FERRULE,
            1.25,
            Material.Brass,
            owner = "hand.hose",
            details = Details(seams = listOf(0.45)),
        )
        val length = len3(sub3(to, from))
        t.tube(
            "hand.hose",
            parts.dial,
            parts.hand,
            listOf(from, to),
            HOSE_R,
            TubeOptions(
                chunk = 6.0,
                gaps = listOf(0.0 to FERRULE, length - FERRULE to length),
                rings = Rings(1.5, 1.6, true, 0.1 to 0.55, tone = Tone.Lo),
            ),
            PartStyle(tone = Tone.Hi, material = Material.Steel),
        )
    }

    private companion object {
        val FINGERS =
            listOf(
                Finger(1, -9.2, -13.0, port = true, cable = "red"),
                Finger(2, 0.0, 0.0, guide = true, flex = true),
                Finger(3, 9.2, 13.0, port = true, cable = "blue"),
            )
        const val D2R = PI / 180
        const val RL = 2.35
        val LENGTHS = doubleArrayOf(7.4, 5.8, 3.6)
        const val GAP = 0.6
        const val DISC_T = 1.2
        const val PITCH = 8 * D2R
        const val JUNCTION_H = 6.0
        const val CABLE_R = 0.7
        const val BEND = 8 * CABLE_R
        const val HOSE_R = 0.9
        const val HOSE_Y = 4.6
        const val FERRULE = 1.3
    }
}
