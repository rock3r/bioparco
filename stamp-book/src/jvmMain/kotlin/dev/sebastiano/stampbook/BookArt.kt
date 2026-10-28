package dev.sebastiano.stampbook

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Surface

/**
 * Everything drawn once, at [scale] pixels per dp: the two stamps, the bird stamp's shadow, and for
 * each colourway the board (with the book's shadow) and the cover's face, frosted to clear. A frame
 * then only copies images. That matters most for the cover: it turns in 3D, and every pixel drawn
 * through a perspective costs a filtered sample, so it is one image rather than a stack of fills.
 */
internal class BookArt(
    val scale: Float,
    val stampA: ImageBitmap,
    val stampB: ImageBitmap,
    /**
     * The bird stamp already turned to its resting tilt inside the book, to copy while it rests.
     */
    val stampBTucked: Placed,
    val stampShadow: Placed,
    val inks: List<InkArt>,
)

/** One colourway's board and cover faces. */
internal class InkArt(
    /** The back board over the whole book's soft shadow. */
    val board: Placed,
    /**
     * The cover's face as its frost clears, in [FROST_LEVELS] steps: from the open cover, a solid
     * tinted sheet a little shaded towards its free edge, to the shut one, with the stamps showing
     * through it blurred. Picking one face a frame keeps the turning cover to a single image.
     */
    val faces: List<ImageBitmap>,
)

/** An image whose top-left goes [at] from the top-left of what it belongs to. */
internal class Placed(val image: ImageBitmap, val at: DpOffset)

/**
 * Draws the book's art at [scale] pixels per dp. Slow-ish (a few hundred ms), so call it off the
 * UI.
 */
internal fun bakeBookArt(scale: Float): BookArt {
    val text = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)
    val inside =
        bake(Book.Folder, scale) {
            placed(Book.StampAClosed, Book.StampA, 0f) { drawQuietStamp(text) }
            placed(Book.StampBClosed, Book.StampB, Book.B_CLOSED_TURN) { drawBirdStamp(text) }
        }
    val frost = blurred(inside, sigma = FROST_BLUR * scale, corner = Book.Corner.value * scale)
    val bookShadow = bookShadow(scale)
    return BookArt(
        scale = scale,
        stampA = bake(Book.StampA, scale) { drawQuietStamp(text) },
        stampB = bake(Book.StampB, scale) { drawBirdStamp(text) },
        stampBTucked = tucked(Book.StampB, Book.B_CLOSED_TURN, scale) { drawBirdStamp(text) },
        stampShadow = stampShadow(scale),
        inks =
            StampBookPalette.inks.map { ink ->
                InkArt(
                    board = board(ink, bookShadow, scale),
                    faces =
                        List(FROST_LEVELS) { level ->
                            val amount = level / (FROST_LEVELS - 1f)
                            bake(Book.Folder, scale) { drawCoverFace(ink, frost, amount, text) }
                        },
                )
            },
    )
}

/** Draws [draw] in dp units onto a fresh bitmap of [size] at [scale] pixels per dp. */
private fun bake(size: DpSize, scale: Float, draw: DrawScope.() -> Unit): ImageBitmap {
    val image =
        ImageBitmap(ceil(size.width.value * scale).toInt(), ceil(size.height.value * scale).toInt())
    val pixels = Size(image.width.toFloat(), image.height.toFloat())
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), pixels) {
        scale(scale, scale, pivot = Offset.Zero) { draw() }
    }
    return image
}

/** Draws [draw] as a [size] stamp whose top-left is at [at], turned [degrees] about its middle. */
private fun DrawScope.placed(
    at: DpOffset,
    size: DpSize,
    degrees: Float,
    draw: DrawScope.() -> Unit,
) {
    translate(at.x.value, at.y.value) {
        rotate(degrees, pivot = Offset(size.width.value / 2f, size.height.value / 2f)) { draw() }
    }
}

/** A [size] stamp turned [degrees] about its middle, on a canvas grown to hold its corners. */
private fun tucked(size: DpSize, degrees: Float, scale: Float, draw: DrawScope.() -> Unit): Placed {
    val radians = Math.toRadians(degrees.toDouble())
    val w = size.width.value
    val h = size.height.value
    val grownW = abs(w * cos(radians)) + abs(h * sin(radians))
    val grownH = abs(w * sin(radians)) + abs(h * cos(radians))
    val padX = ceil((grownW - w) / 2.0).toFloat() + 1f
    val padY = ceil((grownH - h) / 2.0).toFloat() + 1f
    val image =
        bake(DpSize((w + 2 * padX).dp, (h + 2 * padY).dp), scale) {
            placed(DpOffset(padX.dp, padY.dp), size, degrees, draw)
        }
    return Placed(image, DpOffset(-padX.dp, -padY.dp))
}

/**
 * The whole book's shadow, cast a little below it, on a canvas [BOOK_SHADOW_PAD] bigger all round
 * than the board plus the cover that sits [Book.BackShift] to its left.
 */
private fun bookShadow(scale: Float): ImageBitmap {
    val canvas = boardCanvas()
    val silhouette =
        bake(canvas, scale) {
            drawRoundRect(
                Color.Black,
                topLeft = Offset(BOOK_SHADOW_PAD.value, BOOK_SHADOW_PAD.value + SHADOW_DROP.value),
                size = Size(Book.Folder.width.value, Book.Folder.height.value),
                cornerRadius = CornerRadius(Book.Corner.value),
            )
        }
    return blurred(silhouette, sigma = BOOK_SHADOW_BLUR * scale)
}

/** The board with the book's shadow under it, in [ink]'s colours. */
private fun board(ink: FolderInk, shadow: ImageBitmap, scale: Float): Placed {
    val canvas = boardCanvas()
    val image =
        bake(canvas, scale) {
            drawImage(
                shadow,
                dstSize = IntSize(canvas.width.value.toInt(), canvas.height.value.toInt()),
                alpha = BOOK_SHADOW_ALPHA,
            )
            translate(Book.BackShift.x.value + BOOK_SHADOW_PAD.value, BOOK_SHADOW_PAD.value) {
                val size = Size(Book.Folder.width.value, Book.Folder.height.value)
                val corner = CornerRadius(Book.Corner.value)
                drawRoundRect(ink.back, size = size, cornerRadius = corner)
                drawRoundRect(
                    Brush.verticalGradient(
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.07f),
                        endY = size.height,
                    ),
                    size = size,
                    cornerRadius = corner,
                )
            }
        }
    return Placed(image, DpOffset(-Book.BackShift.x - BOOK_SHADOW_PAD, -BOOK_SHADOW_PAD))
}

/** Room for the board, the cover to its left, and the shadow all round. */
private fun boardCanvas(): DpSize =
    DpSize(
        Book.Folder.width + Book.BackShift.x + BOOK_SHADOW_PAD * 2,
        Book.Folder.height + SHADOW_DROP + BOOK_SHADOW_PAD * 2,
    )

private fun stampShadow(scale: Float): Placed {
    val pad = STAMP_SHADOW_PAD
    val silhouette =
        bake(DpSize(Book.StampB.width + pad * 2, Book.StampB.height + pad * 2), scale) {
            drawRect(
                Color.Black,
                topLeft = Offset(pad.value, pad.value),
                size = Size(Book.StampB.width.value, Book.StampB.height.value),
            )
        }
    return Placed(blurred(silhouette, sigma = STAMP_SHADOW_BLUR * scale), DpOffset(-pad, -pad))
}

/** A Gaussian blur of [source], trimmed to rounded corners of [corner] pixels if given. */
private fun blurred(source: ImageBitmap, sigma: Float, corner: Float = 0f): ImageBitmap {
    val surface = Surface.makeRasterN32Premul(source.width, source.height)
    if (corner > 0f) {
        val bounds = RRect.makeXYWH(0f, 0f, source.width.toFloat(), source.height.toFloat(), corner)
        surface.canvas.clipRRect(bounds, antiAlias = true)
    }
    val paint =
        Paint().apply { imageFilter = ImageFilter.makeBlur(sigma, sigma, FilterTileMode.DECAL) }
    surface.canvas.drawImage(Image.makeFromBitmap(source.asSkiaBitmap()), 0f, 0f, paint)
    return surface.makeImageSnapshot().toComposeImageBitmap()
}

/** Draws [image], baked at the screen's density, with its top-left [at] dp from here. */
internal fun DrawScope.drawPlaced(image: ImageBitmap, at: DpOffset, alpha: Float = 1f) {
    // Whole pixels and the image's own size: a plain copy, with no filtering.
    drawImage(
        image,
        dstOffset = IntOffset(at.x.toPx().roundToInt(), at.y.toPx().roundToInt()),
        dstSize = IntSize(image.width, image.height),
        alpha = alpha,
    )
}

/** Draws [image] stretched over this whole scope. */
internal fun DrawScope.drawFilling(image: ImageBitmap, alpha: Float = 1f) {
    drawImage(
        image,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        alpha = alpha,
    )
}

/** Paper with perforation holes punched out of every edge. */
internal fun DrawScope.drawPerforatedPaper(width: Float, height: Float, paper: Color) {
    drawRect(paper, size = Size(width, height))
    for (hole in Perforation.holes(width, height, PERFORATION_PITCH)) {
        drawCircle(
            Color.Black,
            radius = HOLE_RADIUS,
            center = Offset(hole.x, hole.y),
            blendMode = BlendMode.Clear,
        )
    }
}

/** The holes sit about this far apart, in dp, along every edge. */
private const val PERFORATION_PITCH = 9.5f

private const val HOLE_RADIUS = 3.1f

/**
 * How many steps the cover's frost clears in. Its two-face cross-fade, both faces through the
 * perspective, cost 5–7 ms a software frame; the steps never show on a cover in motion.
 */
internal const val FROST_LEVELS = 6

/** How soft the stamps look through the frosted cover, as a blur sigma in dp. */
private const val FROST_BLUR = 12f

private const val BOOK_SHADOW_BLUR = 14f

private const val BOOK_SHADOW_ALPHA = 0.16f

/** A Gaussian has faded to nothing about three sigmas out. */
private val BOOK_SHADOW_PAD = (BOOK_SHADOW_BLUR * 3).dp

/** The book's shadow falls this far below it. */
private val SHADOW_DROP = 10.dp

private const val STAMP_SHADOW_BLUR = 6f

private val STAMP_SHADOW_PAD = (STAMP_SHADOW_BLUR * 3).dp
