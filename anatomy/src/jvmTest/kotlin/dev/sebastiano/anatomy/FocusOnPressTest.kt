package dev.sebastiano.anatomy

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.sebastiano.anatomy.kit.focusOnPress
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class FocusOnPressTest {
    @Test
    fun `a press focuses the figure, so its keys work after a click`() {
        var focused = false
        val scene = ImageComposeScene(40, 40, Density(1f))
        try {
            scene.setContent {
                Box(
                    Modifier.size(40.dp)
                        .focusOnPress()
                        .onFocusChanged { focused = it.isFocused }
                        .focusable()
                )
            }
            scene.render(0L)
            assertFalse(focused)

            scene.sendPointerEvent(PointerEventType.Press, Offset(20f, 20f))
            scene.sendPointerEvent(PointerEventType.Release, Offset(20f, 20f))
            scene.render(16_000_000L)
            assertTrue(focused, "expected a press to focus it")
        } finally {
            scene.close()
        }
    }
}
