package dev.sebastiano.departureboard

/**
 * The board's 5×7 dot font. A glyph is a bit mask read row by row from the top left: bit `row *
 * COLUMNS + column` is lit. The shapes are classic 5×7 capitals; the digit 0 has no slash, so it
 * reads the same as the letter O, as it does there.
 */
object DotFont {
    const val COLUMNS = 5
    const val ROWS = 7

    /** What a cell shows while it scrambles, besides the odd blank. */
    const val SCRAMBLE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    private val shapes: Map<Char, List<String>> =
        mapOf(
            'A' to
                listOf(
                    ".###.",
                    "#...#",
                    "#...#",
                    "#####",
                    "#...#",
                    "#...#",
                    "#...#",
                ),
            'B' to
                listOf(
                    "####.",
                    "#...#",
                    "#...#",
                    "####.",
                    "#...#",
                    "#...#",
                    "####.",
                ),
            'C' to
                listOf(
                    ".###.",
                    "#...#",
                    "#....",
                    "#....",
                    "#....",
                    "#...#",
                    ".###.",
                ),
            'D' to
                listOf(
                    "####.",
                    "#...#",
                    "#...#",
                    "#...#",
                    "#...#",
                    "#...#",
                    "####.",
                ),
            'E' to
                listOf(
                    "#####",
                    "#....",
                    "#....",
                    "####.",
                    "#....",
                    "#....",
                    "#####",
                ),
            'F' to
                listOf(
                    "#####",
                    "#....",
                    "#....",
                    "####.",
                    "#....",
                    "#....",
                    "#....",
                ),
            'G' to
                listOf(
                    ".###.",
                    "#...#",
                    "#....",
                    "#.###",
                    "#...#",
                    "#...#",
                    ".####",
                ),
            'H' to
                listOf(
                    "#...#",
                    "#...#",
                    "#...#",
                    "#####",
                    "#...#",
                    "#...#",
                    "#...#",
                ),
            'I' to
                listOf(
                    ".###.",
                    "..#..",
                    "..#..",
                    "..#..",
                    "..#..",
                    "..#..",
                    ".###.",
                ),
            'J' to
                listOf(
                    "..###",
                    "...#.",
                    "...#.",
                    "...#.",
                    "...#.",
                    "#..#.",
                    ".##..",
                ),
            'K' to
                listOf(
                    "#...#",
                    "#..#.",
                    "#.#..",
                    "##...",
                    "#.#..",
                    "#..#.",
                    "#...#",
                ),
            'L' to
                listOf(
                    "#....",
                    "#....",
                    "#....",
                    "#....",
                    "#....",
                    "#....",
                    "#####",
                ),
            'M' to
                listOf(
                    "#...#",
                    "##.##",
                    "#.#.#",
                    "#.#.#",
                    "#...#",
                    "#...#",
                    "#...#",
                ),
            'N' to
                listOf(
                    "#...#",
                    "#...#",
                    "##..#",
                    "#.#.#",
                    "#..##",
                    "#...#",
                    "#...#",
                ),
            'O' to
                listOf(
                    ".###.",
                    "#...#",
                    "#...#",
                    "#...#",
                    "#...#",
                    "#...#",
                    ".###.",
                ),
            'P' to
                listOf(
                    "####.",
                    "#...#",
                    "#...#",
                    "####.",
                    "#....",
                    "#....",
                    "#....",
                ),
            'Q' to
                listOf(
                    ".###.",
                    "#...#",
                    "#...#",
                    "#...#",
                    "#.#.#",
                    "#..#.",
                    ".##.#",
                ),
            'R' to
                listOf(
                    "####.",
                    "#...#",
                    "#...#",
                    "####.",
                    "#.#..",
                    "#..#.",
                    "#...#",
                ),
            'S' to
                listOf(
                    ".####",
                    "#....",
                    "#....",
                    ".###.",
                    "....#",
                    "....#",
                    "####.",
                ),
            'T' to
                listOf(
                    "#####",
                    "..#..",
                    "..#..",
                    "..#..",
                    "..#..",
                    "..#..",
                    "..#..",
                ),
            'U' to
                listOf(
                    "#...#",
                    "#...#",
                    "#...#",
                    "#...#",
                    "#...#",
                    "#...#",
                    ".###.",
                ),
            'V' to
                listOf(
                    "#...#",
                    "#...#",
                    "#...#",
                    "#...#",
                    "#...#",
                    ".#.#.",
                    "..#..",
                ),
            'W' to
                listOf(
                    "#...#",
                    "#...#",
                    "#...#",
                    "#.#.#",
                    "#.#.#",
                    "#.#.#",
                    ".#.#.",
                ),
            'X' to
                listOf(
                    "#...#",
                    "#...#",
                    ".#.#.",
                    "..#..",
                    ".#.#.",
                    "#...#",
                    "#...#",
                ),
            'Y' to
                listOf(
                    "#...#",
                    "#...#",
                    ".#.#.",
                    "..#..",
                    "..#..",
                    "..#..",
                    "..#..",
                ),
            'Z' to
                listOf(
                    "#####",
                    "....#",
                    "...#.",
                    "..#..",
                    ".#...",
                    "#....",
                    "#####",
                ),
            '0' to
                listOf(
                    ".###.",
                    "#...#",
                    "#...#",
                    "#...#",
                    "#...#",
                    "#...#",
                    ".###.",
                ),
            '1' to
                listOf(
                    "..#..",
                    ".##..",
                    "..#..",
                    "..#..",
                    "..#..",
                    "..#..",
                    ".###.",
                ),
            '2' to
                listOf(
                    ".###.",
                    "#...#",
                    "....#",
                    "...#.",
                    "..#..",
                    ".#...",
                    "#####",
                ),
            '3' to
                listOf(
                    "#####",
                    "...#.",
                    "..#..",
                    "...#.",
                    "....#",
                    "#...#",
                    ".###.",
                ),
            '4' to
                listOf(
                    "...#.",
                    "..##.",
                    ".#.#.",
                    "#..#.",
                    "#####",
                    "...#.",
                    "...#.",
                ),
            '5' to
                listOf(
                    "#####",
                    "#....",
                    "####.",
                    "....#",
                    "....#",
                    "#...#",
                    ".###.",
                ),
            '6' to
                listOf(
                    "..##.",
                    ".#...",
                    "#....",
                    "####.",
                    "#...#",
                    "#...#",
                    ".###.",
                ),
            '7' to
                listOf(
                    "#####",
                    "....#",
                    "...#.",
                    "..#..",
                    ".#...",
                    ".#...",
                    ".#...",
                ),
            '8' to
                listOf(
                    ".###.",
                    "#...#",
                    "#...#",
                    ".###.",
                    "#...#",
                    "#...#",
                    ".###.",
                ),
            '9' to
                listOf(
                    ".###.",
                    "#...#",
                    "#...#",
                    ".####",
                    "....#",
                    "...#.",
                    ".##..",
                ),
            ':' to
                listOf(
                    ".....",
                    "..#..",
                    "..#..",
                    ".....",
                    "..#..",
                    "..#..",
                    ".....",
                ),
            '-' to
                listOf(
                    ".....",
                    ".....",
                    ".....",
                    ".###.",
                    ".....",
                    ".....",
                    ".....",
                ),
            '.' to
                listOf(
                    ".....",
                    ".....",
                    ".....",
                    ".....",
                    ".....",
                    ".##..",
                    ".##..",
                ),
            '\'' to
                listOf(
                    "..#..",
                    "..#..",
                    ".#...",
                    ".....",
                    ".....",
                    ".....",
                    ".....",
                ),
            '/' to
                listOf(
                    ".....",
                    "....#",
                    "...#.",
                    "..#..",
                    ".#...",
                    "#....",
                    ".....",
                ),
            '&' to
                listOf(
                    ".##..",
                    "#..#.",
                    "#.#..",
                    ".#...",
                    "#.#.#",
                    "#..#.",
                    ".##.#",
                ),
        )

    private val glyphs: Map<Char, Long> = shapes.mapValues { (_, rows) -> maskOf(rows) }

    /** Every character with a glyph, blank first. */
    val CHARACTERS: String = " " + shapes.keys.joinToString("")

    fun has(char: Char): Boolean = char in glyphs

    /** The glyph for [char]; anything the board cannot show is blank. */
    fun glyph(char: Char): Long = glyphs[char] ?: 0L

    fun lit(char: Char, row: Int, column: Int): Boolean =
        (glyph(char) ushr (row * COLUMNS + column)) and 1L == 1L

    private fun maskOf(rows: List<String>): Long {
        var mask = 0L
        rows.forEachIndexed { row, line ->
            line.forEachIndexed { column, dot ->
                if (dot == '#') mask = mask or (1L shl (row * COLUMNS + column))
            }
        }
        return mask
    }
}
