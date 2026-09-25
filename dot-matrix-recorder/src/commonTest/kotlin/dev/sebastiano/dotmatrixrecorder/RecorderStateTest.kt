package dev.sebastiano.dotmatrixrecorder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecorderStateTest {
    @Test
    fun recordStartsACountdownFromIdle() {
        val state = RecorderState.Idle.reduce(RecorderEvent.RecordPressed(nowMs = 1_000))
        assertEquals(RecorderState.CountingDown(startedAtMs = 1_000), state)
    }

    @Test
    fun countdownShowsThreeTwoOneThenFlashesThenStartsRecordingOnTheBoundary() {
        val counting = RecorderState.CountingDown(startedAtMs = 1_000)
        assertEquals(3, counting.countdownDigit(nowMs = 1_000))
        assertEquals(3, counting.countdownDigit(nowMs = 1_799))
        assertEquals(2, counting.countdownDigit(nowMs = 1_800))
        assertEquals(1, counting.countdownDigit(nowMs = 2_600))
        assertEquals(1, counting.countdownDigit(nowMs = 3_399))
        // The last beat is the all-dots flash, still part of the countdown.
        assertNull(counting.countdownDigit(nowMs = 3_400))
        assertEquals(counting, counting.reduce(RecorderEvent.Tick(nowMs = 3_599)))
        // A late frame must not eat the first recorded second.
        assertEquals(
            RecorderState.Recording(startedAtMs = 3_600),
            counting.reduce(RecorderEvent.Tick(nowMs = 3_850)),
        )
    }

    @Test
    fun onlyTheCountdownHasADigit() {
        assertNull(RecorderState.Idle.countdownDigit(nowMs = 0))
        assertNull(RecorderState.Recording(startedAtMs = 0).countdownDigit(nowMs = 10))
    }

    @Test
    fun elapsedTimeOnlyRunsWhileRecording() {
        assertEquals(0, RecorderState.Idle.elapsedMs(nowMs = 9_000))
        assertEquals(0, RecorderState.CountingDown(startedAtMs = 0).elapsedMs(nowMs = 2_000))
        assertEquals(2_500, RecorderState.Recording(startedAtMs = 1_000).elapsedMs(nowMs = 3_500))
        // A clock that steps backwards never shows a negative timer.
        assertEquals(0, RecorderState.Recording(startedAtMs = 1_000).elapsedMs(nowMs = 900))
    }

    @Test
    fun restartGoesBackToTheCountdown() {
        val recording = RecorderState.Recording(startedAtMs = 0)
        assertEquals(
            RecorderState.CountingDown(startedAtMs = 7_000),
            recording.reduce(RecorderEvent.RestartPressed(nowMs = 7_000)),
        )
    }

    @Test
    fun stopAndDeleteBothReturnToIdle() {
        val recording = RecorderState.Recording(startedAtMs = 0)
        assertEquals(RecorderState.Idle, recording.reduce(RecorderEvent.StopPressed))
        assertEquals(RecorderState.Idle, recording.reduce(RecorderEvent.DeletePressed))
        val counting = RecorderState.CountingDown(startedAtMs = 0)
        assertEquals(RecorderState.Idle, counting.reduce(RecorderEvent.DeletePressed))
    }

    @Test
    fun eventsThatDoNotApplyAreIgnored() {
        assertEquals(RecorderState.Idle, RecorderState.Idle.reduce(RecorderEvent.StopPressed))
        assertEquals(
            RecorderState.Idle,
            RecorderState.Idle.reduce(RecorderEvent.RestartPressed(nowMs = 5)),
        )
        val recording = RecorderState.Recording(startedAtMs = 0)
        assertEquals(recording, recording.reduce(RecorderEvent.RecordPressed(nowMs = 5)))
    }

    @Test
    fun timerIsMinutesAndSeconds() {
        assertEquals("00:00", formatTimer(elapsedMs = 0))
        assertEquals("00:00", formatTimer(elapsedMs = 999))
        assertEquals("00:05", formatTimer(elapsedMs = 5_400))
        assertEquals("01:02", formatTimer(elapsedMs = 62_000))
        assertEquals("100:00", formatTimer(elapsedMs = 6_000_000))
    }
}
