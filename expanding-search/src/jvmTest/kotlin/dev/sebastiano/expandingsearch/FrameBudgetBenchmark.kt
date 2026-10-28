package dev.sebastiano.expandingsearch

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshots.Snapshot
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
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Assume.assumeTrue

/**
 * Traces whole frames of the specimen drawn in software, to check they fit a 60 Hz frame. Skipped
 * unless `BIOPARCO_TRACE_DIR` names a directory:
 * ```
 * BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :expanding-search:jvmTest --tests '*FrameBudgetBenchmark*' --rerun
 * python3 tracing/tools/trace-summary.py /tmp/traces/expanding-search-benchmark
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
        val tracing = startTracing(File(directory, "expanding-search-benchmark"))
        try {
            val query = TextFieldState()
            val scene =
                ImageComposeScene(WIDTH, HEIGHT, Density(1f)) {
                    IntUiTheme(isDark = false) { ExpandingSearchStage(query = query) }
                }
            val driver = SceneDriver(scene, query)
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
    private class SceneDriver(
        private val scene: ImageComposeScene,
        private val query: TextFieldState,
    ) {
        private var nanos = 0L

        /** The recording's run: open, hover down, type "to", clear, find nothing, clear, close. */
        fun scenario(label: (String) -> String) {
            repeat(30) { frame(label("idle")) }
            click(CENTRE_X, PILL_Y)
            repeat(30) { frame(label("opening")) }
            for (row in 1..3) {
                move(CENTRE_X - 60f, rowY(row))
                repeat(15) { frame(label("hovering")) }
            }
            move(CENTRE_X, PILL_Y)
            repeat(15) { frame(label("hovering")) }
            type("t")
            repeat(24) { frame(label("typing")) }
            type("to")
            repeat(24) { frame(label("typing")) }
            type("")
            repeat(24) { frame(label("clearing")) }
            type("zz")
            repeat(24) { frame(label("no match")) }
            type("")
            repeat(24) { frame(label("clearing")) }
            click(CROSS_X, PILL_Y)
            repeat(30) { frame(label("closing")) }
        }

        fun frame(scenario: String) {
            nanos += FRAME_NANOS
            Snapshot.sendApplyNotifications()
            Tracing.section("scenario $scenario") { scene.render(nanos) }
        }

        fun type(text: String) {
            if (text.isEmpty()) query.clearText() else query.edit { replace(0, length, text) }
        }

        fun move(x: Float, y: Float) = scene.sendPointerEvent(PointerEventType.Move, Offset(x, y))

        fun click(x: Float, y: Float) {
            move(x, y)
            scene.sendPointerEvent(PointerEventType.Press, Offset(x, y))
            scene.sendPointerEvent(PointerEventType.Release, Offset(x, y))
        }

        /**
         * A row's middle: the pill is 120dp down and 54dp tall, then an 8dp gap and 5dp padding.
         */
        fun rowY(row: Int) = 120f + 54f + 8f + 5f + 39f * (row + 0.5f)
    }

    private companion object {
        /** The recording's window size. */
        const val WIDTH = 720
        const val HEIGHT = 560
        const val CENTRE_X = WIDTH / 2f
        const val PILL_Y = 120f + 27f

        /** The cross: 25dp in from the full pill's right end, which is 263dp wide. */
        const val CROSS_X = CENTRE_X + 263f / 2f - 25f
        const val FRAME_NANOS = 16_666_667L
        const val WARM_UP_PASSES = 8
    }
}
