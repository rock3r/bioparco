package dev.sebastiano.bioparco.recordings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.dotmatrixrecorder.App
import dev.sebastiano.dotmatrixrecorder.RecorderTags
import dev.sebastiano.spectre.core.AutomatorNode
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

class DotMatrixRecorderRecordingTest {
    @Test
    @Tag("recording")
    fun recordDotMatrixRecorder(): Unit = runSpectreTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        val output = RecordingPaths.file(RecordingPaths.DOT_MATRIX_RECORDER)
        Files.createDirectories(output.parent)
        val window =
            SpecimenWindow(
                title = "bioparco · dot-matrix recorder",
                // Just enough room for the tallest face (the recording menu) and the hint.
                size = DpSize(360.dp, 280.dp),
            ) {
                App()
            }
        window.start()
        try {
            val automator = ComposeAutomator.inProcess()
            delay(800)
            val handle = AutoRecorder().startWindow(window.frame().asTitledWindow(), output)
            try {
                val pill = automator.waitForNode(tag = RecorderTags.PILL)
                // Park the pointer in the gap left of the pill, then glide in so the hover reads.
                automator.parkBeside(pill)
                delay(900)
                automator.moveTo(pill)
                delay(900)
                automator.click(automator.waitForNode(tag = RecorderTags.RECORD))
                // 3, 2, 1, then the menu turns into timer, restart, delete.
                delay(3_900)
                automator.parkBeside(automator.waitForNode(tag = RecorderTags.PILL))
                delay(3_000)
                automator.moveTo(automator.waitForNode(tag = RecorderTags.PILL))
                delay(900)
                automator.click(automator.waitForNode(tag = RecorderTags.STOP))
                delay(700)
                automator.parkBeside(automator.waitForNode(tag = RecorderTags.PILL))
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

/** Moves the pointer just outside the left edge of [node], wherever the pill has grown to. */
private suspend fun ComposeAutomator.parkBeside(node: AutomatorNode) {
    val bounds = node.boundsOnScreen
    moveTo(bounds.x - 24, bounds.y + bounds.height / 2)
}
