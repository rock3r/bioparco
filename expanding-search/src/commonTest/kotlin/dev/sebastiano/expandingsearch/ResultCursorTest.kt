package dev.sebastiano.expandingsearch

import kotlin.test.Test
import kotlin.test.assertEquals

class ResultCursorTest {
    @Test
    fun downAndUpStepThroughTheRowsAndWrapAround() {
        assertEquals(1, stepCursor(current = 0, count = 4, step = 1))
        assertEquals(0, stepCursor(current = 3, count = 4, step = 1))
        assertEquals(3, stepCursor(current = 0, count = 4, step = -1))
        assertEquals(2, stepCursor(current = 3, count = 4, step = -1))
    }

    @Test
    fun withNoRowsTheCursorStaysOnTheFirst() {
        assertEquals(0, stepCursor(current = 0, count = 0, step = 1))
        assertEquals(0, stepCursor(current = 0, count = 0, step = -1))
    }

    @Test
    fun aCursorPastTheEndOfAShorterListComesBackInside() {
        // The list shrank from five rows to two while the cursor was on the fifth.
        assertEquals(0, stepCursor(current = 4, count = 2, step = 1))
        assertEquals(1, stepCursor(current = 4, count = 2, step = -1))
    }

    @Test
    fun theCrossClearsTextFirstAndClosesOnlyWhenTheFieldIsEmpty() {
        assertEquals(Dismissal.Clear, dismissalFor("to"))
        assertEquals(Dismissal.Close, dismissalFor(""))
    }
}
