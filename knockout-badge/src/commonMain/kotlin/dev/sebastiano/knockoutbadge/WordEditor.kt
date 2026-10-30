package dev.sebastiano.knockoutbadge

/**
 * The word cut out of the sticker, typed straight onto the badge. The first key after a pause
 * starts a new word, keys in quick succession add to it, and Backspace edits whatever is on show.
 */
class WordEditor(initial: String) {
    var word: String = initial
        private set

    private var lastEdit = Long.MIN_VALUE / 2

    /** Types [char] at [atMillis]; returns whether the word changed. */
    fun type(char: Char, atMillis: Long): Boolean {
        if (char.isWhitespace() || char.isISOControl()) return false
        val fresh = atMillis - lastEdit > PAUSE_MILLIS
        lastEdit = atMillis
        val base = if (fresh) "" else word
        if (base.length >= MAX_LENGTH) return false
        word = base + char
        return true
    }

    /** Deletes the last character at [atMillis]; returns whether the word changed. */
    fun erase(atMillis: Long): Boolean {
        if (word.isEmpty()) return false
        lastEdit = atMillis
        word = word.dropLast(1)
        return true
    }

    companion object {
        const val PAUSE_MILLIS = 1_500L
        const val MAX_LENGTH = 6
    }
}
