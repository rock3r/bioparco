package dev.sebastiano.bioparco.recordings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.spectre.core.ComposeAutomator
import dev.sebastiano.spectre.recording.AutoRecorder
import dev.sebastiano.spectre.recording.screencapturekit.asTitledWindow
import dev.sebastiano.spectre.testing.runSpectreTest
import dev.sebastiano.stampbook.App
import dev.sebastiano.stampbook.StampBookTags
import java.awt.GraphicsEnvironment
import java.nio.file.Files
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

class StampBookRecordingTest {
    @Test
    @Tag("recording")
    fun recordStampBook(): Unit = runSpectreTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        val output = RecordingPaths.file(RecordingPaths.STAMP_BOOK)
        Files.createDirectories(output.parent)
        val window =
            SpecimenWindow(title = "bioparco · stamp book", size = DpSize(800.dp, 600.dp)) { App() }
        window.start()
        try {
            val automator = ComposeAutomator.inProcess()
            // The book is shut until something moves it, so these bounds are its resting spots.
            val book = automator.waitForNode(tag = StampBookTags.FOLDER).boundsOnScreen
            val swatches = List(3) { automator.waitForNode(tag = StampBookTags.swatch(it)) }
            val bookX = book.x + book.width / 2
            val bookY = book.y + book.height / 2
            // Off to the side, clear of the spread the open book makes.
            val awayX = book.x - 120
            val awayY = book.y + book.height / 3

            /** Glides the pointer to (x, y) in small steps, as a hand would. */
            suspend fun glide(fromX: Int, fromY: Int, toX: Int, toY: Int, millis: Long) {
                val steps = 24
                for (step in 1..steps) {
                    automator.moveTo(
                        fromX + (toX - fromX) * step / steps,
                        fromY + (toY - fromY) * step / steps,
                    )
                    delay(millis / steps)
                }
            }

            /** Hovers the book open, drifts over the bird stamp, then leaves so it shuts. */
            suspend fun browse() {
                glide(awayX, awayY, bookX, bookY, 450)
                delay(1_100)
                glide(bookX, bookY, bookX + book.width * 6 / 5, bookY - book.height / 8, 600)
                delay(900)
                glide(bookX + book.width * 6 / 5, bookY - book.height / 8, awayX, awayY, 500)
                delay(900)
            }

            automator.moveTo(awayX, awayY)
            // The stamps bake on a background thread.
            delay(800)
            val handle = AutoRecorder().startWindow(window.frame().asTitledWindow(), output)
            try {
                delay(600)
                browse()
                automator.click(swatches[1])
                delay(700)
                browse()
                automator.click(swatches[2])
                delay(700)
                browse()
                automator.click(swatches[0])
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
