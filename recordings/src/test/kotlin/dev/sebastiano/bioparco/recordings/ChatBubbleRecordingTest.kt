package dev.sebastiano.bioparco.recordings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import chatbubble.ChatApp
import chatbubble.ChatTags
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

class ChatBubbleRecordingTest {
    @Test
    @Tag("recording")
    fun recordChatBubble(): Unit = runSpectreTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        val output = RecordingPaths.file(RecordingPaths.CHAT_BUBBLE)
        Files.createDirectories(output.parent)
        val window =
            SpecimenWindow(
                title = "bioparco · chat bubble transition",
                size = DpSize(420.dp, 760.dp),
            ) {
                ChatApp()
            }
        window.start()
        try {
            val automator = ComposeAutomator.inProcess()
            val handle = AutoRecorder().startWindow(window.frame().asTitledWindow(), output)
            try {
                val composer = automator.waitForNode(tag = ChatTags.COMPOSER)
                automator.click(composer)
                automator.typeText("From the enclosure, with love")
                delay(200)
                automator.click(automator.waitForNode(tag = ChatTags.SEND))
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
