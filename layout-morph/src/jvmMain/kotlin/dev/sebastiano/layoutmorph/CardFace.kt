package dev.sebastiano.layoutmorph

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * What a card's drawing depends on, in dp. The engine moves cards every frame, but a card only
 * redraws its face when one of these changes: while it morphs, not while it slides or turns.
 */
internal data class CardLook(
    val width: Float,
    val height: Float,
    val row: Float,
    val caption: Float,
    val back: Boolean,
) {
    companion object {
        fun of(pose: CardPose) =
            CardLook(
                width = pose.width,
                height = pose.height,
                row = pose.row,
                caption = pose.caption,
                back = kotlin.math.cos(pose.turn.toRadians()) < 0f,
            )
    }
}

/** A card's text, laid out once. Faces scale and clip it rather than laying it out again. */
internal class CardText(
    val title: TextLayoutResult,
    val narrowTitle: TextLayoutResult,
    val detail: TextLayoutResult,
    val trailing: TextLayoutResult,
    val captionSub: TextLayoutResult,
    val tileTitle: TextLayoutResult,
    val tilePrice: TextLayoutResult,
) {
    companion object {
        /** Captions narrower than a grid tile's, in dp, end their name in an ellipsis. */
        const val NARROW_CAPTION = 88f

        /** Text cards lay their title out for a tile this wide, in dp, and scale from there. */
        const val TILE_TEXT_WIDTH = 96f

        fun measure(
            piece: Piece,
            measurer: TextMeasurer,
            base: TextStyle,
            density: Float,
        ): CardText {
            val ink = base.copy(color = MorphPalette.ink)
            val muted = base.copy(color = MorphPalette.muted)
            fun line(text: String, style: TextStyle) =
                measurer.measure(
                    text,
                    style,
                    overflow = TextOverflow.Clip,
                    softWrap = false,
                    maxLines = 1,
                )
            val tileWidth = (TILE_TEXT_WIDTH - 2 * TILE_TEXT_INSET) * density
            return CardText(
                title =
                    line(piece.title, ink.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)),
                narrowTitle =
                    measurer.measure(
                        piece.title,
                        ink.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        constraints = Constraints(maxWidth = (NARROW_CAPTION * density).toInt()),
                    ),
                detail = line(piece.detail, muted.copy(fontSize = 9.5.sp)),
                trailing = line(piece.trailing, ink.copy(fontSize = 10.5.sp)),
                captionSub =
                    line(
                        if (piece.isMaker) piece.detail.substringBefore(" · ") else piece.trailing,
                        muted.copy(fontSize = 9.sp),
                    ),
                tileTitle =
                    measurer.measure(
                        piece.title,
                        ink.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 15.sp,
                        ),
                        maxLines = 3,
                        constraints = Constraints(maxWidth = tileWidth.toInt()),
                    ),
                tilePrice = line(piece.trailing, muted.copy(fontSize = 10.sp)),
            )
        }
    }
}

private const val TILE_TEXT_INSET = 9f

private const val TILE_INSET = 4f
private const val PICTURE_RADIUS = 7f

/** How far a card's shadow shows under its lower edge, in dp. */
private const val SHADOW_DROP = 1.5f

private const val OPAQUE = 0.996f

/** How far through a morph between tile and row the faces start and finish cross-fading. */
private const val FADE_FROM = 0.3f
private const val FADE_TO = 0.7f

/** The tallest a caption strip grows, in dp. */
private const val MAX_CAPTION = 40f

/** Cards at least this wide, in dp, have room for a caption's whole name. */
private const val WIDE_CARD = 150f

/** Draws card [piece] as [look] says, centred in this draw scope. */
internal fun DrawScope.drawCard(piece: Piece, text: CardText, look: CardLook) {
    val w = look.width.dp.toPx()
    val h = look.height.dp.toPx()
    if (w <= 1f || h <= 1f) return
    translate((size.width - w) / 2, (size.height - h) / 2) {
        val radius = (10f - 3f * look.row).dp.toPx().coerceAtMost(minOf(w, h) / 2)
        val tile = smoothstep(FADE_FROM, FADE_TO, 1f - look.row)
        drawCardBase(w, h, radius, look.back)
        if (look.back) return@translate
        // No clip to the card: every part of a face keeps inside it, and clips are dear in
        // software, above all on a turned card, where each becomes an anti-aliased mask.
        // The faces cross-fade over the middle of the morph only: while both show, the card
        // draws twice, each face in a layer of its own.
        faded(tile, w, h) { drawTileFace(piece, text, w, h, look.caption) }
        faded(smoothstep(FADE_FROM, FADE_TO, look.row), w, h) { drawRowFace(piece, text, w, h) }
    }
}

/**
 * The card on its shadow, with a hairline edge, all opaque fills: a darker lip showing under the
 * card's lower edge, the hairline's colour, then the card a pixel inside it.
 *
 * Strokes would be the obvious way to draw the edge and a soft shadow, but a turned card's stroke
 * is a path the stroker rebuilds on every frame. Three of them per card were 40% of the fanned
 * stack's frame; these fills cost next to nothing.
 */
private fun DrawScope.drawCardBase(w: Float, h: Float, radius: Float, back: Boolean) {
    val px = 1f
    drawRoundRect(
        MorphPalette.lip,
        Offset(0f, SHADOW_DROP.dp.toPx()),
        Size(w, h),
        CornerRadius(radius),
    )
    drawRoundRect(MorphPalette.hairline, size = Size(w, h), cornerRadius = CornerRadius(radius))
    drawRoundRect(
        if (back) MorphPalette.cardBack else MorphPalette.card,
        Offset(px, px),
        Size(w - 2 * px, h - 2 * px),
        CornerRadius((radius - px).coerceAtLeast(0f)),
    )
}

/** How far a tile's picture sits inside the card's edge. */
private fun DrawScope.tileInset(w: Float) = minOf(TILE_INSET.dp.toPx(), w * 0.04f)

/** The caption strip's height: two lines of text however big the card grows. */
private fun DrawScope.captionStrip(h: Float, caption: Float) =
    minOf(h * (1f - 1f / Arrangements.TILE_TALL), MAX_CAPTION.dp.toPx()) * caption

/** Runs [draw] at [alpha]: skipped when hidden, straight when opaque, in a layer only between. */
private inline fun DrawScope.faded(alpha: Float, w: Float, h: Float, draw: DrawScope.() -> Unit) {
    when {
        alpha <= 0.004f -> Unit
        alpha >= OPAQUE -> draw()
        else -> {
            val canvas = drawContext.canvas
            canvas.saveLayer(Rect(0f, 0f, w, h), Paint().apply { this.alpha = alpha })
            draw()
            canvas.restore()
        }
    }
}

/** A picture (or a text panel) above an optional caption strip. */
private fun DrawScope.drawTileFace(
    piece: Piece,
    text: CardText,
    w: Float,
    h: Float,
    caption: Float,
) {
    val inset = tileInset(w)
    val strip = captionStrip(h, caption)
    val picture = Rect(inset, inset, w - inset, h - strip - inset)
    if (picture.width > 1f && picture.height > 1f) {
        drawPicture(piece, text, picture, PICTURE_RADIUS.dp.toPx())
    }
    if (caption > 0.01f) {
        val pad = 6.dp.toPx()
        val top = h - strip + 3.dp.toPx()
        clipRect(pad, h - strip, w - pad, h) {
            val narrow = w - 2 * pad < text.title.size.width && w < WIDE_CARD.dp.toPx()
            val title = if (narrow) text.narrowTitle else text.title
            drawText(title, topLeft = Offset(pad, top), alpha = caption)
            drawText(
                text.captionSub,
                topLeft = Offset(pad, top + title.size.height),
                alpha = caption,
            )
        }
    }
}

/** A list row: thumbnail, name over detail, and the price or piece count at the end. */
private fun DrawScope.drawRowFace(piece: Piece, text: CardText, w: Float, h: Float) {
    val pad = 3.dp.toPx()
    val thumb = (h - 2 * pad).coerceAtLeast(1f)
    drawPicture(piece, text, Rect(pad, pad, pad + thumb, pad + thumb), 5.dp.toPx())
    val left = pad + thumb + 9.dp.toPx()
    val right = w - 10.dp.toPx()
    val trailingLeft = right - text.trailing.size.width
    val lines = text.title.size.height + text.detail.size.height
    val top = (h - lines) / 2
    clipRect(left, 0f, trailingLeft - 6.dp.toPx(), h) {
        drawText(text.title, topLeft = Offset(left, top))
        drawText(text.detail, topLeft = Offset(left, top + text.title.size.height))
    }
    drawText(text.trailing, topLeft = Offset(trailingLeft, (h - text.trailing.size.height) / 2))
}

/** The card's picture in [area]: the art, or for text cards the name and price on a panel. */
private fun DrawScope.drawPicture(piece: Piece, text: CardText, area: Rect, radius: Float) {
    val corner = CornerRadius(radius.coerceAtMost(minOf(area.width, area.height) / 2))
    if (piece.art == Art.Text) {
        // A plain panel needs no clip: it is a rounded fill with the text inside.
        drawRoundRect(MorphPalette.textCard, area.topLeft, area.size, corner)
        translate(area.left, area.top) { drawTextPanel(text, area.size) }
        return
    }
    translate(area.left, area.top) { drawArt(piece, area.size, corner.x) }
}

/** A text card's panel: its name at the top and its price at the bottom, scaled to the panel. */
private fun DrawScope.drawTextPanel(text: CardText, area: Size) {
    val k = (area.width / CardText.TILE_TEXT_WIDTH.dp.toPx()).coerceIn(0.2f, 2.4f)
    val inset = TILE_TEXT_INSET.dp.toPx()
    // Only a panel squashed mid-morph is too short for the name; only then is it clipped.
    val clip = (text.tileTitle.size.height + inset) * k > area.height
    if (clip) drawContext.canvas.save()
    if (clip) drawContext.canvas.clipRect(0f, 0f, area.width, area.height)
    withTransform({ scale(k, k, Offset.Zero) }) {
        drawText(text.tileTitle, topLeft = Offset(inset, inset))
        val priceTop = area.height / k - inset - text.tilePrice.size.height
        if (priceTop > inset + text.tileTitle.size.height) {
            drawText(text.tilePrice, topLeft = Offset(inset, priceTop))
        }
    }
    if (clip) drawContext.canvas.restore()
}
