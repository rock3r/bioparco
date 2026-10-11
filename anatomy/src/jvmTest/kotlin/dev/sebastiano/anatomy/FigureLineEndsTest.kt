package dev.sebastiano.anatomy

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.sebastiano.anatomy.kit.Drawing
import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.IsoCanvas
import dev.sebastiano.anatomy.kit.LineMark
import dev.sebastiano.anatomy.kit.MarkRecorder
import dev.sebastiano.anatomy.kit.Palette
import dev.sebastiano.anatomy.kit.art
import dev.sebastiano.anatomy.kit.lineEnds
import dev.sebastiano.anatomy.kit.pathOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

/** Every figure, as it really paints, keeps every line ending on something. */
class FigureLineEndsTest {
    @Test
    fun `every figure's lines end on something in its resting pose`() {
        for (figure in Figure.entries) {
            val drawing = paint {
                IntUiTheme(isDark = false) { FigureContent(figure, Palette.Light, false) }
            }
            assertTrue(drawing.marks.isNotEmpty(), "${figure.id} painted nothing")
            val report = lineEnds(drawing.marks, Palette.Light)
            assertEquals(
                emptyList(),
                report.loose.map { "${it.what} ${it.end} at ${it.at} d ${it.d}" },
                "${figure.id}: line ends float",
            )
        }
    }

    @Test
    fun `the recorder sees marks where groups and copies were moved to`() {
        val part = art { group("slide") { line(pathOf(listOf(Flat(0.0, 0.0), Flat(1.0, 0.0)))) } }
        val drawing = paint {
            IsoCanvas(100.0, 50.0, Palette.Light, Modifier.width(200.dp)) {
                drawArt(part) { Flat(3.0, 4.0) }
                drawArt(part, Flat(10.0, 20.0))
                line(pathOf(listOf(Flat(0.0, 0.0), Flat(1.0, 0.0))))
            }
        }
        assertEquals(
            listOf(Flat(3.0, 4.0), Flat(10.0, 20.0), Flat(0.0, 0.0)),
            drawing.marks.filter { it.mark is LineMark }.map { it.offset },
        )
    }

    private fun paint(content: @Composable () -> Unit): Drawing {
        val drawing = Drawing()
        val scene = ImageComposeScene(1400, 2400, Density(2f)) { content() }
        MarkRecorder.active = drawing
        try {
            scene.render(0L)
        } finally {
            MarkRecorder.active = null
            scene.close()
        }
        return drawing
    }
}
