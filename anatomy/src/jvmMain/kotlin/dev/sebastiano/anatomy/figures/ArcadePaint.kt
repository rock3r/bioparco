// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import dev.sebastiano.anatomy.kit.Anchor
import dev.sebastiano.anatomy.kit.Art
import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Free
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.IsoScope
import dev.sebastiano.anatomy.kit.Knot
import dev.sebastiano.anatomy.kit.LineMark
import dev.sebastiano.anatomy.kit.Palette
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.Shape
import dev.sebastiano.anatomy.kit.SolidMark
import dev.sebastiano.anatomy.kit.SolidPaths
import dev.sebastiano.anatomy.kit.Tone
import dev.sebastiano.anatomy.kit.VoidMark
import dev.sebastiano.anatomy.kit.axisVector
import dev.sebastiano.anatomy.kit.frameOf
import dev.sebastiano.anatomy.kit.lathe
import dev.sebastiano.anatomy.kit.pathOf
import dev.sebastiano.anatomy.kit.toComposePath
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** The build's settled order, including the original's coin slots between cabinet walls. */
internal class ArcadePaint(palette: Palette) {
    private val colors = ArcadePalettes(palette)
    private val items = ArcadeCabinet.ITEMS
    private val chuteSlot = maxOf(index("chute.floor"), index("chute.far"))
    private val cashSlot = maxOf(index("cash.floor"), index("cash.back"), index("cash.far"))
    private val routes = items.filter { it.route != null }.groupingBy { it.route }.eachCount()
    private val screen = ArcadeScreenPaint()
    private val face = arcadePath(ArcadeBezel.FACE).apply { fillType = PathFillType.EvenOdd }
    private val glass = ArcadeBezel.FACE_CLIP.toComposePath()
    private val opening = ArcadeBezel.OPEN_CLIP.toComposePath()
    private val glare = ArcadeBezel.GLARE.toComposePath()
    private val tray =
        lathe(
            listOf(Knot(0.0, 0.0), Knot(0.0, 9.0), Knot(1.6, 9.0), Knot(1.6, 7.6), Knot(0.8, 7.2)),
            frameOf(Arcade.TRAY, Arcade.Z, Arcade.X, Arcade.Y),
            Arcade.P,
            steps = 48,
        )
    private val clips = mapOf("door" to coinClip(159.8, 400.0), "mech" to coinClip(141.0, -400.0))
    private val firePush = axisVector(Arcade.P, dev.sebastiano.anatomy.kit.mul3(Arcade.CPN, -1.1))
    private var tilt = Double.NaN
    private var upper = Art.Empty
    private var lower = Art.Empty
    private val stack =
        (0..6).map { ArcadeParts.coin(ArcadeCoinMotion.stackSpot(it), Arcade.Z, lit = false) }
    private val pile = mutableListOf<Art>()

    fun paint(scope: IsoScope, m: ArcadeMachine) =
        with(scope) {
            val picture = m.picture()
            colors.tick(m, picture)
            drawArt(ArcadeCabinet.BUILD.back)
            solid(tray, Tone.Lo)
            for (i in 0 until m.stack) drawArt(stack[i])
            updateStick(m)
            for ((i, item) in items.withIndex()) {
                item(scope, m, item)
                if (item.name == "bezel") bezel(scope, m, picture)
                ArcadeCabinet.BUILD.surfaces[item.name]?.let { surface(scope, it, item.name, m) }
                if (i == chuteSlot) {
                    coins(scope, m, "chute")
                    wire(scope, m.wire)
                }
                if (i == cashSlot) {
                    while (pile.size < m.pile) pile.add(
                        ArcadeParts.coin(
                            ArcadeCoinMotion.pileSpot(pile.size),
                            Arcade.Z,
                            pile.size * 1.7,
                            false,
                        )
                    )
                    for (n in 0 until m.pile) drawArt(pile[n])
                    coins(scope, m, "fall")
                }
            }
            coins(scope, m, "fly")
        }

    private fun index(name: String) = items.indexOfFirst { it.name == name }

    private fun updateStick(m: ArcadeMachine) {
        val value = (kotlin.math.round(m.stick * 1000) / 1000)
        if (value == tilt) return
        tilt = value
        upper = ArcadeParts.upper(m.stick * 0.21)
        lower = ArcadeParts.lower(m.stick * 0.21)
    }

    private fun item(
        s: IsoScope,
        m: ArcadeMachine,
        item: dev.sebastiano.anatomy.kit.audit.AuditItem,
    ) =
        with(s) {
            val art =
                when (item.name) {
                    "stick.upper" -> upper
                    "stick.lower" -> lower
                    else -> checkNotNull(item.art)
                }
            val hot =
                m.pulses.any {
                    it.route == item.route && it.hot(item.chunk, routes[item.route] ?: 0)
                }
            val tokens = colors.of(item.name, m, hot)
            withPalette(tokens) {
                when {
                    item.name == "button.fire" -> drawArt(art, firePush * m.fire)
                    item.name == "door.slot" || item.name == "door.return.mouth" ->
                        windowArt(this, art)
                    else -> drawArt(art)
                }
            }
        }

    /** The CSS slot outlines are not texture lines (`iso-line`); they still root other lines. */
    private fun windowArt(s: IsoScope, art: Art) {
        art.marks.forEachIndexed { i, mark ->
            if (mark is LineMark && art.marks.getOrNull(i - 1) is VoidMark) {
                val path = mark.shape.iso
                s.record(
                    SolidMark(
                        SolidPaths(IsoPath.Empty, path, IsoPath.Empty, IsoPath.Empty, emptyList()),
                        mark.tone,
                        null,
                        false,
                        true,
                        null,
                    )
                )
                s.draw.drawPath(
                    mark.shape.path,
                    s.palette.ink(mark.tone),
                    style = Stroke(s.px(0.5f)),
                )
            } else s.drawArt(Art(listOf(mark)))
        }
    }

    private fun coins(s: IsoScope, m: ArcadeMachine, layer: String) {
        for (coin in m.coins) {
            val p = ArcadeCoinMotion.pose(coin)
            if (p.layer != layer) continue
            val art = ArcadeParts.coin(p.p, p.axis, p.spin)
            val clip = clips[p.clip]
            if (clip == null) s.drawArt(art)
            else
                s.drawArt(
                    dev.sebastiano.anatomy.kit.art { group("coin", clip = clip) { add(art) } }
                )
        }
    }

    private fun wire(s: IsoScope, push: Double) {
        val from = Arcade.WIRE_FROM
        val angle = atan2(push, Arcade.WIRE_LENGTH)
        val tip =
            Point3(
                from.x + Arcade.WIRE_LENGTH * cos(angle),
                from.y,
                from.z - Arcade.WIRE_LENGTH * sin(angle),
            )
        s.line(
            pathOf(listOf(Arcade.at(from), Arcade.at(tip))),
            if (push > 0.3) Tone.Lit else Tone.Hi,
            free = Free.End,
        )
    }

    private fun coinClip(y: Double, dx: Double): IsoPath {
        val a = Arcade.at(Point3(-42.0, y, -100.0))
        val b = Arcade.at(Point3(-42.0, y, 300.0))
        return pathOf(listOf(a, b, Flat(b.x + dx, b.y), Flat(a.x + dx, a.y)), true)
    }

    private fun bezel(s: IsoScope, m: ArcadeMachine, p: ArcadePicture) =
        with(s) {
            draw.clipPath(opening) {
                drawPath(glass, colors.glassInk)
                draw.clipPath(glass) { screen.paint(s, m, p) }
                for ((tone, path) in ArcadeBezel.RECESS) drawPath(
                    path.toComposePath(),
                    palette.shades[tone],
                )
            }
            record(VoidMark(Shape(ArcadeBezel.FACE_CLIP)))
            draw.withTransform({ transform(ArcadeBezel.MATRIX.composeMatrix()) }) {
                drawPath(face, palette.shades[1])
            }
            val outer =
                pathOf(
                    listOf(
                            Flat(-60.0, -57.0),
                            Flat(60.0, -57.0),
                            Flat(60.0, 57.0),
                            Flat(-60.0, 57.0),
                        )
                        .map {
                            ArcadeBezel.MATRIX.origin +
                                ArcadeBezel.MATRIX.u * it.x +
                                ArcadeBezel.MATRIX.v * it.y
                        },
                    true,
                )
            record(VoidMark(Shape(outer + ArcadeBezel.OPEN_CLIP)))
            drawArt(ArcadeBezel.LINES)
            draw.drawPath(glare, palette.lit, alpha = 0.045f)
        }

    private fun surface(s: IsoScope, a: ArcadeSurface, name: String, m: ArcadeMachine) =
        with(s) {
            draw.withTransform({ transform(a.matrix.composeMatrix()) }) {
                when (name) {
                    "marquee.plexi" -> {
                        drawPath(a.painted[0], colors.marqueeInk)
                    }
                    "fan.rotor" -> {
                        drawCircle(palette.shades[0], 12f, Offset.Zero)
                        drawPath(a.painted[0], palette.shades[2])
                        drawCircle(palette.top, 3.4f, Offset.Zero)
                    }
                    "meter.face" -> {
                        drawRoundRect(
                            palette.shades[0],
                            Offset.Zero,
                            Size(6.8f, 4.8f),
                            androidx.compose.ui.geometry.CornerRadius(0.4f),
                        )
                    }
                }
            }
            for ((i, path) in a.outlines.withIndex()) {
                val ink = if (name == "fan.rotor" && i > 0) palette.mid else palette.lo
                val width =
                    if (name == "meter.face" || (name == "fan.rotor" && i == 0)) 0.4f else 0.5f
                draw.drawPath(path, ink, style = Stroke(px(width)))
            }
            if (name == "meter.face") {
                val matrix =
                    a.matrix.copy(origin = a.matrix.origin + a.matrix.u * 0.35 + a.matrix.v * 3.55)
                faceText(
                    matrix.copy(u = matrix.u * 0.55),
                    m.meter.toString().padStart(6, '0'),
                    3.0,
                    Tone.Hi,
                    Anchor.Start,
                )
            }
        }
}
