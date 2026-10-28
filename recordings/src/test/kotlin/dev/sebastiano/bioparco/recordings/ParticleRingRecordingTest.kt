package dev.sebastiano.bioparco.recordings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.particlering.App
import dev.sebastiano.particlering.RingTags
import dev.sebastiano.spectre.core.ComposeAutomator
import dev.sebastiano.spectre.recording.AutoRecorder
import dev.sebastiano.spectre.recording.screencapturekit.asTitledWindow
import dev.sebastiano.spectre.testing.runSpectreTest
import java.awt.GraphicsEnvironment
import java.awt.event.KeyEvent
import java.nio.file.Files
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

class ParticleRingRecordingTest {
    @Test
    @Tag("recording")
    fun recordParticleRing(): Unit = runSpectreTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        val output = RecordingPaths.file(RecordingPaths.PARTICLE_RING)
        Files.createDirectories(output.parent)
        val window =
            SpecimenWindow(title = "bioparco · particle ring", size = DpSize(560.dp, 680.dp)) {
                App()
            }
        window.start()
        try {
            val handle = AutoRecorder().startWindow(window.frame().asTitledWindow(), output)
            try {
                val automator = ComposeAutomator.inProcess()
                val stage = automator.waitForNode(tag = RingTags.STAGE)

                /** Waits for the caption to read [caption], so the script checks what it drove. */
                suspend fun expect(caption: String) =
                    automator.waitUntil(
                        description = "the caption reads $caption",
                        timeout = 10.seconds,
                    ) {
                        allNodes().any { it.testTag == RingTags.CAPTION && it.text == caption }
                    }

                // Where the ring's near side is: a ring radius is 27% of the stage's width or a
                // fifth of its height, and the ring's centre is two fifths of the way down.
                val bounds = stage.boundsOnScreen
                val unit = minOf(bounds.width * 0.27f, bounds.height * 0.2f)
                val ringX = bounds.x + bounds.width / 2
                val ringY = (bounds.y + bounds.height * 0.4f + unit * 0.45f).toInt()

                expect("Ready to read your Health data")
                delay(1_500)
                // The icon bursts into the ring, which starts reading.
                automator.click(automator.waitForNode(tag = RingTags.ICON))
                expect("Reading your workouts")
                delay(700)
                // A flick along the near side stirs the ring, which coasts to its own pace.
                automator.swipe(
                    startX = ringX - (unit * 0.8f).toInt(),
                    startY = ringY,
                    endX = ringX + (unit * 0.8f).toInt(),
                    endY = ringY,
                    steps = 30,
                    duration = 480.milliseconds,
                )
                delay(1_100)
                // Pulled down, the ring leans towards the viewer, and rocks back when let go.
                automator.swipe(
                    startX = ringX,
                    startY = ringY,
                    endX = ringX,
                    endY = ringY + (unit * 0.9f).toInt(),
                    steps = 30,
                    duration = 700.milliseconds,
                )
                expect("Reading your heart rate")
                // Then it folds into the logo by itself.
                expect("Your highlights are ready")
                delay(1_800)

                // A click on the logo plays it again; Space starts it, and Space folds it early.
                automator.click(automator.waitForNode(tag = RingTags.LOGO))
                expect("Ready to read your Health data")
                delay(1_000)
                automator.pressKey(KeyEvent.VK_SPACE)
                expect("Reading your workouts")
                delay(1_200)
                automator.pressKey(KeyEvent.VK_SPACE)
                expect("Your highlights are ready")
                delay(1_800)
            } finally {
                handle.stop()
            }
        } finally {
            window.stop()
        }
        assertTrue(Files.size(output) > 1_000, "expected a non-empty $output")
    }
}
