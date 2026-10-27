package dev.sebastiano.departureboard

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import dev.sebastiano.bioparco.tracing.Tracing
import kotlinx.coroutines.flow.first

/**
 * The board and its frame loop. Writes go straight into [model]; [frameMillis] is the time the draw
 * phase renders, and only the draw phase reads it, so a running scramble never recomposes. While
 * nothing scrambles the loop stops asking for frames, and the next write wakes it.
 */
@Stable
internal class BoardState(private val clock: () -> Long = ::monotonicMillis) {
    val model = BoardModel(BOARD_ROWS, BOARD_COLUMNS)

    private val frame = mutableLongStateOf(clock())
    private val writes = mutableIntStateOf(0)

    val frameMillis: Long
        get() = frame.longValue

    fun write(row: Int, text: String, scramble: Scramble, delayMs: Long = 0) {
        Tracing.section("board write") { model.write(row, text, clock(), scramble, delayMs) }
        writes.intValue++
    }

    suspend fun runFrames() {
        while (true) {
            withFrameNanos { frame.longValue = clock() }
            if (!model.isAnimating(frame.longValue)) {
                val seen = writes.intValue
                snapshotFlow { writes.intValue }.first { it != seen }
            }
        }
    }
}

private fun monotonicMillis(): Long = System.nanoTime() / 1_000_000
