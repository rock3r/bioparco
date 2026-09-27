package dev.sebastiano.bioparco.recordings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.scrolleffects.App
import dev.sebastiano.scrolleffects.CarouselEffect
import dev.sebastiano.scrolleffects.ScrollEffectsTags
import dev.sebastiano.spectre.core.ComposeAutomator
import dev.sebastiano.spectre.recording.AutoRecorder
import dev.sebastiano.spectre.recording.screencapturekit.asTitledWindow
import dev.sebastiano.spectre.testing.runSpectreTest
import java.awt.GraphicsEnvironment
import java.awt.event.KeyEvent
import java.nio.file.Files
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

class ScrollEffectsRecordingTest {
    @Test
    @Tag("recording")
    fun recordScrollEffects(): Unit = runSpectreTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        val output = RecordingPaths.file(RecordingPaths.SCROLL_EFFECTS)
        Files.createDirectories(output.parent)
        val window =
            SpecimenWindow(title = "bioparco · scroll effects", size = DpSize(900.dp, 780.dp)) {
                App()
            }
        window.start()
        try {
            val automator = ComposeAutomator.inProcess()
            val stage = automator.waitForNode(tag = ScrollEffectsTags.STAGE)
            delay(800)
            val handle = AutoRecorder().startWindow(window.frame().asTitledWindow(), output)
            try {
                val center = stage.centerOnScreen
                suspend fun swipe(distance: Int) {
                    automator.swipe(
                        startX = center.x,
                        startY = center.y,
                        endX = center.x + distance,
                        endY = center.y,
                        steps = 18,
                        duration = 420.milliseconds,
                    )
                }
                suspend fun show(effect: CarouselEffect) {
                    // The tab merges its number and name; the name alone finds it.
                    automator.click(automator.waitForNode(text = effect.label))
                    delay(700)
                }

                // Stretch: a slow drag shows the flip, then the arrow key springs on.
                delay(700)
                swipe(-260)
                delay(1_200)
                automator.pressKey(KeyEvent.VK_RIGHT)
                delay(1_200)
                show(CarouselEffect.Bulge)
                swipe(-260)
                delay(1_100)
                show(CarouselEffect.Drum)
                swipe(260)
                delay(1_100)
                show(CarouselEffect.Shatter)
                automator.pressKey(KeyEvent.VK_RIGHT)
                delay(1_300)
                show(CarouselEffect.Thanos)
                swipe(-260)
                delay(1_500)
                show(CarouselEffect.Glitch)
                automator.pressKey(KeyEvent.VK_RIGHT)
                delay(1_500)
            } finally {
                handle.stop()
            }
        } finally {
            window.stop()
        }
        assertTrue(Files.size(output) > 1_000, "expected a non-empty $output")
    }
}
