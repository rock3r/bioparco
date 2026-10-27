package dev.sebastiano.wheelpicker

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
 * [layout] drawn once into an image, with [padding] clear pixels around it. Text drawn this way
 * costs an image draw per frame. Drawing a text layout with a new colour or alpha every frame, as a
 * fade does, makes Skia update the paragraph's paint and lay it out again on every call.
 */
internal fun textImage(layout: TextLayoutResult, density: Density, padding: Int = 0): ImageBitmap {
    val image = ImageBitmap(layout.size.width + 2 * padding, layout.size.height + 2 * padding)
    CanvasDrawScope().draw(
        density,
        LayoutDirection.Ltr,
        Canvas(image),
        Size(image.width.toFloat(), image.height.toFloat()),
    ) {
        drawText(layout, topLeft = Offset(padding.toFloat(), padding.toFloat()))
    }
    return image
}
