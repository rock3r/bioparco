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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import dev.sebastiano.bioparco.tracing.Tracing
import kotlin.math.min
import kotlinx.coroutines.flow.first

/**
 * The original's `useLoop`: runs [tick] once a frame, with `dt` in seconds clamped to
 * [LONGEST_FRAME], for as long as it returns true. It starts on first composition and stops when
 * the figure leaves composition. While [isOnScreen] says no, it waits without asking for frames,
 * then carries on where it was, so a figure at rest or scrolled away costs nothing. [isOnScreen]
 * must read snapshot state, so the loop hears when it changes. Each step is a [trace] section. Call
 * the returned function to start the loop again after it settled.
 */
@Composable
fun rememberFrameLoop(
    calm: Boolean,
    isOnScreen: () -> Boolean = { true },
    trace: String = "tick",
    tick: (dt: Double, calm: Boolean) -> Boolean,
): () -> Unit {
    val latestTick by rememberUpdatedState(tick)
    val latestCalm by rememberUpdatedState(calm)
    val latestOnScreen by rememberUpdatedState(isOnScreen)
    var kicks by remember { mutableIntStateOf(1) }
    val running = remember { BooleanArray(1) }
    LaunchedEffect(kicks) {
        running[0] = true
        var last = 0L
        try {
            while (true) {
                if (!latestOnScreen()) {
                    snapshotFlow { latestOnScreen() }.first { it }
                    last = 0L // Off-screen time is not a frame: resume with a nominal one.
                }
                val more = withFrameNanos { now ->
                    val dt = if (last == 0L) 1.0 / 60 else min((now - last) / 1e9, LONGEST_FRAME)
                    last = now
                    Tracing.section(trace) { latestTick(dt, latestCalm) }
                }
                if (!more) break
            }
        } finally {
            running[0] = false
        }
    }
    return remember { { if (!running[0]) kicks++ } }
}
