package dev.sebastiano.peelsticker

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import dev.sebastiano.bioparco.tracing.Tracing
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Surface

/**
 * A sticker ready to draw: its [front] (the picture on a white die-cut border) and its [back] (the
 * grey backing with a printed watermark), both [SIZE] texels square. The die-cut spans [CUT] texels
 * around the centre. [silhouette] maps the die-cut in cells of [CELL] texels.
 */
internal class StickerTexture(val front: Image, val back: Image, val silhouette: Silhouette) :
    AutoCloseable {
    override fun close() {
        front.close()
        back.close()
    }

    companion object {
        const val SIZE = 1024
        const val CUT = 1000f
        const val CELL = 4
    }
}

/**
 * Prints [picture] onto a new sticker. The white border is 4.1% of the picture's box, as in the
 * original, with softly rounded inside corners. [measurer] and [fontFamily] set the watermark.
 */
internal fun printSticker(
    picture: StickerPicture,
    measurer: TextMeasurer,
    fontFamily: FontFamily,
): StickerTexture = Tracing.section("print sticker") { printTexture(picture, measurer, fontFamily) }

private fun printTexture(
    picture: StickerPicture,
    measurer: TextMeasurer,
    fontFamily: FontFamily,
): StickerTexture {
    val size = StickerTexture.SIZE
    val artSide = StickerTexture.CUT / (1f + 2f * BORDER)
    val artBox = Rect(Offset(size / 2f, size / 2f), artSide / 2f)

    val art = Surface.makeRasterN32Premul(size, size)
    Tracing.section("draw picture") { picture.draw(art.canvas.asComposeCanvas(), artBox) }
    val artAlpha = alphaOf(art, size)
    val cut =
        Tracing.section("die-cut") {
            dieCut(artAlpha, size, size, border = artSide * BORDER, fillet = artSide * FILLET)
        }
    val cutMask = maskImage(cut, size)

    val front = Surface.makeRasterN32Premul(size, size)
    front.canvas.drawImage(cutMask, 0f, 0f)
    art.makeImageSnapshot().use { front.canvas.drawImage(it, 0f, 0f) }
    val back = Tracing.section("backing") { printBacking(cutMask, measurer, fontFamily) }
    val silhouette = Tracing.section("silhouette") { silhouetteOf(cut, size) }

    val result = StickerTexture(front.makeImageSnapshot(), back, silhouette)
    cutMask.close()
    art.close()
    front.close()
    return result
}

/**
 * The backing: plain white inside the die-cut, with a faint watermark printed mirrored on it. The
 * original prints on grey; this keeps its text-to-base contrast, about 1.38:1, on white instead.
 */
private fun printBacking(cutMask: Image, measurer: TextMeasurer, fontFamily: FontFamily): Image {
    val size = StickerTexture.SIZE
    val bitmap = ImageBitmap(size, size)
    val canvas = Canvas(bitmap)
    canvas.skiaCanvas.drawImage(cutMask, 0f, 0f)
    val text =
        measurer.measure(
            WATERMARK,
            TextStyle(
                fontFamily = fontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = WATERMARK_SIZE.sp,
            ),
            // Texels, whatever the window's density.
            density = Density(1f),
        )
    val extent = Size(size.toFloat(), size.toFloat())
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, extent) {
        // Seen from behind, the backing is mirrored; print it mirrored so a fold reads it right.
        withTransform({ scale(-1f, 1f, center) }) {
            val pitchX = text.size.width + WATERMARK_GAP
            var row = 0
            var y = -WATERMARK_ROW
            while (y < size + WATERMARK_ROW) {
                var x = -pitchX + (row % 2) * pitchX / 2f
                while (x < size + pitchX) {
                    drawText(text, WATERMARK_INK, Offset(x, y), blendMode = BlendMode.SrcAtop)
                    x += pitchX
                }
                y += WATERMARK_ROW
                row++
            }
        }
    }
    return Image.makeFromBitmap(bitmap.asSkiaBitmap().apply { setImmutable() })
}

private fun alphaOf(surface: Surface, size: Int): FloatArray {
    val bitmap = Bitmap()
    bitmap.allocPixels(ImageInfo.makeN32Premul(size, size))
    surface.readPixels(bitmap, 0, 0)
    val bytes = bitmap.readPixels() ?: ByteArray(size * size * 4)
    bitmap.close()
    return FloatArray(size * size) { (bytes[it * 4 + 3].toInt() and 0xFF) / 255f }
}

/** White with [coverage] as alpha, premultiplied. */
private fun maskImage(coverage: FloatArray, size: Int): Image {
    val bytes = ByteArray(size * size * 4)
    for (i in coverage.indices) {
        val value = (coverage[i] * 255f + 0.5f).toInt().toByte()
        bytes[i * 4] = value
        bytes[i * 4 + 1] = value
        bytes[i * 4 + 2] = value
        bytes[i * 4 + 3] = value
    }
    val info = ImageInfo.makeN32(size, size, ColorAlphaType.PREMUL)
    return Image.makeRaster(info, bytes, size * 4)
}

private fun silhouetteOf(cut: FloatArray, size: Int): Silhouette {
    val cell = StickerTexture.CELL
    val cells = size / cell
    val coverage =
        BooleanArray(cells * cells) { i ->
            val x = (i % cells) * cell + cell / 2
            val y = (i / cells) * cell + cell / 2
            cut[y * size + x] >= 0.5f
        }
    return Silhouette(cells, cells, coverage)
}

private inline fun <T : AutoCloseable, R> T.use(block: (T) -> R): R =
    try {
        block(this)
    } finally {
        close()
    }

/** The white border, as a share of the picture's box. */
private const val BORDER = 0.041f

/** The radius that rounds the border's inside corners, as a share of the picture's box. */
private const val FILLET = 0.03f

private val WATERMARK_INK = Color(0xFFDBDADF)
private const val WATERMARK = "Bioparco"

/** In texels. The original's is about 47. */
private const val WATERMARK_SIZE = 40f
private const val WATERMARK_GAP = 90f
private const val WATERMARK_ROW = 150f
