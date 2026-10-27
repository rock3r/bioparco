package dev.sebastiano.departureboard

/** Test tags for driving the specimen from Spectre. */
object DepartureBoardTags {
    const val BOARD = "departure-board"
    const val SEARCH = "departure-board-search"
    const val REFRESH = "departure-board-refresh"
    const val DATE = "departure-board-date"

    fun suggestion(city: String): String =
        "departure-board-suggestion-" + searchKey(city).replace(' ', '-')
}
