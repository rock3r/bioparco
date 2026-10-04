package dev.sebastiano.bioparco.recordings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.hairline.App
import dev.sebastiano.hairline.Figure
import dev.sebastiano.hairline.HairlineTags
import dev.sebastiano.spectre.core.ComposeAutomator
import dev.sebastiano.spectre.recording.AutoRecorder
import dev.sebastiano.spectre.recording.screencapturekit.asTitledWindow
import dev.sebastiano.spectre.testing.runSpectreTest
import java.awt.GraphicsEnvironment
import java.nio.file.Files
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

class HairlineRecordingTest {
    @Test
    @Tag("recording")
    fun recordHairline(): Unit = runSpectreTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        val output = RecordingPaths.file(RecordingPaths.HAIRLINE)
        Files.createDirectories(output.parent)
        // Three columns: the first two rows are the original's six hero figures.
        val window =
            SpecimenWindow(title = "bioparco · hairline", size = DpSize(1000.dp, 780.dp)) { App() }
        window.start()
        try {
            val automator = ComposeAutomator.inProcess()
            automator.waitForNode(tag = HairlineTags.figure(Figure.Turntable))
            delay(800)
            val handle = AutoRecorder().startWindow(window.frame().asTitledWindow(), output)
            try {
                /**
                 * Glides the pointer over [figure] through viewBox points (400 × 320), [ms] per
                 * step.
                 */
                suspend fun glide(
                    figure: Figure,
                    vararg points: Pair<Int, Int>,
                    steps: Int = 16,
                    ms: Long = 30,
                ) {
                    val b = automator.waitForNode(tag = HairlineTags.figure(figure)).boundsOnScreen
                    fun at(p: Pair<Int, Int>) =
                        (b.x + p.first * b.width / 400f).toInt() to
                            (b.y + p.second * b.height / 320f).toInt()
                    for (k in 1 until points.size) {
                        val (x0, y0) = at(points[k - 1])
                        val (x1, y1) = at(points[k])
                        for (s in 0..steps) {
                            automator.moveTo(x0 + (x1 - x0) * s / steps, y0 + (y1 - y0) * s / steps)
                            delay(ms)
                        }
                    }
                }

                delay(600)
                // Riffle: the cards stand up one after another under the pointer.
                glide(
                    Figure.Riffle,
                    110 to 230,
                    170 to 190,
                    250 to 120,
                    290 to 90,
                    steps = 24,
                    ms = 40,
                )
                delay(500)
                // Terrain: the pillars rise around the pointer as it crosses the plinth.
                glide(
                    Figure.Terrain,
                    90 to 200,
                    200 to 120,
                    320 to 190,
                    210 to 250,
                    steps = 22,
                    ms = 35,
                )
                delay(400)
                // Exploded: across opens the gap, down picks a layer.
                glide(
                    Figure.Exploded,
                    60 to 120,
                    340 to 120,
                    220 to 70,
                    220 to 220,
                    steps = 20,
                    ms = 35,
                )
                delay(600)
                // Phosphor: paint a stroke, then let it fade back into the loop.
                glide(
                    Figure.Phosphor,
                    140 to 120,
                    260 to 150,
                    170 to 200,
                    280 to 210,
                    steps = 14,
                    ms = 30,
                )
                glide(Figure.Phosphor, 280 to 210, 390 to 300, steps = 4, ms = 30)
                delay(900)
                // Slow: hovering the belt slows the clock.
                glide(Figure.Slow, 120 to 140, 200 to 160, steps = 10, ms = 30)
                delay(1_400)
                // Turntable: a flick spins it; it coasts and settles on a quarter turn.
                glide(Figure.Turntable, 40 to 176, 360 to 176, steps = 8, ms = 16)
                glide(Figure.Turntable, 360 to 176, 395 to 315, steps = 3, ms = 16)
                delay(2_000)
                // The dark palette, and one more pass over the pillars.
                automator.click(automator.waitForNode(text = "Dark"))
                delay(500)
                glide(Figure.Terrain, 330 to 200, 120 to 150, 200 to 240, steps = 22, ms = 35)
                glide(Figure.Terrain, 200 to 240, 395 to 315, steps = 4, ms = 30)
                delay(1_200)
            } finally {
                handle.stop()
            }
        } finally {
            window.stop()
        }
        assertTrue(Files.size(output) > 1_000, "expected a non-empty $output")
    }
}
