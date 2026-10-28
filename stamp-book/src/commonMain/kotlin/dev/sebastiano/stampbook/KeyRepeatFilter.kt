package dev.sebastiano.stampbook

/**
 * Lets a key's first press through and swallows the repeats the OS sends while it is held, so
 * holding Space opens or closes the book once rather than flapping it.
 */
class KeyRepeatFilter {
    private val held = mutableSetOf<Long>()

    /** True for a fresh press of [key], false for a repeat while it is still held. */
    fun press(key: Long): Boolean = held.add(key)

    fun release(key: Long) {
        held.remove(key)
    }

    /** Forgets every held key, for when the release will never arrive (focus moved away). */
    fun reset() {
        held.clear()
    }
}
