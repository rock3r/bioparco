package dev.sebastiano.anatomy

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import dev.sebastiano.anatomy.kit.rememberFrameLoop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrameLoopTest {
    @Test
    fun `a figure off screen asks for no frames and resumes where it was`() {
        val onScreen = mutableStateOf(true)
        var ticks = 0
        val dts = mutableListOf<Double>()
        val scene = ImageComposeScene(40, 40, Density(1f))
        try {
            scene.setContent {
                rememberFrameLoop(calm = false, isOnScreen = { onScreen.value }) { dt, _ ->
                    ticks++
                    dts += dt
                    true
                }
            }
            var time = 0L
            fun frames(count: Int) =
                repeat(count) {
                    time += FRAME
                    scene.render(time)
                }

            frames(10)
            val before = ticks
            assertTrue(before >= 9, "expected the loop to tick every frame, got $before")

            onScreen.value = false
            frames(1) // The frame that sees the change may still tick once.
            val paused = ticks
            frames(30)
            assertEquals(paused, ticks, "an off-screen loop must not tick")
            assertEquals(
                false,
                scene.hasInvalidations(),
                "an off-screen loop must not ask for frames",
            )

            onScreen.value = true
            frames(10)
            assertTrue(ticks >= paused + 9, "expected the loop to resume, got ${ticks - paused}")
            assertTrue(dts.all { it <= 0.05 }, "time spent off screen must not reach dt: $dts")
        } finally {
            scene.close()
        }
    }

    @Test
    fun `a loop that settled under reduced motion moves again when it is turned off`() {
        val calm = mutableStateOf(true)
        var ticks = 0
        val scene = ImageComposeScene(40, 40, Density(1f))
        try {
            scene.setContent {
                // Like a flame: still while calm, always moving otherwise.
                rememberFrameLoop(calm = calm.value) { _, isCalm ->
                    ticks++
                    !isCalm
                }
            }
            var time = 0L
            fun frames(count: Int) =
                repeat(count) {
                    time += FRAME
                    scene.render(time)
                }

            frames(5)
            assertEquals(1, ticks, "a calm loop settles on its first frame")

            calm.value = false
            frames(10)
            assertTrue(ticks >= 9, "expected the loop to start again, got ${ticks - 1} ticks")
        } finally {
            scene.close()
        }
    }

    private companion object {
        const val FRAME = 16_666_667L
    }
}
