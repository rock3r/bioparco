// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.ui.component.Text

/** Below this card width the hint moves above the readout, as on the original's phones. */
private val NARROW = 528.dp

/** A legend key's swatch: a raised or flat solid, or a line in a style or colour. */
enum class KeyMark {
    Raised,
    Flat,
    Lit,
    Edge,
    Dim,
    Dotted,
    Dashed,
    Red,
    Green,
    Blue,
}

/** One legend entry under the card. */
data class LegendKey(val mark: KeyMark, val label: String)

/**
 * The card a figure sits in: `Fig n` at top left, a short title at top right, the interaction hint
 * at bottom left and the live readout at bottom right, with legend keys and a true caption below.
 * [readout] is read only by the readout's own text, so a readout that changes every frame
 * recomposes nothing else.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Plate(
    fig: String,
    title: String,
    hint: String,
    readout: () -> String,
    palette: Palette,
    modifier: Modifier = Modifier,
    keys: List<LegendKey> = emptyList(),
    caption: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().background(palette.card).border(1.dp, palette.mid)
        ) {
            // The original's phone layout: under 560 px of page the hint sits above the readout.
            val narrow = maxWidth < NARROW
            val padding =
                if (narrow) PaddingValues(start = 12.dp, end = 12.dp, top = 48.dp, bottom = 70.dp)
                else PaddingValues(horizontal = 24.dp, vertical = 52.dp)
            Box(Modifier.padding(padding)) { content() }
            Box(Modifier.matchParentSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
                Corner(fig, palette.text, Modifier.align(Alignment.TopStart))
                Corner(title.uppercase(), palette.muted, Modifier.align(Alignment.TopEnd), 0.1.em)
                Corner(
                    hint.uppercase(),
                    palette.muted,
                    Modifier.align(Alignment.BottomStart)
                        .padding(bottom = if (narrow) 18.dp else 0.dp),
                    0.1.em,
                )
                Readout(readout, palette.text, Modifier.align(Alignment.BottomEnd))
            }
        }
        if (keys.isNotEmpty() || caption != null) {
            Column(
                Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (keys.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (key in keys) Key(key, palette)
                    }
                }
                if (caption != null) {
                    Text(caption, color = palette.muted, fontSize = 13.sp, lineHeight = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun Corner(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    spacing: TextUnit = 0.04.em,
) {
    Text(
        text,
        modifier = modifier,
        color = color,
        fontSize = 10.5.sp,
        fontFamily = FontFamily.Monospace,
        letterSpacing = spacing,
        maxLines = 1,
    )
}

@Composable
private fun Readout(readout: () -> String, color: Color, modifier: Modifier = Modifier) {
    Corner(readout(), color, modifier)
}

@Composable
private fun Key(key: LegendKey, palette: Palette, modifier: Modifier = Modifier) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Swatch(key.mark, palette)
        Text(key.label, color = palette.muted, fontSize = 13.sp)
    }
}

@Composable
private fun Swatch(mark: KeyMark, palette: Palette, modifier: Modifier = Modifier) {
    Canvas(modifier.size(16.dp, 10.dp)) {
        val unit = size.width / 16f
        val color =
            when (mark) {
                KeyMark.Raised,
                KeyMark.Flat -> palette.mid
                KeyMark.Lit -> palette.lit
                KeyMark.Dim -> palette.lo
                KeyMark.Red -> palette.red
                KeyMark.Green -> palette.green
                KeyMark.Blue -> palette.blue
                else -> palette.hi
            }
        val effect =
            when (mark) {
                KeyMark.Dotted -> PathEffect.dashPathEffect(floatArrayOf(0.01f * unit, 3f * unit))
                KeyMark.Dashed -> PathEffect.dashPathEffect(floatArrayOf(3f * unit, 2.5f * unit))
                else -> null
            }
        val width = if (mark == KeyMark.Dotted) 1.4.dp.toPx() else 1.dp.toPx()
        val stroke =
            DrawStroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect)
        fun path(d: String) =
            PathParser().parsePathString(d).toPath().apply {
                transform(Matrix().apply { scale(unit, unit) })
            }
        when (mark) {
            KeyMark.Raised,
            KeyMark.Lit -> {
                drawPath(path("M2 4.5 8 1.5 14 4.5 14 6 8 9 2 6Z"), color, style = stroke)
                drawPath(path("M2 4.5 8 7.5 14 4.5"), palette.lo, style = stroke)
            }
            KeyMark.Flat -> drawPath(path("M2 6.5 8 3.5 14 6.5 8 9.5Z"), color, style = stroke)
            else -> drawPath(path("M1.5 5h13"), color, style = stroke)
        }
    }
}
