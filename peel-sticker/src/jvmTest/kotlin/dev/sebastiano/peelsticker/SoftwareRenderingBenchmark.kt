package dev.sebastiano.peelsticker

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.sebastiano.bioparco.tracing.TRACE_DIR_ENVIRONMENT
import dev.sebastiano.bioparco.tracing.Tracing
import dev.sebastiano.bioparco.tracing.startTracing
import java.io.File
import kotlin.math.round
import kotlin.test.Test
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.junit.Assume.assumeTrue

/**
 * Traces the peel sticker drawn in software, the way the optimisation in `PERFORMANCE.md` was
 * measured. Skipped unless `BIOPARCO_TRACE_DIR` names a directory:
 * ```
 * BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :peel-sticker:jvmTest --tests '*SoftwareRenderingBenchmark*' --rerun
 * python3 tracing/tools/trace-summary.py /tmp/traces/peel-sticker-benchmark
 * ```
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class SoftwareRenderingBenchmark {
    private val directory: String? = System.getenv(TRACE_DIR_ENVIRONMENT)

    /**
     * The whole app in an `ImageComposeScene` at the recordings' window size, one `scenario …`
     * section per rendered frame: idle, hovering with Prism, peeling, letting go, swapping the
     * picture, then Halftone, Ripple and Sparkle. The scene redraws everything on every frame, so
     * compare scenarios with each other, not with a window's frame rate.
     */
    @Test
    fun appFrames() =
        traced("app") {
            val scene = ImageComposeScene(WIDTH, HEIGHT, Density(1f)) { App() }
            val driver = SceneDriver(scene)
            driver.waitForSticker()
            repeat(20) { driver.frame("idle") }
            repeat(40) { driver.move("hover", 330f + it * 2, 280f) }
            driver.press("peel", GRAB_X, GRAB_Y)
            repeat(40) { driver.move("peel", GRAB_X - it * 7f, GRAB_Y + it * 7f) }
            driver.release("release", 260f, 430f)
            repeat(40) { driver.frame("release") }
            driver.key(Key.P)
            repeat(8) { driver.frame("swap") }
            driver.waitForSticker()
            driver.key(Key.Five)
            repeat(30) { driver.move("halftone", 430f + it, 300f) }
            driver.key(Key.Four)
            repeat(30) { driver.move("ripple", 430f, 300f + it) }
            driver.key(Key.Two)
            repeat(30) { driver.move("sparkle", 430f - it, 300f) }
            scene.close()
        }

    /**
     * The renderer straight onto a raster surface, where Skia draws at call time, so each layer's
     * section times its pixels, not the recording of its draw calls.
     */
    @Test
    fun rasterLayers() =
        traced("raster") {
            val measurer =
                TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)
            val texture = printSticker(StickerPicture.Ex, measurer, FontFamily.Default)
            val renderer = StickerRenderer(texture)
            val surface = Surface.makeRasterN32Premul(WIDTH, STAGE_HEIGHT)
            val bounds = Rect.makeWH(WIDTH.toFloat(), STAGE_HEIGHT.toFloat())
            val stage = RasterStage(texture)
            val fold = stage.fold(grabX = 518f, grabY = 112f, pointerX = 300f, pointerY = 330f)
            val cases =
                listOf(
                    "still" to stage.frame(null, ShineMode.Prism, shine = 0f),
                    "still, Prism" to stage.frame(null, ShineMode.Prism, shine = 1f),
                    "still, Halftone" to stage.frame(null, ShineMode.Halftone, shine = 1f),
                    "peeling" to stage.frame(fold, ShineMode.Prism, shine = 0f),
                    "peeling, Prism" to stage.frame(fold, ShineMode.Prism, shine = 1f),
                )
            for ((label, frame) in cases) {
                repeat(12) {
                    Tracing.section("raster $label") {
                        renderer.draw(surface.canvas, bounds, frame)
                    }
                }
            }
            // The shadow a GPU window gets, drawn here in software to see what it would cost.
            val peeling = stage.frame(fold, ShineMode.Prism, shine = 0f)
            repeat(12) {
                Tracing.section("raster peeling, shadow as a layer") {
                    renderer.draw(surface.canvas, bounds, peeling, gpu = true)
                }
            }
            renderer.close()
            texture.close()
            surface.close()
        }

    private fun traced(name: String, block: () -> Unit) {
        assumeTrue("Set $TRACE_DIR_ENVIRONMENT to run the benchmark", directory != null)
        val tracing = startTracing(File(directory, "peel-sticker-benchmark/$name"))
        try {
            block()
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

        /** The sticker prints on a background thread: give it real time, a frame at a time. */
        fun waitForSticker() =
            repeat(60) {
                Thread.sleep(20)
                frame("warm-up")
            }

        fun move(scenario: String, x: Float, y: Float) {
            scene.sendPointerEvent(PointerEventType.Move, Offset(x, y))
            frame(scenario)
        }

        fun press(scenario: String, x: Float, y: Float) {
            move(scenario, x, y)
            scene.sendPointerEvent(PointerEventType.Press, Offset(x, y))
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

    /** The sticker where the stage in a [WIDTH] × [HEIGHT] window puts it, at density 1. */
    private class RasterStage(private val texture: StickerTexture) {
        private val texScale = StickerTexture.SIZE / TEXTURE_PIXELS
        // Whole pixels, as the stage rounds them: the plain sticker is then a copy.
        private val originX = round((WIDTH - TEXTURE_PIXELS) / 2f)
        private val originY = round((STAGE_HEIGHT - TEXTURE_PIXELS) / 2f)

        fun fold(grabX: Float, grabY: Float, pointerX: Float, pointerY: Float): PeelFold? =
            peelFold(grabX, grabY, pointerX, pointerY, ::extent, StickerTexture.CUT / texScale)

        fun frame(fold: PeelFold?, mode: ShineMode, shine: Float) =
            StickerFrame(originX, originY, texScale, fold, mode, 500f, 500f, shine, 1f, 1f)

        private fun extent(dx: Float, dy: Float): Float =
            originX * dx +
                originY * dy +
                texture.silhouette.extent(dx, dy) * StickerTexture.CELL / texScale
    }

    private companion object {
        const val WIDTH = 760
        const val HEIGHT = 690
        const val STAGE_HEIGHT = 552
        const val TEXTURE_PIXELS = 377f

        /** The top right tip of the X at this window size, just inside its die-cut. */
        const val GRAB_X = 518f
        const val GRAB_Y = 192f
        const val FRAME_NANOS = 16_000_000L
    }
}
