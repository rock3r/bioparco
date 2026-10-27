package dev.sebastiano.departureboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BoardModelTest {
    private val steady = Scramble(columnLagMs = 30, holdMs = 400, jitterMs = 0)

    private fun BoardModel.row(row: Int, nowMs: Long): String =
        (0 until columns).map { charAt(row, it, nowMs) }.joinToString("")

    @Test
    fun `a new board is blank`() {
        val board = BoardModel(rows = 2, columns = 4)

        assertEquals("    ", board.row(0, nowMs = 0))
        assertFalse(board.isAnimating(nowMs = 0))
    }

    @Test
    fun `a write keeps the old text until its cells start, and ends on the new text`() {
        val board = BoardModel(rows = 1, columns = 4)
        board.write(0, "ABCD", nowMs = 0, scramble = Scramble.Instant)
        board.write(0, "WXYZ", nowMs = 1_000, scramble = steady, delayMs = 100)

        assertEquals("ABCD", board.row(0, nowMs = 1_099))
        assertEquals("WXYZ", board.row(0, nowMs = 1_000 + 100 + 3 * 30 + 400))
    }

    @Test
    fun `scrambling cells show board characters`() {
        val board = BoardModel(rows = 1, columns = 8)
        board.write(0, "LONDON", nowMs = 0, scramble = steady)

        for (now in 0L until 600L step 7) {
            for (column in 0 until 8) {
                val shown = board.charAt(0, column, now)
                assertTrue(shown == ' ' || shown in DotFont.SCRAMBLE, "'$shown' at $now ms")
            }
        }
    }

    @Test
    fun `cells settle from left to right`() {
        val board = BoardModel(rows = 1, columns = 6)
        board.write(0, "TOKYO ", nowMs = 0, scramble = steady)

        // The first cell has settled, the last has not.
        val now = 400L + 2 * 30
        assertEquals("TOK", board.row(0, now).take(3))
        assertTrue(board.settleAt(0, 0) < board.settleAt(0, 5))
    }

    @Test
    fun `a scramble really scrambles`() {
        val board = BoardModel(rows = 1, columns = 1)
        board.write(0, "A", nowMs = 0, scramble = steady)

        val seen = (0L until 400L step SCRAMBLE_STEP_MS).map { board.charAt(0, 0, it) }.toSet()
        assertTrue(seen.size > 3, "only saw $seen")
    }

    @Test
    fun `a changed-cells-only write leaves matching cells alone`() {
        val board = BoardModel(rows = 1, columns = 5)
        board.write(0, "06:28", nowMs = 0, scramble = Scramble.Instant)
        board.write(0, "06:29", nowMs = 1_000, scramble = steady.copy(changedOnly = true))

        for (now in 1_000L until 1_600L step 5) {
            assertEquals("06:2", board.row(0, now).take(4), "at $now ms")
        }
        assertNotEquals('9', board.charAt(0, 4, nowMs = 1_000 + 4 * 30 + 1))
        assertEquals("06:29", board.row(0, nowMs = 1_000 + 4 * 30 + 400))
    }

    @Test
    fun `the board animates until the last cell has settled`() {
        val board = BoardModel(rows = 2, columns = 3)
        board.write(1, "ABC", nowMs = 0, scramble = steady)
        val lastSettle = board.settleAt(1, 2)

        assertTrue(board.isAnimating(lastSettle - 1))
        assertFalse(board.isAnimating(lastSettle))
    }

    @Test
    fun `the same seed scrambles the same way`() {
        val one = BoardModel(rows = 1, columns = 4, seed = 7)
        val two = BoardModel(rows = 1, columns = 4, seed = 7)
        one.write(0, "ROME", nowMs = 0, scramble = steady)
        two.write(0, "ROME", nowMs = 0, scramble = steady)

        for (now in 0L until 600L step 11) assertEquals(one.row(0, now), two.row(0, now))
    }

    @Test
    fun `jitter delays some cells but never past the jitter`() {
        val board = BoardModel(rows = 1, columns = 12)
        board.write(0, "BUENOS AIRES", nowMs = 0, scramble = steady.copy(jitterMs = 150))

        val extras = (0 until 12).map { board.settleAt(0, it) - (it * 30L + 400L) }
        assertTrue(extras.all { it in 0L..150L }, "$extras")
        assertTrue(extras.toSet().size > 1, "$extras")
    }
}
