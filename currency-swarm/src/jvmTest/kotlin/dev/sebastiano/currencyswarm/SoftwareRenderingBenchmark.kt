package dev.sebastiano.currencyswarm

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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import org.junit.Assume.assumeTrue

/**
 * Traces the whole specimen drawn in software, at the recording's window size. `ImageComposeScene`
 * draws on a raster surface, where Skia draws at call time, so each `scenario …` section is a full
 * frame's pixels, not just its recorded draw calls. Skipped unless `BIOPARCO_TRACE_DIR` is set:
 * ```
 * BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :currency-swarm:jvmTest --tests '*SoftwareRenderingBenchmark*' --rerun
 * python3 tracing/tools/trace-summary.py /tmp/traces/currency-swarm-benchmark
 * ```
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class SoftwareRenderingBenchmark {
    private val directory: String? = System.getenv(TRACE_DIR_ENVIRONMENT)

    @Test
    fun appFrames() {
        assumeTrue("Set $TRACE_DIR_ENVIRONMENT to run the benchmark", directory != null)
        val tracing = startTracing(File(directory, "currency-swarm-benchmark"))
        try {
            val scene = ImageComposeScene(WIDTH, HEIGHT, Density(1f)) { App() }
            val driver = SceneDriver(scene)
            repeat(120) { driver.frame("warm-up") }
            // The cloud condenses into the first sign.
            repeat(90) { driver.frame("intro") }
            // Every dot at rest, wobbling.
            repeat(60) { driver.frame("hold") }
            // The pointer circles through the sign, pushing dots aside.
            repeat(90) {
                val angle = it / 90f * 2f * Math.PI.toFloat()
                driver.move("pointer", CENTRE_X + cos(angle) * 70f, SIGN_Y + sin(angle) * 90f)
            }
            driver.exit()
            // A morph to the next sign, then morphs that interrupt each other mid-flight.
            driver.key(Key.DirectionRight)
            repeat(90) { driver.frame("morph") }
            repeat(4) {
                driver.key(Key.DirectionRight)
                repeat(20) { driver.frame("interrupted morph") }
            }
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
            // clear copy the whole surface into fresh memory first, which a window never does: up
            // to 8 ms a frame here, more than everything the specimen draws.
            Tracing.section("scenario $scenario") { scene.render(nanos).close() }
        }

        fun move(scenario: String, x: Float, y: Float) {
            scene.sendPointerEvent(PointerEventType.Move, Offset(x, y))
            frame(scenario)
        }

        fun exit() {
            scene.sendPointerEvent(PointerEventType.Exit, Offset(-1f, -1f))
        }

        fun key(key: Key) {
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        }
    }

    private companion object {
        const val WIDTH = 560
        const val HEIGHT = 680
        const val CENTRE_X = WIDTH / 2f

        /** About the middle of the sign. */
        const val SIGN_Y = 240f
        const val FRAME_NANOS = 16_000_000L
    }
}
