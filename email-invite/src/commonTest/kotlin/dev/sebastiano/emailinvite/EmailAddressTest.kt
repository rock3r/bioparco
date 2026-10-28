package dev.sebastiano.emailinvite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmailAddressTest {
    @Test
    fun `the address being typed in the video arms Send only once it is whole`() {
        // The video's Send stays grey through "new@gmail.c" and turns black on "new@gmail.com".
        for (partial in listOf("", "n", "ne", "new", "new@", "new@g", "new@gmail", "new@gmail.")) {
            assertFalse(EmailAddress.isSendable(partial), "'$partial' should not arm Send")
        }
        assertFalse(EmailAddress.isSendable("new@gmail.c"))
        assertTrue(EmailAddress.isSendable("new@gmail.co"))
        assertTrue(EmailAddress.isSendable("new@gmail.com"))
    }

    @Test
    fun `an address needs a name before the at`() {
        assertFalse(EmailAddress.isSendable("@gmail.com"))
    }

    @Test
    fun `an address has exactly one at`() {
        assertFalse(EmailAddress.isSendable("new@old@gmail.com"))
    }

    @Test
    fun `the domain needs a name before its dot and no empty labels`() {
        assertFalse(EmailAddress.isSendable("new@.com"))
        assertFalse(EmailAddress.isSendable("new@gmail..com"))
        assertFalse(EmailAddress.isSendable("new@gmail.com."))
    }

    @Test
    fun `spaces never arm Send`() {
        assertFalse(EmailAddress.isSendable("new @gmail.com"))
        assertFalse(EmailAddress.isSendable("new@gmail.com "))
    }

    @Test
    fun `subdomains and plus tags are fine`() {
        assertTrue(EmailAddress.isSendable("ada.lovelace+invites@mail.example.org"))
    }

    @Test
    fun `the new avatar shows the first letter of the address, upper case`() {
        assertEquals('N', EmailAddress.initialOf("new@gmail.com"))
        assertEquals('A', EmailAddress.initialOf("ada@example.org"))
    }

    @Test
    fun `the initial skips punctuation but keeps digits`() {
        assertEquals('J', EmailAddress.initialOf("_.jane@example.org"))
        assertEquals('4', EmailAddress.initialOf("42@example.org"))
    }

    @Test
    fun `an address with nothing to show gets a question mark`() {
        assertEquals('?', EmailAddress.initialOf("@example.org"))
        assertEquals('?', EmailAddress.initialOf(""))
    }
}
