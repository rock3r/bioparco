package dev.sebastiano.tabswitch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TabBarLayoutTest {
    // Four tabs whose open widths differ, so the shared space visibly changes with the selection.
    private val layout =
        TabBarLayout(
            expandedWidths = floatArrayOf(120f, 130f, 110f, 125f),
            slot = 60f,
            iconInset = 30f,
        )

    @Test
    fun theBarFitsTheWidestOpenTabNextToTheOthersClosed() {
        assertEquals(130f + 3 * 60f, layout.width)
    }

    @Test
    fun anOpenTabTakesItsOwnWidthAndTheClosedOnesShareTheRest() {
        val spans = FloatArray(8)
        layout.spans(oneHot(0), spans)
        assertEquals(0f, spans[0])
        assertEquals(120f, spans[1])
        val share = (layout.width - 120f) / 3f
        for (tab in 1..3) assertEquals(share, spans[2 * tab + 1] - spans[2 * tab], 1e-4f)
    }

    @Test
    fun theTabsTileTheBarWhateverHalfOpenMixTheyAreIn() {
        val spans = FloatArray(8)
        for (expansion in
            listOf(
                oneHot(2),
                floatArrayOf(0.3f, 0.7f, 0f, 0f),
                floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f),
            )) {
            layout.spans(expansion, spans)
            assertEquals(0f, spans[0])
            for (tab in 1..3) assertEquals(spans[2 * tab - 1], spans[2 * tab], 1e-4f)
            assertEquals(layout.width, spans[7], 1e-3f)
        }
    }

    @Test
    fun theSettledSpanIsTheSpanWithOnlyThatTabOpen() {
        val spans = FloatArray(8)
        for (tab in 0..3) {
            layout.spans(oneHot(tab), spans)
            assertEquals(spans[2 * tab], layout.settledLeft(tab), 1e-4f)
            assertEquals(spans[2 * tab + 1], layout.settledRight(tab), 1e-4f)
        }
    }

    @Test
    fun aClosedIconSitsInTheMiddleAndAnOpenOneAtItsInset() {
        assertEquals(150f, layout.iconCentre(left = 100f, right = 200f, expansion = 0f))
        assertEquals(130f, layout.iconCentre(left = 100f, right = 200f, expansion = 1f))
        assertEquals(140f, layout.iconCentre(left = 100f, right = 200f, expansion = 0.5f))
    }

    @Test
    fun aPointIsOnTheTabWhoseSpanHoldsIt() {
        val spans = FloatArray(8)
        layout.spans(oneHot(0), spans)
        assertEquals(0, layout.tabAt(0f, spans))
        assertEquals(0, layout.tabAt(119.9f, spans))
        assertEquals(1, layout.tabAt(120f, spans))
        assertEquals(3, layout.tabAt(layout.width - 0.1f, spans))
    }

    @Test
    fun pointsOffTheBarHitNothing() {
        val spans = FloatArray(8)
        layout.spans(oneHot(0), spans)
        assertNull(layout.tabAt(-0.1f, spans))
        assertNull(layout.tabAt(layout.width, spans))
    }

    private fun oneHot(tab: Int) = FloatArray(4) { if (it == tab) 1f else 0f }
}
