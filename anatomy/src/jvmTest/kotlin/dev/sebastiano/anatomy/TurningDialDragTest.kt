package dev.sebastiano.anatomy

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.sebastiano.anatomy.figures.TurningDialFigure
import dev.sebastiano.anatomy.kit.Palette
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

@OptIn(ExperimentalComposeUiApi::class)
class TurningDialDragTest {
    @Test
    fun `a touch drag that turns the dial does not scroll the page`() {
        val scroll = ScrollState(0)
        val scene = ImageComposeScene(650, 700, Density(1f))
        try {
            scene.setContent {
                IntUiTheme(isDark = false) {
                    Column(Modifier.verticalScroll(scroll)) {
                        TurningDialFigure(
                            Palette.Light,
                            reducedMotion = true,
                            Modifier.width(650.dp),
                        )
                        Spacer(Modifier.height(2_000.dp))
                    }
                }
            }
            var time = 0L
            fun frame() {
                time += 16_000_000L
                scene.render(time)
            }
            frame()
            // Up the right-hand side of the dial: a turn, and a drag that would scroll the page.
            val x = CANVAS_LEFT + 0.66f * CANVAS_WIDTH
            val from = CANVAS_TOP + 0.55f * CANVAS_HEIGHT
            val to = CANVAS_TOP + 0.30f * CANVAS_HEIGHT
            scene.sendPointerEvent(
                PointerEventType.Press,
                Offset(x, from),
                type = PointerType.Touch,
            )
            for (step in 1..20) {
                val y = from + (to - from) * step / 20
                scene.sendPointerEvent(
                    PointerEventType.Move,
                    Offset(x, y),
                    type = PointerType.Touch,
                )
                frame()
            }
            scene.sendPointerEvent(
                PointerEventType.Release,
                Offset(x, to),
                type = PointerType.Touch,
            )
            frame()
            assertEquals(0, scroll.value, "the page scrolled under the dial")
        } finally {
            scene.close()
        }
    }

    private companion object {
        // The card's padding around its canvas, and the canvas at a 650 dp card.
        const val CANVAS_LEFT = 24f
        const val CANVAS_TOP = 52f
        const val CANVAS_WIDTH = 602f
        const val CANVAS_HEIGHT = CANVAS_WIDTH * 470f / 640f
    }
}
