package dev.sebastiano.routinetimeline

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * [layout] drawn once into an image. Text drawn this way costs an image draw per frame, and fades
 * for free. Drawing a text layout with a new alpha every frame makes Skia lay the paragraph out
 * again on every call (see wheel-picker's README).
 */
internal fun textImage(layout: TextLayoutResult, density: Density): ImageBitmap {
    val image = ImageBitmap(layout.size.width.coerceAtLeast(1), layout.size.height.coerceAtLeast(1))
    CanvasDrawScope().draw(
        density,
        LayoutDirection.Ltr,
        Canvas(image),
        Size(image.width.toFloat(), image.height.toFloat()),
    ) {
        drawText(layout, topLeft = Offset.Zero)
    }
    return image
}
