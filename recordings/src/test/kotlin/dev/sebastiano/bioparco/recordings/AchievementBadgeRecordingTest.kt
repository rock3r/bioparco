package dev.sebastiano.bioparco.recordings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.achievementbadge.App
import dev.sebastiano.achievementbadge.BadgeTags
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

class AchievementBadgeRecordingTest {
    @Test
    @Tag("recording")
    fun recordAchievementBadge(): Unit = runSpectreTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        val output = RecordingPaths.file(RecordingPaths.ACHIEVEMENT_BADGE)
        Files.createDirectories(output.parent)
        val window =
            SpecimenWindow(title = "bioparco · achievement badge", size = DpSize(760.dp, 820.dp)) {
                App()
            }
        window.start()
        try {
            val automator = ComposeAutomator.inProcess()
            val replay = automator.waitForNode(tag = BadgeTags.REPLAY)
            // The first celebration plays while the window opens. Let it finish, then replay it
            // on camera from a resting badge.
            delay(2_800)
            val handle = AutoRecorder().startWindow(window.frame().asTitledWindow(), output)
            try {
                delay(400)
                automator.click(replay)
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
