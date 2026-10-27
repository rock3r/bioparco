package dev.sebastiano.squeezeprogress

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
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
 * BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :squeeze-progress:jvmTest --tests '*FrameBudgetBenchmark*' --rerun
 * python3 tracing/tools/trace-summary.py /tmp/traces/squeeze-progress-benchmark
 * ```
 *
 * An `ImageComposeScene` draws every frame from scratch onto a CPU surface, so each `scenario …`
 * section is composition, layout, drawing and Skia's rasterising together: the most a software
 * frame of this specimen can cost. A window redraws less.
 */
@OptIn(ExperimentalComposeUiApi::class)
class FrameBudgetBenchmark {
    private val directory: String? = System.getenv(TRACE_DIR_ENVIRONMENT)

    @Test
    fun appFrames() {
        assumeTrue("Set $TRACE_DIR_ENVIRONMENT to run the benchmark", directory != null)
        val tracing = startTracing(File(directory, "squeeze-progress-benchmark"))
        try {
            val scene = ImageComposeScene(WIDTH, HEIGHT, Density(1f)) { App() }
            val driver = SceneDriver(scene)
            // Even an empty scene costs about 9 ms a frame for its first couple of thousand frames,
            // then under half a millisecond: the JIT is still compiling Compose and Skiko. Run the
            // whole scenario a few times first, so the measured pass shows the steady state.
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

        /** Play, pause (it crumples and settles), resume (it shakes out), play on. */
        fun scenario(label: (String) -> String) {
            repeat(120) { frame(label("playing")) }
            click()
            // The crumple overshoots and settles in about a quarter of a second.
            repeat(20) { frame(label("crumpling")) }
            repeat(40) { frame(label("paused")) }
            click()
            repeat(90) { frame(label("releasing")) }
            repeat(60) { frame(label("playing again")) }
        }

        fun frame(scenario: String) {
            nanos += FRAME_NANOS
            Tracing.section("scenario $scenario") { scene.render(nanos) }
        }

        /** Clicks the button, which sits centred at 71% of the height. */
        fun click() {
            val button = Offset(WIDTH / 2f, HEIGHT * 0.71f)
            scene.sendPointerEvent(PointerEventType.Move, button)
            scene.sendPointerEvent(PointerEventType.Press, button)
            scene.sendPointerEvent(PointerEventType.Release, button)
        }
    }

    private companion object {
        /** The recording's window size. */
        const val WIDTH = 800
        const val HEIGHT = 600
        const val FRAME_NANOS = 16_666_667L
        const val WARM_UP_PASSES = 8
    }
}
