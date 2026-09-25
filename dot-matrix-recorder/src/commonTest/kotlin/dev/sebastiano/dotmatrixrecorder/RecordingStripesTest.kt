package dev.sebastiano.dotmatrixrecorder

import kotlin.test.Test
import kotlin.test.assertTrue

class RecordingStripesTest {
    @Test
    fun theFirstStripeEntersAloneFromTheLeft() {
        val brightness = FloatArray(GRID * GRID)
        val presence = FloatArray(GRID * GRID)

        // The very first recording frame, straight after the countdown flash.
        recordingStripesProgram(startedAtMs = 1_000) { 1_000 }.render(brightness, presence)

        // No earlier stripe may already be sitting on the right-hand side of the lens.
        for (row in 0 until GRID) {
            for (col in 2 until GRID) {
                val level = brightness[row * GRID + col]
                assertTrue(level < 0.2f, "dot ($row, $col) is lit ($level) before any stripe")
            }
        }
    }
}
