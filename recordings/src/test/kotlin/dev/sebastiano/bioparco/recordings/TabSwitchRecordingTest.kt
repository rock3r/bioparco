package dev.sebastiano.bioparco.recordings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.spectre.core.ComposeAutomator
import dev.sebastiano.spectre.recording.AutoRecorder
import dev.sebastiano.spectre.recording.screencapturekit.asTitledWindow
import dev.sebastiano.spectre.testing.runSpectreTest
import dev.sebastiano.tabswitch.App
import dev.sebastiano.tabswitch.TabSwitchTags
import java.awt.GraphicsEnvironment
import java.awt.event.KeyEvent
import java.nio.file.Files
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

class TabSwitchRecordingTest {
    @Test
    @Tag("recording")
    fun recordTabSwitch(): Unit = runSpectreTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        val output = RecordingPaths.file(RecordingPaths.TAB_SWITCH)
        Files.createDirectories(output.parent)
        val window =
            SpecimenWindow(title = "bioparco · tab switch", size = DpSize(560.dp, 320.dp)) { App() }
        window.start()
        try {
            val automator = ComposeAutomator.inProcess()
            automator.waitForNode(tag = TabSwitchTags.BAR)

            /** Waits for the selected tab to be [label], so the script checks what it drove. */
            suspend fun expect(label: String) =
                automator.waitUntil(description = "$label is selected", timeout = 5.seconds) {
                    allNodes().any { it.testTag == TabSwitchTags.SELECTED && it.text == label }
                }

            /** Rests the pointer on [label]'s tab, which leans the pill, then clicks it. */
            suspend fun pick(label: String, hover: Long = 450) {
                val tab = automator.waitForNode(tag = TabSwitchTags.tab(label))
                automator.moveTo(tab)
                delay(hover)
                automator.click(tab)
                expect(label)
            }

            val handle = AutoRecorder().startWindow(window.frame().asTitledWindow(), output)
            try {
                expect("Home")
                delay(900)
                // The video's run: each tab in turn, the pointer resting on it before the click.
                pick("Saved")
                delay(900)
                pick("Inbox")
                delay(900)
                pick("Profile")
                delay(1_000)
                // A long jump back: the pill draws itself into a ball and rolls home.
                pick("Home", hover = 600)
                delay(1_100)
                // The keyboard steps a tab at a time; a quick double step retargets mid-flight.
                automator.pressKey(KeyEvent.VK_RIGHT)
                expect("Saved")
                delay(700)
                automator.pressKey(KeyEvent.VK_RIGHT)
                delay(120)
                automator.pressKey(KeyEvent.VK_RIGHT)
                expect("Profile")
                delay(900)
                automator.pressKey(KeyEvent.VK_1)
                expect("Home")
                delay(1_200)
            } finally {
                handle.stop()
            }
        } finally {
            window.stop()
        }
        assertTrue(Files.size(output) > 1_000, "expected a non-empty $output")
    }
}
