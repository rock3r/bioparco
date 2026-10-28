package dev.sebastiano.emailinvite

/** Where the pill is in its one-way loop: collapsed, editing an address, or showing Sent. */
enum class InvitePhase {
    Collapsed,
    Editing,
    Sent,
}

/**
 * The pill's states and the only ways between them:
 * ```
 * Collapsed --open--> Editing --submit (a whole address)--> Sent --settle--> Collapsed
 *                     Editing --cancel--> Collapsed
 * ```
 *
 * Every call returns whether it changed anything, so a held Enter sends once and a click that lands
 * mid-transition does nothing.
 */
class InviteFlow {
    var phase: InvitePhase = InvitePhase.Collapsed
        private set

    fun open(): Boolean = move(from = InvitePhase.Collapsed, to = InvitePhase.Editing)

    fun submit(text: String): Boolean =
        EmailAddress.isSendable(text) && move(from = InvitePhase.Editing, to = InvitePhase.Sent)

    fun cancel(): Boolean = move(from = InvitePhase.Editing, to = InvitePhase.Collapsed)

    /** Ends the Sent beat, once the new guest has joined the stack. */
    fun settle(): Boolean = move(from = InvitePhase.Sent, to = InvitePhase.Collapsed)

    private fun move(from: InvitePhase, to: InvitePhase): Boolean {
        if (phase != from) return false
        phase = to
        return true
    }
}
