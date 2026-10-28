package dev.sebastiano.stampbook

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
 * BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :stamp-book:jvmTest --tests '*FrameBudgetBenchmark*' --rerun
 * python3 tracing/tools/trace-summary.py /tmp/traces/stamp-book-benchmark
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
        val tracing = startTracing(File(directory, "stamp-book-benchmark"))
        try {
            val scene = ImageComposeScene(WIDTH, HEIGHT, Density(1f)) { App() }
            val driver = SceneDriver(scene)
            // The art bakes on a background thread, and the JIT is still compiling Compose and
            // Skiko for the first couple of thousand frames. Warm up first, so the measured pass
            // shows the steady state.
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

        /** Rest shut, hover it open, rest open, leave it to close, then recolour it. */
        fun scenario(label: (String) -> String) {
            repeat(30) { frame(label("shut")) }
            move(BOOK)
            repeat(70) { frame(label("opening")) }
            move(BIRD_STAMP)
            repeat(30) { frame(label("open")) }
            move(AWAY)
            repeat(50) { frame(label("closing")) }
            click(SWATCHES[(++picked) % SWATCHES.size])
            repeat(5) { frame(label("recolouring")) }
        }

        private var picked = 0

        fun frame(scenario: String) {
            nanos += FRAME_NANOS
            // Close each frame's image at once: left to the GC, the 2 MB snapshots make the
            // timings swing by several milliseconds.
            Tracing.section("scenario $scenario") { scene.render(nanos).close() }
        }

        fun move(at: Offset) {
            scene.sendPointerEvent(PointerEventType.Move, at)
        }

        fun click(at: Offset) {
            move(at)
            scene.sendPointerEvent(PointerEventType.Press, at)
            scene.sendPointerEvent(PointerEventType.Release, at)
        }
    }

    private companion object {
        /** The recording's window size. */
        const val WIDTH = 800
        const val HEIGHT = 600
        const val FRAME_NANOS = 16_666_667L
        const val WARM_UP_PASSES = 10

        // The 440 × 372 content box is centred, so its top-left is at (180, 114).
        val BOOK = Offset(290f, 274f)
        val BIRD_STAMP = Offset(540f, 250f)
        val AWAY = Offset(80f, 80f)
        val SWATCHES = List(3) { Offset(264.5f + 30f * it, 454f) }
    }
}
