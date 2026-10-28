package dev.sebastiano.stampbook

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/* Per frame, everything here only copies baked images (see BookArt). */

/** The board behind the cover, with the whole book's soft shadow under it. */
internal fun DrawScope.drawBoard(art: BookArt, motion: StampBookMotion) {
    val board = art.inks[motion.swatch].board
    drawPlaced(board.image, board.at)
}

/** The pale stamp, with the open cover's shadow falling across its left edge. */
internal fun DrawScope.drawStampA(art: BookArt, cover: Float) {
    drawPlaced(art.stampA, AtOrigin)
    if (cover <= 0f) return
    // Keep off the perforations, which are holes onto the board.
    val inset = 4.dp.toPx()
    val reach = 30.dp.toPx()
    drawRect(
        Brush.horizontalGradient(
            0f to Color.Black.copy(alpha = COVER_SHADOW_ALPHA * cover.coerceAtMost(1f)),
            1f to Color.Transparent,
            startX = inset,
            endX = inset + reach,
        ),
        topLeft = Offset(inset, inset),
        size = Size(reach, size.height - 2f * inset),
    )
}

/** The bird stamp's shadow, which drops and darkens as the stamp lifts. */
internal fun DrawScope.drawStampBShadow(art: BookArt, lift: Float) {
    val shadow = art.stampShadow
    val drop = 3.dp + 7.dp * lift
    drawPlaced(
        shadow.image,
        DpOffset(shadow.at.x, shadow.at.y + drop),
        alpha = STAMP_SHADOW_ALPHA + STAMP_LIFT_SHADOW_ALPHA * lift,
    )
}

/**
 * The cover, frosted while it is shut (the stamps show through it blurred), clearing to a solid
 * sheet as it swings open. Drawn inside the cover's turning layer, so it is always one image.
 */
internal fun DrawScope.drawCover(art: BookArt, motion: StampBookMotion) {
    val ink = art.inks[motion.swatch]
    val frost = frost(motion.cover.value)
    drawFilling(ink.faces[(frost * (ink.faces.size - 1)).roundToInt()])
}

/**
 * How frosted the cover looks as it opens: it clears in the first part of the swing, as in the
 * video.
 */
internal fun frost(open: Float): Float {
    val left = (1f - open / FROST_CLEARS_AT).coerceIn(0f, 1f)
    return left * left
}

/**
 * Bakes a cover face for [ink], in dp units: the colour, [amount] of the [frost] (the blurred
 * stamps), the tint over it, a sheen at the top, a rim, the label and two ruled lines. As the frost
 * goes, a little shade comes in towards the free edge.
 */
internal fun DrawScope.drawCoverFace(
    ink: FolderInk,
    frost: ImageBitmap,
    amount: Float,
    text: TextMeasurer,
) {
    val size = Size(Book.Folder.width.value, Book.Folder.height.value)
    val corner = CornerRadius(Book.Corner.value)
    drawRoundRect(ink.cover, size = size, cornerRadius = corner)
    if (amount > 0f) {
        drawImage(
            frost,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            alpha = FROST_ALPHA * amount,
        )
    }
    drawRoundRect(ink.cover.copy(alpha = TINT_ALPHA), size = size, cornerRadius = corner)
    drawRoundRect(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.2f),
            0.45f to Color.Transparent,
            endY = size.height,
        ),
        size = size,
        cornerRadius = corner,
    )
    if (amount < 1f) {
        drawRoundRect(
            Brush.horizontalGradient(
                0f to Color.Transparent,
                1f to Color.Black.copy(alpha = TURN_SHADE_ALPHA * (1f - amount)),
                endX = size.width,
            ),
            size = size,
            cornerRadius = corner,
        )
    }
    drawRoundRect(
        Color.White.copy(alpha = 0.18f),
        topLeft = Offset(0.5f, 0.5f),
        size = Size(size.width - 1f, size.height - 1f),
        cornerRadius = corner,
        style = Stroke(width = 1f),
    )
    val left = 20f
    val titleTop = size.height - 98f
    val title = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = ink.label)
    drawText(text.measure("Raccolta", title), topLeft = Offset(left, titleTop))
    val note = TextStyle(fontSize = 10.sp, color = ink.note)
    drawText(text.measure("Mini archive", note), topLeft = Offset(left, titleTop + 24f))
    for (y in listOf(44f, 34f)) {
        drawLine(
            ink.note.copy(alpha = ink.note.alpha * 0.6f),
            Offset(left, size.height - y),
            Offset(size.width - left, size.height - y),
            strokeWidth = 0.8f,
        )
    }
}

internal fun DrawScope.drawSwatch(colour: Color, selected: Boolean) {
    drawCircle(colour, radius = 8.dp.toPx())
    if (selected) {
        drawCircle(StampBookPalette.Ring, radius = 11.5.dp.toPx(), style = Stroke(1.5.dp.toPx()))
    }
}

private val AtOrigin = DpOffset(0.dp, 0.dp)

/** The cover is clear of frost by this far through its swing. */
private const val FROST_CLEARS_AT = 0.4f

private const val COVER_SHADOW_ALPHA = 0.22f

private const val STAMP_SHADOW_ALPHA = 0.1f

private const val STAMP_LIFT_SHADOW_ALPHA = 0.14f

/** The frost's strength while shut, before the cover's own tint goes over it. */
private const val FROST_ALPHA = 0.95f

/** How much of the cover's colour lies over the frost: the rest is the stamps showing through. */
private const val TINT_ALPHA = 0.68f

private const val TURN_SHADE_ALPHA = 0.16f
