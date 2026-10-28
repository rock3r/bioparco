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
                Enter("Hold to Confirm", 1, from = null),
                Keep("Action Button", 2),
                Leave("Quantity Stepper", 3),
                Leave("Invite Field", 4),
            ),
            steps.toSet(),
        )
    }

    @Test
    fun aRowThatChangesSlotFadesOutWhereItWasAndBackInWhereItGoes() {
        // Clearing "to": rows sliding to new slots would pass through each other.
        val steps =
            planRows(
                shown = listOf("Toast", "Hold to Confirm", "Action Button").slots(),
                leaving = emptyList(),
                next = listOf("Action Button", "Toast", "Code Input", "Segmented Control"),
            )

        assertEquals(
            setOf(
                Leave("Toast", 0),
                Enter("Toast", 1, from = 0),
                Leave("Hold to Confirm", 1),
                Leave("Action Button", 2),
                Enter("Action Button", 0, from = 2),
                Enter("Code Input", 2, from = null),
                Enter("Segmented Control", 3, from = null),
            ),
            steps.toSet(),
        )
    }

    @Test
    fun aRowStillFadingOutComesBackIfItIsWantedInTheSameSlot() {
        // Backspacing "to" to "t" at once: Tab Bar was still leaving slot 1 and is wanted back.
        val steps =
            planRows(
                shown = listOf("Toast", "Hold to Confirm").slots(),
                leaving = listOf("Tab Bar" to 1, "Invite Field" to 4),
                next = listOf("Toast", "Tab Bar"),
            )

        assertEquals(
            setOf(Keep("Toast", 0), Leave("Hold to Confirm", 1), Revive("Tab Bar", 1)),
            steps.toSet(),
        )
    }

    @Test
    fun openingEntersEveryRowAndClosingLeavesEveryRow() {
        val rows = listOf("Action Button", "Toast")

        assertEquals(
            listOf(Enter("Action Button", 0, from = null), Enter("Toast", 1, from = null)),
            planRows(emptyList(), emptyList(), rows),
        )
        assertEquals(
            listOf(Leave("Action Button", 0), Leave("Toast", 1)),
            planRows(rows.slots(), emptyList(), emptyList()),
        )
    }

    private fun List<String>.slots() = mapIndexed { slot, key -> key to slot }
}
