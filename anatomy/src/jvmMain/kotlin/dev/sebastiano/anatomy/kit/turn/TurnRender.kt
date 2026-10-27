// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.translate
import dev.sebastiano.anatomy.kit.Art
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.IsoScope
import dev.sebastiano.anatomy.kit.Palette
import dev.sebastiano.anatomy.kit.SolidMark
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.WEIGHT
import dev.sebastiano.anatomy.kit.toComposePath

/*
 * Paints the runtime's frames the way `TURN_CSS` styles the original's live SVG: fills in paper,
 * side sheets and the top in the ramp's tones (stroked in their own colour where a surface is
 * seamless), flat faces in a crossfade between two tones, lines in their tone and opacity, and
 * every part in its material's ramp. Each part is also handed to the frame's mark recorder as
 * ordinary marks, so the line-end check sees live parts as it sees static art.
 */

/**
 * Paints the live layer [layer] of [controller], in this frame's painting order. Billboards (a
 * shader's SVG fallback) are drawn at [billboards] times their facing fade: hand them over to the
 * shader with the original's `(1 − u)/(1 − u·α)`.
 */
fun IsoScope.drawTurnLayer(controller: TurnController, layer: String, billboards: Float = 1f) {
    val order = controller.orderIndex(layer) ?: return
    val scene = controller.scene
    for (index in order) {
        val run = scene.runs[index]
        val part = run.part
        val f = controller.frames[index]
        withPalette(materialPalette(palette, part.material)) {
            when (run) {
                is FixedRun -> drawFixed(run, f, billboards)
                is TubeRun -> drawTubePart(run, f)
                else -> drawSolidPart(run, f, billboards)
            }
        }
    }
}

/** A path's Compose path, or null when it is empty. */
internal fun IsoPath.orNull(): Path? = if (isEmpty) null else toComposePath()

/** The ramp a face crossfades along: the four side tones and the top, or the lit tokens. */
internal fun Palette.ramp(lit: Boolean): List<Color> =
    if (lit) List(4) { litShade } + litTop else shades + top

/** A flat face's colour for level [q]: `color-mix` of two neighbouring tones. */
internal fun faceColour(ramp: List<Color>, q: Int): Color {
    val band = minOf(3, q / TURN_Q)
    val mix = (q - TURN_Q * band).toFloat() / TURN_Q
    return mixSrgb(ramp[band], 1 - mix, ramp[band + 1])
}

private fun IsoScope.drawSolidPart(run: TurnRun, f: TurnFrame, billboards: Float) {
    val part = run.part
    val lit = part.lit
    val seamless = run is RoundRun || run is BallRun || run is LatheRun
    val ramp = palette.ramp(lit)
    val top = if (lit) palette.litTop else palette.top
    for ((r, role) in run.roles.withIndex()) {
        when (role.kind) {
            RoleKind.Fill -> fill(f.d[r], if (lit) palette.face else palette.paper)
            RoleKind.Shade ->
                sheet(f.d[r], if (lit) palette.litShade else palette.shades[role.shade], seamless)
            RoleKind.Top -> sheet(f.d[r], top, seamless)
            RoleKind.Face -> face(f.d[r], faceColour(ramp, f.q[r]), f.w[r])
            RoleKind.TopXform -> fill(topOf(run as PrismRun, f.t[r]), top)
            else -> drawLineRole(run, role, f, r, billboards)
        }
    }
    drawDots(run, f)
    if (recording) recordSolid(run, f)
}

/** A part's line roles: creases, inner lines, faded slots, planes and its edge. */
private fun IsoScope.drawLineRole(
    run: TurnRun,
    role: Role,
    f: TurnFrame,
    r: Int,
    billboards: Float,
) {
    val part = run.part
    when (role.kind) {
        RoleKind.Crease,
        RoleKind.Inner -> stroke(f.d[r], palette.ink(role.tone!!), 1f)
        RoleKind.Bevel,
        RoleKind.RibSlot -> stroke(f.d[r], palette.ink(role.tone!!), f.o[r].toFloat())
        RoleKind.Plane ->
            drawPlane(part.planes[role.name.drop(5).toInt()], f.t[r], f.o[r], billboards)
        RoleKind.Edge -> stroke(f.d[r], if (part.lit) palette.lit else palette.ink(part.tone), 1f)
        else -> Unit
    }
}

/** The prism's plan placed by its top matrix. */
private fun topOf(run: PrismRun, t: Affine?): IsoPath {
    t ?: return IsoPath.Empty
    val ring = run.ring
    val points =
        (0 until ring.size / 2).map { i ->
            val x = ring[2 * i]
            val y = ring[2 * i + 1]
            dev.sebastiano.anatomy.kit.Flat(t.x(x, y), t.y(x, y))
        }
    return t3PathOf(points, true)
}

internal fun IsoScope.fill(path: IsoPath, colour: Color) {
    path.orNull()?.let { draw.drawPath(it, colour, style = Fill) }
}

/** A tone sheet; a seamless one is stroked 0.4 px in its own colour so neighbours meet. */
private fun IsoScope.sheet(path: IsoPath, colour: Color, seamless: Boolean) {
    val p = path.orNull() ?: return
    draw.drawPath(p, colour, style = Fill)
    if (seamless) draw.drawPath(p, colour, style = hairline(0.4f))
}

/** A flat face: filled and stroked in its crossfaded tone, the stroke narrowed on slivers. */
private fun IsoScope.face(path: IsoPath, colour: Color, width: Double) {
    val p = path.orNull() ?: return
    draw.drawPath(p, colour, style = Fill)
    val stroke = if (width >= 0) width.toFloat() else 0.4f
    if (stroke > 0f) draw.drawPath(p, colour, style = hairline(stroke))
}

internal fun IsoScope.stroke(path: IsoPath, colour: Color, alpha: Float, width: Float = WEIGHT) {
    if (alpha <= 0f) return
    val p = path.orNull() ?: return
    draw.drawPath(p, colour, alpha = alpha.coerceIn(0f, 1f), style = hairline(width))
}

private fun IsoScope.drawDots(run: TurnRun, f: TurnFrame) {
    for ((r, role) in run.roles.withIndex()) {
        val o = if (role.kind == RoleKind.Dot) f.o[r].toFloat() else 0f
        if (o <= 0f) continue
        draw.drawCircle(
            palette.dot,
            role.size.toFloat(),
            androidx.compose.ui.geometry.Offset(f.cx[r].toFloat(), f.cy[r].toFloat()),
            alpha = palette.dotAlpha(role.tone!!) * o,
        )
    }
}

/** A fixed picture: its art moved by the frame's shift, its details before its outline. */
private fun IsoScope.drawFixed(run: FixedRun, f: TurnFrame, billboards: Float) {
    val art = run.part.art ?: Art.Empty
    val solid = art.marks.lastOrNull() as? SolidMark
    draw.translate(f.shiftX.toFloat(), f.shiftY.toFloat()) {
        if (solid == null) drawArt(art) else drawArt(Art(art.marks.dropLast(1)))
        if (solid != null) solidBody(solid)
        for ((r, role) in run.roles.withIndex()) {
            when (role.kind) {
                RoleKind.RibSlot -> stroke(f.d[r], palette.ink(role.tone!!), f.o[r].toFloat())
                RoleKind.Plane ->
                    drawPlane(
                        run.part.planes[role.name.drop(5).toInt()],
                        f.t[r],
                        f.o[r],
                        billboards,
                    )
                else -> Unit
            }
        }
        if (solid != null) {
            stroke(solid.paths.outline, if (solid.lit) palette.lit else palette.ink(solid.tone), 1f)
            if (recording) record(solid)
        }
        drawDots(run, f)
        if (recording) recordDetails(run, f)
    }
}

/** A kit solid without its outline: fill, sheets, top, crease, bevel and inner lines. */
private fun IsoScope.solidBody(mark: SolidMark) {
    val p = palette
    fill(mark.paths.fill, if (mark.lit) p.face else p.paper)
    if (!mark.flat) {
        mark.paths.shades.forEachIndexed { shade, path ->
            sheet(path, if (mark.lit) p.litShade else p.shades[shade], mark.paths.seamless)
        }
        sheet(mark.paths.top, if (mark.lit) p.litTop else p.top, mark.paths.seamless)
    }
    mark.crease?.let { tone ->
        stroke(mark.paths.crease, p.ink(tone), 1f)
        mark.paths.bevel?.let { stroke(it, p.ink(tone), 1f) }
    }
    mark.inner?.let { drawArt(it) }
}

/** A tube chunk: body, shine and shade stripes, rings by opacity, and its edges. */
private fun IsoScope.drawTubePart(run: TubeRun, f: TurnFrame) {
    val ink = tubeInk(palette, run.part.material, run.hue, run.part.tone)
    for ((r, role) in run.roles.withIndex()) {
        when (role.kind) {
            RoleKind.Body -> fill(f.d[r], ink.body)
            RoleKind.Shine -> fill(f.d[r], ink.shine)
            RoleKind.TubeShade -> fill(f.d[r], ink.shade)
            RoleKind.Ring -> ring(f.d[r], palette.ink(role.tone ?: Tone.Lo), f.o[r].toFloat())
            RoleKind.TubeEdge -> stroke(f.d[r], ink.edge, 1f)
            else -> Unit
        }
    }
    if (recording) recordTube(run, f)
}

private fun IsoScope.ring(path: IsoPath, colour: Color, alpha: Float) {
    if (alpha <= 0f) return
    val p = path.orNull() ?: return
    val style =
        androidx.compose.ui.graphics.drawscope.Stroke(
            px(0.45f),
            cap = androidx.compose.ui.graphics.StrokeCap.Butt,
            join = androidx.compose.ui.graphics.StrokeJoin.Round,
        )
    draw.drawPath(p, colour, alpha = alpha.coerceIn(0f, 1f), style = style)
}
