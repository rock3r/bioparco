package dev.sebastiano.expandingsearch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComponentSearchTest {
    @Test
    fun anEmptyQueryShowsTheFourSuggestionsWithNothingMatched() {
        val results = searchComponents("")

        assertEquals(
            listOf("Action Button", "Toast", "Code Input", "Segmented Control"),
            results.map { it.name },
        )
        assertTrue(results.none { it.hasMatch })
    }

    @Test
    fun aBlankQueryCountsAsEmpty() {
        assertEquals(searchComponents(""), searchComponents("   "))
    }

    @Test
    fun resultsRankByWhereTheMatchStartsAsInTheVideo() {
        // The video types "to": Toast, then Hold to Confirm, then Action Button.
        assertEquals(
            listOf("Toast", "Hold to Confirm", "Action Button"),
            searchComponents("to").map { it.name },
        )
    }

    @Test
    fun tiesKeepCatalogueOrderAndTheListStopsAtFive() {
        // Halfway through typing, "t" lists these five, in this order.
        assertEquals(
            listOf("Toast", "Tab Bar", "Action Button", "Quantity Stepper", "Invite Field"),
            searchComponents("t").map { it.name },
        )
    }

    @Test
    fun theMatchIsTheFirstOccurrence() {
        val results = searchComponents("to").associateBy { it.name }

        assertEquals(0 until 2, results.getValue("Toast").match)
        assertEquals(5 until 7, results.getValue("Hold to Confirm").match)
        // "Action Button" has its "t"s at 2, 9 and 10; the "to" is at 10.
        assertEquals(10 until 12, results.getValue("Action Button").match)
    }

    @Test
    fun matchingIgnoresCaseAndSurroundingSpaces() {
        val results = searchComponents("  HOLD to ")

        assertEquals(listOf("Hold to Confirm"), results.map { it.name })
        assertEquals(0 until 7, results.single().match)
    }

    @Test
    fun noMatchMeansNoResults() {
        assertTrue(searchComponents("zebra").isEmpty())
    }
}
