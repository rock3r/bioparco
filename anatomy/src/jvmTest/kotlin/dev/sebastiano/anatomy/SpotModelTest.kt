// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.figures.SpotModel
import dev.sebastiano.anatomy.figures.SpotMotion
import dev.sebastiano.anatomy.figures.SpotRig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpotModelTest {
    @Test
    fun `a tangent pair stays apart and only a smaller gap joins`() {
        for ((index, gap) in SpotRig.GAPS.withIndex()) {
            assertEquals(index, SpotModel.joinedAt(gap).size)
            assertEquals(index + 1, SpotModel.joinedAt(gap + 0.25).size)
            assertEquals(0.0, SpotModel.neckOf(gap, gap))
            assertTrue(SpotModel.neckOf(gap, gap + 0.25) > 0)
        }
        assertEquals("merge 24 px · 3 of 6 joined", SpotModel.readoutOf(24.0))
        assertEquals(
            "merge 25 px: 4 of 6 pairs joined, gaps 4, 10, 16 and 24 px; 32 and 44 px stay apart",
            SpotModel.spokenOf(24.5),
        )
    }

    @Test
    fun `pointer travel saturates at the two ends`() {
        assertEquals(0.0, SpotModel.mergeAtX(-100.0))
        assertEquals(0.0, SpotModel.mergeAtX(96.0))
        assertEquals(12.0, SpotModel.mergeAtX(198.0), 1e-14)
        assertEquals(48.0, SpotModel.mergeAtX(504.0))
        assertEquals(48.0, SpotModel.mergeAtX(1000.0))
    }

    @Test
    fun `calm answers input immediately without starting a tour`() {
        val motion = SpotMotion()
        motion.aim(60.0)
        assertFalse(motion.tick(0.016, true))
        assertEquals(48.0, motion.shown)
        motion.aim(-3.0)
        assertFalse(motion.tick(0.016, true))
        assertEquals(0.0, motion.shown)
    }

    @Test
    fun `motion eases twice then settles and the idle timer can resume the tour`() {
        val motion = SpotMotion()
        motion.engage(true)
        motion.aim(48.0)
        assertTrue(motion.tick(0.016, false))
        assertTrue(motion.shown > 20 && motion.shown < motion.lead && motion.lead < 48)
        repeat(200) { motion.tick(0.016, false) }
        assertEquals(48.0, motion.shown)
        assertFalse(motion.tick(0.016, false))
        motion.engage(false)
        motion.resumeTour()
        // Resuming starts at the first stop, then visits the opposite extreme.
        repeat(400) { motion.tick(0.016, false) }
        assertTrue(motion.goal == 0.0)
        assertTrue(motion.shown < 48)
    }
}
