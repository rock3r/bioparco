package dev.sebastiano.bioparco.recordings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.expandingsearch.App
import dev.sebastiano.expandingsearch.ExpandingSearchTags
import dev.sebastiano.spectre.core.ComposeAutomator
import dev.sebastiano.spectre.recording.AutoRecorder
import dev.sebastiano.spectre.recording.screencapturekit.asTitledWindow
import dev.sebastiano.spectre.testing.runSpectreTest
import java.awt.GraphicsEnvironment
import java.awt.event.KeyEvent
import java.nio.file.Files
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

class ExpandingSearchRecordingTest {
    @Test
    @Tag("recording")
    fun recordExpandingSearch(): Unit = runSpectreTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        val output = RecordingPaths.file(RecordingPaths.EXPANDING_SEARCH)
        Files.createDirectories(output.parent)
        val window =
            SpecimenWindow(title = "bioparco · expanding search", size = DpSize(720.dp, 560.dp)) {
                App()
            }
        window.start()
        try {
            val automator = ComposeAutomator.inProcess()
            automator.waitForNode(tag = ExpandingSearchTags.STAGE)
            val open = automator.waitForNode(tag = ExpandingSearchTags.OPEN)
            val handle = AutoRecorder().startWindow(window.frame().asTitledWindow(), output)
            try {
                delay(900)
                // The video's run: open, run the pointer down the suggestions, type "to", clear,
                // close. Then a second search, picked with the keyboard.
                automator.click(open)
                delay(1_000)
                for (name in listOf("Toast", "Code Input", "Segmented Control")) {
                    automator.moveTo(automator.waitForNode(tag = ExpandingSearchTags.row(name)))
                    delay(450)
                }
                // Off the rows, the highlight goes back to the first.
                automator.moveTo(automator.waitForNode(tag = ExpandingSearchTags.FIELD))
                delay(700)
                automator.typeText("t")
                delay(800)
                automator.typeText("o")
                delay(1_200)
                val clear = automator.waitForNode(tag = ExpandingSearchTags.CLEAR)
                automator.click(clear)
                delay(900)

                automator.typeText("in")
                delay(1_000)
                automator.pressKey(KeyEvent.VK_DOWN)
                delay(450)
                automator.pressKey(KeyEvent.VK_DOWN)
                delay(600)
                automator.pressEnter()
                delay(1_100)
                automator.click(clear)
                delay(800)
                // Empty now, so the cross folds it all away.
                automator.click(clear)
                delay(1_400)
            } finally {
                handle.stop()
            }
        } finally {
            window.stop()
        }
        assertTrue(Files.size(output) > 1_000, "expected a non-empty $output")
    }
}
