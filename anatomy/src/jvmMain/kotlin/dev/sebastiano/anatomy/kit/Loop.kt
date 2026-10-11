// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlin.math.min

/**
 * The original's `useLoop`: runs [tick] once a frame, with `dt` in seconds clamped to
 * [LONGEST_FRAME], for as long as it returns true. It starts on first composition and stops when
 * the figure leaves composition, so a figure at rest or scrolled away costs nothing. Call the
 * returned function to start it again after it settled.
 */
@Composable
fun rememberFrameLoop(calm: Boolean, tick: (dt: Double, calm: Boolean) -> Boolean): () -> Unit {
    val latestTick by rememberUpdatedState(tick)
    val latestCalm by rememberUpdatedState(calm)
    var kicks by remember { mutableIntStateOf(1) }
    val running = remember { BooleanArray(1) }
    LaunchedEffect(kicks) {
        running[0] = true
        var last = 0L
        try {
            while (true) {
                val more = withFrameNanos { now ->
                    val dt = if (last == 0L) 1.0 / 60 else min((now - last) / 1e9, LONGEST_FRAME)
                    last = now
                    latestTick(dt, latestCalm)
                }
                if (!more) break
            }
        } finally {
            running[0] = false
        }
    }
    return remember { { if (!running[0]) kicks++ } }
}
