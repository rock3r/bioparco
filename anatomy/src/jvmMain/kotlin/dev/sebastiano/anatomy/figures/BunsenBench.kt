package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Anchor
import dev.sebastiano.anatomy.kit.Art
import dev.sebastiano.anatomy.kit.ArtBuilder
import dev.sebastiano.anatomy.kit.Axis
import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Free
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Tone.Faint
import dev.sebastiano.anatomy.kit.Tone.Hi
import dev.sebastiano.anatomy.kit.Tone.Lo
import dev.sebastiano.anatomy.kit.Tone.Mid
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.boxCorners
import dev.sebastiano.anatomy.kit.corners
import dev.sebastiano.anatomy.kit.cylinder
import dev.sebastiano.anatomy.kit.depthOf
import dev.sebastiano.anatomy.kit.extrude
import dev.sebastiano.anatomy.kit.fitProjection
import dev.sebastiano.anatomy.kit.haloOf
import dev.sebastiano.anatomy.kit.insetPlan
import dev.sebastiano.anatomy.kit.iso
import dev.sebastiano.anatomy.kit.lineOnTop
import dev.sebastiano.anatomy.kit.onTop
import dev.sebastiano.anatomy.kit.pathOf
import dev.sebastiano.anatomy.kit.planOutline
import dev.sebastiano.anatomy.kit.ring
import dev.sebastiano.anatomy.kit.sideArc
import dev.sebastiano.anatomy.kit.sideRing
import dev.sebastiano.anatomy.kit.sideSeam
import dev.sebastiano.anatomy.kit.slabOf
import dev.sebastiano.anatomy.kit.topMatrix
import dev.sebastiano.anatomy.kit.towardViewer
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/*
 * A Bunsen burner on a lab bench, in world millimetres. The burner stands on a heat mat with its
 * axis at the world origin, so the flame shader's rays meet the axis at `rayOf`. Behind it a stand
 * carries a ruler whose zero is the tube's rim, with a cursor at the flame's tip; to the right the
 * bench gas tap feeds it through a hose to the barb. Its air collar is the subject: turning it
 * slides its two slots over the tube's two ports.
 */
object BunsenBench {
    const val WIDTH = 600.0
    const val HEIGHT = 380.0

    private val BOARD = Plan(-98.0, -54.0, 248.0, 108.0, 8.0)
    private const val BOARD_H = 12.0
    private const val FOOT_H = 4.0
    private val MAT = Plan(-36.0, -36.0, 72.0, 72.0, 3.0)
    private const val MAT_H = 3.0

    /** The burner: base, body, the flange carrying the air scale, the collar, and the tube. */
    private const val BASE_R = 30.0
    private const val BODY_R = 8.5
    private const val FLANGE_R = 16.0
    const val FLANGE_TOP = 25.0
    const val COLLAR_R = 7.6
    const val COLLAR_TOP = 40.0
    const val TUBE_R = 6.5
    const val BORE_R = Bunsen.BORE_MM / 2
    const val RIM = 80.0

    private val STAND = Floor(-70.0, 36.0)
    private const val ROD_R = 3.0
    private const val ROD_TOP = RIM + 90
    const val SCALE_TOP = 80.0

    private val TAP = Floor(122.0, -24.0)
    private val NOZZLE_TIP = Point3(104.0, TAP.y, 20.2)
    private val BARB_TIP = Point3(31.0, 0.0, 17.4)

    /** The air ports and the collar's slots: half-width and height. */
    const val SLOT_HALF = 25 * PI / 180
    const val SLOT_Z0 = 29.0
    const val SLOT_Z1 = 36.0

    /** The tab turns 60° ahead of the slot; the scale under it spans the collar's travel. */
    const val TAB_LEAD = 60 * PI / 180

    private fun projectionAt() =
        fitProjection(
            boxCorners(BOARD, -BOARD_H - FOOT_H, 0.0) +
                listOf(Point3(0.0, 0.0, RIM + 80), Point3(STAND.x, STAND.y, ROD_TOP)),
            WIDTH,
            HEIGHT,
            pad = 20.0,
            azimuth = 60.0,
        )

    val P = projectionAt()

    /** The floor angle that faces the viewer, and the ports' centre, a little to its right. */
    val FACING: Double = towardViewer(P).let { atan2(it.y, it.x) }
    val PORT: Double = FACING - 35 * PI / 180

    const val LABEL =
        "A Bunsen burner on a lab bench, fed with methane from the bench gas tap through a hose. " +
            "Its air collar has two slots that turn over two ports at the foot of the tube: shut, " +
            "the gas burns as a tall, lazy, luminous yellow flame; open, it draws in primary air " +
            "and burns as a short roaring blue flame with a sharp inner cone. A ruler behind the " +
            "flame, zeroed at the rim, has a cursor at the flame's tip."

    fun at(x: Double, y: Double, z: Double) = iso(Point3(x, y, z), P)

    /** A point on a vertical cylinder about the burner's axis. */
    fun onCylinder(r: Double, angle: Double, z: Double) = at(r * cos(angle), r * sin(angle), z)

    private fun ArtBuilder.board() {
        halo(haloOf(BOARD, -BOARD_H - FOOT_H, BOARD_H, P))
        for ((x, y) in corners(BOARD, 14.0).sortedBy { depthOf(Point3(it.x, it.y, 0.0), P) }) {
            solid(cylinder(x, y, 6.0, -BOARD_H - FOOT_H, FOOT_H, P, 24), tone = Lo)
        }
        solid(
            slabOf(BOARD, -BOARD_H, BOARD_H, P, 8, 1.6),
            tone = Lo,
            inner = art { line(sideSeam(BOARD, -4.0, P, 8), tone = Faint) },
        )
        line(planOutline(insetPlan(BOARD, 4.0), 0.0, P), tone = Faint)
        val screws = corners(BOARD, 8.0)
        line(IsoPath.join(screws.map { ring(it.x, it.y, 1.8, 0.0, P, 14) }), tone = Lo)
        dots(screws.map { at(it.x, it.y, 0.0) }, size = 0.5)
        val label = Plan(78.0, 26.0, 40.0, 12.0, 1.0)
        solid(slabOf(label, 0.0, 0.8, P, 3), tone = Lo, crease = null)
        line(planOutline(insetPlan(label, 1.4), 0.8, P, 3), tone = Faint)
        for ((index, length) in listOf(22.0, 15.0, 18.0).withIndex()) {
            val y = label.y + 3.4 + index * 2.0
            line(
                lineOnTop(Floor(label.x + 4, y), Floor(label.x + 4 + length, y), 0.8, P),
                tone = Lo,
                free = Free.Both,
            )
        }
        dots(listOf(at(label.x + 34.5, label.y + 3.4, 0.8), at(label.x + 34.5, label.y + 8.6, 0.8)))
    }

    /** The across-the-screen floor direction, and the floor direction toward the viewer. */
    private val ACROSS = towardViewer(P).let { Floor(it.y, -it.x) }
    private val TOWARD = towardViewer(P)

    /** A floor rectangle centred on [c], [w] across the screen and [d] deep toward the viewer. */
    private fun facingRect(c: Floor, w: Double, d: Double): List<Floor> {
        val a = ACROSS
        val t = TOWARD
        return listOf(-1.0 to -1.0, 1.0 to -1.0, 1.0 to 1.0, -1.0 to 1.0).map { (u, v) ->
            Floor(c.x + a.x * u * w / 2 + t.x * v * d / 2, c.y + a.y * u * w / 2 + t.y * v * d / 2)
        }
    }

    private val RULER = Floor(STAND.x + ACROSS.x * 8.5, STAND.y + ACROSS.y * 8.5)
    private const val RULER_W = 9.0
    private const val RULER_D = 1.6

    private fun ArtBuilder.stand() {
        val foot = Plan(STAND.x - 14, STAND.y - 14, 28.0, 28.0, 3.0)
        halo(haloOf(foot, 0.0, 7.0, P), blur = 6.0)
        solid(slabOf(foot, 0.0, 7.0, P, 4, 0.8), tone = Mid)
        dots(corners(foot, 3.5).map { at(it.x, it.y, 7.0) }, size = 0.45)
        solid(cylinder(STAND.x, STAND.y, 5.0, 7.0, 2.0, P, 24), tone = Mid)
        solid(
            cylinder(STAND.x, STAND.y, ROD_R, 9.0, ROD_TOP - 9, P, 24),
            tone = Mid,
            crease = null,
            inner = art { line(sideArc(STAND.x, STAND.y, ROD_R, 40.0, P, 14), tone = Faint) },
        )
        // The boss head that carries the ruler, its zero level with the burner's rim.
        val boss = Plan(STAND.x - 5.5, STAND.y - 5.5, 11.0, 11.0, 1.6)
        solid(slabOf(boss, RIM - 12, 8.0, P, 3, 0.5), tone = Mid)
        solid(extrude(facingRect(RULER, RULER_W + 3, 5.0), RIM - 12, 8.0, P), tone = Mid)
        dots(listOf(frontOf(RULER, RULER_W / 2 - 2, RIM - 8, 3.2)), size = 0.5)
        solid(cylinder(STAND.x, STAND.y, 3.8, ROD_TOP, 2.0, P, 20, 0.3), tone = Mid)
    }

    /** A point on the ruler's front face, [u] across from its centre line, at height [z]. */
    private fun frontOf(c: Floor, u: Double, z: Double, depth: Double = RULER_D): Flat =
        at(c.x + ACROSS.x * u + TOWARD.x * depth / 2, c.y + ACROSS.y * u + TOWARD.y * depth / 2, z)

    private fun ArtBuilder.ruler() {
        solid(
            extrude(facingRect(RULER, RULER_W, RULER_D), RIM - 4, SCALE_TOP + 8, P),
            tone = Mid,
        )
        // Ticks hang from the edge nearer the flame: every 5 mm, longer every centimetre.
        val edge = RULER_W / 2
        val minor = ArrayList<IsoPath>()
        val major = ArrayList<IsoPath>()
        for (mm in 0..SCALE_TOP.toInt() step 5) {
            val long = mm % 10 == 0
            val z = RIM + mm
            val tick =
                pathOf(
                    listOf(
                        frontOf(RULER, edge, z),
                        frontOf(RULER, edge - if (long) 4.2 else 2.4, z),
                    )
                )
            if (long) major.add(tick) else minor.add(tick)
        }
        line(IsoPath.join(minor), tone = Lo)
        line(IsoPath.join(major), tone = Mid)
        line(pathOf(listOf(frontOf(RULER, edge, RIM), frontOf(RULER, -edge, RIM))), tone = Hi)
    }

    /** The cursor on the ruler, built at the rim and moved up to the flame's tip. */
    private fun ArtBuilder.cursor() {
        solid(
            extrude(facingRect(RULER, RULER_W + 2.4, RULER_D + 2.0), RIM - 1.2, 2.4, P, true, 0.3),
            tone = Mid,
        )
        line(
            pathOf(
                listOf(
                    frontOf(RULER, RULER_W / 2 + 1.2, RIM, RULER_D + 2.0),
                    frontOf(RULER, RULER_W / 2 - 3.0, RIM, RULER_D + 2.0),
                )
            ),
            tone = Hi,
            width = 0.9,
        )
    }

    private fun ArtBuilder.tap() {
        val (x, y) = TAP
        halo(haloOf(Plan(x - 12, y - 12, 24.0, 24.0, 12.0), 0.0, 3.0, P), blur = 6.0)
        solid(cylinder(x, y, 12.0, 0.0, 2.5, P, 40, 0.5), tone = Mid)
        dots(
            (0 until 3).map {
                at(x + 9.6 * cos(it * 2 * PI / 3 + 0.4), y + 9.6 * sin(it * 2 * PI / 3 + 0.4), 2.5)
            },
            size = 0.45,
        )
        solid(
            cylinder(x, y, 7.5, 2.5, 33.5, P, 32),
            tone = Mid,
            crease = null,
            inner = art { line(sideArc(x, y, 7.5, 10.0, P, 20), tone = Faint) },
        )
        // The nozzle the hose is pushed onto, pointing at the burner, with two barbs.
        solid(
            slabOf(
                Plan(NOZZLE_TIP.x, y - 2.2, x - NOZZLE_TIP.x - 6, 4.4, 1.0),
                18.0,
                4.4,
                P,
                2,
                0.3,
            ),
            tone = Mid,
        )
        for (bx in listOf(NOZZLE_TIP.x + 1.5, NOZZLE_TIP.x + 5.0)) {
            solid(slabOf(Plan(bx, y - 2.8, 1.5, 5.6, 0.5), 17.4, 5.6, P, 2), tone = Mid)
        }
        solid(cylinder(x, y, 9.0, 36.0, 4.0, P, 36, 0.6), tone = Mid)
        solid(cylinder(x, y, 2.4, 40.0, 3.0, P, 16), tone = Mid)
        // The lever lies along the nozzle: the tap is open.
        solid(slabOf(Plan(x - 17.0, y - 2.0, 19.0, 4.0, 1.2), 43.0, 2.4, P, 3, 0.4), tone = Mid)
        solid(cylinder(x - 15.0, y, 2.6, 45.4, 2.2, P, 18, 0.3), tone = Mid)
        dots(listOf(at(x, y, 45.4)), size = 0.5, tone = Hi)
        faceText(
            topMatrix(Floor(x + 1.0, y + 6.2), 40.0, P),
            "GAS",
            size = 2.4,
            tone = Lo,
            anchor = Anchor.Middle,
        )
    }

    /** A box of matches and a spent match: what lit the burner. */
    private fun ArtBuilder.matches() {
        val box = Plan(-90.0, -48.0, 48.0, 32.0, 1.2)
        halo(haloOf(box, 0.0, 14.0, P), blur = 5.0)
        solid(
            slabOf(box, 0.0, 14.0, P, 2, 0.5),
            tone = Mid,
            inner =
                art { line(sideSeam(box, 3.0, P, 2) + sideSeam(box, 11.0, P, 2), tone = Faint) },
        )
        // The label panel on the sleeve's top.
        line(planOutline(insetPlan(box, 5.0), 14.0, P, 2), tone = Faint)
        // A spent match on the lid, its head burnt.
        val match = Plan(-84.0, -28.0, 26.0, 1.6, 0.6)
        solid(slabOf(match, 14.0, 1.6, P, 2), tone = Lo)
        solid(
            slabOf(Plan(match.x + match.w - 0.4, -28.4, 3.4, 2.4, 1.0), 14.0, 2.2, P, 3),
            tone = Hi,
        )
    }

    private fun ArtBuilder.base() {
        solid(
            slabOf(MAT, 0.0, MAT_H, P, 3, 0.4),
            tone = Lo,
            inner = art { line(sideSeam(MAT, 1.5, P, 3), tone = Faint) },
        )
        line(planOutline(insetPlan(MAT, 4.0), MAT_H, P, 3), tone = Faint)
        halo(cylinder(0.0, 0.0, BASE_R, MAT_H, 6.0, P, 64).fill, blur = 6.0)
        solid(
            cylinder(0.0, 0.0, BASE_R, MAT_H, 6.0, P, 72, 0.8),
            tone = Mid,
            inner = art { line(sideArc(0.0, 0.0, BASE_R, MAT_H + 2.5, P, 40), tone = Faint) },
        )
        solid(cylinder(0.0, 0.0, 22.0, MAT_H + 6, 3.0, P, 64, 0.5), tone = Mid)
        dots((0 until 3).map { onCylinder(26.0, FACING + (it - 1) * 0.9, MAT_H + 6) }, size = 0.45)
        solid(cylinder(0.0, 0.0, BODY_R, 12.0, 11.0, P, 36), tone = Mid)
    }

    private fun ArtBuilder.barb() {
        solid(
            slabOf(
                Plan(BODY_R - 1.5, -2.4, BARB_TIP.x - BODY_R + 1.5, 4.8, 1.0),
                15.0,
                4.8,
                P,
                2,
                0.3,
            ),
            tone = Mid,
        )
        for (x in listOf(19.0, 23.0, 27.0)) {
            solid(slabOf(Plan(x, -3.0, 1.8, 6.0, 0.6), 14.4, 6.0, P, 2), tone = Mid)
        }
        line(sideRing(BARB_TIP, 1.2, Axis.X, P, 16), tone = Lo)
    }

    private fun ArtBuilder.flange() {
        solid(cylinder(0.0, 0.0, FLANGE_R, FLANGE_TOP - 2, 2.0, P, 56, 0.5), tone = Mid)
        // The air scale: a tick every 10% of the collar's travel, hanging from the flange's edge.
        val closed = PORT + 2 * SLOT_HALF + TAB_LEAD
        val ticks = (0..10).map { closed - it * (2 * SLOT_HALF) / 10 }
        val minor = ArrayList<IsoPath>()
        val major = ArrayList<IsoPath>()
        ticks.forEachIndexed { index, angle ->
            val long = index % 5 == 0
            val inner = if (long) 12.6 else 13.8
            val path =
                lineOnTop(
                    Floor(inner * cos(angle), inner * sin(angle)),
                    Floor(15.6 * cos(angle), 15.6 * sin(angle)),
                    FLANGE_TOP,
                    P,
                )
            if (long) major.add(path) else minor.add(path)
        }
        line(IsoPath.join(minor), tone = Lo)
        line(IsoPath.join(major), tone = Mid)
    }

    /**
     * Everything behind the collar: bench, stand and ruler, the tap and hose, base, barb, flange.
     */
    val BACK: Art = art {
        board()
        matches()
        stand()
        ruler()
        group("cursor") { cursor() }
        base()
        barb()
        wire(
            at(BARB_TIP.x, BARB_TIP.y, BARB_TIP.z),
            at(NOZZLE_TIP.x, NOZZLE_TIP.y, NOZZLE_TIP.z),
            via = listOf(at(46.0, 0.0, 0.9), at(98.0, TAP.y, 0.9)),
            size = 1.0,
        )
        tap()
        flange()
    }

    /** The collar itself: round, so it is the same at every turn. Its slots and tab move. */
    val COLLAR: Art = art {
        solid(
            cylinder(0.0, 0.0, COLLAR_R, FLANGE_TOP, COLLAR_TOP - FLANGE_TOP, P, 48, 0.4),
            tone = Hi,
            crease = Lo,
            lit = true,
        )
    }

    /** The tube above the collar, up to the rim. */
    val TUBE: Art = art {
        solid(
            cylinder(0.0, 0.0, TUBE_R, COLLAR_TOP, RIM - COLLAR_TOP, P, 40, 0.3),
            tone = Mid,
            crease = null,
            inner = art { line(sideArc(0.0, 0.0, TUBE_R, COLLAR_TOP + 4, P, 20), tone = Faint) },
        )
    }

    /** The bore at the rim, a dark disc under the flame's root. */
    val BORE: IsoPath = ring(0.0, 0.0, BORE_R, RIM, P, 40)

    /** The rim's near edges, over the flame's root so the strokes stay crisp. */
    val FRONT: Art = art {
        line(sideArc(0.0, 0.0, TUBE_R, RIM, P, 28), tone = Mid)
        line(nearHalf(BORE_R, RIM), tone = Lo)
    }

    /** The near half of a circle about the axis on a top face. */
    private fun nearHalf(r: Double, z: Double): IsoPath =
        onTop(
            (0..24).map {
                val a = FACING - PI / 2 + it / 24.0 * PI
                Floor(r * cos(a), r * sin(a))
            },
            z,
            P,
        )

    /** The far half, seen as the bore's back edge. */
    val BORE_BACK: IsoPath =
        onTop(
            (0..24).map {
                val a = FACING + PI / 2 + it / 24.0 * PI
                Floor(BORE_R * cos(a), BORE_R * sin(a))
            },
            RIM,
            P,
        )
}
