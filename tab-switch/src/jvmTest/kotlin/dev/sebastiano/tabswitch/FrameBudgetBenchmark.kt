package dev.sebastiano.tabswitch

import androidx.compose.runtime.snapshots.Snapshot
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
 * BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :tab-switch:jvmTest --tests '*FrameBudgetBenchmark*' --rerun
 * python3 tracing/tools/trace-summary.py /tmp/traces/tab-switch-benchmark
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
        val tracing = startTracing(File(directory, "tab-switch-benchmark"))
        try {
            val scene = ImageComposeScene(WIDTH, HEIGHT, Density(1f)) { App() }
            val driver = SceneDriver(scene)
            // While the JIT is still compiling Compose and Skiko, frames cost several times what
            // they do later. Run the whole scenario a few times first, so the measured pass shows
            // the steady state.
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

        /** The recording's run: hover and step right through every tab, then jump back home. */
        fun scenario(label: (String) -> String) {
            repeat(20) { frame(label("idle")) }
            for (tab in 1 until Tabs.size) {
                // Over the right half of the bar, which leans the pill towards the next tab.
                move(WIDTH / 2f + 40f * tab, BAR_Y)
                repeat(15) { frame(label("hovering")) }
                key(Key.DirectionRight)
                repeat(40) { frame(label("switching")) }
            }
            // The long jump back to the first tab, retargeted halfway.
            key(Key.Two)
            repeat(8) { frame(label("switching")) }
            key(Key.One)
            repeat(50) { frame(label("switching")) }
            scene.sendPointerEvent(PointerEventType.Exit, Offset(0f, 0f))
            repeat(20) { frame(label("idle")) }
        }

        fun frame(scenario: String) {
            nanos += FRAME_NANOS
            Snapshot.sendApplyNotifications()
            Tracing.section("scenario $scenario") { scene.render(nanos) }
        }

        fun move(x: Float, y: Float) = scene.sendPointerEvent(PointerEventType.Move, Offset(x, y))

        fun key(key: Key) {
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        }
    }

    private companion object {
        /** The recording's window size, and the bar's middle: halfway down, above the hint. */
        const val WIDTH = 560
        const val HEIGHT = 320
        const val BAR_Y = (HEIGHT - 50) / 2f
        const val FRAME_NANOS = 16_666_667L
        const val WARM_UP_PASSES = 8
    }
}
