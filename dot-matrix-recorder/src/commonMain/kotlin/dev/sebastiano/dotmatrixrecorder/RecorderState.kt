package dev.sebastiano.dotmatrixrecorder

/** How long each of the 3, 2, 1 digits stays on the lens. Measured from the reference video. */
private const val MS_PER_DIGIT = 800L

/** The all-dots flash between "1" and recording. */
private const val FLASH_MS = 200L

/** How long the countdown lasts, flash included, before recording starts. */
const val COUNTDOWN_MS: Long = MS_PER_DIGIT * 3 + FLASH_MS

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

/** 3, 2 or 1 while counting down; `null` during the closing flash and outside the countdown. */
fun RecorderState.countdownDigit(nowMs: Long): Int? {
    if (this !is RecorderState.CountingDown) return null
    val step = ((nowMs - startedAtMs).coerceAtLeast(0) / MS_PER_DIGIT).toInt()
    return if (step < 3) 3 - step else null
}

fun RecorderState.elapsedMs(nowMs: Long): Long =
    if (this is RecorderState.Recording) (nowMs - startedAtMs).coerceAtLeast(0) else 0

fun formatTimer(elapsedMs: Long): String {
    val totalSeconds = elapsedMs / 1_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}
