package dev.sebastiano.bioparco.recordings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.borderbeam.App
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

class BorderBeamRecordingTest {
    @Test
    @Tag("recording")
    fun recordBorderBeam(): Unit = runSpectreTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        val output = RecordingPaths.file(RecordingPaths.BORDER_BEAM)
        Files.createDirectories(output.parent)
        val window =
            SpecimenWindow(title = "bioparco · border beam", size = DpSize(980.dp, 760.dp)) {
                App()
            }
        window.start()
        try {
            delay(800)
            val handle = AutoRecorder().startWindow(window.frame().asTitledWindow(), output)
            try {
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
