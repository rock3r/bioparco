// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.figures.Raptor.AZIMUTH
import dev.sebastiano.anatomy.figures.Raptor.BEAM_H
import dev.sebastiano.anatomy.figures.Raptor.BEAM_Z
import dev.sebastiano.anatomy.figures.Raptor.DECKF
import dev.sebastiano.anatomy.figures.Raptor.DEG
import dev.sebastiano.anatomy.figures.Raptor.FADE
import dev.sebastiano.anatomy.figures.Raptor.FTP
import dev.sebastiano.anatomy.figures.Raptor.FTP_AT
import dev.sebastiano.anatomy.figures.Raptor.LUGS
import dev.sebastiano.anatomy.figures.Raptor.P
import dev.sebastiano.anatomy.figures.Raptor.TOP
import dev.sebastiano.anatomy.figures.Raptor.Z_EXIT
import dev.sebastiano.anatomy.figures.Raptor.at
import dev.sebastiano.anatomy.figures.Raptor.dir
import dev.sebastiano.anatomy.figures.Raptor.ex
import dev.sebastiano.anatomy.figures.Raptor.polar
import dev.sebastiano.anatomy.kit.ArtBuilder
import dev.sebastiano.anatomy.kit.Floor
import dev.sebastiano.anatomy.kit.Frame
import dev.sebastiano.anatomy.kit.Knot
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.add3
import dev.sebastiano.anatomy.kit.arcOf
import dev.sebastiano.anatomy.kit.audit.BoxOptions
import dev.sebastiano.anatomy.kit.audit.PipeOptions
import dev.sebastiano.anatomy.kit.audit.ShapeMeta
import dev.sebastiano.anatomy.kit.audit.Style
import dev.sebastiano.anatomy.kit.audit.TubeOptions
import dev.sebastiano.anatomy.kit.audit.body
import dev.sebastiano.anatomy.kit.audit.disc
import dev.sebastiano.anatomy.kit.audit.orientedBox
import dev.sebastiano.anatomy.kit.audit.pipe
import dev.sebastiano.anatomy.kit.audit.solid
import dev.sebastiano.anatomy.kit.corners
import dev.sebastiano.anatomy.kit.cross3
import dev.sebastiano.anatomy.kit.cylinder
import dev.sebastiano.anatomy.kit.disc
import dev.sebastiano.anatomy.kit.dotsOf
import dev.sebastiano.anatomy.kit.extrude
import dev.sebastiano.anatomy.kit.fillet
import dev.sebastiano.anatomy.kit.frameAlong
import dev.sebastiano.anatomy.kit.knurl
import dev.sebastiano.anatomy.kit.lathe
import dev.sebastiano.anatomy.kit.len3
import dev.sebastiano.anatomy.kit.mul3
import dev.sebastiano.anatomy.kit.onTop
import dev.sebastiano.anatomy.kit.pointOf
import dev.sebastiano.anatomy.kit.prismOf
import dev.sebastiano.anatomy.kit.ribsOf
import dev.sebastiano.anatomy.kit.segment
import dev.sebastiano.anatomy.kit.sideArc
import dev.sebastiano.anatomy.kit.stadium
import dev.sebastiano.anatomy.kit.sub3
import dev.sebastiano.anatomy.kit.unit3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The stand's parts that hang with the engine: the methane feed line and its bracket, the quick
 * disconnects, the two gimbal actuators, and the crosshead with its load cell and beam.
 */

internal fun RaptorBuild.standParts() {
    val feedTop = BEAM_Z - 6
    val feed = listOf(ex(FTP, 265.0), ex(FTP, 290.0), Point3(FTP_AT.x, FTP_AT.y, feedTop))
    parts.pipe(
        "stand",
        feed,
        7.2,
        "stand.feed",
        PipeOptions(
            bend = 2.0,
            bellows = Triple(0.12, 0.32, 7),
            bands = listOf(0.62),
            maxLength = 14.0,
            free = true to false,
        ),
    )
    val bracket = Plan(FTP_AT.x - 13, FTP_AT.y - 13, 26.0, 26.0, 2.0)
    val extra: ArtBuilder.() -> Unit = {
        dots(corners(bracket, 3.0).map { at(Point3(it.x, it.y, feedTop + 6)) }, 0.45)
    }
    parts.boxAt(
        "stand",
        bracket,
        feedTop,
        6.0,
        options = BoxOptions(2.0, name = "stand.feed-bracket", extra = extra),
    )
    for ((deg, reach) in listOf(135.0 to 50.0, -45.0 to 46.0)) {
        val quick = polar(deg, reach, TOP + 21)
        parts.boxAt(
            "stand",
            Plan(quick.x - 4, quick.y - 4, 8.0, 8.0, 1.0),
            quick.z,
            BEAM_Z - quick.z,
            options = BoxOptions(1.0, name = "stand.quick-disconnect"),
        )
    }
}

private const val CLEVIS_BALL = 4.6
private val HEAD =
    listOf(Knot(9.0, 0.0), Knot(9.0, 6.2), Knot(11.0, 6.6), Knot(13.0, 6.6), Knot(13.0, 0.0))
private val BARREL =
    listOf(
        Knot(15.0, 0.0),
        Knot(15.0, 6.6),
        Knot(35.0, 6.6),
        Knot(37.0, 6.2),
        Knot(39.0, 4.0),
        Knot(39.0, 0.0),
    )

internal fun RaptorBuild.actuators() {
    for ((deg, foot) in LUGS) {
        val lug = lugBall(foot)
        val top = Point3(cos(deg * DEG) * 92, sin(deg * DEG) * 92, BEAM_Z)
        val id = recorder.nameFor("stand.actuator")
        parts.boxAt(
            "stand",
            Plan(top.x - 5, top.y - 5, 10.0, 10.0, 1.0),
            BEAM_Z - 3,
            3.0,
            options = BoxOptions(name = "$id.mount", bevel = 0.4),
        )
        val pivot = add3(top, Point3(0.0, 0.0, -3 - CLEVIS_BALL))
        parts.ballAt("stand", pivot, CLEVIS_BALL, Tone.Hi, name = "$id.pivot", owner = id)
        val axis = unit3(sub3(lug, pivot))
        val span = len3(sub3(lug, pivot))
        val f = frameAlong(pivot, axis)
        val clevisFrom = sqrt(CLEVIS_BALL * CLEVIS_BALL - 3.0 * 3.0)
        fun meta(part: String) = ShapeMeta(name = "$id.$part", owner = id)
        parts.put(
            "stand",
            add3(pivot, mul3(axis, 5.0)),
            listOf(recorder.solid(disc(f, clevisFrom, 9.0, 3.0, meta("clevis")))),
            name = "$id.clevis",
        ) {
            solid(disc(clevisFrom, 9.0, 3.0, f, P), Tone.Mid)
        }
        val side = unit3(cross3(axis, Point3(0.0, 0.0, 1.0)))
        val sf = Frame(pivot, axis, side, cross3(axis, side))
        parts.put(
            "stand",
            add3(pivot, mul3(axis, 11.0)),
            listOf(recorder.solid(body(f, HEAD, meta("head")))),
            name = "$id.head",
        ) {
            solid(lathe(HEAD, f, P, 40), Tone.Hi)
        }
        gearbox(id, pivot, axis, side, sf, f)
        parts.put(
            "stand",
            add3(pivot, mul3(axis, 25.0)),
            listOf(recorder.solid(body(f, BARREL, meta("barrel")))),
            name = "$id.barrel",
        ) {
            solid(
                lathe(BARREL, f, P, 40),
                Style(
                    inner =
                        listOf(21.0, 27.0, 32.0)
                            .map { arcOf(f, it, 6.6, P) }
                            .reduce { a, b -> a + b }
                ),
            )
        }
        val mf = frameAlong(add3(pivot, mul3(side, 10.9)), axis)
        parts.put(
            "stand",
            pointOf(mf, 22.0, 0.0, 0.0),
            listOf(recorder.solid(disc(mf, 15.0, 29.0, 4.2, meta("motor")))),
            -0.5,
            name = "$id.motor",
        ) {
            solid(disc(15.0, 29.0, 4.2, mf, P), Tone.Mid)
            faded(ribsOf(mf, 15.0, 29.0, 4.2, 14, P, fade = 0.1 to 0.55), Tone.Lo)
        }
        val rodEnd = span - sqrt(LUG_BALL * LUG_BALL - 2.1 * 2.1)
        parts.put(
            "stand",
            add3(pivot, mul3(axis, (39 + rodEnd) / 2)),
            listOf(recorder.solid(disc(f, 39.0, rodEnd, 2.1, meta("rod")))),
            name = "$id.rod",
        ) {
            solid(disc(39.0, rodEnd, 2.1, f, P), Tone.Hi)
        }
        parts.ballAt("stand", lug, LUG_BALL, name = "$id.eye", owner = id)
    }
}

private fun RaptorBuild.gearbox(
    id: String,
    pivot: Point3,
    axis: Point3,
    side: Point3,
    sf: Frame,
    f: Frame,
) {
    val meta = ShapeMeta(name = "$id.gearbox", owner = id)
    val shapes =
        listOf(
            recorder.solid(disc(f, 13.0, 15.0, 7.4, meta)),
            recorder.solid(
                disc(frameAlong(add3(pivot, mul3(side, 10.9)), axis), 13.0, 15.0, 5.0, meta)
            ),
            recorder.solid(
                orientedBox(
                    add3(add3(pivot, mul3(axis, 14.0)), mul3(side, 5.45)),
                    listOf(side, sf.v, axis),
                    listOf(5.45, 5.0, 1.0),
                    meta,
                )
            ),
        )
    parts.put("stand", add3(pivot, mul3(axis, 14.0)), shapes, name = "$id.gearbox") {
        solid(prismOf(sf, stadium(7.4, 5.0, 10.9), 13.0, 15.0, P), Tone.Mid)
        val face = pointOf(sf, 15.0, 0.0, 0.0)
        dots(listOf(at(face), at(add3(face, mul3(side, 10.9)))), 0.42)
    }
}

private const val BEAM_HALF = 14.0
private const val BEAM_END = 196.0

internal fun RaptorBuild.beam() {
    val along = unit3(Point3(1.0, -1.0, 0.0))
    val across = unit3(Point3(1.0, 1.0, 0.0))
    fun flat(t: Double, offset: Double) = add3(mul3(along, t), mul3(across, offset))
    val ring =
        listOf(
                flat(-BEAM_END, -BEAM_HALF),
                flat(BEAM_END, -BEAM_HALF),
                flat(BEAM_END, BEAM_HALF),
                flat(-BEAM_END, BEAM_HALF),
            )
            .map { Floor(it.x, it.y) }
    val beamTop = BEAM_Z + BEAM_H
    fun beamLine(offset: Double, z: Double) =
        onTop(
            listOf(flat(-BEAM_END, offset), flat(BEAM_END, offset)).map { Floor(it.x, it.y) },
            z,
            P,
        )
    val bolts = ArrayList<dev.sebastiano.anatomy.kit.Flat>()
    var t = -176.0
    while (t <= 176) {
        for (side in listOf(-1.0, 1.0)) flat(t, side * 8.5).let {
            bolts += at(Point3(it.x, it.y, beamTop))
        }
        t += 32
    }
    var face = dev.sebastiano.anatomy.kit.IsoPath.Empty
    t = -168.0
    while (t <= 168) {
        val p = flat(t, BEAM_HALF)
        face += segment(Point3(p.x, p.y, BEAM_Z), Point3(p.x, p.y, beamTop), P)
        t += 56
    }
    val base = Z_EXIT + TOP
    parts.put(
        "stand",
        Point3(0.0, 0.0, BEAM_Z - 13),
        listOf(
            recorder.solid(disc(DECKF, base, BEAM_Z - 8, 12.6, ShapeMeta(name = "stand.loadcell")))
        ),
        50.0,
        name = "stand.loadcell",
    ) {
        solid(cylinder(0.0, 0.0, 12.6, base, BEAM_Z - 8 - base, P, 40), Tone.Mid)
        line(knurl(0.0, 0.0, 12.6, base + 4, base + 10, 40, P), Tone.Lo)
        line(sideArc(0.0, 0.0, 12.6, base + 11.5, P, 24), Tone.Faint)
    }
    parts.put(
        "stand",
        Point3(0.0, 0.0, BEAM_Z - 4),
        listOf(
            recorder.solid(
                disc(DECKF, BEAM_Z - 8, BEAM_Z, 34.0, ShapeMeta(name = "stand.crosshead"))
            )
        ),
        60.0,
        name = "stand.crosshead",
    ) {
        solid(cylinder(0.0, 0.0, 34.0, BEAM_Z - 8, 8.0, P, 72, 0.8), Tone.Mid)
        val faded = dotsOf(DECKF, BEAM_Z, 29.5, 16, P, fade = FADE)
        dots(faded.points, 0.4, alphas = faded.alphas)
    }
    gland()
    parts.put(
        "stand",
        Point3(0.0, 0.0, BEAM_Z + BEAM_H / 2),
        listOf(
            recorder.solid(
                orientedBox(
                    Point3(0.0, 0.0, BEAM_Z + BEAM_H / 2),
                    listOf(along, across, Point3(0.0, 0.0, 1.0)),
                    listOf(BEAM_END, BEAM_HALF, BEAM_H / 2),
                    ShapeMeta(name = "stand.beam"),
                )
            )
        ),
        200.0,
        name = "stand.beam",
    ) {
        solid(extrude(ring, BEAM_Z, BEAM_H, P, bevel = 0.9), Tone.Mid)
        line(beamLine(-BEAM_HALF + 5, beamTop) + beamLine(BEAM_HALF - 5, beamTop), Tone.Lo)
        line(face, Tone.Faint)
        dots(bolts, 0.45)
    }
}

private fun RaptorBuild.gland() {
    val deg = AZIMUTH - 90
    val gland = Point3(cos(deg * DEG) * 12.6, sin(deg * DEG) * 12.6, Z_EXIT + TOP + 7)
    val f = frameAlong(gland, dir(deg))
    parts.put(
        "stand",
        gland,
        listOf(recorder.solid(disc(f, 0.0, 4.0, 2.0, ShapeMeta(name = "stand.gland")))),
        61.0,
        name = "stand.gland",
    ) {
        solid(disc(0.0, 4.0, 2.0, f, P), Tone.Mid)
    }
    val bend = add3(gland, mul3(dir(deg), 11.0))
    val route =
        fillet(
            listOf(add3(gland, mul3(dir(deg), 4.0)), bend, Point3(bend.x, bend.y, BEAM_Z - 8)),
            4.0,
        )
    parts.tubeItems(
        "stand",
        route,
        0.62,
        "stand.loadcell-cable",
        TubeOptions(Tone.Mid, 61.0, maxLength = 60.0, spacing = 1.0),
    )
}
