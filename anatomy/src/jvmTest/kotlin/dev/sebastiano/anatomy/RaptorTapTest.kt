package dev.sebastiano.anatomy

import androidx.compose.foundation.layout.width
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.sebastiano.anatomy.figures.Raptor
import dev.sebastiano.anatomy.figures.RaptorEngineFigure
import dev.sebastiano.anatomy.kit.Palette
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.skia.Image

@OptIn(ExperimentalComposeUiApi::class)
class RaptorTapTest {
    @Test
    fun `a touch tap takes the engine apart, and it stays apart after the finger lifts`() {
        val scene = ImageComposeScene(650, 1_400, Density(1f))
        try {
            // Reduced motion: no tour, and springs reach their targets in one frame.
            scene.setContent {
                IntUiTheme(isDark = false) {
                    RaptorEngineFigure(Palette.Light, reducedMotion = true, Modifier.width(650.dp))
                }
            }
            var time = 0L
            fun frames(count: Int): Image {
                var image: Image? = null
                repeat(count) {
                    time += 16_000_000L
                    image = scene.render(time)
                }
                return image!!
            }
            val before = frames(5).pixels()

            val engine = Offset(CANVAS_LEFT + CANVAS_WIDTH / 2, CANVAS_TOP + canvasHeight() / 2)
            scene.sendPointerEvent(PointerEventType.Press, engine, type = PointerType.Touch)
            val tapped = frames(5).pixels()
            assertFalse(before.contentEquals(tapped), "a tap should take the engine apart")

            scene.sendPointerEvent(PointerEventType.Release, engine, type = PointerType.Touch)
            val lifted = frames(40).pixels()
            assertTrue(tapped.contentEquals(lifted), "the engine closed when the finger lifted")
        } finally {
            scene.close()
        }
    }

    private fun Image.pixels(): ByteArray = encodeToData()!!.bytes

    private fun canvasHeight(): Float = CANVAS_WIDTH * (Raptor.H / Raptor.W).toFloat()

    private companion object {
        // The card's padding around its canvas at a 650 dp card.
        const val CANVAS_LEFT = 24f
        const val CANVAS_TOP = 52f
        const val CANVAS_WIDTH = 602f
    }
}
