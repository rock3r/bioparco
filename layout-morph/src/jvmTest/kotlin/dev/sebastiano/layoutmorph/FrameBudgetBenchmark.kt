package dev.sebastiano.layoutmorph

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
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
 * BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :layout-morph:jvmTest --tests '*FrameBudgetBenchmark*' --rerun
 * python3 tracing/tools/trace-summary.py /tmp/traces/layout-morph-benchmark
 * ```
 *
 * An `ImageComposeScene` draws every frame from scratch onto a CPU surface, so each `scenario …`
 * section is composition, layout, drawing and Skia's rasterising together: the most a software
 * frame of this specimen can cost. Each rendered image is closed straight away: left open, it makes
 * the next frame copy the whole surface.
 */
@OptIn(ExperimentalComposeUiApi::class)
class FrameBudgetBenchmark {
    private val directory: String? = System.getenv(TRACE_DIR_ENVIRONMENT)

    @Test
    fun appFrames() {
        assumeTrue("Set $TRACE_DIR_ENVIRONMENT to run the benchmark", directory != null)
        val tracing = startTracing(File(directory, "layout-morph-benchmark"))
        try {
            val motion = MorphMotion(ShopCollection.pieces)
            val scene = ImageComposeScene(WIDTH, HEIGHT, Density(1f)) { App(motion) }
            val driver = SceneDriver(scene, motion)
            // The JIT is still compiling Compose and Skiko for the first few thousand frames.
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
        private val motion: MorphMotion,
    ) {
        private var nanos = 0L

        /** Every flight between arrangements, a carousel drag and a thrown helix. */
        fun scenario(label: (String) -> String) {
            repeat(30) { frame(label("grid")) }
            for (next in
                listOf(
                    Arrangement.Masonry,
                    Arrangement.List,
                    Arrangement.Stack,
                    Arrangement.Carousel,
                )) {
                motion.select(next)
                repeat(FLIGHT_FRAMES) { frame(label("to ${next.label.lowercase()}")) }
            }
            repeat(20) {
                motion.drag(-14f)
                frame(label("carousel drag"))
            }
            motion.release(-600f)
            repeat(40) { frame(label("carousel settle")) }
            motion.select(Arrangement.Helix)
            repeat(FLIGHT_FRAMES) { frame(label("to helix")) }
            repeat(60) { frame(label("helix drift")) }
            repeat(10) {
                motion.drag(26f)
                frame(label("helix throw"))
            }
            motion.release(1_400f)
            repeat(90) { frame(label("helix spin")) }
            motion.select(Arrangement.Grid)
            repeat(FLIGHT_FRAMES) { frame(label("to grid")) }
        }

        fun frame(scenario: String) {
            nanos += FRAME_NANOS
            Tracing.section("scenario $scenario") { scene.render(nanos).close() }
        }
    }

    private companion object {
        /** The recording's window size, less its title bar. */
        const val WIDTH = 960
        const val HEIGHT = 630
        const val FRAME_NANOS = 16_666_667L
        const val FLIGHT_FRAMES = 75
        const val WARM_UP_PASSES = 6
    }
}
