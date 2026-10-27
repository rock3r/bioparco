package dev.sebastiano.holocard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.sebastiano.bioparco.tracing.TRACE_DIR_ENVIRONMENT
import dev.sebastiano.bioparco.tracing.Tracing
import dev.sebastiano.bioparco.tracing.startTracing
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Surface
import org.junit.Assume.assumeTrue

/**
 * Traces the holo card drawn in software, to check every frame fits in a 60 Hz refresh. Skipped
 * unless `BIOPARCO_TRACE_DIR` names a directory:
 * ```
 * BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :holo-card:jvmTest --tests '*HoloCardBenchmark*' --rerun
 * python3 tracing/tools/trace-summary.py /tmp/traces/holo-card-benchmark
 * ```
 *
 * See `PERFORMANCE.md` for what the numbers mean.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class HoloCardBenchmark {
    private val directory: String? = System.getenv(TRACE_DIR_ENVIRONMENT)

    /**
     * The whole app in an `ImageComposeScene` at the recordings' window size, one `scenario …`
     * section per frame. The scene rasterises the whole window on every `render()`, in software, so
     * each section is the full CPU cost of one frame: composition, layout, recording and pixels.
     */
    @Test
    fun appFrames() =
        traced("app") {
            for (density in floatArrayOf(1f, 2f)) {
                val tag = if (density == 1f) "" else " @2x"
                val scene =
                    ImageComposeScene(
                        (WIDTH * density).toInt(),
                        (HEIGHT * density).toInt(),
                        Density(density),
                    ) {
                        App()
                    }
                val driver = SceneDriver(scene, density)
                driver.warmUp()
                repeat(60) { driver.frame("idle$tag") }
                repeat(120) {
                    val angle = it / 120f * 2f * Math.PI.toFloat()
                    driver.move("hover$tag", CARD_X + cos(angle) * 140f, CARD_Y + sin(angle) * 200f)
                }
                driver.press("drag$tag", CARD_X, CARD_Y)
                repeat(60) { driver.move("drag$tag", CARD_X + it * 3f, CARD_Y - it * 2f) }
                driver.release("wobble$tag", CARD_X + 180f, CARD_Y - 120f)
                repeat(60) { driver.frame("wobble$tag") }
                driver.click(THUMB_X + THUMB_PITCH, THUMB_Y)
                repeat(60) { driver.frame("swap$tag") }
                driver.move("away$tag", 40f, 40f)
                driver.key(Key.Spacebar)
                repeat(180) { driver.frame("autopilot$tag") }
                driver.key(Key.Spacebar)
                scene.close()
            }
        }

    /** The foil program alone on a raster surface, where Skia draws its pixels at call time. */
    @Test
    fun rasterFoil() =
        traced("raster") {
            val reliefs = Reliefs.bake()
            for (size in intArrayOf(196, 392)) {
                val surface = Surface.makeRasterN32Premul(size, size)
                for (friend in Friend.entries) {
                    for (turned in listOf(false, true)) {
                        val label =
                            "raster foil ${friend.title} ${size}px" + if (turned) ", turned" else ""
                        repeat(60) {
                            val t = it / 60f
                            val canvas = surface.canvas
                            canvas.save()
                            if (turned) turn(canvas, size / 2f, TURN_DEGREES)
                            Tracing.section(label) {
                                HoloFoil.draw(
                                    canvas,
                                    reliefs[friend.mascot],
                                    size.toFloat(),
                                    friend.hue,
                                    sin(t * 6f),
                                    cos(t * 4f),
                                )
                                surface.flushAndSubmit(syncCpu = true)
                            }
                            canvas.restore()
                        }
                    }
                }
                surface.close()
            }
        }

    /** Turns [canvas] about the vertical axis through [centre], in perspective, like the card. */
    private fun turn(canvas: Canvas, centre: Float, degrees: Float) {
        val radians = Math.toRadians(degrees.toDouble())
        canvas.translate(centre, centre)
        canvas.concat(
            Matrix33(
                cos(radians).toFloat(),
                0f,
                0f,
                0f,
                1f,
                0f,
                (sin(radians) / CAMERA).toFloat(),
                0f,
                1f,
            )
        )
        canvas.translate(-centre, -centre)
    }

    /**
     * What each part of the card costs in software, as whole-window `ImageComposeScene` frames at
     * density 1: the card flat and turned, with and without its foil, and with the clip the face no
     * longer needs. The cases are rendered round-robin, a few frames each, so a slow spell on a
     * shared machine lands on all of them alike. Writes `build/reports/holo-card-cost-ladder.md`.
     */
    @Test
    fun costLadder() {
        assumeTrue("Set $TRACE_DIR_ENVIRONMENT to run the benchmark", directory != null)
        val reliefs = Reliefs.bake()
        val turned = Tilt(9f, -12f)
        val cases =
            listOf(
                "Empty window" to
                    ladderScene { Box(Modifier.fillMaxSize().background(Color.White)) },
                "Card, flat, no foil" to ladderScene { LadderCard(null, null, clip = false) },
                "Card, flat, foil" to ladderScene { LadderCard(reliefs, null, clip = false) },
                "Card, turned, no foil" to ladderScene { LadderCard(null, turned, clip = false) },
                "Card, turned, foil" to ladderScene { LadderCard(reliefs, turned, clip = false) },
                "Card, turned, foil, clipped to its corners" to
                    ladderScene { LadderCard(reliefs, turned, clip = true) },
                "The app at rest" to ladderScene { App() },
            )
        var nanos = 0L
        repeat(60) {
            for ((_, scene) in cases) scene.render(nanos).close()
            nanos += FRAME_NANOS
            Thread.sleep(10)
        }
        val times = cases.associate { it.first to mutableListOf<Double>() }
        repeat(LADDER_ROUNDS) {
            for ((name, scene) in cases) {
                repeat(LADDER_FRAMES) {
                    val start = System.nanoTime()
                    scene.render(nanos).close()
                    nanos += FRAME_NANOS
                    times.getValue(name) += (System.nanoTime() - start) / 1e6
                }
            }
        }
        cases.forEach { it.second.close() }
        val report = buildString {
            appendLine("| Case | min | median | p95 (ms) |")
            appendLine("|---|---|---|---|")
            for ((name, list) in times) {
                list.sort()
                appendLine(
                    "| $name | %.2f | %.2f | %.2f |"
                        .format(list.first(), list[list.size / 2], list[list.size * 95 / 100])
                )
            }
        }
        println(report)
        File("build/reports").mkdirs()
        File("build/reports/holo-card-cost-ladder.md").writeText(report)
    }

    private fun ladderScene(content: @Composable () -> Unit) =
        ImageComposeScene(WIDTH, HEIGHT, Density(1f), content = content)

    /** One card in the middle of the window, as the stage lays it out, optionally [turned]. */
    @Composable
    private fun LadderCard(reliefs: Reliefs?, turned: Tilt?, clip: Boolean) {
        IntUiTheme(isDark = false) {
            Box(Modifier.fillMaxSize().background(Color(0xFFF6F6F4)), Alignment.Center) {
                CardFace(
                    friend = Friend.Bloomie,
                    reliefs = reliefs,
                    tilt = { turned ?: Tilt.Flat },
                    modifier =
                        Modifier.size(300.dp, 430.dp).graphicsLayer {
                            rotationX = turned?.x ?: 0f
                            rotationY = turned?.y ?: 0f
                            cameraDistance = 14f * density
                            if (clip) {
                                shape = RoundedCornerShape(CardCorner)
                                this.clip = true
                            }
                        },
                )
            }
        }
    }

    private fun traced(name: String, block: () -> Unit) {
        assumeTrue("Set $TRACE_DIR_ENVIRONMENT to run the benchmark", directory != null)
        val tracing = startTracing(File(directory, "holo-card-benchmark/$name"))
        try {
            block()
        } finally {
            tracing.close()
        }
    }

    /** Renders frames of [scene] 16 ms apart, each one a section named after its scenario. */
    private class SceneDriver(private val scene: ImageComposeScene, private val density: Float) {
        private var nanos = 0L

        fun frame(scenario: String) {
            nanos += FRAME_NANOS
            Tracing.section("scenario $scenario") { scene.render(nanos).close() }
        }

        /** The reliefs bake on a background thread: give it real time, a frame at a time. */
        fun warmUp() =
            repeat(60) {
                Thread.sleep(15)
                frame("warm-up")
            }

        fun move(scenario: String, x: Float, y: Float) {
            scene.sendPointerEvent(PointerEventType.Move, Offset(x, y) * density)
            frame(scenario)
        }

        fun press(scenario: String, x: Float, y: Float) {
            move(scenario, x, y)
            scene.sendPointerEvent(PointerEventType.Press, Offset(x, y) * density)
            frame(scenario)
        }

        fun release(scenario: String, x: Float, y: Float) {
            scene.sendPointerEvent(PointerEventType.Release, Offset(x, y) * density)
            frame(scenario)
        }

        fun click(x: Float, y: Float) {
            scene.sendPointerEvent(PointerEventType.Move, Offset(x, y) * density)
            scene.sendPointerEvent(PointerEventType.Press, Offset(x, y) * density)
            scene.sendPointerEvent(PointerEventType.Release, Offset(x, y) * density)
        }

        fun key(key: Key) {
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        }
    }

    private companion object {
        const val WIDTH = 980
        const val HEIGHT = 740
        const val CARD_X = 490f
        const val CARD_Y = 322f
        const val THUMB_X = 430f
        const val THUMB_Y = 677f
        const val THUMB_PITCH = 40f
        const val FRAME_NANOS = 16_000_000L
        const val LADDER_ROUNDS = 40
        const val LADDER_FRAMES = 5
        const val TURN_DEGREES = 14f

        /** Compose's default camera distance for a layer, 8 × 72 points, in pixels at density 1. */
        const val CAMERA = 1008.0
    }
}
