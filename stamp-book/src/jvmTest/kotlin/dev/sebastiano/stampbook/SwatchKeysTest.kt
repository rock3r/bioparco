package dev.sebastiano.stampbook

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

// Building key events by hand is internal API; a test scene has no window to type into.
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class SwatchKeysTest {
    @Test
    fun `space on a focused swatch picks its colour instead of opening the book`() {
        // Tab to the gold swatch, then the magenta one.
        val probe = afterKeys(listOf(Key.Tab, Key.Tab, Key.Spacebar))
        val (red, green, blue) = probe
        assertTrue(red > 150 && green < 120 && blue > 100, "expected a shut magenta cover, $probe")
    }

    @Test
    fun `space on the stage still opens the book`() {
        val probe = afterKeys(listOf(Key.Spacebar))
        assertEquals(PALE_STAMP, probe, "expected the pale stamp slid out")
    }

    /** Presses [keys] on a fresh specimen, lets it settle, and reads the probe pixel's RGB. */
    private fun afterKeys(keys: List<Key>): Triple<Int, Int, Int> {
        val scene = ImageComposeScene(WIDTH, HEIGHT, Density(1f)) { App() }
        var nanos = 0L
        fun frames(count: Int) =
            repeat(count) {
                nanos += FRAME_NANOS
                scene.render(nanos).close()
            }
        // The art bakes on a background thread.
        frames(1)
        Thread.sleep(1_500)
        frames(30)
        for (key in keys) {
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
            frames(2)
        }
        frames(90)
        val image = scene.render(nanos)
        val colour = Bitmap.makeFromImage(image).getColor(PROBE_X, PROBE_Y)
        image.close()
        scene.close()
        return Triple((colour shr 16) and 0xFF, (colour shr 8) and 0xFF, colour and 0xFF)
    }

    private companion object {
        const val WIDTH = 800
        const val HEIGHT = 600
        const val FRAME_NANOS = 16_666_667L

        // The stage has focus at start. The book's top-left is at (180, 138); 200 across and 150
        // down is on the shut cover, and on the pale stamp once it has slid out.
        const val PROBE_X = 380
        const val PROBE_Y = 288

        /** The pale stamp's paper. */
        val PALE_STAMP = Triple(0xEE, 0xE8, 0xD9)
    }
}
