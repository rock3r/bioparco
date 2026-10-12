// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private const val TEXT_REFERENCE = 24.0

/** Art and text use the same affine face transform; local y runs down on a side face. */
fun FaceMatrix.composeMatrix(): Matrix =
    Matrix().apply {
        this[0, 0] = u.x.toFloat()
        this[0, 1] = u.y.toFloat()
        this[1, 0] = v.x.toFloat()
        this[1, 1] = v.y.toFloat()
        this[3, 0] = origin.x.toFloat()
        this[3, 1] = origin.y.toFloat()
    }

private fun IsoScope.measureFaceText(text: String, spacing: Double) =
    textMeasurer.measure(
        text,
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = (TEXT_REFERENCE / draw.density).sp,
            letterSpacing = spacing.em,
        ),
    )

/** Actual glyph advance in local face units, for a cursor immediately after displayed text. */
fun IsoScope.faceTextAdvance(text: String, size: Double, spacing: Double = 0.04): Double =
    measureFaceText(text, spacing).multiParagraph.width * size / TEXT_REFERENCE

internal fun IsoScope.drawText(mark: TextMark) {
    record(mark)
    val layout = measureFaceText(mark.text, mark.spacing)
    val anchorX =
        when (mark.anchor) {
            Anchor.Start -> 0f
            Anchor.Middle -> layout.size.width / 2f
            Anchor.End -> layout.size.width.toFloat()
        }
    val shrink = (mark.size / TEXT_REFERENCE).toFloat()
    draw.withTransform({
        transform(mark.matrix.composeMatrix())
        scale(shrink, shrink, Offset.Zero)
        translate(-anchorX, -layout.firstBaseline)
    }) {
        drawText(layout, color = palette.ink(mark.tone))
    }
}
