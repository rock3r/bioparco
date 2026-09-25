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
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.skiaPaint
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
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
    blendMode: BlendMode = BlendMode.SrcOver,
    block: DrawScope.() -> Unit,
) {
    if (alpha <= 0.002f) return
    val paint =
        Paint().apply {
            this.alpha = alpha.coerceIn(0f, 1f)
            this.blendMode = blendMode
            if (colorMatrix != null) {
                colorFilter = ColorFilter.colorMatrix(ColorMatrix(colorMatrix))
            }
            if (blurPx > 0.5f) {
                // CSS blur() is a Gaussian std-deviation, but a 1px ring at that sigma
                // collapses under Skia. Callers pass a larger sigma when the aura must read.
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

/** Paints CSS `radial-gradient` blobs, first on top. [scale] turns CSS px radii into pixels. */
internal fun DrawScope.drawBlobList(blobs: List<BeamBlob>, scale: Float = 1f) {
    for (index in blobs.lastIndex downTo 0) {
        val blob = blobs[index]
        drawSoftEllipse(
            center = Offset(blob.x * size.width, blob.y * size.height),
            radiusX = blob.rx * scale,
            radiusY = blob.ry * scale,
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

internal fun DrawScope.roundedPath(inset: Float, radius: Float): Path =
    Path().apply {
        addRoundRect(
            RoundRect(
                Rect(inset, inset, size.width - inset, size.height - inset),
                CornerRadius(radius),
            )
        )
    }

/**
 * CSS `box-shadow: inset 0 0 <blur> <spread> color`: everything outside the box shrunk by [spread],
 * blurred with a standard deviation of half the blur radius.
 */
internal fun DrawScope.drawInsetShadow(radius: Float, color: Color, blur: Float, spread: Float) {
    if (color.alpha <= 0f) return
    val sigma = blur / 2f
    val margin = sigma * 3f + spread
    val shadow =
        Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(-margin, -margin, size.width + margin, size.height + margin))
            addRoundRect(
                RoundRect(
                    Rect(spread, spread, size.width - spread, size.height - spread),
                    CornerRadius((radius - spread).coerceAtLeast(0f)),
                )
            )
        }
    val paint =
        Paint().apply {
            this.color = color
            skiaPaint.maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, sigma, true)
        }
    drawIntoCanvas { canvas -> canvas.drawPath(shadow, paint) }
}

/**
 * The inner edge fade: `linear-gradient(white, transparent <band>, transparent calc(100% - <band>),
 * white)` down and across, combined with `mask-composite: add` (source-over). Like CSS, a stop that
 * would come before the previous one is moved up to it.
 */
internal fun DrawScope.drawEdgeFade(band: Float) {
    fun stops(length: Float): Array<Pair<Float, Color>> {
        val near = (band / length).coerceIn(0f, 1f)
        val far = (1f - band / length).coerceIn(near, 1f)
        return arrayOf(
            0f to Color.White,
            near to Color.Transparent,
            far to Color.Transparent,
            1f to Color.White,
        )
    }
    drawRect(Brush.verticalGradient(*stops(size.height)))
    drawRect(Brush.horizontalGradient(*stops(size.width)))
}

/**
 * The 1-border-width ring of a box whose outer corners have [outerRadius]: border box minus content
 * box.
 */
internal fun DrawScope.ringPath(outerRadius: Float, width: Float): Path =
    Path().apply {
        fillType = PathFillType.EvenOdd
        addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(outerRadius)))
        addRoundRect(
            RoundRect(
                Rect(width, width, size.width - width, size.height - width),
                CornerRadius((outerRadius - width).coerceAtLeast(0f)),
            )
        )
    }

/**
 * CSS `radial-gradient(ellipse <radiusX> <radiusY> at <center>, ...)`. [stops] run from the center
 * (0) to the ellipse edge (1); past the last stop the last color holds. With [cover] the gradient
 * fills the whole layer, which a mask needs; otherwise only the ellipse is painted.
 */
internal fun DrawScope.drawEllipseGradient(
    center: Offset,
    radiusX: Float,
    radiusY: Float,
    stops: Array<Pair<Float, Color>>,
    blendMode: BlendMode = BlendMode.SrcOver,
    cover: Boolean = false,
) {
    if (radiusX < 0.05f || radiusY < 0.05f) {
        if (cover) drawRect(Color.Transparent, blendMode = blendMode)
        return
    }
    val brush = Brush.radialGradient(*stops, center = Offset.Zero, radius = 1f)
    translate(center.x, center.y) {
        scale(radiusX, radiusY, pivot = Offset.Zero) {
            if (cover) {
                val reach = maxOf(size.width / radiusX, size.height / radiusY) * 2f + 2f
                drawRect(
                    brush,
                    Offset(-reach, -reach),
                    Size(reach * 2f, reach * 2f),
                    blendMode = blendMode,
                )
            } else {
                drawCircle(brush, radius = 1f, center = Offset.Zero, blendMode = blendMode)
            }
        }
    }
}

/**
 * Offscreen layer composited with [alpha] and [blendMode]. [colorMatrix] and [blurSigma] filter the
 * layer as a whole, the way a CSS `filter` does; a mask filter would be ignored here.
 */
internal inline fun DrawScope.withLayer(
    alpha: Float = 1f,
    colorMatrix: FloatArray? = null,
    blurSigma: Float = 0f,
    blendMode: BlendMode = BlendMode.SrcOver,
    block: DrawScope.() -> Unit,
) {
    if (alpha <= MIN_LAYER_ALPHA) return
    val paint =
        Paint().apply {
            this.alpha = alpha.coerceIn(0f, 1f)
            this.blendMode = blendMode
            if (colorMatrix != null) colorFilter = ColorFilter.colorMatrix(ColorMatrix(colorMatrix))
            if (blurSigma > 0f) {
                skiaPaint.imageFilter =
                    ImageFilter.makeBlur(blurSigma, blurSigma, FilterTileMode.DECAL)
            }
        }
    drawIntoCanvas { canvas -> canvas.saveLayer(Rect(Offset.Zero, size), paint) }
    try {
        block()
    } finally {
        drawIntoCanvas { canvas -> canvas.restore() }
    }
}

internal const val MIN_LAYER_ALPHA = 0.002f
