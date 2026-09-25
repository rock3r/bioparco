package dev.sebastiano.borderbeam

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.skiaPaint
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter

internal fun colorMatrix4x5(hueDegrees: Float, brightness: Float, saturation: Float): FloatArray {
    val matrix = composedFilterMatrix(hueDegrees, brightness, saturation)
    return floatArrayOf(
        matrix[0],
        matrix[1],
        matrix[2],
        0f,
        0f,
        matrix[3],
        matrix[4],
        matrix[5],
        0f,
        0f,
        matrix[6],
        matrix[7],
        matrix[8],
        0f,
        0f,
        0f,
        0f,
        0f,
        1f,
        0f,
    )
}

internal fun DrawScope.withFilteredLayer(
    alpha: Float,
    colorMatrix: FloatArray?,
    blurPx: Float,
    outset: Float = 0f,
    block: DrawScope.() -> Unit,
) {
    if (alpha <= 0.002f) return
    val paint =
        Paint().apply {
            this.alpha = alpha.coerceIn(0f, 1f)
            if (colorMatrix != null) {
                colorFilter = ColorFilter.colorMatrix(ColorMatrix(colorMatrix))
            }
            if (blurPx > 0.5f) {
                // CSS `blur()`'s argument is the Gaussian standard deviation.
                skiaPaint.maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, blurPx, true)
            }
        }
    val bounds = Rect(-outset, -outset, size.width + outset, size.height + outset)
    drawIntoCanvas { canvas -> canvas.saveLayer(bounds, paint) }
    try {
        block()
    } finally {
        drawIntoCanvas { canvas -> canvas.restore() }
    }
}

internal fun DrawScope.drawSoftEllipse(
    center: Offset,
    radiusX: Float,
    radiusY: Float,
    color: Color,
) {
    if (radiusX < 0.4f || radiusY < 0.4f || color.alpha <= 0.004f) return
    val brush =
        Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = 0f)),
            center = Offset.Zero,
            radius = 1f,
        )
    translate(center.x, center.y) {
        scale(radiusX, radiusY, pivot = Offset.Zero) {
            drawCircle(brush = brush, radius = 1f, center = Offset.Zero)
        }
    }
}

internal fun DrawScope.drawBlobList(blobs: List<BeamBlob>) {
    for (index in blobs.lastIndex downTo 0) {
        val blob = blobs[index]
        drawSoftEllipse(
            center = Offset(blob.x * size.width, blob.y * size.height),
            radiusX = blob.rx,
            radiusY = blob.ry,
            color = Color(blob.r / 255f, blob.g / 255f, blob.b / 255f, blob.a.coerceIn(0f, 1f)),
        )
    }
}

internal fun DrawScope.drawConicWash(
    degrees: Float,
    stops: List<Pair<Float, Float>>,
    ink: Color,
    blendMode: BlendMode,
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val extent = maxOf(size.width, size.height) * 2f
    val brush =
        Brush.sweepGradient(
            colorStops =
                stops
                    .map { (position, alpha) -> position to ink.copy(alpha = alpha) }
                    .toTypedArray(),
            center = center,
        )
    rotate(degrees = degrees - 90f, pivot = center) {
        drawRect(
            brush = brush,
            topLeft = Offset(center.x - extent, center.y - extent),
            size = Size(extent * 2f, extent * 2f),
            blendMode = blendMode,
        )
    }
}

internal fun DrawScope.drawEllipseMask(center: Offset, radiusX: Float, radiusY: Float) {
    if (radiusX < 0.4f || radiusY < 0.4f) return
    val brush =
        Brush.radialGradient(
            colorStops =
                arrayOf(
                    0f to Color.White,
                    0.45f to Color.White.copy(alpha = 0.5f),
                    1f to Color.Transparent,
                ),
            center = Offset.Zero,
            radius = 1f,
        )
    val cover = maxOf(size.width, size.height) / minOf(radiusX, radiusY) * 2f
    translate(center.x, center.y) {
        scale(radiusX, radiusY, pivot = Offset.Zero) {
            drawRect(
                brush = brush,
                topLeft = Offset(-cover, -cover),
                size = Size(cover * 2f, cover * 2f),
                blendMode = BlendMode.DstIn,
            )
        }
    }
}

internal fun DrawScope.clipRounded(radius: Float, block: DrawScope.() -> Unit) {
    val path =
        Path().apply {
            addRoundRect(RoundRect(Rect(0f, 0f, size.width, size.height), CornerRadius(radius)))
        }
    clipPath(path, block = block)
}

/**
 * Feather that keeps color within [band] px of the rounded edge. CSS adds the two edge gradients.
 */
internal fun DrawScope.drawEdgeBand(band: Float) {
    val dstIn = Paint().apply { blendMode = BlendMode.DstIn }
    drawIntoCanvas { canvas -> canvas.saveLayer(Rect(0f, 0f, size.width, size.height), dstIn) }
    try {
        val vertical =
            Brush.linearGradient(
                colorStops =
                    arrayOf(
                        0f to Color.White,
                        (band / size.height).coerceIn(0f, 0.5f) to Color.Transparent,
                        (1f - band / size.height).coerceIn(0.5f, 1f) to Color.Transparent,
                        1f to Color.White,
                    ),
                start = Offset(0f, 0f),
                end = Offset(0f, size.height),
            )
        val horizontal =
            Brush.linearGradient(
                colorStops =
                    arrayOf(
                        0f to Color.White,
                        (band / size.width).coerceIn(0f, 0.5f) to Color.Transparent,
                        (1f - band / size.width).coerceIn(0.5f, 1f) to Color.Transparent,
                        1f to Color.White,
                    ),
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
            )
        drawRect(vertical)
        drawRect(horizontal, blendMode = BlendMode.Plus)
    } finally {
        drawIntoCanvas { canvas -> canvas.restore() }
    }
}

internal fun DrawScope.clipRing(radius: Float, band: Float, block: DrawScope.() -> Unit) {
    val outer =
        Path().apply {
            addRoundRect(RoundRect(Rect(0f, 0f, size.width, size.height), CornerRadius(radius)))
        }
    val inset = band.coerceAtLeast(0.75f)
    val innerRadius = (radius - inset).coerceAtLeast(0f)
    val inner =
        Path().apply {
            addRoundRect(
                RoundRect(
                    Rect(inset, inset, size.width - inset, size.height - inset),
                    CornerRadius(innerRadius),
                )
            )
        }
    val ring = Path().apply { op(outer, inner, PathOperation.Difference) }
    clipPath(ring, block = block)
}
