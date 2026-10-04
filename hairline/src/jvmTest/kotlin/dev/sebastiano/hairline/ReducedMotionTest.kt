package dev.sebastiano.hairline

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Reduced motion freezes ambient motion, so a figure at rest must stop asking for frames. */
class ReducedMotionTest {
    @Test
    fun phosphorSleepsOnceItsReducedPoseSettles() =
        withReducedMotion(true) {
            val stage = ParityTest.FakeStage()
            val phosphor = MountedFigure(Figure.Phosphor, stage, null) {}
            stage.step("adv 2", phosphor)
            assertFalse(stage.running, "a reduced Phosphor at rest should sleep")

            // Painting wakes it, and the afterglow fades back to the still before it sleeps again.
            stage.step("move 200 140", phosphor)
            stage.step("out", phosphor)
            stage.step("adv 2", phosphor)
            assertTrue(stage.running, "the afterglow should still be fading")
            stage.step("adv 600", phosphor)
            assertFalse(stage.running, "the afterglow should settle, then sleep")
            phosphor.destroy()
        }
}
