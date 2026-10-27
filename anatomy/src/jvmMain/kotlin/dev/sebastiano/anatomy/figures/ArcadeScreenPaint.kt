// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import dev.sebastiano.anatomy.kit.IsoScope
import dev.sebastiano.anatomy.kit.Palette
import kotlin.math.floor

internal fun arcadePath(d: String): Path = PathParser().parsePathString(d).toPath()

/** ROM artwork is parsed once, in the CRT's own 224 × 288 pixel plane. */
internal class ArcadeScreenPaint {
    private val hatch = arcadePath(ArcadeScreen.HATCH)
    private val test = ArcadeScreen.TEST.map(::arcadePath)
    private val headers =
        arcadePath(
            ArcadeScreen.text("1UP", 24.0, 8.0) +
                ArcadeScreen.text("HI-SCORE", 80.0, 8.0) +
                ArcadeScreen.text("2UP", 176.0, 8.0)
        )
    private val high = arcadePath(ArcadeScreen.text("12500", 92.0, 20.0))
    private val title =
        arcadePath(ArcadeScreen.text("ANATOMY", ArcadeScreen.centred("ANATOMY", 2.0), 44.0, 2.0))
    private val aliens =
        ArcadeScreen.FORMATION.map { a ->
            listOf("A", "B").map { f ->
                arcadePath(
                    ArcadeScreen.sprite(
                        (if (a.eye) "eye" else "jelly") + f,
                        a.x + if (a.eye) 1.0 else 0.0,
                        a.y.toDouble(),
                    )
                )
            }
        }
    private val ship = arcadePath(ArcadeScreen.sprite("ship", -5.5, 0.0))
    private val boom = arcadePath(ArcadeScreen.sprite("boom", 0.0, 0.0))
    private val shot = arcadePath("M0 0h1v5h-1z")
    private val floorLine = arcadePath("M8 262h208v1h-208z")
    private val insert =
        arcadePath(ArcadeScreen.text("INSERT COIN", ArcadeScreen.centred("INSERT COIN"), 200.0))
    private val start =
        arcadePath(ArcadeScreen.text("PUSH START", ArcadeScreen.centred("PUSH START"), 200.0))
    private val scan = arcadePath(ArcadeScreen.SCAN)
    private var scoreValue = -1
    private var score = Path()
    private var creditValue = -1
    private var credit = Path()

    fun paint(scope: IsoScope, m: ArcadeMachine, p: ArcadePicture) {
        val alpha = if (p.sx == 0.0) 0f else fixed3(p.bright)
        scope.draw.withTransform({ transform(ArcadeBezel.SCREEN_MATRIX.composeMatrix()) }) {
            scale(maxOf(0.001f, fixed3(p.sx)), maxOf(0.004f, fixed3(p.sy)), Offset.Zero) {
                translate(-29f, -37.3f) {
                    scale(0.2589f, 0.2589f, Offset.Zero) {
                        if (p.mode == "test") selfTest(scope.palette, p.t, alpha)
                        if (p.mode == "attract") attract(scope.palette, m, alpha)
                    }
                }
                drawPath(scan, scope.palette.paper, alpha = alpha * 0.55f, style = Stroke(0.14f))
            }
            drawOval(
                scope.palette.lit,
                Offset(-1.2f, -1.2f),
                Size(2.4f, 2.4f),
                alpha = fixed3(p.dot),
            )
        }
    }

    private fun DrawScope.selfTest(p: Palette, time: Double, alpha: Float) {
        drawPath(hatch, p.lit, alpha = alpha * 0.35f)
        listOf(1.0, 1.7, 2.3, 2.8).forEachIndexed { i, at ->
            if (time > at) drawPath(test[i], p.lit, alpha = alpha)
        }
    }

    private fun DrawScope.attract(p: Palette, m: ArcadeMachine, alpha: Float) {
        val g = m.game ?: return
        if (g.score != scoreValue) {
            scoreValue = g.score
            score = arcadePath(ArcadeScreen.text(g.score.toString().padStart(5, '0'), 16.0, 20.0))
        }
        if (m.credits != creditValue) {
            creditValue = m.credits
            credit = arcadePath(ArcadeScreen.text("CREDIT ${minOf(9, m.credits)}", 120.0, 274.0))
        }
        for (path in listOf(headers, score, high, title, floorLine, credit)) drawPath(
            path,
            p.lit,
            alpha = alpha,
        )
        translate(g.offset.toFloat(), 0f) {
            for (i in aliens.indices) if (g.alive[i])
                drawPath(aliens[i][g.frame], p.lit, alpha = alpha)
        }
        translate(ArcadeScreen.n(g.shipX).toFloat(), 250f) { drawPath(ship, p.lit, alpha = alpha) }
        g.shot?.let { s ->
            translate(s.x.toFloat(), s.y.toFloat()) { drawPath(shot, p.lit, alpha = alpha) }
        }
        g.boom?.let { b ->
            translate(b.x.toFloat(), b.y.toFloat()) { drawPath(boom, p.lit, alpha = alpha) }
        }
        if (floor(m.clock / 0.5).toInt() % 2 == 0)
            drawPath(if (m.credits > 0) start else insert, p.lit, alpha = alpha)
    }

    private fun fixed3(x: Double) = (kotlin.math.round(x * 1000) / 1000).toFloat()
}
