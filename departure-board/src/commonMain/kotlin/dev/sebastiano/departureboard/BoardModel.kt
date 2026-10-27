package dev.sebastiano.departureboard

/** How long a scrambling cell shows each random glyph. */
const val SCRAMBLE_STEP_MS = 55L

/**
 * How a write moves through a row. Cell `c` starts scrambling `c × columnLagMs` after the write and
 * settles [holdMs] later, plus up to [jitterMs] picked per cell, so both edges sweep left to right
 * and the settle edge frays a little. With [changedOnly], cells already showing their new character
 * stay still: a minute ticking over only touches the digits that change.
 */
data class Scramble(
    val columnLagMs: Long,
    val holdMs: Long,
    val jitterMs: Long,
    val changedOnly: Boolean = false,
) {
    companion object {
        /** No scramble at all: cells show their new character at once. */
        val Instant = Scramble(columnLagMs = 0, holdMs = 0, jitterMs = 0)

        /** A whole board coming in, or one row being replaced. */
        val Board = Scramble(columnLagMs = 28, holdMs = 420, jitterMs = 160)

        /** The clock ticking over: only the digits that change, and briefly. */
        val Tick = Scramble(columnLagMs = 40, holdMs = 260, jitterMs = 80, changedOnly = true)
    }
}

/**
 * What every cell of the board shows at any time. Plain state and arithmetic, no clock: callers
 * pass the time in, so the draw phase can ask for any frame and tests need no waiting.
 *
 * Each cell remembers the character it is leaving, the one it is heading for, and when it starts
 * and stops scrambling. In between it shows a random glyph that changes every [SCRAMBLE_STEP_MS],
 * chosen by hashing the cell, the write and the step, so a frame is the same however often it is
 * drawn.
 */
class BoardModel(val rows: Int, val columns: Int, private val seed: Int = 1) {
    private val from = CharArray(rows * columns) { ' ' }
    private val to = CharArray(rows * columns) { ' ' }
    private val startAt = LongArray(rows * columns)
    private val settleAt = LongArray(rows * columns)
    private val writes = IntArray(rows * columns)
    private var lastSettle = Long.MIN_VALUE

    /** Sends [text] to [row], starting [delayMs] after [nowMs]. Missing characters are blanks. */
    fun write(row: Int, text: String, nowMs: Long, scramble: Scramble, delayMs: Long = 0) {
        for (column in 0 until columns) {
            val cell = row * columns + column
            val next = text.getOrElse(column) { ' ' }
            val current = charAt(cell, nowMs)
            if (scramble.changedOnly && current == next && nowMs >= settleAt[cell]) continue
            from[cell] = current
            to[cell] = next
            writes[cell]++
            startAt[cell] = nowMs + delayMs + column * scramble.columnLagMs
            settleAt[cell] =
                startAt[cell] + scramble.holdMs + jitter(cell, writes[cell], scramble.jitterMs)
            lastSettle = maxOf(lastSettle, settleAt[cell])
        }
    }

    fun charAt(row: Int, column: Int, nowMs: Long): Char = charAt(row * columns + column, nowMs)

    fun settleAt(row: Int, column: Int): Long = settleAt[row * columns + column]

    /** Whether any cell is still waiting to start or scrambling at [nowMs]. */
    fun isAnimating(nowMs: Long): Boolean = nowMs < lastSettle

    private fun charAt(cell: Int, nowMs: Long): Char =
        when {
            nowMs < startAt[cell] -> from[cell]
            nowMs >= settleAt[cell] -> to[cell]
            else -> scrambleGlyph(cell, writes[cell], (nowMs - startAt[cell]) / SCRAMBLE_STEP_MS)
        }

    private fun scrambleGlyph(cell: Int, write: Int, step: Long): Char {
        val hash = mix(seed, cell, write * STEP_STRIDE + step.toInt())
        // About one glyph in eight is a blank, so the scramble flickers rather than fills.
        if (hash and BLANK_MASK == 0) return ' '
        val pick = (hash ushr BLANK_BITS) % DotFont.SCRAMBLE.length
        return DotFont.SCRAMBLE[pick]
    }

    private fun jitter(cell: Int, write: Int, jitterMs: Long): Long =
        if (jitterMs == 0L) 0L else (mix(seed xor JITTER_SALT, cell, write) % (jitterMs + 1))

    /** A small integer hash, non-negative. Murmur3's finaliser over the three inputs. */
    private fun mix(a: Int, b: Int, c: Int): Int {
        var h = a * PRIME_A + b * PRIME_B + c * PRIME_C
        h = h xor (h ushr 16)
        h *= FMIX_1
        h = h xor (h ushr 13)
        h *= FMIX_2
        h = h xor (h ushr 16)
        return h and Int.MAX_VALUE
    }

    private companion object {
        const val STEP_STRIDE = 1_000
        const val BLANK_BITS = 3
        const val BLANK_MASK = (1 shl BLANK_BITS) - 1
        const val JITTER_SALT = 0x5bd1e995
        const val PRIME_A = 0x01000193
        const val PRIME_B = 0x27d4eb2f
        const val PRIME_C = 0x165667b1
        const val FMIX_1 = -0x7a143595
        const val FMIX_2 = -0x3d4d51cb
    }
}
