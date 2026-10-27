package dev.sebastiano.departureboard

/** Cells for `HH:MM`. */
const val TIME_CELLS = 5

/** An empty column between the time and the city. It has no dots at all. */
const val GUTTER_COLUMN = TIME_CELLS

const val CITY_CELLS = 15

/** Columns in a row: the time, the gutter and the city. */
const val BOARD_COLUMNS = TIME_CELLS + 1 + CITY_CELLS

const val BOARD_ROWS = 5

private val accents =
    mapOf(
        "ÁÀÂÄÃÅĀ" to 'A',
        "ÉÈÊËĒ" to 'E',
        "ÍÌÎÏĪ" to 'I',
        "ÓÒÔÖÕØŌ" to 'O',
        "ÚÙÛÜŪ" to 'U',
        "ÇĆČ" to 'C',
        "ÑŃ" to 'N',
        "ŚŠŞ" to 'S',
        "ŹŻŽ" to 'Z',
        "Ł" to 'L',
        "Ğ" to 'G',
        "Ý" to 'Y',
    )

private val folded: Map<Char, Char> =
    accents.flatMap { (letters, plain) -> letters.map { it to plain } }.toMap()

/** [text] as the board spells it: capitals without accents, and blanks for anything else. */
fun boardText(text: String): String =
    text
        .uppercase()
        .map { char ->
            val plain = folded[char] ?: char
            if (DotFont.has(plain)) plain else ' '
        }
        .joinToString("")

/** A row of the board: [time], the gutter, then [city] padded or cut to its field. */
fun rowText(time: String, city: String): String =
    time.padEnd(TIME_CELLS).take(TIME_CELLS) +
        " " +
        boardText(city).take(CITY_CELLS).padEnd(CITY_CELLS)

fun clockText(hour: Int, minute: Int): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/** `GMT-7`, `GMT+5:30`, or plain `GMT`, as the reference labels its cities. */
fun gmtLabel(offsetSeconds: Int): String {
    if (offsetSeconds == 0) return "GMT"
    val sign = if (offsetSeconds < 0) "-" else "+"
    val minutes = kotlin.math.abs(offsetSeconds) / 60
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0) "GMT$sign$hours" else "GMT$sign$hours:${rest.toString().padStart(2, '0')}"
}

/** [text] for matching a query: lower case, without accents. */
internal fun searchKey(text: String): String =
    text.uppercase().map { folded[it] ?: it }.joinToString("").lowercase()
