package dev.sebastiano.processingfield

/**
 * Which mark the field is drawn with.
 *
 * Both variants describe the same shape with the same field maths; only the mark changes. Picking
 * one is a question of texture, not of meaning.
 */
enum class ProcessingFieldStyle(val label: String) {
    Dots("Dots"),
    Lines("Lines"),
}
