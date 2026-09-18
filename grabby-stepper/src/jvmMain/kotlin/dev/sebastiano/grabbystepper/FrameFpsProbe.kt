package dev.sebastiano.grabbystepper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos

@Composable
fun FrameFpsProbe(tag: String = "window", enabled: Boolean = DEBUG_MOTION_PROBES) {
    if (!enabled) return
    LaunchedEffect(tag) {
        var frames = 0
        var windowStart = 0L
        while (true) {
            withFrameNanos { now ->
                if (windowStart == 0L) windowStart = now
                frames++
                val elapsed = now - windowStart
                if (elapsed >= 1_000_000_000L) {
                    val fps = frames * 1_000_000_000.0 / elapsed
                    System.err.println("[fps] $tag ${"%.1f".format(fps)} (frames=$frames)")
                    frames = 0
                    windowStart = now
                }
            }
        }
    }
}
