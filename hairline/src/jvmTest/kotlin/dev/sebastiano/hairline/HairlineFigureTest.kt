package dev.sebastiano.hairline

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * The Compose host, end to end and offscreen: pointer positions reach the figure in viewBox units,
 * frames run while it moves, a mouse leaving puts it back, and the drawing has ink in it.
 */
class HairlineFigureTest {
    @Test
    fun terrainAnswersThePointerAndRests() {
        var caption by mutableStateOf("")
        val scene =
            ImageComposeScene(WIDTH, HEIGHT, Density(1f)) {
                HairlineFigure(Figure.Terrain, Modifier, onRead = { caption = it })
            }
        try {
            var t = 0L
            fun frames(n: Int) = repeat(n) { scene.render(t).also { t += FRAME_NS } }
            frames(5)
            assertEquals("rest", caption)
            val rest = scene.render(t)
            assertTrue(inked(rest) > 0.02, "the figure should draw something")
            save(rest, "terrain-rest")

            // The middle of a 400 × 320 stage is viewBox (200, 160), which the original reads as
            // cell 2·2 (its "centre" checkpoint in the parity golden).
            scene.sendPointerEvent(PointerEventType.Enter, Offset(WIDTH / 2f, HEIGHT / 2f))
            scene.sendPointerEvent(PointerEventType.Move, Offset(WIDTH / 2f, HEIGHT / 2f))
            frames(40)
            assertEquals("cell 2·2", caption)
            val raised = scene.render(t)
            save(raised, "terrain-raised")
            assertTrue(differs(rest, raised), "the pillars should have risen")

            scene.sendPointerEvent(PointerEventType.Exit, Offset(-10f, -10f))
            frames(5)
            assertEquals("rest", caption)
        } finally {
            scene.close()
        }
    }

    @Test
    fun reducedMotionBelongsToEachFigure() {
        // Turntable's caption reads its elevation spring, which follows the pointer's height: one
        // frame after a move, a reduced figure already reads the target and a moving one does not.
        val reduced = captionOneFrameAfterMove(reducedMotion = true, neighbourReduced = null)
        val moving = captionOneFrameAfterMove(reducedMotion = false, neighbourReduced = null)
        assertTrue(
            reduced != moving,
            "the move should change the caption only when reduced: $reduced",
        )
        // A reduced neighbour, composed after it, must not make the first figure jump.
        assertEquals(
            moving,
            captionOneFrameAfterMove(reducedMotion = false, neighbourReduced = true),
        )
        assertEquals(
            reduced,
            captionOneFrameAfterMove(reducedMotion = true, neighbourReduced = false),
        )
    }

    private fun captionOneFrameAfterMove(
        reducedMotion: Boolean,
        neighbourReduced: Boolean?,
    ): String {
        var caption = ""
        val scene =
            ImageComposeScene(WIDTH, 2 * HEIGHT, Density(1f)) {
                Column {
                    HairlineFigure(
                        Figure.Turntable,
                        Modifier.width(WIDTH.dp),
                        reducedMotion = reducedMotion,
                        onRead = { caption = it },
                    )
                    if (neighbourReduced != null) {
                        HairlineFigure(
                            Figure.Turntable,
                            Modifier.width(WIDTH.dp),
                            reducedMotion = neighbourReduced,
                        )
                    }
                }
            }
        try {
            var t = 0L
            repeat(5) { scene.render(t).also { t += FRAME_NS } }
            // the bottom of the first figure: the elevation spring's target is far from its rest
            val p = Offset(WIDTH / 2f, HEIGHT * 0.95f)
            scene.sendPointerEvent(PointerEventType.Enter, p)
            scene.sendPointerEvent(PointerEventType.Move, p)
            scene.render(t)
            return caption
        } finally {
            scene.close()
        }
    }

    @Test
    fun everyFigureDrawsInBothPalettes() {
        for (figure in Figure.entries) {
            for (dark in listOf(false, true)) {
                val scene =
                    ImageComposeScene(WIDTH, HEIGHT, Density(1f)) {
                        HairlineFigure(figure, Modifier, dark = dark)
                    }
                try {
                    scene.render(0L)
                    val image = scene.render(FRAME_NS)
                    save(image, "${figure.id}-${if (dark) "dark" else "light"}")
                    assertTrue(inked(image) > 0.01, "${figure.id} should draw something")
                } finally {
                    scene.close()
                }
            }
        }
    }

    /** The share of pixels that are not transparent. */
    private fun inked(image: Image): Double {
        val pixels = image.toPixels()
        return pixels.count { it.toInt() and 0xFF000000.toInt() != 0 }.toDouble() / pixels.size
    }

    private fun differs(a: Image, b: Image): Boolean = !a.toPixels().contentEquals(b.toPixels())

    private fun Image.toPixels(): IntArray {
        val bitmap = org.jetbrains.skia.Bitmap.makeFromImage(this)
        val bytes = bitmap.readPixels() ?: return IntArray(0)
        return IntArray(bytes.size / 4) { i ->
            (bytes[i * 4 + 3].toInt() and 0xFF shl 24) or (bytes[i * 4].toInt() and 0xFF)
        }
    }

    private fun save(image: Image, name: String) {
        val dir = File(System.getenv("HAIRLINE_RENDERS") ?: return)
        dir.mkdirs()
        val data = image.encodeToData(EncodedImageFormat.PNG) ?: return
        File(dir, "$name.png").writeBytes(data.bytes)
    }

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 320
        const val FRAME_NS = 16_666_667L
    }
}
