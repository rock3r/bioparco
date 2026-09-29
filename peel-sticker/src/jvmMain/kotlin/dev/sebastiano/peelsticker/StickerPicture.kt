package dev.sebastiano.peelsticker

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.min
import kotlin.math.roundToInt

/** What is printed on the sticker. It draws itself centred in a square box. */
internal sealed interface StickerPicture {
    fun draw(canvas: Canvas, box: Rect)

    /** The X logo, the first sticker in the original video. */
    data object Ex : StickerPicture {
        override fun draw(canvas: Canvas, box: Rect) {
            val path = PathParser().parsePathString(X_LOGO).toPath()
            val scale = min(box.width / X_WIDTH, box.height / X_HEIGHT)
            path.transform(
                Matrix().apply {
                    translate(
                        box.left + (box.width - X_WIDTH * scale) / 2f,
                        box.top + (box.height - X_HEIGHT * scale) / 2f,
                    )
                    scale(scale, scale)
                }
            )
            canvas.drawPath(path, fill(Color.Black))
        }
    }

    /**
     * The four-colour G the original video drops in next, measured off the video: a ring with an
     * inner radius of 0.59, slanted colour boundaries, and a bar that starts just right of centre.
     * Points are in radii from the centre, y down.
     */
    data object Gee : StickerPicture {
        override fun draw(canvas: Canvas, box: Rect) {
            val radius = min(box.width, box.height) / 2f
            val centre = box.center
            fun polygon(vararg points: Float): Path =
                Path().apply {
                    for (i in points.indices step 2) {
                        val x = centre.x + points[i] * radius
                        val y = centre.y + points[i + 1] * radius
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
            fun disc(r: Float) = Path().apply { addOval(Rect(centre, r)) }
            fun cut(a: Path, b: Path, operation: PathOperation) =
                Path().apply { op(a, b, operation) }

            val ring = cut(disc(radius), disc(radius * 0.59f), PathOperation.Difference)
            val bar =
                cut(
                    disc(radius),
                    polygon(0.031f, -0.1735f, 1.1f, -0.1735f, 1.1f, 0.2294f, 0.031f, 0.2294f),
                    PathOperation.Intersect,
                )
            val blueArc =
                polygon(
                    0.031f,
                    -0.1735f,
                    3f,
                    -0.1735f,
                    3f,
                    2.4f,
                    2.57f,
                    2.4f,
                    0.17f,
                    0.425f,
                    0.031f,
                    0.425f,
                )
            val green =
                polygon(0.17f, 0.425f, 2.57f, 2.4f, 0f, 3.5f, -2.843f, 2.05f, -0.4426f, 0.1232f)
            val yellow =
                polygon(
                    -0.4426f,
                    0.1232f,
                    -2.843f,
                    2.05f,
                    -3.5f,
                    0f,
                    -2.881f,
                    -2.056f,
                    -0.4403f,
                    -0.0852f,
                )
            val red =
                polygon(
                    -0.4403f,
                    -0.0852f,
                    -2.881f,
                    -2.056f,
                    0f,
                    -3.5f,
                    2.654f,
                    -2.412f,
                    0.2014f,
                    -0.393f,
                )

            for ((region, color) in listOf(red to GeeRed, yellow to GeeYellow, green to GeeGreen)) {
                canvas.drawPath(cut(ring, region, PathOperation.Intersect), fill(color))
            }
            val blue = cut(cut(ring, blueArc, PathOperation.Intersect), bar, PathOperation.Union)
            canvas.drawPath(blue, fill(GeeBlue))
        }
    }

    /** A picture dropped onto the window, fitted into the box. */
    class Dropped(private val image: ImageBitmap) : StickerPicture {
        override fun draw(canvas: Canvas, box: Rect) {
            val scale = min(box.width / image.width, box.height / image.height)
            val size =
                IntSize((image.width * scale).roundToInt(), (image.height * scale).roundToInt())
            val topLeft = box.center - Offset(size.width / 2f, size.height / 2f)
            canvas.drawImageRect(
                image,
                dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                dstSize = size,
                paint = Paint().apply { filterQuality = FilterQuality.High },
            )
        }
    }
}

private fun fill(color: Color) =
    Paint().apply {
        this.color = color
        isAntiAlias = true
    }

private const val X_WIDTH = 1200f
private const val X_HEIGHT = 1227f
private const val X_LOGO =
    "M714.163 519.284L1160.89 0H1055.03L667.137 450.887L357.328 0H0L468.492 681.821L0 1226.37H105.866" +
        "L515.491 750.218L842.672 1226.37H1200L714.163 519.284ZM569.165 687.828L521.697 619.934" +
        "L144.011 79.6944H306.615L611.412 515.685L658.88 583.579L1055.08 1150.3H892.476L569.165 687.828Z"

private val GeeRed = Color(0xFFE02832)
private val GeeYellow = Color(0xFFF3B607)
private val GeeGreen = Color(0xFF2DC140)
private val GeeBlue = Color(0xFF5686F1)
