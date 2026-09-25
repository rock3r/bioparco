package dev.sebastiano.achievementbadge

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.skiaPaint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter

/** Art units across the stage's shorter side: the badge plus its rays and rings. */
private const val SCENE_SPAN = 1400f

private val R = BadgeArt.STAR_RADIUS
private val C = BadgeArt.CENTER

/** Draws the whole celebration for one moment: [seconds] since it started. */
internal fun DrawScope.drawBadgeScene(seconds: Float, paint: BadgePaint) {
    drawRect(
        Brush.radialGradient(
            listOf(BadgeColors.stageCenter, BadgeColors.stage),
            center = center,
            radius = size.maxDimension * 0.6f,
        )
    )
    val frame = badgeFrame(seconds)
    val scale = size.minDimension / SCENE_SPAN
    withTransform({
        translate(size.width / 2f, size.height / 2f)
        scale(scale, scale, pivot = Offset.Zero)
        translate(-C.x, -C.y)
    }) {
        drawBackdrop(frame)
        drawConfetti(frame.confettiSeconds)
        drawTwinkles(seconds, paint.twinkle)
        withTransform({ translate(0f, frame.floatOffset) }) { drawBadge(frame, paint) }
    }
}

private fun DrawScope.drawBackdrop(frame: BadgeFrame) {
    if (frame.glowAlpha > 0f) {
        // A warm disc inside the shockwave ring.
        val glow = BadgeColors.warmGlow.copy(alpha = 0.3f * frame.glowAlpha)
        drawCircle(
            Brush.radialGradient(
                0f to glow,
                0.7f to glow.copy(alpha = glow.alpha * 0.6f),
                1f to Color.Transparent,
                center = C,
                radius = R * 1.3f,
            ),
            radius = R * 1.3f,
            center = C,
        )
    }
    if (frame.raysAlpha > 0f) drawRays(frame)
    drawRing(frame.goldRing, BadgeColors.goldRing, width = 9f)
    drawRing(frame.pinkRing, BadgeColors.pinkRing, width = 8f)
}

private fun DrawScope.drawRing(ring: Ring, color: Color, width: Float) {
    if (ring.alpha <= 0f) return
    drawCircle(
        color.copy(alpha = ring.alpha),
        radius = R * ring.radius,
        center = C,
        style = Stroke(width),
    )
}

/** Twelve soft beams behind the badge, alternating long and short, turning slowly. */
private fun DrawScope.drawRays(frame: BadgeFrame) {
    val inner = R * 0.8f
    val beam = Path()
    val paint =
        Paint().apply {
            style = PaintingStyle.Fill
            skiaPaint.maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, 6f, true)
        }
    for (index in 0 until RAY_COUNT) {
        val outer = R * (if (index % 2 == 0) 1.8f else 1.5f) * frame.raysLength
        val angle = (frame.raysRotation + index * 360f / RAY_COUNT - 90f) * DEG
        beam.reset()
        beam.moveTo(C.x + inner * cos(angle - 0.02f), C.y + inner * sin(angle - 0.02f))
        beam.lineTo(C.x + outer * cos(angle - 0.042f), C.y + outer * sin(angle - 0.042f))
        beam.lineTo(C.x + outer * cos(angle + 0.042f), C.y + outer * sin(angle + 0.042f))
        beam.lineTo(C.x + inner * cos(angle + 0.02f), C.y + inner * sin(angle + 0.02f))
        beam.close()
        val brush =
            Brush.radialGradient(
                0f to Color.Transparent,
                (inner / outer) to Color.Transparent,
                ((inner / outer) + 0.2f).coerceAtMost(0.95f) to
                    BadgeColors.ray.copy(alpha = 0.42f * frame.raysAlpha),
                1f to Color.Transparent,
                center = C,
                radius = outer,
            )
        brush.applyTo(size, paint, alpha = 1f)
        drawIntoCanvas { it.drawPath(beam, paint) }
    }
}

private fun DrawScope.drawConfetti(seconds: Float) {
    if (seconds < 0f || seconds > Confetti.LIFETIME) return
    for (piece in CONFETTI) {
        val state = piece.stateAt(seconds)
        if (state.alpha <= 0f) continue
        val color = BadgeColors.confetti[piece.colorIndex].copy(alpha = state.alpha)
        val p = state.position
        withTransform({ rotate(state.rotation, pivot = p) }) {
            when (piece.shape) {
                ConfettiShape.Dash -> {
                    val w = 9f * piece.size
                    val h = 27f * piece.size
                    drawRoundRect(
                        color,
                        Offset(p.x - w / 2f, p.y - h / 2f),
                        Size(w, h),
                        CornerRadius(w / 2f),
                    )
                }
                ConfettiShape.Dot -> drawCircle(color, radius = 6.5f * piece.size, center = p)
                ConfettiShape.Triangle -> {
                    val s = 11f * piece.size
                    val triangle =
                        Path().apply {
                            moveTo(p.x, p.y - s)
                            lineTo(p.x + s * 0.9f, p.y + s * 0.6f)
                            lineTo(p.x - s * 0.9f, p.y + s * 0.6f)
                            close()
                        }
                    drawPath(triangle, color)
                }
            }
        }
    }
}

private fun DrawScope.drawTwinkles(seconds: Float, shape: Path) {
    for (twinkle in twinklesAt(seconds)) {
        if (twinkle.scale <= 0f) continue
        val s = twinkle.scale * twinkle.size / 20f
        withTransform({
            translate(twinkle.position.x, twinkle.position.y)
            scale(s, s, pivot = Offset.Zero)
        }) {
            drawPath(shape, BadgeColors.twinkles[twinkle.colorIndex].copy(alpha = 0.9f))
        }
    }
}

private fun DrawScope.drawBadge(frame: BadgeFrame, paint: BadgePaint) {
    if (frame.ghostAlpha > 0f) {
        withTransform({ scale(frame.ghostScale, frame.ghostScale, pivot = C) }) {
            for (star in listOf(paint.backStar, paint.frontStar)) {
                drawPath(star, BadgeColors.ghost, alpha = frame.ghostAlpha)
                drawPath(star, Color.White, alpha = 0.07f * frame.ghostAlpha, style = Stroke(3f))
            }
        }
    }
    if (frame.backScale > 0f) {
        withTransform({
            rotate(frame.backRotation, pivot = C)
            scale(frame.backScale, frame.backScale, pivot = C)
        }) {
            drawPath(paint.backStar, paint.backFill)
            drawInnerRim(paint.backStar, paint.backRim, width = 10f)
        }
    }
    if (frame.frontScale > 0f) {
        withTransform({
            rotate(frame.frontRotation, pivot = C)
            scale(frame.frontScale, frame.frontScale, pivot = C)
        }) {
            drawFace(frame, paint)
        }
    }
    if (frame.trophyAlpha > 0f) drawTrophy(frame, paint)
    if (frame.shine >= 0f) drawShine(frame.shine, paint.frontStar)
}

/** The purple face: fan, rim, neon line and sparkle. */
private fun DrawScope.drawFace(frame: BadgeFrame, paint: BadgePaint) {
    drawPath(paint.frontStar, paint.frontFill)
    if (frame.fanReveal > 0f) {
        clipPath(paint.frontStar) {
            withTransform({
                scale(frame.fanReveal, 1f, pivot = Offset(BadgeArt.TROPHY_AXIS, BadgeArt.fanBottom))
            }) {
                drawFan(paint)
            }
        }
    }
    drawInnerRim(paint.frontStar, paint.frontRim, width = 10f)
    drawNeon(paint.neonTop.fromMiddle(frame.neonTop), frame.neonTop)
    for (run in paint.neonLow) drawNeon(run.fromMiddle(frame.neonLow), frame.neonLow)
    if (frame.sparkleScale > 0f) {
        withTransform({
            scale(frame.sparkleScale, frame.sparkleScale, pivot = BadgeArt.sparkleCenter)
        }) {
            drawPath(paint.sparkle, paint.sparkleFill)
            drawPath(paint.sparkle, BadgeColors.sparkleEdge, alpha = 0.9f, style = Stroke(3f))
        }
    }
}

private fun DrawScope.drawFan(paint: BadgePaint) {
    drawPath(paint.fanLeftBand, paint.fanBand)
    drawPath(paint.fanRightBand, paint.fanBand)
    drawPath(paint.fanPink, BadgeColors.pink)
    drawPath(paint.fanPink, paint.fanPinkSide)
    drawPath(paint.fanCentral, paint.fanPinkCentral)
    for (line in paint.fanOuterLines) drawPath(line, Color.White, alpha = 0.18f, style = Stroke(5f))
    for (line in paint.fanCentralLines) drawPath(
        line,
        Color.White,
        alpha = 0.3f,
        style = Stroke(6f),
    )
}

private fun DrawScope.drawNeon(path: Path, progress: Float) {
    if (progress <= 0f) return
    val glow =
        Paint().apply {
            color = BadgeColors.neonGlow.copy(alpha = 0.7f)
            style = PaintingStyle.Stroke
            strokeWidth = 14f
            strokeCap = StrokeCap.Round
            strokeJoin = StrokeJoin.Round
            skiaPaint.maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, 6f, true)
        }
    drawIntoCanvas { it.drawPath(path, glow) }
    drawPath(path, Color.White, style = Stroke(7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawTrophy(frame: BadgeFrame, paint: BadgePaint) {
    val alpha = frame.trophyAlpha
    val placeTrophy: DrawTransform.() -> Unit = {
        translate(0f, frame.trophyOffset)
        scale(frame.trophyScale, frame.trophyScale, pivot = BadgeArt.cupPivot)
    }
    withTransform(placeTrophy) {
        if (frame.handleScale > 0f) {
            withTransform({ scale(frame.handleScale, 1f, pivot = BadgeArt.cupPivot) }) {
                for (handle in listOf(paint.leftHandle, paint.rightHandle)) {
                    drawPath(handle, paint.handleFill, alpha = alpha)
                    drawInnerRim(handle, paint.handleRim, width = 8f, alpha = alpha)
                }
            }
        }
        drawPath(paint.neck, paint.neckFill, alpha = alpha)
        drawPath(paint.bowl, paint.bowlFill, alpha = alpha)
    }
    // The base's foot is the face's bottom tip, so it is clipped in badge space, not trophy space.
    clipPath(paint.frontStar) {
        withTransform(placeTrophy) {
            drawPath(paint.base, paint.baseFill, alpha = alpha)
            drawInnerRim(paint.base, SolidColor(BadgeColors.baseEdge), width = 14f, alpha = alpha)
        }
    }
    withTransform(placeTrophy) { drawRim(paint, alpha) }
}

private fun DrawScope.drawRim(paint: BadgePaint, alpha: Float) {
    val rim = BadgeArt.rim
    val corner = CornerRadius(BadgeArt.RIM_ROUNDING)
    val rimPath = Path().apply { addRoundRect(RoundRect(rim, corner)) }
    drawPath(rimPath, paint.rimFill, alpha = alpha)
    clipPath(rimPath) {
        drawRect(
            BadgeColors.rimLip,
            Offset(rim.left, rim.bottom - 10f),
            Size(rim.width, 10f),
            alpha = 0.35f * alpha,
        )
        drawPath(rimPath, paint.rimShine, alpha = alpha, style = Stroke(15f))
    }
    for (highlight in BadgeArt.rimHighlights) {
        val r = CornerRadius(BadgeArt.HIGHLIGHT_ROUNDING)
        drawRoundRect(paint.highlightFill, highlight.topLeft, highlight.size, r, alpha = alpha)
        drawRoundRect(
            Color.White,
            highlight.topLeft,
            highlight.size,
            r,
            alpha = 0.35f * alpha,
            style = Stroke(3f),
        )
    }
}

/** The glossy sweep that crosses the face once the neon is lit. */
private fun DrawScope.drawShine(progress: Float, face: Path) {
    val x = C.x + lerp(-460f, 460f, progress)
    clipPath(face) {
        withTransform({ rotate(28f, pivot = Offset(x, C.y)) }) {
            drawRect(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = 0.42f),
                    1f to Color.Transparent,
                    startX = x - 34f,
                    endX = x + 34f,
                ),
                topLeft = Offset(x - 34f, C.y - R * 1.4f),
                size = Size(68f, R * 2.8f),
            )
            drawRect(
                Color.White.copy(alpha = 0.22f),
                topLeft = Offset(x + 50f, C.y - R * 1.4f),
                size = Size(10f, R * 2.8f),
            )
        }
    }
}

/** A stroke that only shows inside [path]: [width] / 2 of highlight along the inner edge. */
private fun DrawScope.drawInnerRim(path: Path, brush: Brush, width: Float, alpha: Float = 1f) {
    clipPath(path) { drawPath(path, brush, alpha = alpha, style = Stroke(width)) }
}

private val CONFETTI = confettiPieces()
private const val RAY_COUNT = 12
private const val DEG = (PI / 180.0).toFloat()
