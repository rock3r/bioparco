package dev.sebastiano.bioparco.recordings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.routinetimeline.App
import dev.sebastiano.routinetimeline.RoutineTimelineTags
import dev.sebastiano.spectre.core.ComposeAutomator
import dev.sebastiano.spectre.recording.AutoRecorder
import dev.sebastiano.spectre.recording.screencapturekit.asTitledWindow
import dev.sebastiano.spectre.testing.runSpectreTest
import java.awt.GraphicsEnvironment
import java.awt.event.KeyEvent
import java.nio.file.Files
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

class RoutineTimelineRecordingTest {
    @Test
    @Tag("recording")
    fun recordRoutineTimeline(): Unit = runSpectreTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        val output = RecordingPaths.file(RecordingPaths.ROUTINE_TIMELINE)
        Files.createDirectories(output.parent)
        val window =
            SpecimenWindow(title = "bioparco · routine timeline", size = DpSize(620.dp, 580.dp)) {
                // Now is frozen at 9:52 AM, where the source video starts, so every run matches.
                App(fixedNow = 9 * 60 + 52)
            }
        window.start()
        try {
            val automator = ComposeAutomator.inProcess()
            val dial = automator.waitForNode(tag = RoutineTimelineTags.DIAL)
            val now = automator.waitForNode(tag = RoutineTimelineTags.NOW)
            delay(600)
            val handle = AutoRecorder().startWindow(window.frame().asTitledWindow(), output)
            try {
                val bounds = dial.boundsOnScreen
                // The card is 510dp wide, with the dial's centre 86dp in and 262dp down.
                val dp = bounds.width / 510f
                val centreX = bounds.x + 86 * dp
                val centreY = bounds.y + 262 * dp

                /**
                 * A straight drag at [dx]dp right of the dial's centre, from [fromDy] to [toDy]dp
                 * below it. Upwards turns the dial anticlockwise, which runs the time on. A pointer
                 * event every 8 ms, as a 125 Hz mouse sends them, so the script never caps the
                 * frame rate itself.
                 */
                suspend fun drag(dx: Int, fromDy: Int, toDy: Int, millis: Int) {
                    automator.swipe(
                        startX = (centreX + dx * dp).roundToInt(),
                        startY = (centreY + fromDy * dp).roundToInt(),
                        endX = (centreX + dx * dp).roundToInt(),
                        endY = (centreY + toDy * dp).roundToInt(),
                        steps = millis / 8,
                        duration = millis.milliseconds,
                    )
                }

                delay(900)
                // A slow turn through the morning: caffeine lights up, the list moves on.
                drag(dx = 140, fromDy = 110, toDy = -110, millis = 2_400)
                delay(900)
                // A flick: the dial coasts into the evening, the sky arc and the sun turn violet.
                drag(dx = 140, fromDy = 70, toDy = -90, millis = 140)
                delay(1_700)
                // Quarter-hour steps from the keyboard and the wheel.
                repeat(4) {
                    automator.pressKey(KeyEvent.VK_DOWN)
                    delay(280)
                }
                delay(400)
                repeat(4) {
                    automator.scrollWheel(dial, 1)
                    delay(280)
                }
                delay(600)
                // On past midnight, into the sleep window.
                drag(dx = 140, fromDy = 100, toDy = -100, millis = 1_800)
                delay(1_000)
                // Now spins the short way back to 9:52 AM.
                automator.click(now)
                delay(2_600)
            } finally {
                handle.stop()
            }
        } finally {
            window.stop()
        }
        assertTrue(Files.size(output) > 1_000, "expected a non-empty $output")
    }
}
