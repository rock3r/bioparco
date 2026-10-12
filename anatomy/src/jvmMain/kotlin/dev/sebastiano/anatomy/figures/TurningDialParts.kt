// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import androidx.compose.ui.graphics.Color
import dev.sebastiano.anatomy.figures.TurningDial.COVER
import dev.sebastiano.anatomy.figures.TurningDial.COVER_TOP
import dev.sebastiano.anatomy.figures.TurningDial.DIAL_R
import dev.sebastiano.anatomy.figures.TurningDial.FACE_R
import dev.sebastiano.anatomy.figures.TurningDial.FACE_Z
import dev.sebastiano.anatomy.figures.TurningDial.HUB_R
import dev.sebastiano.anatomy.figures.TurningDial.HUB_TOP
import dev.sebastiano.anatomy.figures.TurningDial.LOCK_Z0
import dev.sebastiano.anatomy.figures.TurningDial.LOCK_Z1
import dev.sebastiano.anatomy.figures.TurningDial.PALM
import dev.sebastiano.anatomy.figures.TurningDial.PALM_TOP
import dev.sebastiano.anatomy.figures.TurningDial.PAWL_AT
import dev.sebastiano.anatomy.figures.TurningDial.PINION_R
import dev.sebastiano.anatomy.figures.TurningDial.PLATE_Z1
import dev.sebastiano.anatomy.figures.TurningDial.SKIRT_TOP
import dev.sebastiano.anatomy.figures.TurningDial.WHEEL_AT
import dev.sebastiano.anatomy.figures.TurningDial.WHEEL_R
import dev.sebastiano.anatomy.figures.TurningDial.frameOf
import dev.sebastiano.anatomy.figures.TurningDial.vert
import dev.sebastiano.anatomy.kit.Anchor
import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Knot
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.audit.BallFlat
import dev.sebastiano.anatomy.kit.jsRound
import dev.sebastiano.anatomy.kit.pathOf
import dev.sebastiano.anatomy.kit.pointOf
import dev.sebastiano.anatomy.kit.turn.Bolt
import dev.sebastiano.anatomy.kit.turn.DetailDot
import dev.sebastiano.anatomy.kit.turn.Details
import dev.sebastiano.anatomy.kit.turn.LiveLayer
import dev.sebastiano.anatomy.kit.turn.Material
import dev.sebastiano.anatomy.kit.turn.PartStyle
import dev.sebastiano.anatomy.kit.turn.PlaneArt
import dev.sebastiano.anatomy.kit.turn.PlaneDisc
import dev.sebastiano.anatomy.kit.turn.PlaneLine
import dev.sebastiano.anatomy.kit.turn.PlaneMark
import dev.sebastiano.anatomy.kit.turn.PlaneText
import dev.sebastiano.anatomy.kit.turn.RibSeams
import dev.sebastiano.anatomy.kit.turn.RibSpec
import dev.sebastiano.anatomy.kit.turn.RoundEnd
import dev.sebastiano.anatomy.kit.turn.TurnGroup
import dev.sebastiano.anatomy.kit.turn.Turning
import dev.sebastiano.anatomy.kit.turn.ball
import dev.sebastiano.anatomy.kit.turn.jsCos
import dev.sebastiano.anatomy.kit.turn.jsSin
import dev.sebastiano.anatomy.kit.turn.lathe
import dev.sebastiano.anatomy.kit.turn.round
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** The turning dial's live parts: the dial, the lock wheel, the pawl and the hand. */
internal class TurningDialParts(val t: Turning) {
    lateinit var dial: TurnGroup
    lateinit var lock: LiveLayer
    lateinit var face: LiveLayer
    lateinit var hand: LiveLayer
    var led: TurningDial.Led? = null
    val fingerPorts = HashMap<Int, Pair<Point3, Point3>>()
    val podAt = Point3(-7.0, 11.4, COVER_TOP + 0.3)

    /** How brightly the LED's SVG fallback shows: the original's `--td-fallback`. */
    var fallback: () -> Float = { 1f }

    fun build() {
        dial = t.turnGroup("dial", Floor(0.0, 0.0))
        val wheel = t.turnGroup("wheel", WHEEL_AT)
        val pawl =
            t.hingeGroup(
                "pawl",
                Point3(PAWL_AT.x, PAWL_AT.y, 0.0),
                Point3(0.0, 0.0, 1.0),
                -4.0 to 4.0,
            )
        lock = t.layer("lock")
        face = t.layer("dial")
        hand = t.layer("hand")
        t.stack(listOf("back", "lock", "mid", "dial", "hand", "front"))
        dialParts()
        lockParts(wheel)
        pawlParts(pawl)
        palm()
        piston()
        TurningDialHand(this).fingers()
        TurningDialHand(this).cables()
        thumbAndPod()
    }

    private fun dialParts() {
        val f = vert(0.0, 0.0)
        val steel = PartStyle(tone = Tone.Hi, crease = Tone.Lo, material = Material.Steel)
        t.round(
            "dial.body",
            dial,
            face,
            f,
            PLATE_Z1,
            SKIRT_TOP,
            DIAL_R,
            style = steel,
            details =
                Details(
                    ribs =
                        RibSpec(
                            PLATE_Z1 + 0.9,
                            SKIRT_TOP - 0.7,
                            150,
                            fade = 0.08 to 0.5,
                            seams = RibSeams.Both,
                        )
                ),
        )
        val bevel =
            t.round("dial.bevel", dial, face, f, SKIRT_TOP, FACE_Z, DIAL_R, FACE_R, style = steel)
        t.plane(
            bevel,
            Point3(0.0, 0.0, FACE_Z),
            Point3(1.0, 0.0, 0.0),
            Point3(0.0, 1.0, 0.0),
            faceMarkup(),
            0.04 to 0.3,
        )
        t.round(
            "dial.hub",
            dial,
            hand,
            f,
            FACE_Z,
            HUB_TOP,
            HUB_R,
            style = PartStyle(tone = Tone.Mid, material = Material.Chrome),
            details = Details(seams = listOf(FACE_Z + 0.9)),
        )
    }

    private fun lockParts(wheel: TurnGroup) {
        t.round(
            "lock.pinion",
            dial,
            lock,
            vert(0.0, 0.0),
            LOCK_Z0,
            LOCK_Z1,
            PINION_R,
            style = PartStyle(tone = Tone.Mid, material = Material.Brass),
            details =
                Details(ribs = RibSpec(LOCK_Z0 + 0.5, LOCK_Z1 - 0.5, 48, seams = RibSeams.Both)),
        )
        val disc =
            t.round(
                "lock.wheel",
                wheel,
                lock,
                vert(WHEEL_AT.x, WHEEL_AT.y),
                LOCK_Z0,
                LOCK_Z1,
                WHEEL_R,
                style = PartStyle(tone = Tone.Hi, crease = Tone.Lo, material = Material.Brass),
                details =
                    Details(
                        ribs = RibSpec(LOCK_Z0 + 0.5, LOCK_Z1 - 0.5, 192, seams = RibSeams.Both)
                    ),
            )
        t.plane(
            disc,
            Point3(WHEEL_AT.x, WHEEL_AT.y, LOCK_Z1),
            Point3(1.0, 0.0, 0.0),
            Point3(0.0, 1.0, 0.0),
            wheelFace(),
        )
        t.round(
            "lock.wheel.hub",
            wheel,
            lock,
            vert(WHEEL_AT.x, WHEEL_AT.y),
            LOCK_Z1,
            LOCK_Z1 + 0.5,
            6.0,
            style = PartStyle(tone = Tone.Mid, material = Material.Steel),
            details = Details(bolts = listOf(Bolt(LOCK_Z1 + 0.5, 3.8, 4, phase = 0.4, size = 0.5))),
        )
    }

    private fun pawlParts(pawl: TurnGroup) {
        t.round(
            "pawl.post",
            null,
            face,
            vert(PAWL_AT.x, PAWL_AT.y),
            PLATE_Z1,
            PLATE_Z1 + 1,
            2.4,
            style = PartStyle(tone = Tone.Mid, material = Material.Gunmetal),
        )
        val ring = ArrayList<Floor>()
        for (j in 0..10) ring.add(
            Floor(
                PAWL_AT.x + 2.2 * jsCos((j / 10.0) * PI),
                PAWL_AT.y + 2.2 * jsSin((j / 10.0) * PI),
            )
        )
        ring.add(Floor(PAWL_AT.x - 0.6, DIAL_R + 0.4))
        ring.add(Floor(PAWL_AT.x, DIAL_R + 0.15))
        ring.add(Floor(PAWL_AT.x + 0.6, DIAL_R + 0.4))
        t.prism(
            "pawl.arm",
            pawl,
            face,
            UP,
            ring,
            PLATE_Z1 + 1,
            PLATE_Z1 + 2.6,
            bevel = 0.3,
            style = PartStyle(tone = Tone.Hi, crease = Tone.Lo, material = Material.Chrome),
        )
        t.round(
            "pawl.pin",
            pawl,
            face,
            vert(PAWL_AT.x, PAWL_AT.y),
            PLATE_Z1 + 2.6,
            PLATE_Z1 + 3.3,
            1.1,
            style = PartStyle(tone = Tone.Mid, material = Material.Steel),
            details = Details(bolts = listOf(Bolt(PLATE_Z1 + 3.3, 0.0, 1, size = 0.45))),
        )
    }

    /**
     * A plan prism on the dial, in the hand layer: the original's `plan(name, rect, z, h, extra)`.
     */
    fun plan(
        name: String,
        rect: Plan,
        z: Double,
        h: Double,
        material: Material,
        bevel: Double? = null,
        steps: Int = 2,
        tone: Tone = Tone.Mid,
        crease: Tone? = Tone.Faint,
        details: Details = Details(),
    ) =
        t.prism(
            name,
            dial,
            hand,
            rect,
            z,
            h,
            steps,
            bevel,
            PartStyle(tone = tone, crease = crease, material = material),
            details,
        )

    /**
     * A round on the dial, in the hand layer: the original's `along(name, F, s0, s1, r, extra)`.
     */
    fun along(
        name: String,
        f: Frame,
        s0: Double,
        s1: Double,
        r: Double,
        material: Material,
        tone: Tone = Tone.Mid,
        crease: Tone? = Tone.Faint,
        owner: String? = null,
        details: Details = Details(),
    ) =
        t.round(
            name,
            dial,
            hand,
            f,
            s0,
            s1,
            r,
            style = PartStyle(tone = tone, crease = crease, material = material, owner = owner),
            details = details,
        )

    private fun palm() {
        val sideScrews = ArrayList<DetailDot>()
        for ((y, ny) in listOf(PALM.y to -1.0, PALM.y + PALM.d to 1.0)) {
            for (x in listOf(-11.0, 3.0)) sideScrews.add(
                DetailDot(Point3(HUB_TOP + 5.2, x, y), Point3(0.0, ny, 0.0), 0.45, Tone.Mid)
            )
        }
        for (y in listOf(-7.0, 7.0)) sideScrews.add(
            DetailDot(Point3(HUB_TOP + 5.2, PALM.x, y), Point3(-1.0, 0.0, 0.0), 0.45, Tone.Mid)
        )
        t.prism(
            "hand.palm",
            dial,
            hand,
            PALM,
            HUB_TOP,
            PALM_TOP - HUB_TOP,
            4,
            0.9,
            PartStyle(tone = Tone.Hi, crease = Tone.Lo, material = Material.Gold),
            Details(seams = listOf(HUB_TOP + 2.6), dots = sideScrews),
        )
        val coverScrews =
            listOf(Floor(-13.7, -11.4), Floor(-13.7, 11.4), Floor(9.3, -11.4), Floor(9.3, 11.4))
                .map {
                    DetailDot(Point3(COVER_TOP, it.x, it.y), Point3(0.0, 0.0, 1.0), 0.48, Tone.Mid)
                }
        t.prism(
            "hand.cover",
            dial,
            hand,
            COVER,
            PALM_TOP,
            COVER_TOP - PALM_TOP,
            3,
            0.45,
            PartStyle(tone = Tone.Mid, material = Material.Gold),
            Details(dots = coverScrews),
        )
        connector()
    }

    /** The connector block on the cover: its cable ports, clamps and the terminal block. */
    private fun connector() {
        plan(
            "hand.connector",
            CONN,
            COVER_TOP,
            5.2,
            Material.Gunmetal,
            bevel = 0.35,
            details = Details(seams = listOf(COVER_TOP + 1.2)),
        )
        for ((colour, y) in PORTS) {
            along(
                "hand.port.$colour",
                frameOf(Point3(PORT_X, y, CABLE_Z), X_AXIS, Z_AXIS),
                0.0,
                0.7,
                1.15,
                Material.Brass,
                owner = "cable.$colour",
            )
        }
        for ((colour, y) in PORTS) {
            along(
                "hand.clamp.$colour",
                frameOf(Point3(CLAMP_X, y, CABLE_Z), X_AXIS, Z_AXIS),
                -CLAMP_T / 2,
                CLAMP_T / 2,
                CLAMP_R,
                Material.Brass,
                owner = "cable.$colour",
            )
        }
        plan(
            "hand.clamp.left",
            Plan(CLAMP_X - 1.3, -10.4, 2.6, 6.6, 0.6),
            COVER_TOP,
            CABLE_Z - CLAMP_R - COVER_TOP,
            Material.Gunmetal,
        )
        plan(
            "hand.clamp.right",
            Plan(CLAMP_X - 1.3, 6.9, 2.6, 3.4, 0.6),
            COVER_TOP,
            CABLE_Z - CLAMP_R - COVER_TOP,
            Material.Gunmetal,
        )
        plan(
            "hand.terminal",
            TERM,
            COVER_TOP,
            CABLE_Z + 1.8 - COVER_TOP,
            Material.Gunmetal,
            bevel = 0.3,
        )
    }

    private fun piston() {
        for ((index, x) in listOf(-5.1, 0.1).withIndex()) {
            plan(
                "hand.saddle.$index",
                Plan(x, PIST_Y - 1.9, 2.4, 3.8, 0.5),
                COVER_TOP,
                SADDLE_H,
                Material.Gunmetal,
            )
        }
        along(
            "hand.piston.body",
            PIST_F,
            BODY0,
            BODY1,
            BODY_R,
            Material.Gunmetal,
            Tone.Hi,
            Tone.Lo,
            details = Details(seams = listOf(BODY0 + 1.6, BODY1 - 1.6)),
        )
        val g0 = BODY1
        val g1 = BODY1 + 1.4
        val glandRadius = { sv: Double ->
            2.3 + 0.55 * sqrt(max(0.0, 1 - ((sv - g0) / (g1 - g0)).let { it * it }))
        }
        val glandSlope = { sv: Double ->
            val x = min(0.999, (sv - g0) / (g1 - g0))
            (-0.55 * x) / ((g1 - g0) * sqrt(1 - x * x))
        }
        t.lathe(
            "hand.piston.gland",
            dial,
            hand,
            PIST_F,
            listOf(Knot(g0, glandRadius(g0)), Knot(g1, glandRadius(g1))),
            slope = glandSlope,
            radius = glandRadius,
            style = PartStyle(tone = Tone.Mid, material = Material.Brass),
            details = Details(bolts = listOf(Bolt(g1, 1.75, 6, phase = 0.3, size = 0.34))),
        )
        along("hand.piston.rod", PIST_F, g1, ROD_END, 0.95, Material.Chrome, Tone.Hi)
        plan(
            "hand.clevis",
            Plan(ROD_END, PIST_Y - 1.9, 2.4, 3.8, 0.5),
            COVER_TOP,
            PIST_Z + 2.4 - COVER_TOP,
            Material.Steel,
            bevel = 0.3,
            details =
                Details(
                    dots =
                        listOf(-1.0, 1.0).map { side ->
                            DetailDot(
                                Point3(PIST_Z, ROD_END + 1.2, PIST_Y + side * 1.9),
                                Point3(0.0, side, 0.0),
                                0.42,
                            )
                        }
                ),
        )
        val ball = Point3(BODY0 - BALL_R, PIST_Y, PIST_Z)
        t.ball(
            "hand.ball",
            dial,
            hand,
            ball,
            BALL_R,
            style = PartStyle(tone = Tone.Hi, material = Material.Chrome),
        )
        along(
            "hand.ball.post",
            vert(ball.x, ball.y),
            COVER_TOP,
            ball.z - BALL_R,
            1.3,
            Material.Steel,
        )
    }

    private fun thumbAndPod() {
        val knuckle = Point3(-4.0, PALM.y - KNUCKLE_D, FZ)
        t.ball(
            "hand.thumb.knuckle",
            dial,
            hand,
            knuckle,
            KNUCKLE_R,
            listOf(
                BallFlat(Point3(0.0, 1.0, 0.0), KNUCKLE_D),
                BallFlat(Point3(0.0, -1.0, 0.0), KNUCKLE_D),
            ),
            style = PartStyle(tone = Tone.Hi, material = Material.Chrome),
        )
        val thumb =
            frameOf(
                Point3(knuckle.x, knuckle.y - KNUCKLE_D, knuckle.z),
                Point3(0.0, -1.0, 0.0),
                Z_AXIS,
            )
        t.round(
            "hand.thumb.cone",
            dial,
            hand,
            thumb,
            0.0,
            3.0,
            1.8,
            1.25,
            style = PartStyle(tone = Tone.Mid, material = Material.Steel),
        )
        t.round(
            "hand.thumb.link",
            dial,
            hand,
            frameOf(pointOf(thumb, 3 + THUMB_R, 0.0, 0.0), Point3(0.0, -1.0, 0.0), Z_AXIS),
            0.0,
            6.5,
            THUMB_R,
            ends = RoundEnd.Dome to RoundEnd.Dome,
            style = PartStyle(tone = Tone.Hi, crease = Tone.Lo, material = Material.Gold),
            details = Details(seams = listOf(1.2, 5.3)),
        )
        pod()
    }

    /** The hex pod on its socket: a free group that breaks off the cover. */
    private fun pod() {
        val pod = Floor(-7.0, 11.4)
        val podZ = COVER_TOP + 0.3
        t.round(
            "hand.pod.socket",
            dial,
            hand,
            vert(pod.x, pod.y),
            COVER_TOP,
            podZ,
            1.7,
            style = PartStyle(tone = Tone.Mid, material = Material.Gunmetal),
        )
        val group = t.freeGroup("pod", Point3(pod.x, pod.y, podZ), dial)
        val hex =
            (0 until 6).map { j ->
                Floor(1.35 * jsCos(((2 * j + 1) * PI) / 6), 1.35 * jsSin(((2 * j + 1) * PI) / 6))
            }
        t.prism(
            "hand.pod.nut",
            group,
            hand,
            Frame(Point3(pod.x, pod.y, 0.0), Z_AXIS, X_AXIS, Point3(0.0, 1.0, 0.0)),
            hex,
            podZ,
            podZ + 1.4,
            bevel = 0.2,
            style = PartStyle(tone = Tone.Hi, crease = Tone.Lo, material = Material.Brass),
        )
        t.round(
            "hand.pod.cap",
            group,
            hand,
            vert(pod.x, pod.y),
            podZ + 1.4,
            podZ + 2.0,
            0.9,
            style = PartStyle(tone = Tone.Mid, material = Material.Chrome),
            details = Details(bolts = listOf(Bolt(podZ + 2.0, 0.0, 1, size = 0.4))),
        )
    }

    /** The LED's fallback picture: halo, body and pale core, in viewBox px. */
    fun ledArt(): PlaneArt {
        fun ink(pale: Boolean): (dev.sebastiano.anatomy.kit.Palette) -> Color = { p ->
            when {
                p.isDark && pale -> Color(0xFFF2FFF7)
                p.isDark -> Color(0xFF5CF0A0)
                pale -> Color(0xFFE9FFF2)
                else -> Color(0xFF2FB36F)
            }
        }
        return PlaneArt(
            listOf(
                PlaneDisc(1.15, 0.18f, ink(false)),
                PlaneDisc(0.62, 1f, ink(false)),
                PlaneDisc(0.26, 1f, ink(true)),
            )
        )
    }

    companion object {
        val X_AXIS = Point3(1.0, 0.0, 0.0)
        val Z_AXIS = Point3(0.0, 0.0, 1.0)
        val UP = Frame(Point3(0.0, 0.0, 0.0), Z_AXIS, X_AXIS, Point3(0.0, 1.0, 0.0))
        val CONN = Plan(-15.1, -10.4, 4.4, 20.8, 0.9)
        val CABLE_Z = COVER_TOP + 1.9
        val PORT_X = CONN.x + CONN.w
        val PORTS = linkedMapOf("red" to -8.6, "green" to -5.4, "blue" to 8.6)
        const val CLAMP_X = -2.3
        const val CLAMP_R = 1.45
        const val CLAMP_T = 1.1
        val TERM = Plan(7.3, -5.4 - 1.6, 2.6, 3.2, 0.5)
        const val PIST_Y = 0.0
        const val BODY_R = 2.4
        const val SADDLE_H = 0.9
        val PIST_Z = COVER_TOP + SADDLE_H + BODY_R
        val PIST_F = frameOf(Point3(0.0, PIST_Y, PIST_Z), X_AXIS, Z_AXIS)
        const val BODY0 = -6.1
        const val BODY1 = 2.9
        const val ROD_END = 8.9
        const val BALL_R = 2.2
        val FZ = HUB_TOP + 4
        const val KNUCKLE_R = 3.2
        const val KNUCKLE_D = 2.6
        const val THUMB_R = 1.9

        private fun f3(v: Double): Double = jsRound(v * 1000) / 1000

        private fun tick(angle: Double, from: Double, to: Double): IsoPath =
            pathOf(
                listOf(
                    Flat(f3(jsSin(angle) * from), f3(-jsCos(angle) * from)),
                    Flat(f3(jsSin(angle) * to), f3(-jsCos(angle) * to)),
                )
            )

        private fun ringPath(r: Double, steps: Int): IsoPath =
            pathOf(
                (0 until steps).map { j ->
                    Flat(
                        f3(r * jsCos((j.toDouble() / steps) * PI * 2)),
                        f3(r * jsSin((j.toDouble() / steps) * PI * 2)),
                    )
                },
                true,
            )

        private fun ticks(
            rim: Double,
            minorLength: Double,
            majorLength: Double,
        ): Pair<IsoPath, IsoPath> {
            var minor = IsoPath.Empty
            var major = IsoPath.Empty
            for (i in 0 until 100) {
                val angle = (i / 100.0) * PI * 2
                if (i % 10 != 0) minor += tick(angle, rim - minorLength, rim)
                else major += tick(angle, rim - majorLength, rim)
            }
            return minor to major
        }

        private fun numerals(
            r: Double,
            size: Double,
            dy: Double,
            tone: Tone,
            label: (Int) -> String,
        ): List<PlaneMark> =
            (0 until 10).map { i ->
                val angle = (i / 10.0) * PI * 2
                PlaneText(
                    label(i),
                    f3(jsSin(angle) * r),
                    f3(-jsCos(angle) * r),
                    i * 36.0,
                    dy,
                    size,
                    tone,
                    Anchor.Middle,
                )
            }

        /** The dial face: a rim ring, 100 ticks, the numerals 0–90, and an inner ring. */
        fun faceMarkup(): PlaneArt {
            val rim = FACE_R - 0.8
            val (minor, major) = ticks(rim, 1.4, 2.6)
            return PlaneArt(
                listOf(
                    PlaneLine(ringPath(rim, 160), Tone.Lo),
                    PlaneLine(minor, Tone.Lo),
                    PlaneLine(major, Tone.Mid),
                ) +
                    numerals(rim - 5.4, 3.4, 1.2, Tone.Lo) { "${it * 10}" } +
                    PlaneLine(ringPath(33.5, 140), Tone.Faint)
            )
        }

        /** The index wheel's face: a ring, 100 ticks and the digits 0–9. */
        fun wheelFace(): PlaneArt {
            val rim = WHEEL_R - 3.2
            val (minor, major) = ticks(rim, 1.2, 2.4)
            return PlaneArt(
                listOf(
                    PlaneLine(ringPath(rim, 180), Tone.Lo),
                    PlaneLine(minor, Tone.Lo),
                    PlaneLine(major, Tone.Mid),
                ) + numerals(WHEEL_R - 9.4, 4.2, 1.5, Tone.Mid) { "$it" }
            )
        }
    }
}
