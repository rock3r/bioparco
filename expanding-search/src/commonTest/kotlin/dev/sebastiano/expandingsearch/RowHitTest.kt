package dev.sebastiano.expandingsearch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RowHitTest {
    private val rows = RowSlots(padding = 5f, rowHeight = 39f)

    @Test
    fun aPointIsInTheRowWhoseBandHoldsIt() {
        assertEquals(0, rows.slotAt(y = 5f, count = 4))
        assertEquals(0, rows.slotAt(y = 43.9f, count = 4))
        assertEquals(1, rows.slotAt(y = 44f, count = 4))
        assertEquals(3, rows.slotAt(y = 5f + 39f * 4 - 0.1f, count = 4))
    }

    @Test
    fun thePaddingAndAnythingPastTheLastRowHitNothing() {
        assertNull(rows.slotAt(y = 4.9f, count = 4))
        assertNull(rows.slotAt(y = 5f + 39f * 4, count = 4))
        assertNull(rows.slotAt(y = 60f, count = 1))
        assertNull(rows.slotAt(y = 10f, count = 0))
    }

    @Test
    fun aRowsTopIsPaddingPlusWholeRows() {
        assertEquals(5f, rows.top(0f))
        assertEquals(5f + 39f * 2.5f, rows.top(2.5f))
    }

    @Test
    fun thePanelIsTallEnoughForItsRowsAndBothPaddings() {
        assertEquals(10f + 39f * 3, rows.panelHeight(3f))
    }
}
