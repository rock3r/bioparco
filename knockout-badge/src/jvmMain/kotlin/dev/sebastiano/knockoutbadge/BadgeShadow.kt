package dev.sebastiano.knockoutbadge

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Surface

/**
 * The badge's soft shadow on the table. A blur every frame would cost several milliseconds in
 * software, so it is blurred once into an image and drawn on whole pixels.
 */
internal class BadgeShadow(density: Density) : Density by density {
    private val pad = BLUR.toPx() * 2f
    private val image: ImageBitmap = bake()

    private fun bake(): ImageBitmap {
        val width = BadgeSpec.WIDTH.toPx()
        val height = BadgeSpec.HEIGHT.toPx()
        val surface =
            Surface.makeRasterN32Premul(
                ceil(width + pad * 2).toInt(),
                ceil(height + pad * 2).toInt(),
            )
        val paint =
            Paint().apply {
                color = SHADOW
                isAntiAlias = true
                maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, BLUR.toPx() / 2f)
            }
        surface.canvas.drawRRect(
            RRect.makeXYWH(pad, pad, width, height, BadgeSpec.CORNER.toPx()),
            paint,
        )
        return surface.makeImageSnapshot().toComposeImageBitmap().also { surface.close() }
    }

    /** Draws the shadow under a badge centred on [centre], at [alpha]. */
    fun draw(scope: DrawScope, centre: Offset, alpha: Float) {
        if (alpha <= 0f) return
        val left = centre.x - BadgeSpec.WIDTH.toPx() / 2f - pad
        val top = centre.y - BadgeSpec.HEIGHT.toPx() / 2f - pad + DROP.toPx()
        scope.drawImage(
            image,
            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
            dstSize = IntSize(image.width, image.height),
            alpha = alpha,
            filterQuality = FilterQuality.None,
        )
    }

    private companion object {
        val BLUR = 22.dp
        val DROP = 14.dp
        const val SHADOW = 0x8C000000.toInt()
    }
}
