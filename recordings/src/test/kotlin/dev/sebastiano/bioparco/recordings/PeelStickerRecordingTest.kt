package dev.sebastiano.bioparco.recordings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.peelsticker.App
import dev.sebastiano.peelsticker.PeelStickerTags
import dev.sebastiano.peelsticker.ShineMode
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

class PeelStickerRecordingTest {
    @Test
    @Tag("recording")
    fun recordPeelSticker(): Unit = runSpectreTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        val output = RecordingPaths.file(RecordingPaths.PEEL_STICKER)
        Files.createDirectories(output.parent)
        val window =
            SpecimenWindow(title = "bioparco · peel sticker", size = DpSize(760.dp, 690.dp)) {
                App()
            }
        window.start()
        try {
            val automator = ComposeAutomator.inProcess()
            val stage = automator.waitForNode(tag = PeelStickerTags.STAGE)
            delay(800)
            val handle = AutoRecorder().startWindow(window.frame().asTitledWindow(), output)
            try {
                val bounds = stage.boundsOnScreen
                // The sticker's die-cut spans 48.4% of the stage's width, or 70.8% of its height.
                val side = minOf(bounds.width * 0.484f, bounds.height * 0.708f)
                val cx = bounds.x + bounds.width / 2f
                val cy = bounds.y + bounds.height / 2f
                fun x(fraction: Float) = (cx + side * fraction).toInt()
                fun y(fraction: Float) = (cy + side * fraction).toInt()
                suspend fun peel(fromX: Float, fromY: Float, toX: Float, toY: Float, millis: Int) {
                    automator.swipe(
                        startX = x(fromX),
                        startY = y(fromY),
                        endX = x(toX),
                        endY = y(toY),
                        steps = 40,
                        duration = millis.milliseconds,
                    )
                    delay(1_400)
                }
                suspend fun show(mode: ShineMode) {
                    automator.click(automator.waitForNode(text = mode.label))
                    delay(300)
                }
                suspend fun glide(fromX: Float, fromY: Float, toX: Float, toY: Float) {
                    for (step in 0..24) {
                        val t = step / 24f
                        automator.moveTo(
                            x(fromX + (toX - fromX) * t),
                            y(fromY + (toY - fromY) * t),
                        )
                        delay(40)
                    }
                }

                // A first click lands on the already selected tab, so the window has focus
                // before the first drag.
                show(ShineMode.Prism)
                // Prism on the X: the light sweeps across, then the top right tip peels over.
                glide(0.1f, 0.25f, -0.25f, -0.3f)
                delay(400)
                peel(0.37f, -0.43f, -0.25f, 0.3f, 1_300)
                // The bottom left arm peels right across the sticker.
                peel(-0.42f, 0.44f, 0.45f, -0.1f, 1_400)

                // The G, through the other four shines.
                automator.pressKey(KeyEvent.VK_P)
                delay(500)
                show(ShineMode.Halftone)
                glide(0.3f, 0.35f, 0.25f, -0.1f)
                show(ShineMode.Ripple)
                glide(0.25f, -0.05f, 0.1f, 0.1f)
                delay(600)
                show(ShineMode.Sparkle)
                glide(-0.3f, -0.2f, 0.1f, 0.3f)
                delay(500)
                show(ShineMode.White)
                glide(0.3f, 0.3f, -0.3f, -0.2f)
                peel(0.41f, 0.22f, -0.3f, -0.2f, 1_300)
            } finally {
                handle.stop()
            }
        } finally {
            window.stop()
        }
        assertTrue(Files.size(output) > 1_000, "expected a non-empty $output")
    }
}
