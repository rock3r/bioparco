package dev.sebastiano.bioparco.recordings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.anatomy.AnatomyTags
import dev.sebastiano.anatomy.App
import dev.sebastiano.anatomy.Figure
import dev.sebastiano.spectre.core.AutomatorNode
import dev.sebastiano.spectre.core.ComposeAutomator
import dev.sebastiano.spectre.recording.AutoRecorder
import dev.sebastiano.spectre.recording.screencapturekit.asTitledWindow
import dev.sebastiano.spectre.testing.runSpectreTest
import java.awt.GraphicsEnvironment
import java.awt.event.KeyEvent
import java.nio.file.Files
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * A live tour of the anatomy page: each figure is scrolled into view and played with through real
 * pointer and key input, in page order.
 */
class AnatomyRecordingTest {
    @Test
    @Tag("recording")
    fun recordAnatomy(): Unit = runSpectreTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        val output = RecordingPaths.file(RecordingPaths.ANATOMY)
        Files.createDirectories(output.parent)
        val window =
            SpecimenWindow(title = "bioparco · anatomy", size = DpSize(760.dp, 960.dp)) { App() }
        window.start()
        try {
            val automator = ComposeAutomator.inProcess()
            automator.waitForNode(tag = AnatomyTags.figure(Figure.DialIndicator))
            delay(800)
            // The recorder retitles the window, so read its position first.
            val frame = window.frame()
            val tour = Tour(automator, frame.locationOnScreen.y)
            val handle = AutoRecorder().startWindow(frame.asTitledWindow(), output)
            try {
                tour.play()
            } finally {
                handle.stop()
            }
        } finally {
            window.stop()
        }
        assertTrue(Files.size(output) > 1_000, "expected a non-empty $output")
    }
}

private class Tour(private val automator: ComposeAutomator, private val windowTop: Int) {
    suspend fun play() {
        // Fig 1: the idle tour, then the pointer slides the carriage block to block.
        delay(2_500)
        glide(dial(), 0.78f to 0.6f, 0.22f to 0.6f, steps = 40, ms = 40)
        glide(dial(), 0.22f to 0.6f, 0.5f to 0.6f, steps = 20, ms = 40)
        delay(1_200)

        // Fig 2: a burst of requests drains the bucket, then one a second gets through.
        scrollTo(Figure.TokenBucket)
        delay(800)
        automator.click(canvas(BUCKET))
        repeat(11) {
            delay(450)
            automator.pressKey(KeyEvent.VK_SPACE)
        }
        delay(2_500)

        // Fig 3: opening the air collar turns the lazy yellow flame into a blue cone.
        scrollTo(Figure.BunsenBurner)
        delay(1_200)
        glide(canvas(BUNSEN), 0.2f to 0.5f, 0.8f to 0.5f, steps = 50, ms = 60)
        delay(1_500)
        glide(canvas(BUNSEN), 0.8f to 0.5f, 0.45f to 0.5f, steps = 25, ms = 60)
        delay(1_500)

        // Fig 4: pull a shot. Its own tour may be pulling one already: a click stops that first.
        scrollTo(Figure.EspressoMachine)
        delay(1_000)
        if (automator.allNodes().any { it.text?.startsWith("shot ") == true }) {
            automator.click(canvas(ESPRESSO))
            delay(1_500)
        }
        automator.click(canvas(ESPRESSO))
        delay(10_000)

        // Fig 5: turn the dial by hand, then break the pod off.
        scrollTo(Figure.TurningDial)
        delay(1_200)
        drag(canvas(TURNING), 0.36f to 0.33f, 0.66f to 0.36f)
        delay(800)
        drag(canvas(TURNING), 0.66f to 0.55f, 0.38f to 0.52f)
        delay(1_000)
        automator.pressKey(KeyEvent.VK_B)
        delay(3_500)
    }

    private fun dial(): AutomatorNode = canvas(DIAL)

    private fun canvas(prefix: String): AutomatorNode =
        automator.allNodes().first { it.contentDescription?.startsWith(prefix) == true }

    /** Scrolls the page, one wheel notch at a time, until [figure]'s card sits near the top. */
    private suspend fun scrollTo(figure: Figure) {
        var still = 0
        var last = Int.MIN_VALUE
        repeat(MAX_NOTCHES) {
            val node = automator.waitForNode(tag = AnatomyTags.figure(figure))
            val y = node.boundsOnScreen.y
            val gap = y - (windowTop + TOP_MARGIN)
            if (abs(gap) < NOTCH) return
            // Wheel scrolling is animated: give up only once the page has stopped moving for a
            // while.
            still = if (y == last) still + 1 else 0
            if (still > 8) return
            last = y
            automator.moveTo(node.centerOnScreen.x, windowTop + TOP_MARGIN + 200)
            if (still == 0) automator.scrollWheel(node, if (gap > 0) 1 else -1)
            delay(90)
        }
    }

    /** Glides the pointer over [node] through fractions of its bounds. */
    private suspend fun glide(
        node: AutomatorNode,
        vararg points: Pair<Float, Float>,
        steps: Int,
        ms: Long,
    ) {
        val b = node.boundsOnScreen
        fun at(p: Pair<Float, Float>) =
            (b.x + p.first * b.width).toInt() to (b.y + p.second * b.height).toInt()
        for (k in 1 until points.size) {
            val (x0, y0) = at(points[k - 1])
            val (x1, y1) = at(points[k])
            for (s in 0..steps) {
                automator.moveTo(x0 + (x1 - x0) * s / steps, y0 + (y1 - y0) * s / steps)
                delay(ms)
            }
        }
    }

    /** A press, a slow move and a release across [node]. */
    private suspend fun drag(
        node: AutomatorNode,
        from: Pair<Float, Float>,
        to: Pair<Float, Float>,
    ) {
        val b = node.boundsOnScreen
        automator.swipe(
            (b.x + from.first * b.width).toInt(),
            (b.y + from.second * b.height).toInt(),
            (b.x + to.first * b.width).toInt(),
            (b.y + to.second * b.height).toInt(),
            40,
            1_600.milliseconds,
        )
    }

    private companion object {
        const val DIAL = "A dial indicator"
        const val BUCKET = "A token dispenser"
        const val BUNSEN = "A Bunsen burner"
        const val ESPRESSO = "A heat-exchanger"
        const val TURNING = "A combination dial"
        const val TOP_MARGIN = 40
        const val NOTCH = 60
        const val MAX_NOTCHES = 200
    }
}
