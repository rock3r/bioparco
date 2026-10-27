package dev.sebastiano.wheelpicker

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
 * BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :wheel-picker:jvmTest --tests '*SoftwareRenderingBenchmark*' --rerun
 * python3 tracing/tools/trace-summary.py /tmp/traces/wheel-picker-benchmark
 * ```
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class SoftwareRenderingBenchmark {
    private val directory: String? = System.getenv(TRACE_DIR_ENVIRONMENT)

    @Test
    fun appFrames() {
        assumeTrue("Set $TRACE_DIR_ENVIRONMENT to run the benchmark", directory != null)
        val tracing = startTracing(File(directory, "wheel-picker-benchmark"))
        try {
            val scene = ImageComposeScene(WIDTH, HEIGHT, Density(1f)) { App() }
            val driver = SceneDriver(scene)
            repeat(30) { driver.frame("warm-up") }
            repeat(30) { driver.frame("idle") }
            // A slow drag: the ruler moves every frame, the readout rolls as stops go by.
            driver.press("drag", CENTRE_X + 200f, RULER_Y)
            repeat(60) { driver.move("drag", CENTRE_X + 200f - it * 6f, RULER_Y) }
            driver.release("drag", CENTRE_X - 160f, RULER_Y)
            repeat(40) { driver.frame("settle") }
            // Only the readout rolls: one key press, then the ruler is already nearly there.
            driver.key(Key.DirectionRight)
            repeat(30) { driver.frame("roll") }
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
        const val WIDTH = 920
        const val HEIGHT = 560
        const val CENTRE_X = WIDTH / 2f

        /** The ruler's middle, just below the window's centre. */
        const val RULER_Y = 340f
        const val FRAME_NANOS = 16_000_000L
    }
}
