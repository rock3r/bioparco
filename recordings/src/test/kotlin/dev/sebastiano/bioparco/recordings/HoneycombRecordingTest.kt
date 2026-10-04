package dev.sebastiano.bioparco.recordings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.honeycomb.App
import dev.sebastiano.honeycomb.HoneycombTags
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

class HoneycombRecordingTest {
    @Test
    @Tag("recording")
    fun recordHoneycomb(): Unit = runSpectreTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        val output = RecordingPaths.file(RecordingPaths.HONEYCOMB)
        Files.createDirectories(output.parent)
        val window =
            SpecimenWindow(title = "bioparco · honeycomb", size = DpSize(980.dp, 740.dp)) { App() }
        window.start()
        try {
            val automator = ComposeAutomator.inProcess()
            val stage = automator.waitForNode(tag = HoneycombTags.STAGE)
            delay(600)
            val handle = AutoRecorder().startWindow(window.frame().asTitledWindow(), output)
            try {
                val center = stage.centerOnScreen

                suspend fun swipe(dx: Int, dy: Int, millis: Int) {
                    automator.swipe(
                        startX = center.x,
                        startY = center.y,
                        endX = center.x + dx,
                        endY = center.y + dy,
                        steps = 28,
                        duration = millis.milliseconds,
                    )
                    delay(700)
                }

                swipe(-240, -160, 900)
                swipe(280, 30, 800)
                swipe(-30, 220, 900)
                swipe(200, -180, 1_000)
            } finally {
                handle.stop()
            }
        } finally {
            window.stop()
        }
        assertTrue(Files.size(output) > 1_000, "expected a non-empty $output")
    }
}
