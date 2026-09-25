package dev.sebastiano.achievementbadge

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import kotlinx.coroutines.isActive

/**
 * The trophy badge celebrating itself: it pops into an empty slot, bursts, lights up, and then
 * idles with turning rays and twinkles. Changing [playCount] replays the celebration from the
 * start. The clock is read only in the draw phase.
 */
@Composable
fun AchievementBadge(modifier: Modifier = Modifier, playCount: Int = 0) {
    val paint = remember { BadgePaint() }
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playCount) {
        seconds.floatValue = 0f
        val start = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now -> seconds.floatValue = (now - start) / NANOS_PER_SECOND }
        }
    }
    Canvas(modifier) { drawBadgeScene(seconds.floatValue, paint) }
}

private const val NANOS_PER_SECOND = 1_000_000_000f
