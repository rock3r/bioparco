package dev.sebastiano.expandingsearch

/**
 * Moves the keyboard cursor [step] rows through a list of [count] rows, wrapping at both ends. A
 * cursor left past the end of a list that just got shorter comes back inside it.
 */
fun stepCursor(current: Int, count: Int, step: Int): Int {
    if (count <= 0) return 0
    // Past the end, the cursor stands just after the last row: down wraps, up lands on the last.
    if (current >= count) return if (step > 0) 0 else count - 1
    return (current + step).mod(count)
}

/** What the cross (or Escape) does. */
enum class Dismissal {
    /** Empties the field and keeps it open, back on the suggestions. */
    Clear,

    /** Folds the field back into the round button. */
    Close,
}

/** The cross clears the text first, and closes the field only once it is empty, as in the video. */
fun dismissalFor(query: CharSequence): Dismissal =
    if (query.isEmpty()) Dismissal.Close else Dismissal.Clear
