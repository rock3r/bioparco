// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit.turn

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import dev.sebastiano.anatomy.kit.DotsMark
import dev.sebastiano.anatomy.kit.FaceMatrix
import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Free
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.IsoScope
import dev.sebastiano.anatomy.kit.LineMark
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Shape
import dev.sebastiano.anatomy.kit.SolidMark
import dev.sebastiano.anatomy.kit.SolidPaths
import dev.sebastiano.anatomy.kit.Stroke
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.TubeMark
import dev.sebastiano.anatomy.kit.TubePiece
import kotlin.math.cos
import kotlin.math.sin

/*
 * Planes (markup placed by one matrix and faded by facing) and the stand-in marks a live part hands
 * the frame's mark recorder: its fill, outline and sheets as a solid, its seams, ribs and plane
 * lines as lines with their opacity, its dots, and a tube chunk as a tube.
 */

/** [path] in plane units placed by [t] on screen. */
internal fun IsoPath.placed(t: Affine): IsoPath =
    IsoPath(runs.map { run -> IsoPath.Run(run.points.map { Flat(t.x(it.x, it.y), t.y(it.x, it.y)) }, run.closed) })

/** Paints [block] at opacity [alpha] (as one layer), or directly when it is opaque. */
private fun IsoScope.withAlpha(alpha: Double, block: IsoScope.() -> Unit) {
    if (alpha >= 0.999) {
        block()
        return
    }
    draw.drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(-1e4f, -1e4f, 1e4f, 1e4f), Paint().apply { this.alpha = alpha.toFloat() })
        block()
        canvas.restore()
    }
}

/** Paints a plane's marks placed by [t] at opacity [o]. */
internal fun IsoScope.drawPlane(plane: PlaneData, t: Affine?, o: Double, billboards: Float = 1f) {
    if (t == null || o <= 0.0) return
    for (mark in plane.content.marks) {
        when (mark) {
            is PlaneLine -> {
                val path = mark.path.placed(t)
                stroke(path, palette.ink(mark.tone), o.toFloat())
                if (recording) record(LineMark(Shape(path), mark.tone, Stroke.Solid, mark.free, null, o))
            }
            is PlaneText -> withAlpha(o) { faceText(textMatrix(t, mark), mark.text, mark.size, mark.tone, mark.anchor) }
            is PlaneDisc -> {
                val alpha = (mark.alpha * o).toFloat() * if (plane.billboard) billboards else 1f
                if (alpha > 0f) draw.drawCircle(mark.fill(palette), mark.r.toFloat(), Offset(t.e.toFloat(), t.f.toFloat()), alpha = alpha)
            }
        }
    }
}

/** SVG's `matrix(t) translate(x y) rotate(θ) translate(0 dy)` for a line of text. */
private fun textMatrix(t: Affine, text: PlaneText): FaceMatrix {
    val angle = text.rotate * Math.PI / 180
    val c = cos(angle)
    val s = sin(angle)
    val ox = text.x - s * text.dy
    val oy = text.y + c * text.dy
    return FaceMatrix(
        Flat(t.a * c + t.c * s, t.b * c + t.d * s),
        Flat(-t.a * s + t.c * c, -t.b * s + t.d * c),
        Flat(t.x(ox, oy), t.y(ox, oy)),
    )
}

private fun TurnRun.path(f: TurnFrame, name: String): IsoPath = role[name]?.let { f.d[it] } ?: IsoPath.Empty

/** A solid live part as the recorder sees kit art: a solid, then its lines and dots. */
internal fun IsoScope.recordSolid(run: TurnRun, f: TurnFrame) {
    val part = run.part
    var sheets = IsoPath.Empty
    for ((r, role) in run.roles.withIndex()) {
        when (role.kind) {
            RoleKind.Shade, RoleKind.Top, RoleKind.Face -> sheets += f.d[r]
            RoleKind.TopXform -> f.t[r]?.let { t -> sheets += t3PathOf((0 until (run as PrismRun).ring.size / 2).map { Flat(t.x(run.ring[2 * it], run.ring[2 * it + 1]), t.y(run.ring[2 * it], run.ring[2 * it + 1])) }, true) }
            else -> Unit
        }
    }
    val paths =
        SolidPaths(run.path(f, "fill"), run.path(f, "edge"), run.path(f, "crease"), IsoPath.Empty,
            listOf(sheets, IsoPath.Empty, IsoPath.Empty, IsoPath.Empty), seamless = true)
    record(SolidMark(paths, part.tone, part.crease ?: Tone.Faint, part.lit, false, null))
    recordDetails(run, f)
}

/** A part's seams, faded lines and dots, as the recorder's lines and dots. */
internal fun IsoScope.recordDetails(run: TurnRun, f: TurnFrame) {
    val points = ArrayList<Flat>()
    val alphas = ArrayList<Double>()
    var size = 0.5
    var tone = Tone.Mid
    for ((r, role) in run.roles.withIndex()) {
        when (role.kind) {
            RoleKind.Inner -> recordLine(f.d[r], role.tone!!, 1.0)
            RoleKind.RibSlot, RoleKind.Bevel -> if (role.kind == RoleKind.RibSlot) recordLine(f.d[r], role.tone!!, f.o[r])
            RoleKind.Dot -> {
                points.add(Flat(f.cx[r], f.cy[r]))
                alphas.add(f.o[r])
                size = role.size
                tone = role.tone!!
            }
            else -> Unit
        }
    }
    if (points.isNotEmpty()) record(DotsMark(points, alphas, size, tone))
}

private fun IsoScope.recordLine(path: IsoPath, tone: Tone, alpha: Double) {
    if (!path.isEmpty) record(LineMark(Shape(path), tone, Stroke.Solid, Free.None, null, alpha))
}

/** A tube chunk as the recorder sees the kit's tubes. */
internal fun IsoScope.recordTube(run: TubeRun, f: TurnFrame) {
    val rings = ArrayList<Pair<IsoPath, Double>>()
    for ((r, role) in run.roles.withIndex()) if (role.kind == RoleKind.Ring && !f.d[r].isEmpty) rings.add(f.d[r] to f.o[r])
    val piece =
        TubePiece(rings, run.part.spec.let { (it as TubeSpec).ringTone } ?: Tone.Lo, Point3(0.0, 0.0, 0.0), run.path(f, "body"),
            run.path(f, "shine"), run.path(f, "shade"), run.path(f, "edges"), run.wide, emptyList(), emptyList(), true to true, 0.0, 0.0)
    record(TubeMark(piece, run.part.tone))
}
