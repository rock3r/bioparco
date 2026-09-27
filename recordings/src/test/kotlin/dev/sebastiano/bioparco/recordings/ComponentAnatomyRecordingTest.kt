package dev.sebastiano.bioparco.recordings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.componentanatomy.AnatomyTags
import dev.sebastiano.componentanatomy.App
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

class ComponentAnatomyRecordingTest {
    @Test
    @Tag("recording")
    fun recordComponentAnatomy(): Unit = runSpectreTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        val output = RecordingPaths.file(RecordingPaths.COMPONENT_ANATOMY)
        Files.createDirectories(output.parent)
        val window =
            SpecimenWindow(title = "bioparco · component anatomy", size = DpSize(1000.dp, 720.dp)) {
                App()
            }
        window.start()
        try {
            val automator = ComposeAutomator.inProcess()
            val outlined = automator.waitForNode(tag = AnatomyTags.OUTLINED)
            // The beat clock starts with the window, at 120 BPM: one bar every 2 s. Music stays
            // off; the recording has no sound track.
            val handle = AutoRecorder().startWindow(window.frame().asTitledWindow(), output)
            try {
                // Rest, tilt and explode, then Hovered, Pressed and into Focused.
                delay(9_200)
                // Take over: the stage stops following the beat and offers to go back.
                automator.click(outlined)
                delay(2_600)
                automator.click(automator.waitForNode(tag = AnatomyTags.BACK_TO_GROOVIN))
                delay(4_400)
            } finally {
                handle.stop()
            }
        } finally {
            window.stop()
        }
        assertTrue(Files.size(output) > 1_000, "expected a non-empty $output")
    }
}
