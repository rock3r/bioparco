package dev.sebastiano.routinetimeline

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import dev.sebastiano.bioparco.tracing.TRACE_DIR_ENVIRONMENT
import dev.sebastiano.bioparco.tracing.Tracing
import dev.sebastiano.bioparco.tracing.startTracing
import java.io.File
import kotlin.test.Test
import org.junit.Assume.assumeTrue

/**
 * Traces whole frames of the specimen drawn in software, to check they fit a 60 Hz frame. Skipped
 * unless `BIOPARCO_TRACE_DIR` names a directory:
 * ```
 * BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :routine-timeline:jvmTest --tests '*FrameBudgetBenchmark*' --rerun
 * python3 tracing/tools/trace-summary.py /tmp/traces/routine-timeline-benchmark
 * ```
 *
 * An `ImageComposeScene` draws every frame from scratch onto a CPU surface, so each `scenario …`
 * section is composition, layout, drawing and Skia's rasterising together: the most a software
 * frame of this specimen can cost. A window redraws less.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class FrameBudgetBenchmark {
    private val directory: String? = System.getenv(TRACE_DIR_ENVIRONMENT)

    @Test
    fun appFrames() {
        assumeTrue("Set $TRACE_DIR_ENVIRONMENT to run the benchmark", directory != null)
        val tracing = startTracing(File(directory, "routine-timeline-benchmark"))
        try {
            val scene = ImageComposeScene(WIDTH, HEIGHT, Density(1f)) { App(fixedNow = START) }
            val driver = SceneDriver(scene)
            // The JIT is still compiling Compose and Skiko for the first couple of thousand
            // frames: run the whole scenario a few times first to measure the steady state.
            repeat(WARM_UP_PASSES) { driver.scenario { "warm-up" } }
            driver.scenario { it }
            scene.close()
        } finally {
            tracing.close()
        }
    }

    /** Renders frames of [scene] a 60 Hz frame apart, each a section named after its scenario. */
    private class SceneDriver(private val scene: ImageComposeScene) {
        private var nanos = 0L

        /** Rest, a slow turn, a flick that coasts, quarter-hour steps, and Now. */
        fun scenario(label: (String) -> String) {
            repeat(30) { frame(label("resting")) }
            // A slow drag up the right of the dial: three hours or so through the day.
            press(label("turning"), CENTRE_X + 140f, CENTRE_Y + 110f)
            repeat(90) { move(label("turning"), CENTRE_X + 140f, CENTRE_Y + 110f - it * 2.4f) }
            release(label("turning"), CENTRE_X + 140f, CENTRE_Y - 110f)
            repeat(20) { frame(label("turning")) }
            // A flick, then the coast.
            press(label("coasting"), CENTRE_X + 140f, CENTRE_Y + 70f)
            repeat(8) { move(label("coasting"), CENTRE_X + 140f, CENTRE_Y + 70f - it * 20f) }
            release(label("coasting"), CENTRE_X + 140f, CENTRE_Y - 90f)
            repeat(90) { frame(label("coasting")) }
            repeat(4) {
                key(Key.DirectionDown)
                repeat(15) { frame(label("stepping")) }
            }
            // Now: the spin back, list and all.
            key(Key.N)
            repeat(120) { frame(label("back to now")) }
        }

        fun frame(scenario: String) {
            nanos += FRAME_NANOS
            Tracing.section("scenario $scenario") { scene.render(nanos) }
        }

        fun press(scenario: String, x: Float, y: Float) {
            scene.sendPointerEvent(PointerEventType.Move, Offset(x, y))
            scene.sendPointerEvent(PointerEventType.Press, Offset(x, y))
            frame(scenario)
        }

        fun move(scenario: String, x: Float, y: Float) {
            scene.sendPointerEvent(PointerEventType.Move, Offset(x, y))
            frame(scenario)
        }

        fun release(scenario: String, x: Float, y: Float) {
            scene.sendPointerEvent(PointerEventType.Release, Offset(x, y))
            frame(scenario)
        }

        fun key(key: Key) {
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        }
    }

    private companion object {
        /** The recording's window size, and its now: 9:52 AM. */
        const val WIDTH = 620
        const val HEIGHT = 580
        const val START = 9 * 60 + 52

        /** The dial's centre: the 510 × 470 card is centred, its dial 86dp in and 262dp down. */
        const val CENTRE_X = (WIDTH - 510) / 2f + 86f
        const val CENTRE_Y = (HEIGHT - 470) / 2f + 262f
        const val FRAME_NANOS = 16_666_667L
        const val WARM_UP_PASSES = 6
    }
}
