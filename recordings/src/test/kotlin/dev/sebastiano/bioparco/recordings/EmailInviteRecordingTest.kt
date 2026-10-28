package dev.sebastiano.bioparco.recordings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.emailinvite.App
import dev.sebastiano.emailinvite.EmailInviteTags
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

class EmailInviteRecordingTest {
    @Test
    @Tag("recording")
    fun recordEmailInvite(): Unit = runSpectreTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        val output = RecordingPaths.file(RecordingPaths.EMAIL_INVITE)
        Files.createDirectories(output.parent)
        val window =
            SpecimenWindow(title = "bioparco · email invite", size = DpSize(800.dp, 600.dp)) {
                App()
            }
        window.start()
        try {
            val automator = ComposeAutomator.inProcess()
            automator.waitForNode(tag = EmailInviteTags.STAGE)
            val invite = automator.waitForNode(tag = EmailInviteTags.INVITE)
            val handle = AutoRecorder().startWindow(window.frame().asTitledWindow(), output)
            try {
                // The video's take: hover, click Invite, type the address, click Send.
                delay(900)
                automator.moveTo(invite)
                delay(600)
                automator.click(invite)
                delay(900)
                type(automator, "new@gmail.com")
                delay(700)
                automator.click(automator.waitForNode(tag = EmailInviteTags.SEND))
                delay(2_200)
                // Again from the keyboard: Enter opens, Enter sends. The stack is full at four,
                // so L leaves from the back.
                automator.moveBy(0, 160)
                automator.pressEnter()
                delay(700)
                type(automator, "ada@bioparco.dev")
                delay(500)
                automator.pressEnter()
                delay(2_200)
                // Escape closes it without sending.
                automator.pressEnter()
                delay(700)
                type(automator, "oops")
                delay(500)
                automator.pressKey(KeyEvent.VK_ESCAPE)
                delay(1_400)
            } finally {
                handle.stop()
            }
        } finally {
            window.stop()
        }
        assertTrue(Files.size(output) > 1_000, "expected a non-empty $output")
    }

    /** Types like a person, a key at a time, so the caret and Send's arming read on camera. */
    private suspend fun type(automator: ComposeAutomator, text: String) {
        for (character in text) {
            automator.typeText(character.toString())
            delay(KEY_GAP_MILLIS)
        }
    }

    private companion object {
        const val KEY_GAP_MILLIS = 85L
    }
}
