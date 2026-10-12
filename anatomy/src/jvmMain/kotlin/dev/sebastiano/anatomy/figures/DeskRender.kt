// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import dev.sebastiano.anatomy.kit.Anchor
import dev.sebastiano.anatomy.kit.Art
import dev.sebastiano.anatomy.kit.FaceMatrix
import dev.sebastiano.anatomy.kit.FillInk
import dev.sebastiano.anatomy.kit.FillMark
import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Free
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.IsoScope
import dev.sebastiano.anatomy.kit.LineMark
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Shape
import dev.sebastiano.anatomy.kit.Stroke as LineStroke
import dev.sebastiano.anatomy.kit.TextMark
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.circleRing
import dev.sebastiano.anatomy.kit.drawFill
import dev.sebastiano.anatomy.kit.faceTextAdvance
import dev.sebastiano.anatomy.kit.pathOf
import dev.sebastiano.anatomy.kit.translateAlong
import kotlin.math.round

private val TRIM = art {
    line(DeskComputer.FACE_TRIM.onFace(DeskComputer.FRONT), tone = Tone.Faint)
}
private val GRILLE = art { line(DeskComputer.GRILLE.onFace(DeskComputer.FRONT), tone = Tone.Lo) }
private val SLOT_LINE = art {
    line(DeskScreen.SLOT_LINE.onFace(DeskScreen.SLOT.y - DeskScreen.SLOT.depth), tone = Tone.Lo)
}
private val REFLECTION = art {
    line(DeskScreen.reflectionPath.onFace(DeskScreen.GLASS_Y), tone = Tone.Lo)
}

/** Paint order is the original's: the nested screen is between shell and front controls. */
fun IsoScope.drawDesk(pc: DeskModel, calm: Boolean) {
    val d = DeskComputer
    drawArt(d.BASE)
    drawArt(d.SHELL)
    drawArt(TRIM)
    recess(DeskScreen.BEZEL) {
        recess(DeskScreen.CRT, glassColor(pc)) {
            picture(pc, calm)
            drawArt(REFLECTION)
        }
    }
    recess(DeskScreen.SLOT) { drawArt(SLOT_LINE) }
    drawArt(d.SLOT_DOT)
    drawArt(GRILLE)
    // Like the page's CSS, lamp opacity eases down over .4 s, glass over .5 s.
    val led =
        if (pc.power) 0.15 + 0.85 * transition(pc.powerClock / 0.4)
        else 1 - 0.85 * transition(pc.powerClock / 0.4)
    val point = d.at(-46.0, d.FRONT, 36.0)
    draw.drawCircle(
        palette.dot,
        0.8f,
        Offset(point.x.toFloat(), point.y.toFloat()),
        alpha = (0.85 * led).toFloat(),
    )
    drawArt(d.ROCKER_BASE)
    drawArt(if (pc.power) d.ROCKER_ON else d.ROCKER_OFF)
    drawArt(d.PORT)
    drawArt(d.KEYBOARD)
    DeskKeys.keys.forEachIndexed { index, key ->
        val down = pc.keys[index].at
        val offset = translateAlong(d.P, Point3(0.0, 0.0, -down))
        val rounded = Flat(round(offset.x * 100) / 100, round(offset.y * 100) / 100)
        if (down > 0.6) withPalette(palette.copy(mid = palette.hi)) { drawArt(key.art, rounded) }
        else drawArt(key.art, rounded)
    }
    drawArt(d.KEYBOARD_PORT)
    drawArt(d.CABLE)
}

private fun IsoScope.recess(r: DeskRecess, color: Color? = null, content: IsoScope.() -> Unit) {
    clipped(r.clip) {
        drawArt(r.walls)
        if (color == null) drawArt(r.back)
        else withPalette(palette.copy(litShade = color)) { drawArt(r.back) }
        content()
        drawArt(r.edge)
    }
    drawArt(r.rim)
}

private fun IsoScope.glassColor(pc: DeskModel): Color {
    val picture = deskPicture(pc.power, pc.powerClock)
    val elapsed = if (pc.power) pc.powerClock else maxOf(0.0, pc.powerClock - 0.26)
    val t = transition(elapsed / 0.5).toFloat()
    return if (picture.lit) lerp(palette.shades[0], palette.litShade, t)
    else lerp(palette.litShade, palette.shades[0], t)
}

/** The original's cubic-bezier(.32,.72,0,1) CSS ease, solved along x. */
private fun transition(time: Double): Double {
    val x = time.coerceIn(0.0, 1.0)
    var low = 0.0
    var high = 1.0
    repeat(18) {
        val t = (low + high) / 2
        val at = 0.96 * t * (1 - t) * (1 - t) + t * t * t
        if (at < x) low = t else high = t
    }
    val t = (low + high) / 2
    return 2.16 * t * (1 - t) * (1 - t) + 3 * t * t * (1 - t) + t * t * t
}

private fun IsoScope.picture(pc: DeskModel, calm: Boolean) {
    val frame = deskPicture(pc.power, pc.powerClock)
    val sx = round(frame.sx * 1000) / 1000
    val sy = round(frame.sy * 1000) / 1000
    fun scaled(path: IsoPath, x: Double = sx, y: Double = sy) =
        IsoPath(
                path.runs.map { run ->
                    IsoPath.Run(
                        run.points.map {
                            Flat(it.x * x, -DeskScreen.MID + (it.y + DeskScreen.MID) * y)
                        },
                        run.closed,
                    )
                }
            )
            .onFace(DeskScreen.GLASS_Y)
    if (sx > 0 && sy > 0) {
        drawFill(FillMark(Shape(scaled(DeskScreen.GLOW)), FillInk.Lit, 0.07, 6.0))
        drawArt(
            Art(
                listOf(
                    LineMark(
                        Shape(scaled(DeskScreen.SCAN)),
                        Tone.Faint,
                        LineStroke.Solid,
                        Free.None,
                        0.5,
                        0.7,
                        false,
                    )
                )
            )
        )
        drawFill(FillMark(Shape(scaled(DeskScreen.LOGO)), FillInk.Lit, evenOdd = true))
        screenText(pc.line, sx, sy, calm || pc.clock % 1.06 < 0.53)
    }
    if (frame.beam > 0) {
        drawArt(
            Art(
                listOf(
                    LineMark(
                        Shape(scaled(DeskScreen.BEAM, maxOf(sx, 0.001), 1.0)),
                        Tone.Lit,
                        LineStroke.Solid,
                        Free.None,
                        1.4,
                        round(frame.beam * 100) / 100,
                        false,
                    )
                )
            )
        )
    }
    if (frame.dot > 0) {
        val dot =
            pathOf(circleRing(0.0, -DeskScreen.MID, 1.4, 64).map { Flat(it.x, it.y) }, true)
                .onFace(DeskScreen.GLASS_Y)
        drawFill(FillMark(Shape(dot), FillInk.Lit, round(frame.dot * 100) / 100))
    }
}

private fun IsoScope.screenText(line: String, sx: Double, sy: Double, cursor: Boolean) {
    val plane = DeskScreen.CRT.backPlane
    val at =
        plane.map(
            DeskScreen.TEXT_X * sx,
            -DeskScreen.MID + (-DeskScreen.TEXT_Z + DeskScreen.MID) * sy,
        )
    val matrix = FaceMatrix(plane.u * sx, plane.v * sy, at)
    val text = "> $line"
    drawArt(Art(listOf(TextMark(matrix, text, DeskScreen.TEXT_SIZE, Tone.Lit, Anchor.Start, 0.02))))
    if (cursor) {
        val x = DeskScreen.TEXT_X + faceTextAdvance(text, DeskScreen.TEXT_SIZE, 0.02) + 0.4
        val y = -DeskScreen.TEXT_Z - 4.2
        val shape =
            pathOf(
                listOf(
                    Flat(x * sx, -DeskScreen.MID + (y + DeskScreen.MID) * sy),
                    Flat((x + 3) * sx, -DeskScreen.MID + (y + DeskScreen.MID) * sy),
                    Flat((x + 3) * sx, -DeskScreen.MID + (y + 5 + DeskScreen.MID) * sy),
                    Flat(x * sx, -DeskScreen.MID + (y + 5 + DeskScreen.MID) * sy),
                ),
                true,
            )
        drawFill(FillMark(Shape(shape.onFace(DeskScreen.GLASS_Y)), FillInk.Lit))
    }
}
