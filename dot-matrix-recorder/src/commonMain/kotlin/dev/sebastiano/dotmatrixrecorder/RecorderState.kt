package dev.sebastiano.dotmatrixrecorder

/** How long the 3, 2, 1 countdown lasts before recording starts. */
const val COUNTDOWN_MS: Long = 3_000L

private const val MS_PER_DIGIT = COUNTDOWN_MS / 3

/** What the recorder is doing. Time is always passed in, so this stays plain and testable. */
sealed interface RecorderState {
    data object Idle : RecorderState

    data class CountingDown(val startedAtMs: Long) : RecorderState

    data class Recording(val startedAtMs: Long) : RecorderState
}

sealed interface RecorderEvent {
    data class RecordPressed(val nowMs: Long) : RecorderEvent

    data class RestartPressed(val nowMs: Long) : RecorderEvent

    data object StopPressed : RecorderEvent

    data object DeletePressed : RecorderEvent

    data class Tick(val nowMs: Long) : RecorderEvent
}

fun RecorderState.reduce(event: RecorderEvent): RecorderState =
    when (event) {
        is RecorderEvent.RecordPressed ->
            if (this == RecorderState.Idle) RecorderState.CountingDown(event.nowMs) else this
        is RecorderEvent.RestartPressed ->
            if (this is RecorderState.Recording) RecorderState.CountingDown(event.nowMs) else this
        RecorderEvent.StopPressed ->
            if (this is RecorderState.Recording) RecorderState.Idle else this
        RecorderEvent.DeletePressed -> RecorderState.Idle
        is RecorderEvent.Tick ->
            if (this is RecorderState.CountingDown && event.nowMs - startedAtMs >= COUNTDOWN_MS) {
                // Anchor to the countdown end, not the frame that noticed it.
                RecorderState.Recording(startedAtMs + COUNTDOWN_MS)
            } else {
                this
            }
    }

/** 3, 2 or 1 while counting down; `null` otherwise. */
fun RecorderState.countdownDigit(nowMs: Long): Int? {
    if (this !is RecorderState.CountingDown) return null
    val step = ((nowMs - startedAtMs) / MS_PER_DIGIT).coerceIn(0, 2)
    return 3 - step.toInt()
}

fun RecorderState.elapsedMs(nowMs: Long): Long =
    if (this is RecorderState.Recording) (nowMs - startedAtMs).coerceAtLeast(0) else 0

fun formatTimer(elapsedMs: Long): String {
    val totalSeconds = elapsedMs / 1_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}
