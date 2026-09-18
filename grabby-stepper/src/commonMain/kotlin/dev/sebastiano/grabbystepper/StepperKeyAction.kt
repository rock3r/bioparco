package dev.sebastiano.grabbystepper

import androidx.compose.ui.input.key.Key

enum class StepperKeyAction {
    Increment,
    Decrement,
    Reset,
}

fun stepperContentDescription(count: Int): String = "Grabby stepper, count $count"

fun stepperStateDescription(count: Int): String = "Count $count"

/**
 * Desktop keyboard map. Arrows and −/+ step; 0 / Delete / Backspace / Home reset (the pointer
 * equivalent of a vertical pull-to-zero).
 */
fun stepperKeyAction(key: Key): StepperKeyAction? =
    when (key) {
        Key.DirectionRight,
        Key.DirectionUp,
        Key.Equals,
        Key.Plus,
        Key.NumPadAdd -> StepperKeyAction.Increment
        Key.DirectionLeft,
        Key.DirectionDown,
        Key.Minus,
        Key.NumPadSubtract -> StepperKeyAction.Decrement
        Key.Zero,
        Key.NumPad0,
        Key.Delete,
        Key.Backspace,
        Key.MoveHome -> StepperKeyAction.Reset
        else -> null
    }
