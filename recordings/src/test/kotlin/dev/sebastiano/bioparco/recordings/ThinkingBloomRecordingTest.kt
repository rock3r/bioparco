package dev.sebastiano.bioparco.recordings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.spectre.core.ComposeAutomator
import dev.sebastiano.spectre.recording.AutoRecorder
import dev.sebastiano.spectre.recording.screencapturekit.asTitledWindow
import dev.sebastiano.spectre.testing.runSpectreTest
import dev.sebastiano.thinkingbloom.App
import dev.sebastiano.thinkingbloom.ThinkingBloomTags
import java.awt.GraphicsEnvironment
import java.nio.file.Files
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

class ThinkingBloomRecordingTest {
    @Test
    @Tag("recording")
    fun recordThinkingBloom(): Unit = runSpectreTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        val output = RecordingPaths.file(RecordingPaths.THINKING_BLOOM)
        Files.createDirectories(output.parent)
        val window =
            SpecimenWindow(title = "bioparco · thinking bloom", size = DpSize(960.dp, 600.dp)) {
                App()
            }
        window.start()
        try {
            val automator = ComposeAutomator.inProcess()
            automator.waitForNode(tag = ThinkingBloomTags.STAGE)
            delay(600)
            val handle = AutoRecorder().startWindow(window.frame().asTitledWindow(), output)
            try {
                // Two full loops of the bloom, rest included.
                delay(6_000)
            } finally {
                handle.stop()
            }
        } finally {
            window.stop()
        }
        assertTrue(Files.size(output) > 1_000, "expected a non-empty $output")
    }
}
