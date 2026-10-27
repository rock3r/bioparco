package dev.sebastiano.bioparco.recordings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.currencyswarm.App
import dev.sebastiano.currencyswarm.SwarmTags
import dev.sebastiano.spectre.core.ComposeAutomator
import dev.sebastiano.spectre.recording.AutoRecorder
import dev.sebastiano.spectre.recording.screencapturekit.asTitledWindow
import dev.sebastiano.spectre.testing.runSpectreTest
import java.awt.GraphicsEnvironment
import java.awt.event.KeyEvent
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

class CurrencySwarmRecordingTest {
    @Test
    @Tag("recording")
    fun recordCurrencySwarm(): Unit = runSpectreTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        val output = RecordingPaths.file(RecordingPaths.CURRENCY_SWARM)
        Files.createDirectories(output.parent)
        val window =
            SpecimenWindow(title = "bioparco · currency swarm", size = DpSize(560.dp, 680.dp)) {
                App()
            }
        window.start()
        try {
            // Record from the start: the cloud condensing into the first sign is the opening shot.
            val handle = AutoRecorder().startWindow(window.frame().asTitledWindow(), output)
            try {
                val automator = ComposeAutomator.inProcess()
                val stage = automator.waitForNode(tag = SwarmTags.STAGE)

                /** Waits for the caption to name [currency], so the script checks what it drove. */
                suspend fun expect(currency: String) =
                    automator.waitUntil(
                        description = "the caption names $currency",
                        timeout = 4.seconds,
                    ) {
                        allNodes().any { it.testTag == SwarmTags.NAME && it.text == currency }
                    }

                val bounds = stage.boundsOnScreen
                val centreX = bounds.x + bounds.width / 2
                val centreY = bounds.y + bounds.height / 2

                /**
                 * Glides the pointer along [path] (0 to 1 → offset from the stage's centre) over
                 * [millis], one move every 8 ms, as a 125 Hz mouse sends them: the dots only part
                 * as fast as the pointer reports.
                 */
                suspend fun glide(millis: Int, path: (Float) -> Pair<Float, Float>) {
                    val steps = millis / 8
                    for (step in 0..steps) {
                        val (dx, dy) = path(step / steps.toFloat())
                        automator.moveTo(centreX + dx.toInt(), centreY + dy.toInt())
                        delay(8)
                    }
                }

                /**
                 * Parks the pointer in the stage's corner, clear of the dots. The sign waits while
                 * the pointer is over the stage, so the next change is the script's, however slow
                 * the machine: without this, the sign moves on by itself 2.85 s after it set off.
                 */
                suspend fun park() = automator.moveTo(bounds.x + 12, bounds.y + 12)

                expect("Indian rupee")
                park()
                delay(2_000)
                // A slow pass through the sign: the dots part around the pointer and close behind.
                // The sign waits while the pointer is over it.
                glide(2_600) { t ->
                    (-240f + 480f * t) to (-70f + 140f * t + 50f * sin(t * 2f * PI.toFloat()))
                }
                delay(600)
                // Off the dots, the sign heals, then moves on by itself.
                automator.moveTo(bounds.x + bounds.width / 2, bounds.y + bounds.height + 140)
                expect("US dollar")
                park()
                delay(2_000)

                // A click sends the dots on; an arrow key half a second later turns them
                // mid-flight.
                automator.click(stage)
                expect("South Korean won")
                delay(500)
                automator.pressKey(KeyEvent.VK_RIGHT)
                expect("Japanese yen")
                delay(2_200)
                // Round and round the yen.
                glide(2_000) { t ->
                    val angle = t * 2f * PI.toFloat()
                    80f * cos(angle) to 70f * sin(angle)
                }
                delay(500)

                // Back to the rupee from its chip, then one step back with ←.
                automator.click(automator.waitForNode(tag = SwarmTags.chip("INR")))
                expect("Indian rupee")
                park()
                delay(2_000)
                automator.pressKey(KeyEvent.VK_LEFT)
                expect("Japanese yen")
                delay(2_000)
            } finally {
                handle.stop()
            }
        } finally {
            window.stop()
        }
        assertTrue(Files.size(output) > 1_000, "expected a non-empty $output")
    }
}
