// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.figures.Raptor.COLUMN
import dev.sebastiano.anatomy.figures.Raptor.COLUMN_AT
import dev.sebastiano.anatomy.figures.Raptor.CONE_TIP
import dev.sebastiano.anatomy.figures.Raptor.DECK
import dev.sebastiano.anatomy.figures.Raptor.DECKF
import dev.sebastiano.anatomy.figures.Raptor.DECK_H
import dev.sebastiano.anatomy.figures.Raptor.DEG
import dev.sebastiano.anatomy.figures.Raptor.FOOT_H
import dev.sebastiano.anatomy.figures.Raptor.P
import dev.sebastiano.anatomy.figures.Raptor.at
import dev.sebastiano.anatomy.kit.Along
import dev.sebastiano.anatomy.kit.Axis
import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Free
import dev.sebastiano.anatomy.kit.Knot
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Smooth
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.arcOf
import dev.sebastiano.anatomy.kit.arcPoints
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.audit.ShapeMeta
import dev.sebastiano.anatomy.kit.audit.box
import dev.sebastiano.anatomy.kit.audit.disc
import dev.sebastiano.anatomy.kit.audit.solid
import dev.sebastiano.anatomy.kit.audit.tube
import dev.sebastiano.anatomy.kit.corners
import dev.sebastiano.anatomy.kit.cylinder
import dev.sebastiano.anatomy.kit.depthOf
import dev.sebastiano.anatomy.kit.disc
import dev.sebastiano.anatomy.kit.dotsOf
import dev.sebastiano.anatomy.kit.fillet
import dev.sebastiano.anatomy.kit.frameAlong
import dev.sebastiano.anatomy.kit.haloOf
import dev.sebastiano.anatomy.kit.insetPlan
import dev.sebastiano.anatomy.kit.lineOnTop
import dev.sebastiano.anatomy.kit.onTop
import dev.sebastiano.anatomy.kit.planOutline
import dev.sebastiano.anatomy.kit.pointOf
import dev.sebastiano.anatomy.kit.ring
import dev.sebastiano.anatomy.kit.segment
import dev.sebastiano.anatomy.kit.sideTicks
import dev.sebastiano.anatomy.kit.slabOf
import dev.sebastiano.anatomy.kit.solidOf
import dev.sebastiano.anatomy.kit.tangentOf
import dev.sebastiano.anatomy.kit.topMatrix
import dev.sebastiano.anatomy.kit.topTicks
import dev.sebastiano.anatomy.kit.tubePieces
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/*
 * The stand behind the engine, the original's `back`: the deck on its feet with its rule, grate,
 * scale strip and label plate, the flame cone and deluge ring, and the two columns with their
 * splices, cable tray and ladder. It records the deck, cone, deluge and columns for the audit.
 */

private const val FRAME_OWNER = "stand.frame"

private fun meta(name: String) = ShapeMeta(name = name, owner = FRAME_OWNER)

internal fun RaptorBuild.stand() {
    under.halo(haloOf(DECK, -DECK_H - FOOT_H, DECK_H, P))
    deck()
    plates()
    deckTop = slabOf(insetPlan(DECK, 1.0), 0.0, 0.01, P, 8).fill
    shadow = haloOf(Plan(-56.0, -56.0, 112.0, 112.0, 56.0), 0.0, 0.1, P)
    cone()
    deluge()
    columns()
    ladder()
}

private fun RaptorBuild.deck() {
    val feet =
        listOf(
                Floor(-132.0, -132.0),
                Floor(132.0, -132.0),
                Floor(-132.0, 132.0),
                Floor(132.0, 132.0),
                Floor(0.0, -132.0),
                Floor(0.0, 132.0),
                Floor(-132.0, 0.0),
                Floor(132.0, 0.0),
            )
            .sortedBy { depthOf(Point3(it.x, it.y, 0.0), P) }
    for ((x, y) in feet) {
        under.solid(cylinder(x, y, 10.0, -DECK_H - FOOT_H, 2.0, P, 28), Tone.Lo)
        under.solid(cylinder(x, y, 7.5, -DECK_H - FOOT_H + 2, FOOT_H - 2, P, 28), Tone.Mid)
    }
    under.solid(slabOf(DECK, -DECK_H, DECK_H, P, 8, 1.6), Tone.Mid)
    recorder.solid(
        box(
            Point3(DECK.x, DECK.y, -DECK_H),
            Point3(DECK.x + DECK.w, DECK.y + DECK.d, 0.0),
            meta("stand.deck"),
        )
    )
    under.line(planOutline(insetPlan(DECK, 7.0), 0.0, P), Tone.Lo)
    val screws = corners(DECK, 15.0)
    under.line(screws.map { ring(it.x, it.y, 3.0, 0.0, P, 18) }.reduce { a, b -> a + b }, Tone.Lo)
    under.dots(screws.map { at(Point3(it.x, it.y, 0.0)) }, 0.55)
    val ticks =
        sideTicks(
            DECK.x + 20,
            DECK.x + DECK.w - 20,
            10.0,
            5,
            DECK.x + DECK.w,
            0.0,
            2.4 to 4.6,
            P,
            Axis.Y,
        )
    under.line(ticks.minor, Tone.Lo)
    under.line(ticks.major, Tone.Mid)
    val grate = Plan(-98.0, -98.0, 196.0, 196.0, 6.0)
    under.line(planOutline(grate, 0.0, P), Tone.Lo)
    var x = grate.x + 14
    while (x < grate.x + grate.w - 4) {
        under.line(lineOnTop(Floor(x, grate.y), Floor(x, grate.y + grate.d), 0.0, P), Tone.Faint)
        x += 14
    }
}

private fun RaptorBuild.plates() {
    val strip = Plan(-128.0, DECK.y + DECK.d - 15, 196.0, 8.0, 1.0)
    under.solid(slabOf(strip, 0.0, 0.8, P, 3), Tone.Lo, crease = null)
    val ticks =
        topTicks(strip.x + 4, strip.x + strip.w - 4, 4.0, 5, strip.y + 1, 0.8, 2.2 to 4.4, P)
    under.line(ticks.minor, Tone.Lo)
    under.line(ticks.major, Tone.Mid)
    under.dots(
        listOf(
            at(Point3(strip.x + 2.2, strip.y + 4, 0.8)),
            at(Point3(strip.x + strip.w - 2.2, strip.y + 4, 0.8)),
        ),
        0.45,
    )
    val label = Plan(DECK.x + DECK.w - 21, -94.0, 12.0, 60.0, 1.5)
    under.solid(slabOf(label, 0.0, 1.0, P, 3), Tone.Mid)
    under.line(planOutline(insetPlan(label, 1.8), 1.0, P), Tone.Faint)
    under.faceText(
        topMatrix(Floor(label.x + 7.4, label.y + label.d - 5), 1.0, P, Along.Y),
        "RAPTOR 2",
        4.2,
        Tone.Lo,
    )
    val rules =
        listOf(0, 1).map { row ->
            val rx = label.x + 3 + row * 2.2
            onTop(
                listOf(Floor(rx, label.y + 5), Floor(rx, label.y + if (row == 1) 16 else 21)),
                1.0,
                P,
            )
        }
    under.line(rules[0] + rules[1], Tone.Lo, free = Free.Both)
    under.dots(
        listOf(
            at(Point3(label.x + label.w / 2, label.y + 2.6, 1.0)),
            at(Point3(label.x + label.w / 2, label.y + label.d - 2.6, 1.0)),
        ),
        0.45,
    )
}

private const val CONE_Q = 0.8

private fun coneShare(s: Double) = max(1e-4, 1 - (s - 4) / (CONE_TIP - 4))

private fun cone(s: Double) = 48 * coneShare(s).pow(CONE_Q)

private fun coneSlope(s: Double) = (-48 * CONE_Q) / (CONE_TIP - 4) * coneShare(s).pow(CONE_Q - 1)

private fun RaptorBuild.cone() {
    over.solid(disc(0.0, 4.0, 56.0, DECKF, P, bevel = 1.2), Tone.Mid)
    recorder.solid(disc(DECKF, 0.0, 4.0, 56.0, meta("stand.cone-plate")))
    over.dots(dotsOf(DECKF, 4.0, 52.0, 28, P, all = true), 0.45, Tone.Mid)
    val profile =
        List(33) { index ->
            val t = 1 - index / 32.0
            val s = 4 + (CONE_TIP - 4) * (1 - t * t)
            if (index == 32) Knot(CONE_TIP, 0.0) else Knot(s, cone(s), index > 0)
        }
    val inner = listOf(13.0, 22.0).map { arcOf(DECKF, it, cone(it), P, slope = coneSlope(it)) }
    over.solid(
        solidOf(profile, DECKF, P, smooth = Smooth(rows = 5, slope = ::coneSlope)),
        Tone.Hi,
        inner = art { line(inner[0] + inner[1], Tone.Faint) },
    )
    recorder.solid(solid(DECKF, profile, meta = meta("stand.cone")))
}

private fun RaptorBuild.deluge() {
    val ring = arcPoints(DECKF, 6.4, 68.0, 0.0, Math.PI * 2, 72)
    for (part in tubePieces(ring, 2.1, P, maxLength = 400.0, spacing = 2.0, closed = true)) over
        .tube(part, Tone.Mid)
    over.dots(dotsOf(DECKF, 8.6, 68.0, 36, P, all = true), 0.5, Tone.Hi)
    recorder.solid(tube(ring, 2.1, meta("stand.deluge")))
    val teeDeg = -12.0
    val teeR = 2.9
    val tee = frameAlong(pointOf(DECKF, 6.4, 68.0, teeDeg * DEG), tangentOf(DECKF, teeDeg * DEG))
    over.solid(disc(-2.4, 2.4, teeR, tee, P, bevel = 0.3), Tone.Mid)
    recorder.solid(disc(tee, -2.4, 2.4, teeR, meta("stand.deluge-tee")))
    val feed =
        fillet(
            listOf(
                pointOf(DECKF, 6.4, 68 + teeR - 0.2, teeDeg * DEG),
                Point3(100.0, -14.0, 6.4),
                Point3(139.0, -14.0, 6.4),
                Point3(139.0, -14.0, 1.5),
            ),
            6.0,
        )
    for (part in tubePieces(feed, 2.1, P, maxLength = 400.0, caps = false to true)) over.tube(
        part,
        Tone.Mid,
    )
    recorder.solid(tube(feed, 2.1, meta("stand.deluge-feed")))
    over.solid(slabOf(Plan(132.0, -22.0, 13.0, 16.0, 2.0), 0.0, 4.0, P, 3, 0.6), Tone.Mid)
    over.dots(listOf(at(Point3(135.0, -19.0, 4.0)), at(Point3(142.0, -9.0, 4.0))), 0.45)
}

private fun RaptorBuild.columns() {
    val beamZ = Raptor.BEAM_Z
    for ((cx, cy) in COLUMN_AT) {
        val base = Plan(cx - 23, cy - 23, 46.0, 46.0, 3.0)
        over.solid(slabOf(base, 0.0, 5.0, P, 3, 0.8), Tone.Mid)
        recorder.solid(
            box(
                Point3(base.x, base.y, 0.0),
                Point3(base.x + base.w, base.y + base.d, 5.0),
                meta("stand.column-base"),
            )
        )
        over.dots(corners(base, 6.0).map { at(Point3(it.x, it.y, 5.0)) }, 0.55)
        over.line(
            corners(base, 6.0).map { ring(it.x, it.y, 2.4, 5.0, P, 14) }.reduce { a, b -> a + b },
            Tone.Lo,
        )
        val column = Plan(cx - COLUMN / 2, cy - COLUMN / 2, COLUMN, COLUMN, 1.5)
        over.solid(slabOf(column, 5.0, beamZ - 5, P, 3, 0.8), Tone.Mid)
        recorder.solid(
            box(
                Point3(column.x, column.y, 5.0),
                Point3(column.x + column.w, column.y + column.d, beamZ),
                meta("stand.column"),
            )
        )
        val half = sqrt(0.5)
        val ex = cx + COLUMN / 2 - 1.5 + 1.5 * half
        val ey = cy + COLUMN / 2 - 1.5 + 1.5 * half
        over.line(segment(Point3(ex, ey, 5.0), Point3(ex, ey, beamZ), P), Tone.Faint)
        for (z in listOf(124.0, 248.0, 372.0)) splice(column, cx, cy, z)
    }
}

private fun RaptorBuild.splice(column: Plan, cx: Double, cy: Double, z: Double) {
    val s = insetPlan(column, -2.2)
    over.solid(slabOf(s, z, 13.0, P, 3, 0.5), Tone.Mid)
    recorder.solid(
        box(Point3(s.x, s.y, z), Point3(s.x + s.w, s.y + s.d, z + 13), meta("stand.splice"))
    )
    val right = s.x + s.w
    val front = s.y + s.d
    over.dots(
        listOf(
                Point3(right, cy - 6, z + 4),
                Point3(right, cy + 6, z + 4),
                Point3(right, cy - 6, z + 9),
                Point3(right, cy + 6, z + 9),
                Point3(cx - 6, front, z + 4),
                Point3(cx + 6, front, z + 4),
                Point3(cx - 6, front, z + 9),
                Point3(cx + 6, front, z + 9),
            )
            .map { at(it) },
        0.42,
    )
}

private fun RaptorBuild.ladder() {
    val beamZ = Raptor.BEAM_Z
    val trayX = COLUMN_AT[1].x - COLUMN / 2 - 6
    over.solid(
        slabOf(Plan(trayX, COLUMN_AT[1].y + 2, 6.0, 9.0, 0.6), 28.0, beamZ - 36, P, 2),
        Tone.Mid,
    )
    var z = 52.0
    while (z < beamZ - 20) {
        over.solid(
            slabOf(Plan(trayX - 0.8, COLUMN_AT[1].y + 1, 7.6, 11.0, 0.6), z, 3.0, P, 2),
            Tone.Mid,
        )
        z += 40
    }
    val ladderY = COLUMN_AT[0].y + COLUMN / 2
    val x0 = COLUMN_AT[0].x
    for (x in listOf(x0 - 9, x0 + 9)) {
        over.solid(
            slabOf(Plan(x - 1.3, ladderY + 8, 2.6, 2.6, 0.6), 18.0, beamZ - 52, P, 2),
            Tone.Mid,
            crease = null,
        )
    }
    z = 40.0
    while (z < beamZ - 38) {
        over.line(
            segment(Point3(x0 - 9, ladderY + 10.6, z), Point3(x0 + 9, ladderY + 10.6, z), P),
            Tone.Mid,
        )
        z += 28
    }
    for (rz in listOf(52.0, beamZ - 56)) {
        for (x in listOf(x0 - 9, x0 + 9)) {
            over.solid(
                slabOf(Plan(x - 1.0, ladderY, 2.0, 9.0, 0.4), rz, 2.0, P, 2),
                Tone.Mid,
                crease = null,
            )
        }
    }
}
