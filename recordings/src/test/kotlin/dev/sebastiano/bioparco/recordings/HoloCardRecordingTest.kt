package dev.sebastiano.bioparco.recordings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.holocard.App
import dev.sebastiano.holocard.HoloCardTags
import dev.sebastiano.spectre.core.ComposeAutomator
import dev.sebastiano.spectre.recording.AutoRecorder
import dev.sebastiano.spectre.recording.screencapturekit.asTitledWindow
import dev.sebastiano.spectre.testing.runSpectreTest
import java.awt.GraphicsEnvironment
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

class HoloCardRecordingTest {
    @Test
    @Tag("recording")
    fun recordHoloCard(): Unit = runSpectreTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        val output = RecordingPaths.file(RecordingPaths.HOLO_CARD)
        Files.createDirectories(output.parent)
        val window =
            SpecimenWindow(title = "bioparco · holo card", size = DpSize(980.dp, 740.dp)) { App() }
        window.start()
        try {
            val automator = ComposeAutomator.inProcess()
            // The card lies flat until something moves it, so its bounds are its resting spot.
            val card = automator.waitForNode(tag = HoloCardTags.CARD).boundsOnScreen
            // The mascots' normal maps bake on a background thread.
            delay(900)
            val handle = AutoRecorder().startWindow(window.frame().asTitledWindow(), output)
            try {
                val cx = card.x + card.width / 2
                val cy = card.y + card.height / 2

                /** Circles the pointer over the card, [turns] times, so it tips towards it. */
                suspend fun circle(turns: Float, millis: Long) {
                    val steps = (turns * 72).toInt()
                    for (step in 0..steps) {
                        val angle = step / 72f * 2f * PI.toFloat() - PI.toFloat() / 2f
                        automator.moveTo(
                            cx + (cos(angle) * card.width * 0.42f).toInt(),
                            cy + (sin(angle) * card.height * 0.42f).toInt(),
                        )
                        delay(millis / steps)
                    }
                }

                suspend fun pick(index: Int) {
                    automator.click(automator.waitForNode(tag = HoloCardTags.thumbnail(index)))
                    delay(1_200)
                }

                delay(500)
                circle(turns = 1.25f, millis = 2_600)
                // Off the card: it springs back flat with a wobble.
                automator.moveTo(card.x + card.width + 180, card.y + 40)
                delay(800)
                // Grab it and turn it hard, then let go.
                automator.swipe(
                    startX = cx,
                    startY = cy,
                    endX = cx + card.width / 2,
                    endY = cy - card.height / 4,
                    steps = 24,
                    duration = 700.milliseconds,
                )
                delay(1_000)
                pick(1)
                circle(turns = 0.75f, millis = 1_600)
                pick(2)
                circle(turns = 0.5f, millis = 1_000)
                // The hands-off demo, watched from the dock.
                val autopilot = automator.waitForNode(tag = HoloCardTags.AUTOPILOT)
                automator.click(autopilot)
                delay(3_400)
                automator.click(autopilot)
                delay(600)
                pick(0)
            } finally {
                handle.stop()
            }
        } finally {
            window.stop()
        }
        assertTrue(Files.size(output) > 1_000, "expected a non-empty $output")
    }
}
