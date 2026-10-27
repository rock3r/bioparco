package dev.sebastiano.bioparco.recordings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.spectre.core.ComposeAutomator
import dev.sebastiano.spectre.recording.AutoRecorder
import dev.sebastiano.spectre.recording.screencapturekit.asTitledWindow
import dev.sebastiano.spectre.testing.runSpectreTest
import dev.sebastiano.wheelpicker.App
import dev.sebastiano.wheelpicker.WheelTags
import java.awt.GraphicsEnvironment
import java.awt.event.KeyEvent
import java.nio.file.Files
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

class WheelPickerRecordingTest {
    @Test
    @Tag("recording")
    fun recordWheelPicker(): Unit = runSpectreTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        val output = RecordingPaths.file(RecordingPaths.WHEEL_PICKER)
        Files.createDirectories(output.parent)
        val window =
            SpecimenWindow(title = "bioparco · wheel picker", size = DpSize(920.dp, 560.dp)) {
                App()
            }
        window.start()
        try {
            val automator = ComposeAutomator.inProcess()
            val ruler = automator.waitForNode(tag = WheelTags.RULER)

            /** Waits for the readout to settle on [text], so the script checks what it drove. */
            suspend fun expect(text: String) =
                automator.waitUntil(description = "the readout shows $text", timeout = 4.seconds) {
                    allNodes().any { it.testTag == WheelTags.READOUT && it.text == text }
                }

            expect("20m")
            delay(600)
            val handle = AutoRecorder().startWindow(window.frame().asTitledWindow(), output)
            try {
                val bounds = ruler.boundsOnScreen
                val centreX = bounds.x + bounds.width / 2
                val y = bounds.y + bounds.height / 2
                // The ruler is 860dp wide; one stop is 40dp.
                val pitch = bounds.width * 40f / 860f
                val radius = bounds.width / 2f

                // A pointer event every 8 ms, as a 125 Hz mouse sends them: a drag only redraws
                // when the pointer moves, so a slower script would cap the frame rate itself.
                suspend fun drag(fromDx: Int, toDx: Int, millis: Int, steps: Int = millis / 8) {
                    automator.swipe(
                        startX = centreX + fromDx,
                        startY = y,
                        endX = centreX + toDx,
                        endY = y,
                        steps = steps,
                        duration = millis.milliseconds,
                    )
                }

                suspend fun key(code: Int) {
                    automator.pressKey(code)
                    delay(380)
                }

                delay(700)
                // A slow drag: the ticks squeeze and split as they near the rim, the readout rolls.
                drag(fromDx = 240, toDx = -240, millis = 1_900)
                delay(1_000)
                // Flicks: the fringe flares with speed and the ruler coasts to a stop.
                drag(fromDx = 160, toDx = -220, millis = 150)
                delay(1_500)
                drag(fromDx = -160, toDx = 260, millis = 150)
                delay(1_500)

                key(KeyEvent.VK_HOME)
                expect("∞")
                delay(700)
                // A click four stops to the right goes there: the lens is undone to find the tick.
                val four = (radius * sin(4f * pitch / radius)).toInt()
                // Spectre clicks nodes, not points; a swipe that goes nowhere is a click there.
                drag(fromDx = four, toDx = four, millis = 90, steps = 2)
                expect("20m")
                delay(900)
                key(KeyEvent.VK_RIGHT)
                key(KeyEvent.VK_RIGHT)
                expect("30m")
                repeat(3) {
                    automator.scrollWheel(ruler, 1)
                    delay(320)
                }
                expect("45m")
                delay(700)

                // Past the end, the ruler gives a little and springs back.
                key(KeyEvent.VK_END)
                expect("24h")
                delay(700)
                drag(fromDx = 120, toDx = -260, millis = 900)
                expect("24h")
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
