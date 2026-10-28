package dev.sebastiano.expandingsearch

/** One row of results: a component's [name], and the characters of it the query [match]ed. */
data class SearchResult(val name: String, val match: IntRange) {
    val hasMatch: Boolean
        get() = !match.isEmpty()
}

/**
 * The components the field searches. The first four are what the open field suggests before
 * anything is typed, as in the video. The rest are picked so that typing "t" and then "to" lists
 * exactly what the video lists.
 */
object ComponentCatalogue {
    val suggestions = listOf("Action Button", "Toast", "Code Input", "Segmented Control")

    val all =
        suggestions +
            listOf(
                "Tab Bar",
                "Hold to Confirm",
                "Quantity Stepper",
                "Invite Field",
                "Email Invite",
                "Expanding Search",
                "Progress Ring",
                "Range Slider",
                "Command Menu",
                "Color Swatch",
                "Dropdown Menu",
                "Accordion",
                "Number Field",
                "Radio Group",
                "Checkbox",
                "Pagination",
            )
}

/** The most rows the panel lists. */
const val MAX_RESULTS = 5

/**
 * Searches the [ComponentCatalogue] for [query], ignoring case and surrounding spaces.
 *
 * A blank query gives the four suggestions. Otherwise every name containing the query matches at
 * its first occurrence, and names whose match starts sooner come first; ties keep catalogue order.
 * That ordering is what the video shows: "to" lists Toast (at 0), Hold to Confirm (at 5), then
 * Action Button (at 10).
 */
fun searchComponents(query: String): List<SearchResult> {
    val needle = query.trim()
    if (needle.isEmpty()) {
        return ComponentCatalogue.suggestions.map { SearchResult(it, IntRange.EMPTY) }
    }
    return ComponentCatalogue.all
        .mapNotNull { name ->
            val start = name.indexOf(needle, ignoreCase = true)
            if (start < 0) null else SearchResult(name, start until start + needle.length)
        }
        // sortedBy is stable, so equal starts keep catalogue order.
        .sortedBy { it.match.first }
        .take(MAX_RESULTS)
}
