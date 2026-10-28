package dev.sebastiano.stampbook

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalComposeUiApi::class)
class StampBookMotionTest {
    @Test
    fun `leaving before the stamps start moving leaves them tucked in`() {
        var motion: StampBookMotion? = null
        val scene =
            ImageComposeScene(1, 1, Density(1f)) {
                val scope = rememberCoroutineScope()
                motion = remember(scope) { StampBookMotion(scope) }
            }
        var nanos = 0L
        fun frames(count: Int) =
            repeat(count) {
                nanos += FRAME_NANOS
                scene.render(nanos).close()
            }
        frames(1)
        val book = motion!!

        // In and out again within one frame, before the pale stamp's 40 ms delay is up.
        book.show(true)
        frames(1)
        book.show(false)
        frames(120)

        assertEquals(0f, book.cover.value)
        assertEquals(0f, book.slideA.value)
        assertEquals(0f, book.slideB.value)
        scene.close()
    }

    private companion object {
        const val FRAME_NANOS = 16_666_667L
    }
}
