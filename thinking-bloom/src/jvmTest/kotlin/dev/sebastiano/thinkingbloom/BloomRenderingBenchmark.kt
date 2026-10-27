package dev.sebastiano.thinkingbloom

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import dev.sebastiano.bioparco.tracing.TRACE_DIR_ENVIRONMENT
import dev.sebastiano.bioparco.tracing.Tracing
import dev.sebastiano.bioparco.tracing.startTracing
import java.io.File
import kotlin.test.Test
import org.jetbrains.skia.Surface
import org.junit.Assume.assumeTrue

/**
 * Traces the whole app drawn in software, a frame every 60th of a second over two loops. An
 * `ImageComposeScene` records and rasterises each frame on the spot, so a `scene …` section is the
 * full cost of a frame's pixels, which a window's `frame` section does not show. Each section is
 * named after where the band is. Skipped unless `BIOPARCO_TRACE_DIR` names a directory:
 * ```
 * BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :thinking-bloom:jvmTest --tests '*BloomRenderingBenchmark*' --rerun
 * python3 tracing/tools/trace-summary.py /tmp/traces/thinking-bloom-benchmark
 * ```
 */
@OptIn(ExperimentalComposeUiApi::class)
class BloomRenderingBenchmark {
    private val directory: String? = System.getenv(TRACE_DIR_ENVIRONMENT)

    @Test
    fun sceneFrames() {
        assumeTrue("Set $TRACE_DIR_ENVIRONMENT to run the benchmark", directory != null)
        val tracing = startTracing(File(directory, "thinking-bloom-benchmark"))
        try {
            val scene = ImageComposeScene(WIDTH, HEIGHT, Density(1f)) { App() }
            // Warm up: the first frames lay out the label and scatter its blossoms.
            repeat(WARM_UP) { scene.render(it * FRAME_NANOS) }
            val start = WARM_UP * FRAME_NANOS
            val frames = (2 * BloomMath.LOOP_SECONDS * FPS).toInt()
            for (frame in 0..frames) {
                // The stage's clock starts at its first frame, so it reads this frame's offset.
                val seconds = (frame + WARM_UP) / FPS.toFloat()
                Tracing.section("scene ${stage(seconds)}") {
                    scene.render(start + frame * FRAME_NANOS)
                }
            }
            scene.close()
        } finally {
            tracing.close()
        }
    }

    /**
     * The stage straight onto a raster surface at full bloom, where Skia draws at call time, so the
     * sections time the blossoms' pixels rather than the recording of their draw calls.
     */
    @Test
    fun rasterBloom() {
        assumeTrue("Set $TRACE_DIR_ENVIRONMENT to run the benchmark", directory != null)
        val tracing = startTracing(File(directory, "thinking-bloom-benchmark-raster"))
        try {
            val density = Density(1f)
            val measurer = TextMeasurer(createFontFamilyResolver(), density, LayoutDirection.Ltr)
            val style = TextStyle(fontSize = 112.sp, fontWeight = FontWeight.Medium)
            val label = buildBloomLabel("Thinking", style, measurer, density)
            val surface = Surface.makeRasterN32Premul(WIDTH, HEIGHT)
            val canvas = surface.canvas.asComposeCanvas()
            val size = Size(WIDTH.toFloat(), HEIGHT.toFloat())
            val sprites = FlowerSprites()
            // The middle of a sweep, when the most blossoms are open.
            val seconds = BloomMath.SWEEP_SECONDS * 0.42f
            println("blossoms: ${label.blossoms.size}")
            // One pass to draw the sprites, as the first sweep in a window does.
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, canvas, size) {
                drawBloom(label, seconds, sprites)
            }
            repeat(RASTER_FRAMES) {
                Tracing.section("raster full bloom") {
                    surface.canvas.clear(-1)
                    CanvasDrawScope().draw(density, LayoutDirection.Ltr, canvas, size) {
                        drawBloom(label, seconds + it / FPS.toFloat(), sprites)
                    }
                }
            }
            surface.close()
        } finally {
            tracing.close()
        }
    }

    /** Where the band is on the label at [seconds]: the stages a viewer sees. */
    private fun stage(seconds: Float): String {
        val front = BloomMath.front(seconds)
        return when {
            front < 0f -> "plain"
            front < BloomMath.BAND * 0.5f -> "arriving"
            front < 1f + BloomMath.BAND * 0.5f -> "full bloom"
            front < 1f + BloomMath.BAND -> "leaving"
            else -> "plain"
        }
    }

    private companion object {
        const val WIDTH = 960
        const val HEIGHT = 572
        const val FPS = 60
        const val FRAME_NANOS = 1_000_000_000L / FPS
        const val WARM_UP = 10
        const val RASTER_FRAMES = 30
    }
}
