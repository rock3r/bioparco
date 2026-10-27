package dev.sebastiano.bioparco.recordings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.departureboard.App
import dev.sebastiano.departureboard.DepartureBoardTags
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

class DepartureBoardRecordingTest {
    @Test
    @Tag("recording")
    fun recordDepartureBoard(): Unit = runSpectreTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        val output = RecordingPaths.file(RecordingPaths.DEPARTURE_BOARD)
        Files.createDirectories(output.parent)
        val window =
            SpecimenWindow(title = "bioparco · departure board", size = DpSize(1100.dp, 700.dp)) {
                App()
            }
        window.start()
        try {
            val automator = ComposeAutomator.inProcess()
            val board = automator.waitForNode(tag = DepartureBoardTags.BOARD)
            // Let the opening scramble finish off camera: the movie opens on a settled board.
            delay(2_500)
            val handle = AutoRecorder().startWindow(window.frame().asTitledWindow(), output)
            try {
                delay(600)
                automator.click(board)
                delay(2_600)

                // The reference's search: "san" lists the San… cities; one replaces Tokyo.
                automator.click(automator.waitForNode(tag = DepartureBoardTags.SEARCH))
                automator.typeText("san")
                delay(900)
                automator.click(
                    automator.waitForNode(tag = DepartureBoardTags.suggestion("San Francisco"))
                )
                delay(1_800)

                automator.click(automator.waitForNode(tag = DepartureBoardTags.SEARCH))
                automator.typeText("reyk")
                delay(700)
                automator.pressEnter()
                delay(1_800)

                automator.click(automator.waitForNode(tag = DepartureBoardTags.REFRESH))
                delay(2_600)
            } finally {
                handle.stop()
            }
        } finally {
            window.stop()
        }
        assertTrue(Files.size(output) > 1_000, "expected a non-empty $output")
    }
}
