package dev.sebastiano.expandingsearch

/** Test tags for driving the specimen from Spectre. */
object ExpandingSearchTags {
    const val STAGE = "expanding-search-stage"
    const val OPEN = "expanding-search-open"
    const val FIELD = "expanding-search-field"
    const val CLEAR = "expanding-search-clear"
    const val RESULTS = "expanding-search-results"

    fun row(name: String): String = "expanding-search-row-$name"
}
