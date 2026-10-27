package dev.sebastiano.bioparco.recordings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.spectre.core.ComposeAutomator
import dev.sebastiano.spectre.recording.AutoRecorder
import dev.sebastiano.spectre.recording.screencapturekit.asTitledWindow
import dev.sebastiano.spectre.testing.runSpectreTest
import dev.sebastiano.squeezeprogress.App
import dev.sebastiano.squeezeprogress.SqueezeProgressTags
import java.awt.GraphicsEnvironment
import java.nio.file.Files
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

class SqueezeProgressRecordingTest {
    @Test
    @Tag("recording")
    fun recordSqueezeProgress(): Unit = runSpectreTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        val output = RecordingPaths.file(RecordingPaths.SQUEEZE_PROGRESS)
        Files.createDirectories(output.parent)
        val window =
            SpecimenWindow(title = "bioparco · squeeze progress", size = DpSize(800.dp, 600.dp)) {
                App()
            }
        window.start()
        try {
            val automator = ComposeAutomator.inProcess()
            automator.waitForNode(tag = SqueezeProgressTags.STAGE)
            val toggle = automator.waitForNode(tag = SqueezeProgressTags.TOGGLE)
            val handle = AutoRecorder().startWindow(window.frame().asTitledWindow(), output)
            try {
                // A fill takes eight seconds: pause near a third, again near three quarters, then
                // let it fill, hold and rewind.
                delay(2_400)
                automator.click(toggle)
                delay(1_600)
                automator.click(toggle)
                delay(3_400)
                automator.click(toggle)
                delay(1_500)
                automator.click(toggle)
                delay(4_200)
            } finally {
                handle.stop()
            }
        } finally {
            window.stop()
        }
        assertTrue(Files.size(output) > 1_000, "expected a non-empty $output")
    }
}
