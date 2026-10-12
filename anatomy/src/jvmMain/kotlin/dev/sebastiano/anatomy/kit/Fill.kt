// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaPaint
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter

/** Palette-token fills for art on a face: recess walls, glass, silhouettes and phosphor. */
enum class FillInk {
    Paper,
    Top,
    Shade0,
    Shade1,
    Shade2,
    Shade3,
    LitShade,
    Lit,
}

class FillMark(
    val shape: Shape,
    val ink: FillInk,
    val alpha: Double = 1.0,
    val blur: Double = 0.0,
    val evenOdd: Boolean = false,
) : Mark()

fun ArtBuilder.fill(
    path: IsoPath,
    ink: FillInk,
    alpha: Double = 1.0,
    blur: Double = 0.0,
    evenOdd: Boolean = false,
) {
    add(Art(listOf(FillMark(Shape(path), ink, alpha, blur, evenOdd))))
}

internal fun IsoScope.drawFill(mark: FillMark) {
    record(mark)
    val color =
        when (mark.ink) {
            FillInk.Paper -> palette.paper
            FillInk.Top -> palette.top
            FillInk.Shade0 -> palette.shades[0]
            FillInk.Shade1 -> palette.shades[1]
            FillInk.Shade2 -> palette.shades[2]
            FillInk.Shade3 -> palette.shades[3]
            FillInk.LitShade -> palette.litShade
            FillInk.Lit -> palette.lit
        }
    val path = mark.shape.path
    path.fillType = if (mark.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero
    draw.drawIntoCanvas { canvas ->
        val paint =
            Paint().apply { this.color = color.copy(alpha = color.alpha * mark.alpha.toFloat()) }
        if (mark.blur > 0)
            paint.skiaPaint.maskFilter =
                MaskFilter.makeBlur(FilterBlurMode.NORMAL, mark.blur.toFloat())
        canvas.drawPath(path, paint)
    }
}
