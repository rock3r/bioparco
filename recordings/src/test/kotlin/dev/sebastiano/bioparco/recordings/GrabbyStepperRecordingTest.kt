package dev.sebastiano.bioparco.recordings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.grabbystepper.App
import dev.sebastiano.grabbystepper.GrabbyTags
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

class GrabbyStepperRecordingTest {
    @Test
    @Tag("recording")
    fun recordGrabbyStepper(): Unit = runSpectreTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        val output = RecordingPaths.file(RecordingPaths.GRABBY_STEPPER)
        Files.createDirectories(output.parent)
        val window =
            SpecimenWindow(title = "bioparco · grabby stepper", size = DpSize(520.dp, 640.dp)) {
                App()
            }
        window.start()
        try {
            val automator = ComposeAutomator.inProcess()
            val handle = AutoRecorder().startWindow(window.frame().asTitledWindow(), output)
            try {
                val stepper = automator.waitForNode(tag = GrabbyTags.STEPPER)
                val center = stepper.centerOnScreen
                automator.swipe(
                    startX = center.x,
                    startY = center.y,
                    endX = center.x + 92,
                    endY = center.y,
                    steps = 14,
                    duration = 480.milliseconds,
                )
                delay(700)
                automator.swipe(
                    startX = center.x,
                    startY = center.y,
                    endX = center.x,
                    endY = center.y + 150,
                    steps = 16,
                    duration = 520.milliseconds,
                )
                delay(900)
            } finally {
                handle.stop()
            }
        } finally {
            window.stop()
        }
        assertTrue(Files.size(output) > 1_000, "expected a non-empty $output")
    }
}
