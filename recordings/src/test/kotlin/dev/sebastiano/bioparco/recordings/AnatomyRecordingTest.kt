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
 * pointer and key input, in page order, from the first figure to the last.
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
    /**
     * Every figure, in page order. The `when` has no `else`, so a new [Figure] does not compile
     * until it has its part of the tour.
     */
    suspend fun play() {
        for (figure in Figure.entries) {
            if (figure != Figure.entries.first()) scrollTo(figure)
            when (figure) {
                Figure.DialIndicator -> dialIndicator()
                Figure.TokenBucket -> tokenBucket()
                Figure.BunsenBurner -> bunsenBurner()
                Figure.EspressoMachine -> espressoMachine()
                Figure.TurningDial -> turningDial()
                Figure.RaptorEngine -> raptorEngine()
                Figure.DeskComputer -> deskComputer()
                Figure.ArcadeCabinet -> arcadeCabinet()
                Figure.SpotPlate -> spotPlate()
            }
        }
    }

    /** Fig 1: the idle tour, then the pointer slides the carriage block to block. */
    private suspend fun dialIndicator() {
        delay(2_500)
        glide(dial(), 0.78f to 0.6f, 0.22f to 0.6f, steps = 40, ms = 40)
        glide(dial(), 0.22f to 0.6f, 0.5f to 0.6f, steps = 20, ms = 40)
        delay(1_200)
    }

    /** Fig 2: a burst of requests drains the bucket, then one a second gets through. */
    private suspend fun tokenBucket() {
        delay(800)
        automator.click(canvas(BUCKET))
        repeat(11) {
            delay(450)
            automator.pressKey(KeyEvent.VK_SPACE)
        }
        delay(2_500)
    }

    /** Fig 3: opening the air collar turns the lazy yellow flame into a blue cone. */
    private suspend fun bunsenBurner() {
        delay(1_200)
        glide(canvas(BUNSEN), 0.2f to 0.5f, 0.8f to 0.5f, steps = 50, ms = 60)
        delay(1_500)
        glide(canvas(BUNSEN), 0.8f to 0.5f, 0.45f to 0.5f, steps = 25, ms = 60)
        delay(1_500)
    }

    /** Fig 4: pull a shot. Its own tour may be pulling one already: a click stops that first. */
    private suspend fun espressoMachine() {
        delay(1_000)
        if (automator.allNodes().any { it.text?.startsWith("shot ") == true }) {
            automator.click(canvas(ESPRESSO))
            delay(1_500)
        }
        automator.click(canvas(ESPRESSO))
        delay(10_000)
    }

    /** Fig 5: turn the dial by hand, then break the pod off. */
    private suspend fun turningDial() {
        delay(1_200)
        drag(canvas(TURNING), 0.36f to 0.33f, 0.66f to 0.36f)
        delay(800)
        drag(canvas(TURNING), 0.66f to 0.55f, 0.38f to 0.52f)
        delay(1_000)
        automator.pressKey(KeyEvent.VK_B)
        delay(3_500)
    }

    /**
     * Fig 6: hover the Raptor to take it apart, light up assemblies under the pointer, let it close
     * up again, then fire it and shut it down.
     */
    private suspend fun raptorEngine() {
        delay(1_200)
        val raptor = canvas(RAPTOR)
        glide(raptor, 0.5f to 0.98f, 0.5f to 0.45f, steps = 15, ms = 40)
        delay(2_200)
        glide(
            raptor,
            0.5f to 0.45f,
            0.38f to 0.3f,
            0.62f to 0.42f,
            0.5f to 0.7f,
            steps = 30,
            ms = 50,
        )
        delay(1_000)
        glide(raptor, 0.5f to 0.7f, 0.98f to 0.99f, steps = 8, ms = 30)
        automator.moveTo(raptor.boundsOnScreen.x - 30, raptor.boundsOnScreen.y + 200)
        delay(2_500)
        automator.click(automator.allNodes().first { it.text == "IGNITE" })
        delay(9_000)
        automator.click(automator.allNodes().first { it.text == "SHUT DOWN" })
        delay(3_500)
    }

    /** Fig 7: click a key to take over from the tour, type on the screen, then switch it off. */
    private suspend fun deskComputer() {
        delay(1_200)
        val desk = canvas(DESK)
        clickAt(desk, DESK_KEY)
        delay(400)
        automator.typeText("hello, bioparco")
        delay(1_500)
        automator.pressKey(KeyEvent.VK_ESCAPE)
        delay(1_800)
        automator.pressKey(KeyEvent.VK_ESCAPE)
        delay(1_500)
    }

    /** Fig 8: drop a quarter to wake the cabinet, then play its attract game with the stick. */
    private suspend fun arcadeCabinet() {
        delay(1_000)
        val arcade = canvas(ARCADE)
        clickAt(arcade, ARCADE_TRAY)
        delay(5_000)
        for (key in
            listOf(KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_RIGHT, KeyEvent.VK_LEFT)) {
            automator.pressKey(key)
            delay(250)
            automator.pressKey(KeyEvent.VK_SPACE)
            delay(500)
        }
        delay(2_000)
    }

    /** Fig 11: slide across the plate to open the fan, so each pair runs together in turn. */
    private suspend fun spotPlate() {
        delay(1_200)
        val spot = canvas(SPOT)
        glide(spot, 0.1f to 0.5f, 0.9f to 0.5f, steps = 60, ms = 50)
        delay(1_000)
        glide(spot, 0.9f to 0.5f, 0.3f to 0.5f, steps = 30, ms = 50)
        delay(2_000)
    }

    /** A click at a fraction of [node]'s bounds: a press and release that does not move. */
    private suspend fun clickAt(node: AutomatorNode, at: Pair<Float, Float>) {
        val b = node.boundsOnScreen
        val x = (b.x + at.first * b.width).toInt()
        val y = (b.y + at.second * b.height).toInt()
        automator.moveTo(x, y)
        delay(150)
        automator.swipe(x, y, x, y, 1, 80.milliseconds)
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
        const val RAPTOR = "Raptor 2 on a test stand"
        const val DESK = "Desk computer."
        const val ARCADE = "Arcade cabinet."
        const val SPOT = "Merge, in pixels."
        /** Fractions of the canvas: the H key, and the stack of quarters on its tray. */
        val DESK_KEY = 0.4f to 0.72f
        val ARCADE_TRAY = 0.59f to 0.9f
        const val TOP_MARGIN = 40
        const val NOTCH = 60
        const val MAX_NOTCHES = 200
    }
}
