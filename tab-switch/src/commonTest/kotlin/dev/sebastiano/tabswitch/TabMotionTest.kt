package dev.sebastiano.tabswitch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TabMotionTest {
    private val layout =
        TabBarLayout(
            expandedWidths = floatArrayOf(120f, 130f, 110f, 125f),
            slot = 60f,
            iconInset = 30f,
        )

    private var now = 1_000_000_000L

    private fun motion(initial: Int = 0) = TabMotion(layout, minPillWidth = 52f, initial = initial)

    /** Steps [motion] a 60 Hz frame at a time for [seconds], handing each frame to [frame]. */
    private fun run(motion: TabMotion, seconds: Float, frame: (Float) -> Unit = {}) {
        val frames = (seconds * 60f).toInt()
        val start = now
        repeat(frames) {
            now += FRAME
            motion.step(now)
            frame((now - start) / 1e9f)
        }
    }

    @Test
    fun itStartsAtRestOnTheFirstTab() {
        val motion = motion()
        assertEquals(layout.settledLeft(0), motion.pillLeft)
        assertEquals(layout.settledRight(0), motion.pillRight)
        assertEquals(1f, motion.labelAlpha[0])
        assertFalse(motion.step(now + FRAME))
    }

    @Test
    fun aSwitchSettlesWithThePillAndLabelOnTheNewTab() {
        val motion = motion()
        motion.select(2, now)
        run(motion, 1.5f)
        assertEquals(layout.settledLeft(2), motion.pillLeft, 0.01f)
        assertEquals(layout.settledRight(2), motion.pillRight, 0.01f)
        assertEquals(1f, motion.labelAlpha[2])
        assertEquals(0f, motion.labelAlpha[0])
        assertEquals(1f, motion.expansion[2], 1e-3f)
        assertFalse(motion.step(now + FRAME), "settled, so it asks for no more frames")
    }

    @Test
    fun theTrailingEdgeLeavesBeforeTheLeadingOne() {
        // Moving right, the left edge trails.
        val right = motion(initial = 0)
        right.select(1, now)
        run(right, 0.07f)
        val leftMoved =
            (right.pillLeft - layout.settledLeft(0)) /
                (layout.settledLeft(1) - layout.settledLeft(0))
        val rightMoved =
            (right.pillRight - layout.settledRight(0)) /
                (layout.settledRight(1) - layout.settledRight(0))
        assertTrue(leftMoved > 0.3f, "the trailing edge is well on its way: $leftMoved")
        assertTrue(rightMoved < 0.05f, "the leading edge has barely moved: $rightMoved")

        // Moving left, the right edge trails.
        val left = motion(initial = 2)
        left.select(1, now)
        run(left, 0.07f)
        val rightBack =
            (layout.settledRight(2) - left.pillRight) /
                (layout.settledRight(2) - layout.settledRight(1))
        val leftBack =
            (layout.settledLeft(2) - left.pillLeft) /
                (layout.settledLeft(2) - layout.settledLeft(1))
        assertTrue(rightBack > 0.3f, "the trailing edge is well on its way: $rightBack")
        assertTrue(leftBack < 0.05f, "the leading edge has barely moved: $leftBack")
    }

    @Test
    fun thePillPinchesOnTheWayAcross() {
        val motion = motion()
        motion.select(1, now)
        var narrowest = Float.MAX_VALUE
        run(motion, 0.6f) { narrowest = minOf(narrowest, motion.pillRight - motion.pillLeft) }
        val settled =
            minOf(
                layout.settledRight(0) - layout.settledLeft(0),
                layout.settledRight(1) - layout.settledLeft(1),
            )
        assertTrue(narrowest < settled * 0.85f, "narrowest $narrowest against $settled")
    }

    @Test
    fun aLabelWaitsForThePillToArriveOverItsTab() {
        // On a long jump the tab opens long before the pill gets there.
        val motion = motion()
        motion.select(3, now)
        run(motion, 1f) {
            if (motion.labelAlpha[3] > 0f) {
                val cover =
                    (minOf(motion.pillRight, layout.settledRight(3)) -
                        maxOf(motion.pillLeft, layout.settledLeft(3))) /
                        (layout.settledRight(3) - layout.settledLeft(3))
                assertTrue(
                    cover > 0.75f,
                    "the label showed with the pill over $cover of its tab, at ${it}s",
                )
            }
        }
        assertEquals(1f, motion.labelAlpha[3])
    }

    @Test
    fun aLongJumpNeverPinchesThePillNarrowerThanItIsTall() {
        val motion = motion()
        motion.select(3, now)
        run(motion, 1f) {
            assertTrue(motion.pillRight - motion.pillLeft >= 52f - 1e-3f, "at ${it}s")
        }
        motion.select(0, now)
        run(motion, 1f) {
            assertTrue(motion.pillRight - motion.pillLeft >= 52f - 1e-3f, "back at ${it}s")
        }
    }

    @Test
    fun theOldLabelIsGoneBeforeTheNewOneShows() {
        val motion = motion()
        motion.select(1, now)
        var overlap = 0f
        var oldGoneAt = -1f
        var newShownAt = -1f
        run(motion, 1f) {
            overlap = maxOf(overlap, minOf(motion.labelAlpha[0], motion.labelAlpha[1]))
            if (oldGoneAt < 0f && motion.labelAlpha[0] == 0f) oldGoneAt = it
            if (newShownAt < 0f && motion.labelAlpha[1] > 0f) newShownAt = it
        }
        assertEquals(0f, overlap)
        assertTrue(oldGoneAt in 0f..0.1f, "the old label fades fast: ${oldGoneAt}s")
        assertTrue(newShownAt > 0.15f, "the new label waits for room: ${newShownAt}s")
    }

    @Test
    fun theTabsAlwaysShareOneOpening() {
        val motion = motion()
        motion.select(1, now)
        run(motion, 0.1f) { assertEquals(1f, motion.expansion.sum(), 1e-3f) }
        // A second click mid-flight retargets everything without losing or gaining width.
        motion.select(3, now)
        run(motion, 1f) {
            assertEquals(1f, motion.expansion.sum(), 1e-3f)
            assertEquals(layout.width, motion.spans[7], 1e-2f)
        }
        assertEquals(layout.settledLeft(3), motion.pillLeft, 0.01f)
        assertEquals(layout.settledRight(3), motion.pillRight, 0.01f)
    }

    @Test
    fun hoveringANeighbourLeansThePillTowardsIt() {
        val motion = motion()
        motion.hover(2)
        run(motion, 0.6f)
        assertEquals(layout.settledLeft(0), motion.pillLeft, 0.01f)
        assertEquals(layout.settledRight(0) + TabMotion.LEAN, motion.pillRight, 0.01f)
        assertEquals(1f, motion.highlight[2], 0.01f)

        motion.hover(0)
        run(motion, 0.6f)
        assertEquals(layout.settledRight(0), motion.pillRight, 0.01f)
        assertEquals(0f, motion.highlight[0], 0.01f, "the selected tab never lights up")
        assertEquals(0f, motion.highlight[2], 0.01f)
    }

    @Test
    fun hoveringATabToTheLeftLeansTheLeftEdge() {
        val motion = motion(initial = 3)
        motion.hover(1)
        run(motion, 0.6f)
        assertEquals(layout.settledLeft(3) - TabMotion.LEAN, motion.pillLeft, 0.01f)
        assertEquals(layout.settledRight(3), motion.pillRight, 0.01f)
        motion.hover(null)
        run(motion, 0.6f)
        assertEquals(layout.settledLeft(3), motion.pillLeft, 0.01f)
    }

    @Test
    fun itGoesBackToSleepAfterHoveringAndSwitching() {
        // The recording's run: rest on a tab, click it, rest on the next, step with the keyboard.
        val motion = motion()
        motion.hover(1)
        run(motion, 1f)
        assertFalse(motion.step(now + FRAME), "settled after leaning")
        motion.select(1, now)
        run(motion, 1.5f)
        assertFalse(motion.step(now + FRAME), "settled after a click")
        motion.select(3, now)
        run(motion, 1.5f)
        assertFalse(motion.step(now + FRAME), "settled with the pointer left behind")
    }

    @Test
    fun clickingTheSelectedTabChangesNothing() {
        val motion = motion()
        assertFalse(motion.select(0, now))
        assertFalse(motion.step(now + FRAME))
    }

    @Test
    fun aLongStallMovesOnlyOneShortStep() {
        // After a two-second hitch, the pill picks up where a 30 Hz frame would have left it,
        // instead of jumping straight to the end.
        val motion = motion()
        motion.select(3, now)
        now += 2_000_000_000L
        motion.step(now)
        val way =
            (motion.pillLeft - layout.settledLeft(0)) /
                (layout.settledLeft(3) - layout.settledLeft(0))
        assertTrue(way in 0.05f..0.5f, "the trailing edge went $way of the way")
        assertTrue(motion.step(now + FRAME), "and it is still on its way")
    }

    private companion object {
        const val FRAME = 16_666_667L
    }
}
