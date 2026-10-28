package dev.sebastiano.particlering

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
 * BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :particle-ring:jvmTest --tests '*SoftwareRenderingBenchmark*' --rerun
 * python3 tracing/tools/trace-summary.py /tmp/traces/particle-ring-benchmark
 * ```
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class SoftwareRenderingBenchmark {
    private val directory: String? = System.getenv(TRACE_DIR_ENVIRONMENT)

    @Test
    fun appFrames() {
        assumeTrue("Set $TRACE_DIR_ENVIRONMENT to run the benchmark", directory != null)
        val tracing = startTracing(File(directory, "particle-ring-benchmark"))
        try {
            val scene = ImageComposeScene(WIDTH, HEIGHT, Density(1f)) { App() }
            val driver = SceneDriver(scene)
            // The icon breathes while it waits.
            repeat(90) { driver.frame("icon") }
            // The burst, from the icon into the ring.
            driver.key(Key.Spacebar)
            repeat(180) { driver.frame("burst") }
            // The ring reads.
            repeat(90) { driver.frame("ring") }
            // A drag along the ring's near side stirs it and tilts it.
            driver.press(CENTRE_X - 120f, RING_FRONT_Y)
            repeat(30) { driver.move("drag", CENTRE_X - 120f + it * 8f, RING_FRONT_Y + it * 2f) }
            driver.release(CENTRE_X + 120f, RING_FRONT_Y + 60f)
            repeat(60) { driver.frame("coast") }
            // The ring folds into the logo, which fades in and holds.
            driver.key(Key.Spacebar)
            repeat(110) { driver.frame("collapse") }
            repeat(60) { driver.frame("logo") }
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

        fun key(key: Key) {
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        }
    }

    private companion object {
        const val WIDTH = 560
        const val HEIGHT = 680
        const val CENTRE_X = WIDTH / 2f

        /** About the ring's near side. */
        const val RING_FRONT_Y = 300f
        const val FRAME_NANOS = 16_000_000L
    }
}
