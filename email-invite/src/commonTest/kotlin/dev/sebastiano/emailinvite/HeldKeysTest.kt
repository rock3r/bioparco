package dev.sebastiano.emailinvite

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HeldKeysTest {
    @Test
    fun `a fresh press goes through`() {
        assertTrue(HeldKeys().press(ENTER))
    }

    @Test
    fun `repeats while the key is held are swallowed`() {
        val keys = HeldKeys()
        keys.press(ENTER)
        assertFalse(keys.press(ENTER))
        assertFalse(keys.press(ENTER))
    }

    @Test
    fun `releasing the key lets the next press through`() {
        val keys = HeldKeys()
        keys.press(ENTER)
        keys.release(ENTER)
        assertTrue(keys.press(ENTER))
    }

    @Test
    fun `keys are held separately`() {
        val keys = HeldKeys()
        keys.press(ENTER)
        assertTrue(keys.press(ESCAPE))
    }

    @Test
    fun `losing focus forgets every held key`() {
        val keys = HeldKeys()
        keys.press(ENTER)
        keys.reset()
        assertTrue(keys.press(ENTER))
    }

    private companion object {
        const val ENTER = 10L
        const val ESCAPE = 27L
    }
}
