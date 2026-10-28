package dev.sebastiano.emailinvite

/** One avatar in the stack. [id] tells two guests with the same initial apart. */
data class Guest(val id: Int, val initial: Char, val invited: Boolean)

/** What one invite did to the stack: who joined at the front, and who fell off the back. */
data class StackChange(val arrived: Guest, val left: Guest?)

/**
 * The avatars at the left of the pill, newest first. It starts with the video's A, M and L, and
 * shows [MAX_SHOWN] at most: past that, the oldest leaves from the back as a new guest arrives.
 */
class GuestStack {
    private var nextId = 0
    private val guests = mutableListOf(guest('A', false), guest('M', false), guest('L', false))

    val shown: List<Guest>
        get() = guests.toList()

    /** The latest invite, which the pill paints in the accent colour. */
    var newest: Guest? = null
        private set

    fun invite(initial: Char): StackChange {
        val arrived = guest(initial, invited = true)
        guests.add(0, arrived)
        newest = arrived
        val left = if (guests.size > MAX_SHOWN) guests.removeAt(guests.lastIndex) else null
        return StackChange(arrived, left)
    }

    private fun guest(initial: Char, invited: Boolean) = Guest(nextId++, initial, invited)

    companion object {
        const val MAX_SHOWN = 4
    }
}
