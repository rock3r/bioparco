package dev.sebastiano.bioparco.tracing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.FrameWindowScope
import java.awt.Container
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate

/**
 * Makes every frame this window renders a `frame` section, while tracing is on, so frame cadence
 * and stalls show in any specimen's trace. It wraps the window's Skiko render delegate, so it sees
 * every frame Skia renders, whichever part of the UI changed. The section covers Compose recording
 * the frame's draw calls, and any drawing done on the spot. It does not cover Skiko playing those
 * calls back: into the pixels in software, followed by handing them to the window, or on the GPU.
 * That shows as the gap to the next frame.
 */
@Composable
fun FrameWindowScope.TracedFrames() {
    DisposableEffect(window) {
        val layer = if (Tracing.isEnabled) skiaLayerIn(window) else null
        val inner = layer?.renderDelegate
        if (layer != null && inner != null) {
            layer.renderDelegate = SkikoRenderDelegate { canvas, width, height, nanoTime ->
                Tracing.section("frame") { inner.onRender(canvas, width, height, nanoTime) }
            }
        }
        onDispose { if (layer != null && inner != null) layer.renderDelegate = inner }
    }
}

private fun skiaLayerIn(container: Container): SkiaLayer? {
    for (child in container.components) {
        if (child is SkiaLayer) return child
        if (child is Container)
            skiaLayerIn(child)?.let {
                return it
            }
    }
    return null
}
