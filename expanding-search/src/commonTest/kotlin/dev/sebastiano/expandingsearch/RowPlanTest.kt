package dev.sebastiano.expandingsearch

import dev.sebastiano.expandingsearch.RowStep.Enter
import dev.sebastiano.expandingsearch.RowStep.Keep
import dev.sebastiano.expandingsearch.RowStep.Leave
import dev.sebastiano.expandingsearch.RowStep.Revive
import kotlin.test.Test
import kotlin.test.assertEquals

class RowPlanTest {
    @Test
    fun narrowingAQueryKeepsRowsInPlaceAndSwapsTheRest() {
        // From "t" to "to" in the video: Toast and Action Button stay where they are.
        val steps =
            planRows(
                shown =
                    listOf("Toast", "Tab Bar", "Action Button", "Quantity Stepper", "Invite Field")
                        .slots(),
                leaving = emptyList(),
                next = listOf("Toast", "Hold to Confirm", "Action Button"),
            )

        assertEquals(
            setOf(
                Keep("Toast", 0),
                Leave("Tab Bar", 1),
                Enter("Hold to Confirm", 1),
                Keep("Action Button", 2),
                Leave("Quantity Stepper", 3),
                Leave("Invite Field", 4),
            ),
            steps.toSet(),
        )
    }

    @Test
    fun aRowThatChangesSlotSlidesThere() {
        // Typing "t" in the video: Toast slides up a slot as Action Button slides down two,
        // straight through it.
        val steps =
            planRows(
                shown = listOf("Action Button", "Toast", "Code Input", "Segmented Control").slots(),
                leaving = emptyList(),
                next = listOf("Toast", "Tab Bar", "Action Button", "Quantity Stepper"),
            )

        assertEquals(
            setOf(
                Keep("Action Button", 2),
                Keep("Toast", 0),
                Leave("Code Input", 2),
                Leave("Segmented Control", 3),
                Enter("Tab Bar", 1),
                Enter("Quantity Stepper", 3),
            ),
            steps.toSet(),
        )
    }

    @Test
    fun aRowStillFadingOutComesBackWhereverItIsWanted() {
        // Backspacing "to" to "t" at once: Tab Bar was still leaving slot 1 and is wanted back
        // there; Quantity Stepper was leaving slot 3 and is wanted at slot 2.
        val steps =
            planRows(
                shown = listOf("Toast", "Hold to Confirm").slots(),
                leaving = listOf("Tab Bar" to 1, "Quantity Stepper" to 3),
                next = listOf("Toast", "Tab Bar", "Quantity Stepper"),
            )

        assertEquals(
            setOf(
                Keep("Toast", 0),
                Leave("Hold to Confirm", 1),
                Revive("Tab Bar", 1),
                Revive("Quantity Stepper", 2),
            ),
            steps.toSet(),
        )
    }

    @Test
    fun openingEntersEveryRowAndClosingLeavesEveryRow() {
        val rows = listOf("Action Button", "Toast")

        assertEquals(
            listOf(Enter("Action Button", 0), Enter("Toast", 1)),
            planRows(emptyList(), emptyList(), rows),
        )
        assertEquals(
            listOf(Leave("Action Button", 0), Leave("Toast", 1)),
            planRows(rows.slots(), emptyList(), emptyList()),
        )
    }

    private fun List<String>.slots() = mapIndexed { slot, key -> key to slot }
}
