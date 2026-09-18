package dev.sebastiano.grabbystepper

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GrabbyKeysTest {
    @Test
    fun descriptionsIncludeTheCount() {
        assertEquals("Grabby stepper, count 7", stepperContentDescription(7))
        assertEquals("Count 0", stepperStateDescription(0))
    }

    @Test
    fun arrowsAndPlusMinusStep() {
        assertEquals(StepperKeyAction.Decrement, stepperKeyAction(Key.DirectionLeft))
        assertEquals(StepperKeyAction.Decrement, stepperKeyAction(Key.DirectionDown))
        assertEquals(StepperKeyAction.Decrement, stepperKeyAction(Key.Minus))
        assertEquals(StepperKeyAction.Increment, stepperKeyAction(Key.DirectionRight))
        assertEquals(StepperKeyAction.Increment, stepperKeyAction(Key.DirectionUp))
        assertEquals(StepperKeyAction.Increment, stepperKeyAction(Key.Equals))
        assertEquals(StepperKeyAction.Increment, stepperKeyAction(Key.Plus))
    }

    @Test
    fun zeroAndDeleteReset() {
        assertEquals(StepperKeyAction.Reset, stepperKeyAction(Key.Zero))
        assertEquals(StepperKeyAction.Reset, stepperKeyAction(Key.Delete))
        assertEquals(StepperKeyAction.Reset, stepperKeyAction(Key.Backspace))
        assertEquals(StepperKeyAction.Reset, stepperKeyAction(Key.MoveHome))
        assertNull(stepperKeyAction(Key.Escape))
    }
}
