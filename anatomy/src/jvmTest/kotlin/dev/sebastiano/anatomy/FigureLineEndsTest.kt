package dev.sebastiano.anatomy

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.sebastiano.anatomy.kit.ArtBuilder
import dev.sebastiano.anatomy.kit.Drawing
import dev.sebastiano.anatomy.kit.Flat
import dev.sebastiano.anatomy.kit.Free
import dev.sebastiano.anatomy.kit.IsoCanvas
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.LineMark
import dev.sebastiano.anatomy.kit.MarkRecorder
import dev.sebastiano.anatomy.kit.Palette
import dev.sebastiano.anatomy.kit.SolidPaths
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

    @Test
    fun `a clipped group's lines are checked only where the clip lets them show`() {
        val half = square(0.0, 0.0, 20.0, 40.0)
        fun ends(build: ArtBuilder.() -> Unit): List<String> {
            val figure = art(build)
            val drawing = paint {
                IsoCanvas(100.0, 50.0, Palette.Light, Modifier.width(200.dp)) { drawArt(figure) }
            }
            return lineEnds(drawing.marks).loose.map { "${it.end} at ${it.at}" }
        }
        // Out to x 30 on a bare face, but clipped at x 20: the clip cuts it, it doesn't float.
        val cut = ends {
            face()
            group("rod", clip = half) { line(segment(0.0, 20.0, 30.0, 20.0)) }
        }
        assertEquals(emptyList(), cut)
        val bare = ends {
            face()
            line(segment(0.0, 20.0, 30.0, 20.0))
        }
        assertEquals(listOf("End at Flat(x=30.0, y=20.0)"), bare)
        // Inside the clip, a line that stops short still floats.
        val short = ends {
            face()
            group("rod", clip = half) { line(segment(0.0, 20.0, 15.0, 20.0)) }
        }
        assertEquals(listOf("End at Flat(x=15.0, y=20.0)"), short)
        // An outline clipped away is nothing to land on.
        val gone = ends {
            group("plate", clip = half) { face() }
            line(segment(25.0, 20.0, 40.0, 20.0), free = Free.Start)
        }
        assertEquals(listOf("End at Flat(x=40.0, y=20.0)"), gone)
    }

    @Test
    fun `a clip-hidden end is counted like the original but cannot float`() {
        val figure = art {
            face()
            group("window", clip = square(0.0, 0.0, 20.0, 40.0)) {
                line(segment(0.0, 20.0, 30.0, 20.0))
            }
        }
        val drawing = paint {
            IsoCanvas(100.0, 50.0, Palette.Light, Modifier.width(200.dp)) { drawArt(figure) }
        }
        val report = lineEnds(drawing.marks)
        assertEquals(2, report.ends)
        assertEquals(emptyList(), report.loose)
    }

    private fun ArtBuilder.face() {
        val outline = square(0.0, 0.0, 40.0, 40.0)
        solid(SolidPaths(outline, outline, IsoPath.Empty, outline, emptyList()), flat = true)
    }

    private fun square(x0: Double, y0: Double, x1: Double, y1: Double): IsoPath =
        pathOf(listOf(Flat(x0, y0), Flat(x1, y0), Flat(x1, y1), Flat(x0, y1)), true)

    private fun segment(x0: Double, y0: Double, x1: Double, y1: Double): IsoPath =
        pathOf(listOf(Flat(x0, y0), Flat(x1, y1)))

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
