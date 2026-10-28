package dev.sebastiano.bioparco.recordings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.layoutmorph.App
import dev.sebastiano.layoutmorph.Arrangement
import dev.sebastiano.layoutmorph.Arrangements
import dev.sebastiano.layoutmorph.CardPose
import dev.sebastiano.layoutmorph.Field
import dev.sebastiano.layoutmorph.LayoutMorphTags
import dev.sebastiano.layoutmorph.ShopCollection
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

class LayoutMorphRecordingTest {
    @Test
    @Tag("recording")
    fun recordLayoutMorph(): Unit = runSpectreTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        val output = RecordingPaths.file(RecordingPaths.LAYOUT_MORPH)
        Files.createDirectories(output.parent)
        val window =
            SpecimenWindow(title = "bioparco · layout morph", size = DpSize(960.dp, 660.dp)) {
                App()
            }
        window.start()
        try {
            val automator = ComposeAutomator.inProcess()
            automator.waitForNode(tag = LayoutMorphTags.STAGE)
            val bounds = automator.waitForNode(tag = LayoutMorphTags.FIELD).boundsOnScreen
            // AWT's screen units are dp here, so the specimen's own layouts say where cards land.
            val field = Field(bounds.width.toFloat(), bounds.height.toFloat())
            val count = ShopCollection.pieces.size

            suspend fun pick(arrangement: Arrangement, settle: Long) {
                automator.click(
                    automator.waitForNode(tag = LayoutMorphTags.arrangement(arrangement))
                )
                delay(settle)
            }

            /** Glides the pointer through the centres of [cards] laid out by [poses]. */
            suspend fun glide(poses: List<CardPose>, cards: List<Int>, each: Long) {
                for (card in cards) {
                    val pose = poses[card]
                    automator.moveTo(bounds.x + pose.x.toInt(), bounds.y + pose.y.toInt())
                    delay(each)
                }
            }

            val handle = AutoRecorder().startWindow(window.frame().asTitledWindow(), output)
            try {
                delay(700)
                glide(Arrangements.grid(count, field), listOf(1, 2, 11, 12), 280)
                pick(Arrangement.Masonry, 1_300)
                pick(Arrangement.List, 1_400)
                pick(Arrangement.Stack, 1_000)
                // Sweep along the fan: each card under the pointer slides up out of it.
                glide(Arrangements.stack(count, field), (4..19 step 3).toList(), 230)
                delay(400)
                pick(Arrangement.Carousel, 1_300)
                val cy = bounds.y + bounds.height / 2
                val cx = bounds.x + bounds.width / 2
                automator.swipe(
                    startX = cx + 160,
                    startY = cy,
                    endX = cx - 220,
                    endY = cy,
                    steps = 18,
                    duration = 380.milliseconds,
                )
                delay(1_300)
                automator.pressKey(KeyEvent.VK_LEFT)
                delay(500)
                automator.pressKey(KeyEvent.VK_LEFT)
                delay(900)
                pick(Arrangement.Helix, 2_000)
                // Throw it: it spins hard, then slows back to its idle drift.
                automator.swipe(
                    startX = cx - 200,
                    startY = cy,
                    endX = cx + 160,
                    endY = cy,
                    steps = 14,
                    duration = 260.milliseconds,
                )
                delay(2_800)
                pick(Arrangement.Grid, 1_400)
                // A click on a grid card opens it in the carousel. Spectre clicks nodes, and the
                // cards are drawn rather than nodes, so the click is a swipe that goes nowhere.
                val print = Arrangements.grid(count, field)[21]
                val px = bounds.x + print.x.toInt()
                val py = bounds.y + print.y.toInt()
                automator.moveTo(px, py)
                delay(500)
                automator.swipe(
                    startX = px,
                    startY = py,
                    endX = px,
                    endY = py,
                    steps = 1,
                    duration = 60.milliseconds,
                )
                delay(1_800)
                pick(Arrangement.Grid, 1_500)
            } finally {
                handle.stop()
            }
        } finally {
            window.stop()
        }
        assertTrue(Files.size(output) > 1_000, "expected a non-empty $output")
    }
}
