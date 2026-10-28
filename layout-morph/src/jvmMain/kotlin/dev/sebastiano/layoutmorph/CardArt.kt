package dev.sebastiano.layoutmorph

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The pictures on the cards. The original uses photos; these are flat illustrations drawn as
 * vectors, so they stay sharp at every size a card flies through and need no image files.
 *
 * Each picture keeps inside [size] and rounds its own [corner]s: its backdrop and the bands that
 * reach its edges are rounded shapes. A clip would do the same for less code, but in software a
 * clip on a turned card is an anti-aliased mask built on every frame, and it cost the fanned stack
 * a third of its frame. For the same reason the backdrops are flat colours; only the dusk sky and
 * the lamp's glow keep a gradient, because without one they are not the picture.
 */
internal fun DrawScope.drawArt(piece: Piece, size: Size, corner: Float) {
    when (piece.art) {
        Art.Portrait -> drawPortrait(PORTRAITS[piece.look % PORTRAITS.size], size, corner)
        Art.Photo ->
            when (piece.subject) {
                Subject.Lamp -> drawLamp(size, corner)
                Subject.Throw -> drawThrow(size, corner)
                Subject.Chair -> drawChair(size, corner)
                Subject.Carafe -> drawCarafe(size, corner)
                Subject.Seascape -> drawSeascape(size, corner)
                Subject.None -> Unit
            }
        Art.Text -> Unit
    }
}

/** A band across the picture [area] from [top] to [bottom], rounding whichever corners it meets. */
private fun DrawScope.band(area: Size, brush: Brush, top: Float, bottom: Float, corner: Float) {
    val h = area.height
    val upper = if (top <= 0f) CornerRadius(corner) else CornerRadius.Zero
    val lower = if (bottom >= h) CornerRadius(corner) else CornerRadius.Zero
    val shape = RoundRect(0f, top, area.width, bottom, upper, upper, lower, lower)
    drawPath(Path().apply { addRoundRect(shape) }, brush)
}

private fun DrawScope.band(area: Size, color: Color, top: Float, bottom: Float, corner: Float) =
    band(area, SolidColor(color), top, bottom, corner)

/**
 * A straight bar [width] wide from [start] to [end], filled as a quad. `drawLine` would stroke it,
 * and on a turned card the stroker rebuilds every line on every frame.
 */
private fun DrawScope.bar(color: Color, start: Offset, end: Offset, width: Float) {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = sqrt(dx * dx + dy * dy)
    if (length == 0f) return
    val nx = -dy / length * width / 2
    val ny = dx / length * width / 2
    val quad =
        Path().apply {
            moveTo(start.x + nx, start.y + ny)
            lineTo(end.x + nx, end.y + ny)
            lineTo(end.x - nx, end.y - ny)
            lineTo(start.x - nx, start.y - ny)
            close()
        }
    drawPath(quad, color)
}

private enum class Hair {
    Short,
    Buzz,
    Bun,
    Long,
    Curly,
    Bob,
}

private class Portrait(
    val backdrop: Color,
    val skin: Color,
    val hair: Color,
    val style: Hair,
    val shirt: Color,
)

private val PORTRAITS =
    listOf(
        Portrait(
            Color(0xFFDFCCBC),
            Color(0xFFE9B99A),
            Color(0xFF6B3E26),
            Hair.Long,
            Color(0xFF2F3A4A),
        ),
        Portrait(
            Color(0xFFB7C9C5),
            Color(0xFF8D5A3C),
            Color(0xFF1E1612),
            Hair.Curly,
            Color(0xFFD9A441),
        ),
        Portrait(
            Color(0xFFCFC4DD),
            Color(0xFFE8BE9C),
            Color(0xFF3A2418),
            Hair.Bun,
            Color(0xFFF2EFEA),
        ),
        Portrait(
            Color(0xFFCCBEA5),
            Color(0xFFD7A27E),
            Color(0xFF2A1D15),
            Hair.Short,
            Color(0xFF5B6B4E),
        ),
        Portrait(
            Color(0xFFBCC9D6),
            Color(0xFFF0C8A8),
            Color(0xFFB88A5A),
            Hair.Short,
            Color(0xFF1E2328),
        ),
        Portrait(
            Color(0xFFD8D1C7),
            Color(0xFFEDC3A2),
            Color(0xFFD9B77A),
            Hair.Buzz,
            Color(0xFF8C3B2F),
        ),
        Portrait(
            Color(0xFF1D375B),
            Color(0xFFE3B48F),
            Color(0xFF121212),
            Hair.Short,
            Color(0xFFE8E6E1),
        ),
        Portrait(
            Color(0xFFE1C9B5),
            Color(0xFFA8704C),
            Color(0xFF2B1B12),
            Hair.Bob,
            Color(0xFF3C5A72),
        ),
        Portrait(
            Color(0xFFE8CEB5),
            Color(0xFFB97B55),
            Color(0xFF1A1210),
            Hair.Long,
            Color(0xFFC2573A),
        ),
        Portrait(
            Color(0xFFCFD7DE),
            Color(0xFFF3D2B8),
            Color(0xFFE3C27E),
            Hair.Bob,
            Color(0xFF6F7F8C),
        ),
    )

private val FEATURES = Color(0xFF2A1E18)

/** Where a portrait's head sits: centred at ([cx], [y]), [r] its half width, roughly. */
private class Head(val cx: Float, val y: Float, val r: Float)

/** A head-and-shoulders portrait against a soft backdrop. */
private fun DrawScope.drawPortrait(p: Portrait, size: Size, corner: Float) {
    val w = size.width
    val h = size.height
    band(size, p.backdrop, 0f, h, corner)
    val head = Head(cx = w / 2, y = h * 0.43f, r = min(w, h) * 0.2f)
    drawHairBehind(p, head)
    drawShoulders(p, head, size, corner)
    drawHead(p, head)
    drawHairOnTop(p, head)
    drawFace(head)
}

/** Long hair and bobs fall behind the head and shoulders. */
private fun DrawScope.drawHairBehind(p: Portrait, head: Head) {
    val (cx, y, r) = Triple(head.cx, head.y, head.r)
    when (p.style) {
        Hair.Long ->
            drawRoundRect(
                p.hair,
                Offset(cx - 1.18f * r, y - 1.1f * r),
                Size(2.36f * r, 2.75f * r),
                CornerRadius(r, r),
            )
        Hair.Bob ->
            drawRoundRect(
                p.hair,
                Offset(cx - 1.22f * r, y - 1.12f * r),
                Size(2.44f * r, 2.0f * r),
                CornerRadius(r * 0.9f, r * 0.9f),
            )
        else -> Unit
    }
}

/** Shoulders rise from the bottom edge, clear of its rounded corners. */
private fun DrawScope.drawShoulders(p: Portrait, head: Head, size: Size, corner: Float) {
    val h = size.height
    val top = head.y + 1.55f * head.r
    val half = minOf(size.width * 0.42f, size.width / 2 - corner)
    if (top >= h || half <= 0f) return
    val shoulders =
        Path().apply {
            moveTo(head.cx - half, h)
            arcTo(Rect(head.cx - half, top, head.cx + half, 2 * h - top), 180f, 180f, false)
            close()
        }
    drawPath(shoulders, p.shirt)
}

/** The neck, with the chin's shadow on it, and the head. */
private fun DrawScope.drawHead(p: Portrait, head: Head) {
    val (cx, y, r) = Triple(head.cx, head.y, head.r)
    drawRect(p.skin, Offset(cx - 0.36f * r, y + 0.6f * r), Size(0.72f * r, 1.1f * r))
    drawOval(
        Color.Black.copy(alpha = 0.08f),
        Offset(cx - 0.36f * r, y + 0.85f * r),
        Size(0.72f * r, 0.4f * r),
    )
    drawOval(p.skin, Offset(cx - 0.86f * r, y - r), Size(1.72f * r, 2f * r))
}

/** The hair on top of the head. */
private fun DrawScope.drawHairOnTop(p: Portrait, head: Head) {
    val (cx, y, r) = Triple(head.cx, head.y, head.r)
    when (p.style) {
        Hair.Buzz ->
            drawArc(
                p.hair.copy(alpha = 0.9f),
                180f,
                180f,
                true,
                Offset(cx - 0.9f * r, y - 1.06f * r),
                Size(1.8f * r, 1.5f * r),
            )
        Hair.Curly -> {
            for (k in 0..8) {
                val a = (165f + k * 26f).toRadians()
                drawCircle(
                    p.hair,
                    0.42f * r,
                    Offset(cx + cos(a) * 0.86f * r, y - 0.1f * r + sin(a) * 0.95f * r),
                )
            }
            drawArc(
                p.hair,
                180f,
                180f,
                true,
                Offset(cx - 0.95f * r, y - 1.12f * r),
                Size(1.9f * r, 1.7f * r),
            )
        }
        else -> {
            if (p.style == Hair.Bun) drawCircle(p.hair, 0.46f * r, Offset(cx, y - 1.12f * r))
            drawArc(
                p.hair,
                180f,
                180f,
                true,
                Offset(cx - 0.94f * r, y - 1.14f * r),
                Size(1.88f * r, 1.84f * r),
            )
            // A side parting sweeps down one temple.
            drawOval(p.hair, Offset(cx - 0.92f * r, y - 0.6f * r), Size(0.5f * r, 0.9f * r))
        }
    }
}

/** A calm face: eyes, a little open smile, and warm cheeks. */
private fun DrawScope.drawFace(head: Head) {
    val (cx, y, r) = Triple(head.cx, head.y, head.r)
    val eyeY = y + 0.08f * r
    drawCircle(FEATURES, 0.075f * r, Offset(cx - 0.34f * r, eyeY))
    drawCircle(FEATURES, 0.075f * r, Offset(cx + 0.34f * r, eyeY))
    drawArc(
        FEATURES.copy(alpha = 0.75f),
        0f,
        180f,
        true,
        Offset(cx - 0.17f * r, y + 0.3f * r),
        Size(0.34f * r, 0.24f * r),
    )
    drawCircle(Color(0x33E5604A), 0.16f * r, Offset(cx - 0.52f * r, y + 0.32f * r))
    drawCircle(Color(0x33E5604A), 0.16f * r, Offset(cx + 0.52f * r, y + 0.32f * r))
}

/** A glass mushroom lamp, lit, on a dark wood table. */
private fun DrawScope.drawLamp(size: Size, corner: Float) {
    val w = size.width
    val h = size.height
    band(size, Color(0xFF3C342D), 0f, h, corner)
    val cx = w * 0.52f
    val glowY = h * 0.42f
    val glow = minOf(w * 0.4f, h * 0.4f)
    drawCircle(
        Brush.radialGradient(listOf(Color(0xCCFFE9C2), Color(0x00FFE9C2)), Offset(cx, glowY), glow),
        glow,
        Offset(cx, glowY),
    )
    band(size, Color(0xFF241C16), h * 0.8f, h, corner)
    drawRect(Color(0x22FFE2B0), Offset(corner, h * 0.8f), Size(w - 2 * corner, h * 0.012f))
    val domeW = w * 0.5f
    val domeTop = h * 0.3f
    drawArc(
        Color(0xFFFFF8EA),
        180f,
        180f,
        true,
        Offset(cx - domeW / 2, domeTop),
        Size(domeW, domeW * 0.62f),
    )
    drawRect(
        Color(0xFFEFE2C8),
        Offset(cx - domeW / 2, domeTop + domeW * 0.31f - 1f),
        Size(domeW, h * 0.012f),
    )
    val stemW = w * 0.07f
    val stemTop = domeTop + domeW * 0.31f
    drawRect(
        Brush.horizontalGradient(
            listOf(Color(0xFFFFF6E4), Color(0xFFD9C9A8)),
            cx - stemW / 2,
            cx + stemW / 2,
        ),
        Offset(cx - stemW / 2, stemTop),
        Size(stemW, h * 0.8f - stemTop),
    )
    drawOval(Color(0xFFE9DCC0), Offset(cx - w * 0.13f, h * 0.785f), Size(w * 0.26f, h * 0.04f))
    // A book leaning in the lamplight.
    drawRect(Color(0xFF8C6B4A), Offset(w * 0.14f, h * 0.56f), Size(w * 0.08f, h * 0.24f))
    drawRect(Color(0xFFB79A72), Offset(w * 0.22f, h * 0.6f), Size(w * 0.06f, h * 0.2f))
}

/** Three folded linen throws, stacked, with fringed ends. */
private fun DrawScope.drawThrow(size: Size, corner: Float) {
    val w = size.width
    val h = size.height
    band(size, Color(0xFFC6CED0), 0f, h, corner)
    band(size, Color(0xFF9BA6A8), h * 0.84f, h, corner)
    val colours = listOf(Color(0xFFEDE6D8), Color(0xFFD8CDB8), Color(0xFFF3EEE4))
    val left = w * 0.16f
    val width = w * 0.68f
    val fold = h * 0.13f
    colours.forEachIndexed { k, colour ->
        val top = h * 0.84f - fold * (k + 1) - k * h * 0.004f
        drawRoundRect(
            colour,
            Offset(left, top),
            Size(width, fold),
            CornerRadius(fold * 0.45f, fold * 0.45f),
        )
        drawRoundRect(
            Color.Black.copy(alpha = 0.07f),
            Offset(left, top + fold * 0.72f),
            Size(width, fold * 0.28f),
            CornerRadius(fold * 0.3f, fold * 0.3f),
        )
        // Woven stripes near the ends, and the fringe.
        for (s in 0..1) {
            val x = left + width * (0.12f + s * 0.06f)
            bar(
                Color(0x33402C1C),
                Offset(x, top + fold * 0.15f),
                Offset(x, top + fold * 0.85f),
                w * 0.008f,
            )
        }
        for (f in 0..5) {
            val y = top + fold * (0.2f + f * 0.12f)
            bar(
                colour,
                Offset(left + width - 2f, y),
                Offset(left + width + w * 0.05f, y + h * 0.01f),
                w * 0.006f,
            )
        }
    }
}

/** An oak lounge chair with a woven seat, against a pale wall. */
private fun DrawScope.drawChair(size: Size, corner: Float) {
    val w = size.width
    val h = size.height
    band(size, Color(0xFFE7E4DE), 0f, h, corner)
    band(size, Color(0xFFCCC4B7), h * 0.76f, h, corner)
    drawOval(
        Color.Black.copy(alpha = 0.1f),
        Offset(w * 0.18f, h * 0.83f),
        Size(w * 0.66f, h * 0.06f),
    )
    val oak = Color(0xFF9A7650)
    val stroke = w * 0.035f
    // Legs and arms.
    bar(
        oak,
        Offset(w * 0.26f, h * 0.86f),
        Offset(w * 0.34f, h * 0.58f),
        stroke,
    )
    bar(
        oak,
        Offset(w * 0.74f, h * 0.86f),
        Offset(w * 0.68f, h * 0.58f),
        stroke,
    )
    bar(
        oak,
        Offset(w * 0.24f, h * 0.5f),
        Offset(w * 0.72f, h * 0.5f),
        stroke * 0.9f,
    )
    // The leaning back, then the seat.
    val back =
        Path().apply {
            moveTo(w * 0.3f, h * 0.6f)
            lineTo(w * 0.2f, h * 0.24f)
            quadraticTo(w * 0.21f, h * 0.19f, w * 0.27f, h * 0.2f)
            lineTo(w * 0.5f, h * 0.24f)
            quadraticTo(w * 0.55f, h * 0.25f, w * 0.54f, h * 0.31f)
            lineTo(w * 0.5f, h * 0.6f)
            close()
        }
    drawPath(back, Color(0xFFD8CBB2))
    for (k in 1..5) {
        val t = k / 6f
        bar(
            Color(0x22553A1E),
            Offset(w * (0.21f + 0.3f * t), h * (0.22f + 0.02f * t)),
            Offset(w * (0.31f + 0.19f * t), h * 0.59f),
            w * 0.006f,
        )
    }
    drawRoundRect(
        Color(0xFFE6DAC2),
        Offset(w * 0.26f, h * 0.55f),
        Size(w * 0.5f, h * 0.1f),
        CornerRadius(w * 0.04f, w * 0.04f),
    )
    drawRoundRect(
        Color.Black.copy(alpha = 0.06f),
        Offset(w * 0.26f, h * 0.61f),
        Size(w * 0.5f, h * 0.04f),
        CornerRadius(w * 0.03f, w * 0.03f),
    )
}

/** A mouth-blown night carafe with its tumbler, half full. */
private fun DrawScope.drawCarafe(size: Size, corner: Float) {
    val w = size.width
    val h = size.height
    band(size, Color(0xFFE0E7E6), 0f, h, corner)
    band(size, Color(0xFFC6D0CF), h * 0.8f, h, corner)
    // Opaque glass over an edge-coloured copy a touch bigger: an outline drawn with fills.
    val glass = Color(0xFFECF0F0)
    val edge = Color(0xFF95ABAB)
    val outline = w * 0.012f
    val cx = w * 0.42f
    val body =
        Path().apply {
            moveTo(cx - w * 0.06f, h * 0.2f)
            lineTo(cx - w * 0.06f, h * 0.38f)
            cubicTo(cx - w * 0.26f, h * 0.46f, cx - w * 0.24f, h * 0.8f, cx - w * 0.14f, h * 0.81f)
            lineTo(cx + w * 0.14f, h * 0.81f)
            cubicTo(cx + w * 0.24f, h * 0.8f, cx + w * 0.26f, h * 0.46f, cx + w * 0.06f, h * 0.38f)
            lineTo(cx + w * 0.06f, h * 0.2f)
            close()
        }
    scale(1f + 2 * outline / (w * 0.5f), 1f + 2 * outline / (h * 0.61f), Offset(cx, h * 0.505f)) {
        drawPath(body, edge)
    }
    drawPath(body, glass)
    drawRect(Color(0x55A9CCCF), Offset(cx - w * 0.19f, h * 0.58f), Size(w * 0.38f, h * 0.225f))
    bar(
        Color(0xAAFFFFFF),
        Offset(cx - w * 0.15f, h * 0.55f),
        Offset(cx - w * 0.13f, h * 0.74f),
        w * 0.02f,
    )
    // The tumbler, upturned on the neck in the original; beside it here.
    val tx = w * 0.74f
    drawRect(edge, Offset(tx - w * 0.08f, h * 0.6f), Size(w * 0.16f, h * 0.21f))
    drawRect(
        glass,
        Offset(tx - w * 0.08f + outline, h * 0.6f + outline),
        Size(w * 0.16f - 2 * outline, h * 0.21f - 2 * outline),
    )
    drawRect(
        Color(0x55A9CCCF),
        Offset(tx - w * 0.08f + outline, h * 0.7f),
        Size(w * 0.16f - 2 * outline, h * 0.11f - outline),
    )
    drawOval(
        Color.Black.copy(alpha = 0.08f),
        Offset(w * 0.2f, h * 0.8f),
        Size(w * 0.65f, h * 0.035f),
    )
}

/** A dusk seascape: the print in the collection. */
private fun DrawScope.drawSeascape(size: Size, corner: Float) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.64f
    band(
        size,
        Brush.verticalGradient(
            listOf(Color(0xFF3F4466), Color(0xFF8C6E86), Color(0xFFE59C83), Color(0xFFF6C9A0)),
            0f,
            horizon,
        ),
        0f,
        horizon,
        corner,
    )
    drawCircle(Color(0xFFFFE4BE), w * 0.07f, Offset(w * 0.64f, horizon - h * 0.02f))
    band(size, Color(0xFF353D55), horizon, h, corner)
    // The sun's path on the water.
    for (k in 0..4) {
        val y = horizon + h * (0.03f + k * 0.055f)
        val half = w * (0.1f - k * 0.015f)
        bar(
            Color(0x88FFD7A8),
            Offset(w * 0.64f - half, y),
            Offset(w * 0.64f + half, y),
            h * 0.008f,
        )
    }
    val island =
        Path().apply {
            moveTo(w * 0.06f, horizon)
            lineTo(w * 0.12f, horizon - h * 0.07f)
            lineTo(w * 0.2f, horizon - h * 0.12f)
            lineTo(w * 0.27f, horizon - h * 0.08f)
            lineTo(w * 0.36f, horizon - h * 0.05f)
            lineTo(w * 0.42f, horizon)
            close()
        }
    drawPath(island, Color(0xFF2A2B3D))
}
