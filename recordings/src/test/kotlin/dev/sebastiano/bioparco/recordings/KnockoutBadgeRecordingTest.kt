package dev.sebastiano.bioparco.recordings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.knockoutbadge.App
import dev.sebastiano.knockoutbadge.BadgeTags
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

class KnockoutBadgeRecordingTest {
    @Test
    @Tag("recording")
    fun recordKnockoutBadge(): Unit = runSpectreTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        val output = RecordingPaths.file(RecordingPaths.KNOCKOUT_BADGE)
        Files.createDirectories(output.parent)
        val window =
            SpecimenWindow(title = "bioparco · knockout badge", size = DpSize(760.dp, 720.dp)) {
                App()
            }
        window.start()
        try {
            val handle = AutoRecorder().startWindow(window.frame().asTitledWindow(), output)
            try {
                val automator = ComposeAutomator.inProcess()
                val badgeNode = automator.waitForNode(tag = BadgeTags.BADGE)
                val badge = badgeNode.boundsOnScreen
                val cx = badge.x + badge.width / 2
                val cy = badge.y + badge.height / 2

                /** Waits for the badge to report [state], so the script checks what it drove. */
                suspend fun expect(state: String) =
                    automator.waitUntil(description = "the badge is $state", timeout = 10.seconds) {
                        allNodes().any { it.testTag == BadgeTags.STATE && it.text == state }
                    }

                /** Circles the pointer over the badge, which tips away from it as if pressed. */
                suspend fun circle(turns: Float, millis: Long) {
                    val steps = (turns * 72).toInt()
                    for (step in 0..steps) {
                        val angle = step / 72f * 2f * PI.toFloat() - PI.toFloat() / 2f
                        automator.moveTo(
                            cx + (cos(angle) * badge.width * 0.4f).toInt(),
                            cy + (sin(angle) * badge.height * 0.4f).toInt(),
                        )
                        delay(millis / steps)
                    }
                }

                expect(BadgeTags.together("init"))
                delay(1_000)
                circle(turns = 1.25f, millis = 2_600)
                // Off the badge: it settles flat.
                automator.moveTo(badge.x + badge.width + 120, badge.y + badge.height / 3)
                delay(600)

                // Grab the curled corner and peel the sticker back off the footage; let go and the
                // sticker lies back down.
                val corner = automator.waitForNode(tag = BadgeTags.CORNER).boundsOnScreen
                automator.swipe(
                    startX = corner.x + corner.width / 2,
                    startY = corner.y + corner.height / 2,
                    endX = badge.x + (badge.width * 0.3f).toInt(),
                    endY = badge.y + (badge.height * 0.55f).toInt(),
                    steps = 50,
                    duration = 1_500.milliseconds,
                )
                delay(1_000)

                // A click takes it apart into its layers, and another puts it back.
                automator.click(badgeNode)
                expect(BadgeTags.apart("init"))
                delay(3_000)
                automator.click(badgeNode)
                expect(BadgeTags.together("init"))
                delay(1_200)

                // Typing changes the word cut out of the sticker; Space takes the new one apart.
                automator.typeText("cool!")
                expect(BadgeTags.together("cool!"))
                delay(1_600)
                automator.pressKey(KeyEvent.VK_SPACE)
                expect(BadgeTags.apart("cool!"))
                delay(2_400)
                automator.pressKey(KeyEvent.VK_SPACE)
                expect(BadgeTags.together("cool!"))
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
