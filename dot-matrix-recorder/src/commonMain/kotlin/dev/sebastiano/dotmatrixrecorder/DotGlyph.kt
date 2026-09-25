package dev.sebastiano.dotmatrixrecorder

import androidx.compose.runtime.Immutable

/** Side of every icon grid. The whole specimen draws on 5×5 dots. */
const val GRID: Int = 5

/**
 * A still 5×5 dot picture.
 *
 * Rows use `#` for a lit dot, `.` for a dim dot that is still drawn, and a space for a dot that is
 * not there at all. The record icon hides its four corners so it reads as a round lens.
 */
@Immutable
class DotGlyph private constructor(private val lit: FloatArray, private val shown: FloatArray) {
    fun brightness(row: Int, col: Int): Float = lit[row * GRID + col]

    fun presence(row: Int, col: Int): Float = shown[row * GRID + col]

    companion object {
        fun parse(vararg rows: String): DotGlyph {
            require(rows.size == GRID) { "A glyph has $GRID rows, got ${rows.size}" }
            val lit = FloatArray(GRID * GRID)
            val shown = FloatArray(GRID * GRID)
            rows.forEachIndexed { row, line ->
                require(line.length == GRID) { "Row $row must be $GRID wide: '$line'" }
                line.forEachIndexed { col, char ->
                    val index = row * GRID + col
                    when (char) {
                        '#' -> {
                            lit[index] = 1f
                            shown[index] = 1f
                        }
                        '.' -> shown[index] = 1f
                        ' ' -> Unit
                        else -> throw IllegalArgumentException("Unknown dot '$char' in '$line'")
                    }
                }
            }
            return DotGlyph(lit, shown)
        }
    }
}

object DotGlyphs {
    val Record = DotGlyph.parse(" ... ", ".###.", ".###.", ".###.", " ... ")

    /** Every lens dot lit: the beat between "1" and recording. */
    val Flash = DotGlyph.parse(" ### ", "#####", "#####", "#####", " ### ")

    val Screenshot = DotGlyph.parse(".###.", "#...#", "#...#", "#...#", ".###.")

    val Restart = DotGlyph.parse(" ... ", "..#..", ".###.", "..#..", " ... ")

    val Delete = DotGlyph.parse(" ... ", ".#.#.", "..#..", ".#.#.", " ... ")

    /** The screenshot frame closing in, used for the shutter blink. */
    val Shutter = DotGlyph.parse(".....", ".###.", ".#.#.", ".###.", ".....")

    private val digits =
        mapOf(
            // Read dot by dot off the reference video, including its odd, symmetric "3".
            3 to DotGlyph.parse(" ### ", "#...#", "..##.", "#...#", " ### "),
            2 to DotGlyph.parse(" ### ", "...##", ".###.", "##...", " ### "),
            1 to DotGlyph.parse(" .#. ", ".##..", "..#..", "..#..", " ### "),
        )

    fun digit(value: Int): DotGlyph =
        requireNotNull(digits[value]) { "Only the 3, 2, 1 countdown has glyphs, not $value" }
}
