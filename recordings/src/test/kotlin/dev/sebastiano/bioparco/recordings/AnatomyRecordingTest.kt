package dev.sebastiano.bioparco.recordings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.anatomy.AnatomyTags
import dev.sebastiano.anatomy.App
import dev.sebastiano.anatomy.Figure
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

class AnatomyRecordingTest {
    @Test
    @Tag("recording")
    fun recordAnatomy(): Unit = runSpectreTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        val output = RecordingPaths.file(RecordingPaths.ANATOMY)
        Files.createDirectories(output.parent)
        val window =
            SpecimenWindow(title = "bioparco · anatomy", size = DpSize(760.dp, 820.dp)) { App() }
        window.start()
        try {
            val automator = ComposeAutomator.inProcess()
            automator.waitForNode(tag = AnatomyTags.figure(Figure.DialIndicator))
            delay(800)
            val handle = AutoRecorder().startWindow(window.frame().asTitledWindow(), output)
            try {
                /** Glides the pointer across the dial indicator's card, in fractions of it. */
                suspend fun glide(
                    vararg points: Pair<Float, Float>,
                    steps: Int = 24,
                    ms: Long = 40,
                ) {
                    val b =
                        automator
                            .waitForNode(tag = AnatomyTags.figure(Figure.DialIndicator))
                            .boundsOnScreen
                    fun at(p: Pair<Float, Float>) =
                        (b.x + p.first * b.width).toInt() to (b.y + p.second * b.height).toInt()
                    for (k in 1 until points.size) {
                        val (x0, y0) = at(points[k - 1])
                        val (x1, y1) = at(points[k])
                        for (s in 0..steps) {
                            automator.moveTo(x0 + (x1 - x0) * s / steps, y0 + (y1 - y0) * s / steps)
                            delay(ms)
                        }
                    }
                }

                // The idle tour: block to block on its own.
                delay(4_600)
                // The pointer slides the carriage: across and back, then it rests on block 2.
                glide(0.75f to 0.4f, 0.25f to 0.4f, steps = 30, ms = 45)
                glide(0.25f to 0.4f, 0.5f to 0.4f, steps = 20, ms = 45)
                delay(1_200)
                // The dark theme, and the tour taking over again once the pointer is still.
                automator.click(automator.waitForNode(text = "Dark"))
                delay(4_800)
            } finally {
                handle.stop()
            }
        } finally {
            window.stop()
        }
        assertTrue(Files.size(output) > 1_000, "expected a non-empty $output")
    }
}
