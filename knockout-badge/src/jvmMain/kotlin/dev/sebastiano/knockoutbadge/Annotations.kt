package dev.sebastiano.knockoutbadge

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The notes beside the badge when it is apart: one per layer, saying what it is and why it makes
 * the knockout, with a leader line to the layer.
 */
internal class Annotations(
    density: Density,
    measurer: TextMeasurer,
    fontFamily: FontFamily,
    codeFamily: FontFamily,
) : Density by density {
    /**
     * One note, laid out in single-colour pieces: [code], then its [value] after it, then the
     * [words] under them. Their colours are given when they are drawn, so the whole note fades:
     * `drawText` leaves the colour of a styled span at full strength, whatever alpha it is given.
     */
    private class Note(
        val layer: Int,
        val anchor: Offset,
        val code: TextLayoutResult,
        val value: TextLayoutResult?,
        val words: TextLayoutResult,
    )

    private val notes: List<Note> = run {
        val width = BadgeSpec.WIDTH.toPx()
        val height = BadgeSpec.HEIGHT.toPx()
        val title =
            TextStyle(fontFamily = codeFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        val body = TextStyle(fontSize = 11.sp, fontFamily = fontFamily, lineHeight = 15.sp)
        val constraints = Constraints(maxWidth = COLUMN.roundToPx())
        fun note(layer: Int, anchor: Offset, code: String, value: String, words: String) =
            Note(
                layer,
                anchor,
                measurer.measure(if (value.isEmpty()) code else "$code: ", title),
                if (value.isEmpty()) null else measurer.measure(value, title),
                measurer.measure(words, body, constraints = constraints),
            )
        listOf(
            note(
                Layers.PRINT,
                Offset(0f, height * 0.08f),
                "the print",
                "",
                "sits on top, not blended",
            ),
            note(
                Layers.INK,
                Offset(0f, height * 0.1f),
                "color",
                "#fff",
                "white has no darkness to add, so the video comes through untouched",
            ),
            note(
                Layers.BLACK,
                Offset(0f, height * 0.06f),
                "background",
                "#000",
                "black is as dark as it gets, so the video goes all the way to black",
            ),
            note(
                Layers.BLACK,
                Offset(width * 0.25f, height * 0.62f),
                "mix-blend-mode",
                "multiply",
                "darkens the video by however dark the sticker is",
            ),
            note(
                Layers.VIDEO,
                Offset(0f, height * 0.2f),
                "<video>",
                "",
                "plays underneath everything",
            ),
        )
    }

    private var primed = 0

    /** Whether the notes still need drawing unseen before they first show. */
    fun needsPriming(): Boolean = primed < PRIME_FRAMES

    /**
     * Draws the notes at [shown] (0..1), or unseen at 0 to prime them; [place] maps a point in a
     * layer's frame to the stage.
     */
    fun draw(scope: DrawScope, shown: Float, place: (layer: Int, local: Offset) -> Offset) =
        with(scope) {
            val alpha = if (shown > 0f) shown else PRIME_ALPHA.also { primed++ }
            val left = MARGIN.toPx()
            val lineFrom = left + COLUMN.toPx() + GAP.toPx()
            val dot = 2.5.dp.toPx()
            for (note in notes) {
                val anchor = place(note.layer, note.anchor)
                val top = anchor.y - note.code.firstBaseline + 3.dp.toPx()
                drawText(note.code, Color.White, Offset(left, top), alpha = alpha)
                note.value?.let {
                    drawText(it, Accent, Offset(left + note.code.size.width, top), alpha = alpha)
                }
                drawText(
                    note.words,
                    Quiet,
                    Offset(left, top + note.code.size.height),
                    alpha = alpha,
                )
                val y = anchor.y - 3.dp.toPx()
                val end = Offset(anchor.x - 2f * dot, anchor.y)
                drawLine(Accent.copy(alpha = alpha * 0.8f), Offset(lineFrom, y), end, 1.dp.toPx())
                drawCircle(Accent.copy(alpha = alpha), dot, end)
            }
        }

    private companion object {
        val Accent = Color(0xFFFF6B4A)
        val Quiet = Color(0xFF9C9DA4)
        val MARGIN = 28.dp
        val COLUMN = 196.dp
        val GAP = 10.dp
        const val PRIME_FRAMES = 2
    }
}
