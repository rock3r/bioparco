package dev.sebastiano.knockoutbadge

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
 * Traces the whole specimen drawn in software, at the recording's window size. `ImageComposeScene`
 * draws on a raster surface, where Skia draws at call time, so each `scenario …` section is a full
 * frame's pixels, not just its recorded draw calls. Skipped unless `BIOPARCO_TRACE_DIR` is set:
 * ```
 * BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :knockout-badge:jvmTest --tests '*SoftwareRenderingBenchmark*' --rerun
 * python3 tracing/tools/trace-summary.py /tmp/traces/knockout-badge-benchmark
 * ```
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class SoftwareRenderingBenchmark {
    private val directory: String? = System.getenv(TRACE_DIR_ENVIRONMENT)

    @Test
    fun appFrames() {
        assumeTrue("Set $TRACE_DIR_ENVIRONMENT to run the benchmark", directory != null)
        val tracing = startTracing(File(directory, "knockout-badge-benchmark"))
        try {
            val scene = ImageComposeScene(WIDTH, HEIGHT, Density(1f)) { App() }
            val driver = SceneDriver(scene)
            // The footage plays through the word while the badge rests.
            repeat(90) { driver.frame("rest") }
            // The pointer circles over the badge, which tips towards it.
            repeat(90) {
                val angle = it / 90f * 2f * Math.PI.toFloat()
                driver.move(
                    "hover",
                    CENTRE_X + kotlin.math.cos(angle) * 100f,
                    CENTRE_Y + kotlin.math.sin(angle) * 160f,
                )
            }
            driver.leave()
            repeat(30) { driver.frame("settle") }
            // The corner, peeled down and back, then let go.
            driver.press(CORNER_X - 15f, CORNER_Y + 15f)
            repeat(40) { driver.move("peel", CORNER_X - 15f - it * 4f, CORNER_Y + 15f + it * 6f) }
            driver.release(CORNER_X - 175f, CORNER_Y + 255f)
            repeat(40) { driver.frame("unpeel") }
            // Apart, then back together.
            driver.key(Key.Spacebar)
            repeat(80) { driver.frame("explode") }
            repeat(60) { driver.frame("apart") }
            driver.key(Key.Spacebar)
            repeat(80) { driver.frame("assemble") }
            scene.close()
        } finally {
            tracing.close()
        }
    }

    /** Renders frames of [scene] 16 ms apart, each one a section named after its scenario. */
    private class SceneDriver(private val scene: ImageComposeScene) {
        private var nanos = 0L

        fun frame(scenario: String) {
            nanos += FRAME_NANOS
            // render() hands back a snapshot of its surface. Left open, it makes the next frame's
            // clear copy the whole surface into fresh memory first, which a window never does.
            Tracing.section("scenario $scenario") { scene.render(nanos).close() }
        }

        fun press(x: Float, y: Float) {
            scene.sendPointerEvent(PointerEventType.Press, Offset(x, y))
        }

        fun move(scenario: String, x: Float, y: Float) {
            scene.sendPointerEvent(PointerEventType.Move, Offset(x, y))
            frame(scenario)
        }

        fun release(x: Float, y: Float) {
            scene.sendPointerEvent(PointerEventType.Release, Offset(x, y))
        }

        fun leave() {
            scene.sendPointerEvent(PointerEventType.Exit, Offset(-1f, -1f))
        }

        fun key(key: Key) {
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        }
    }

    private companion object {
        const val WIDTH = 760
        const val HEIGHT = 720
        const val CENTRE_X = WIDTH / 2f

        /** The badge's middle: the stage leaves 56 px at the bottom for the help line. */
        const val CENTRE_Y = (HEIGHT - 56) / 2f
        const val CORNER_X = CENTRE_X + 125f
        const val CORNER_Y = CENTRE_Y - 200f
        const val FRAME_NANOS = 16_000_000L
    }
}
