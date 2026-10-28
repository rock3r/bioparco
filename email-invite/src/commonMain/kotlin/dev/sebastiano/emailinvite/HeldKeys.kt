package dev.sebastiano.emailinvite

/**
 * Lets a key's first press through and swallows the repeats the OS sends while it is held. Enter
 * both opens the pill and sends, so a held Enter must not send and then open it again.
 */
class HeldKeys {
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
