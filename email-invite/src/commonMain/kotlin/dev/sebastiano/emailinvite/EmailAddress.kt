package dev.sebastiano.emailinvite

/** What the field needs to know about the address being typed. */
object EmailAddress {
    /**
     * True once [text] looks like a whole address: a name, one `@`, then a dotted domain whose last
     * label has at least two characters. That last rule is what keeps the video's Send grey through
     * `new@gmail.c` and turns it black on `new@gmail.com`.
     *
     * This is a cue for the button, not validation: it does not try to follow RFC 5322.
     */
    fun isSendable(text: String): Boolean {
        if (text.any { it.isWhitespace() }) return false
        val parts = text.split('@')
        if (parts.size != 2) return false
        val (name, domain) = parts
        val labels = domain.split('.')
        return name.isNotEmpty() &&
            labels.size >= 2 &&
            labels.none { it.isEmpty() } &&
            labels.last().length >= MIN_TOP_LEVEL_LENGTH
    }

    /** The letter the new avatar shows: the first letter or digit of the name, upper case. */
    fun initialOf(email: String): Char =
        email.substringBefore('@').firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '?'

    private const val MIN_TOP_LEVEL_LENGTH = 2
}
