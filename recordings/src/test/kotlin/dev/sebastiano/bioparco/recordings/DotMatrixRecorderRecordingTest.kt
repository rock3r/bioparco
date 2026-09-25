package dev.sebastiano.bioparco.recordings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.dotmatrixrecorder.App
import dev.sebastiano.dotmatrixrecorder.RecorderTags
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
                size = DpSize(520.dp, 520.dp),
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
                val center = pill.centerOnScreen
                // Park the pointer beside the pill, then glide in so the hover reads.
                automator.moveTo(center.x - 160, center.y)
                delay(900)
                automator.moveTo(pill)
                delay(900)
                automator.click(automator.waitForNode(tag = RecorderTags.RECORD))
                // 3, 2, 1, then the menu turns into timer, restart, delete.
                delay(3_900)
                automator.moveTo(center.x - 160, center.y)
                delay(3_000)
                automator.moveTo(automator.waitForNode(tag = RecorderTags.PILL))
                delay(900)
                automator.click(automator.waitForNode(tag = RecorderTags.STOP))
                delay(700)
                automator.moveTo(center.x - 160, center.y)
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
