package dev.sebastiano.knockoutbadge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WordEditorTest {
    @Test
    fun `the first key starts a new word`() {
        val editor = WordEditor("init")
        assertTrue(editor.type('c', atMillis = 10_000))
        assertEquals("c", editor.word)
    }

    @Test
    fun `quick keys add to the word`() {
        val editor = WordEditor("init")
        "cool!".forEachIndexed { i, char -> editor.type(char, atMillis = 10_000L + i * 150) }
        assertEquals("cool!", editor.word)
    }

    @Test
    fun `a key after a pause starts another word`() {
        val editor = WordEditor("init")
        "hey".forEachIndexed { i, char -> editor.type(char, atMillis = 10_000L + i * 100) }
        editor.type('y', atMillis = 10_200L + WordEditor.PAUSE_MILLIS + 1)
        assertEquals("y", editor.word)
    }

    @Test
    fun `backspace edits the word on show, and typing after it carries on`() {
        val editor = WordEditor("init")
        assertTrue(editor.erase(atMillis = 10_000))
        assertTrue(editor.erase(atMillis = 10_100))
        editor.type('k', atMillis = 10_200)
        assertEquals("ink", editor.word)
    }

    @Test
    fun `backspace on an empty word changes nothing`() {
        val editor = WordEditor("")
        assertFalse(editor.erase(atMillis = 10_000))
        assertEquals("", editor.word)
    }

    @Test
    fun `the word stops growing at its longest`() {
        val editor = WordEditor("init")
        "abcdefghij".forEachIndexed { i, char -> editor.type(char, atMillis = 10_000L + i * 50) }
        assertEquals("abcdefghij".take(WordEditor.MAX_LENGTH), editor.word)
    }

    @Test
    fun `keys that print nothing are ignored`() {
        val editor = WordEditor("init")
        assertFalse(editor.type('\n', atMillis = 10_000))
        assertFalse(editor.type('\t', atMillis = 10_000))
        assertFalse(editor.type(' ', atMillis = 10_000))
        assertEquals("init", editor.word)
    }
}
