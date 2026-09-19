package dev.sebastiano.bioparco.recordings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.processingfield.App
import dev.sebastiano.processingfield.ProcessingFieldTags
import dev.sebastiano.spectre.core.ComposeAutomator
import dev.sebastiano.spectre.recording.AutoRecorder
import dev.sebastiano.spectre.recording.screencapturekit.asTitledWindow
import dev.sebastiano.spectre.testing.runSpectreTest
import java.awt.GraphicsEnvironment
import java.nio.file.Files
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

class ProcessingFieldRecordingTest {
    @Test
    @Tag("recording")
    fun recordProcessingField(): Unit = runSpectreTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        val output = RecordingPaths.file(RecordingPaths.PROCESSING_FIELD)
        Files.createDirectories(output.parent)
        val window =
            SpecimenWindow(title = "bioparco · processing field", size = DpSize(520.dp, 760.dp)) {
                App()
            }
        window.start()
        try {
            val automator = ComposeAutomator.inProcess()
            delay(800)
            val handle = AutoRecorder().startWindow(window.frame().asTitledWindow(), output)
            try {
                automator.waitForNode(tag = ProcessingFieldTags.FIELD)
                delay(HOLD)
                automator.click(automator.waitForNode(tag = ProcessingFieldTags.STYLE_LINES))
                delay(HOLD)
                automator.nudgeSlider(ProcessingFieldTags.REACH, towardEnd = 0.92f)
                delay(HOLD)
                automator.nudgeSlider(ProcessingFieldTags.PITCH, towardEnd = 0.85f)
                delay(HOLD)
                automator.nudgeSlider(ProcessingFieldTags.FOLD_SPEED, towardEnd = 0.90f)
                delay(HOLD)
                automator.click(automator.waitForNode(tag = ProcessingFieldTags.STILL))
                delay(HOLD)
                automator.click(automator.waitForNode(tag = ProcessingFieldTags.RESET))
                delay(5_000)
            } finally {
                handle.stop()
            }
        } finally {
            window.stop()
        }
        assertTrue(Files.size(output) > 1_000, "expected a non-empty $output")
    }
}

private const val HOLD = 6_000L

private suspend fun ComposeAutomator.nudgeSlider(tag: String, towardEnd: Float) {
    val node = waitForNode(tag = tag)
    val bounds = node.boundsOnScreen
    val y = bounds.y + bounds.height / 2
    val startX = bounds.x + bounds.width / 2
    val endX =
        bounds.x + (bounds.width * towardEnd).toInt().coerceAtMost(bounds.x + bounds.width - 4)
    swipe(startX, y, endX, y, steps = 14, duration = 420.milliseconds)
}
