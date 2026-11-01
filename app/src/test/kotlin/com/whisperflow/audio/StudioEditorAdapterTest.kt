package com.whisperflow.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioEditorAdapterTest {

    @Test
    fun `starts empty and not a password field`() {
        val editor = StudioEditorAdapter()
        assertEquals("", editor.text)
        assertFalse(editor.isPasswordField())
        assertFalse(editor.hasSelection())
    }

    @Test
    fun `composing text is visible but not committed`() {
        val editor = StudioEditorAdapter()
        assertTrue(editor.setComposing("hello"))
        assertEquals("hello", editor.text)
    }

    @Test
    fun `commit replaces composing text`() {
        val editor = StudioEditorAdapter()
        editor.setComposing("hel")
        editor.commit("hello ")
        assertEquals("hello ", editor.text)
    }

    @Test
    fun `finishComposing keeps composing text`() {
        val editor = StudioEditorAdapter()
        editor.setComposing("world")
        editor.finishComposing()
        assertEquals("world", editor.text)
        editor.commit("!")
        assertEquals("world!", editor.text)
    }

    @Test
    fun `textBeforeCursor returns suffix`() {
        val editor = StudioEditorAdapter()
        editor.commit("hello world")
        assertEquals("world", editor.textBeforeCursor(5))
        assertEquals("hello world", editor.textBeforeCursor(100))
        assertEquals("", editor.textBeforeCursor(0))
    }

    @Test
    fun `deleteSurroundingCodePoints removes trailing text`() {
        val editor = StudioEditorAdapter()
        editor.commit("hello world")
        assertTrue(editor.deleteSurroundingCodePoints(6))
        assertEquals("hello", editor.text)
    }

    @Test
    fun `deleteSurroundingCodePoints respects surrogate pairs`() {
        val editor = StudioEditorAdapter()
        editor.commit("A😀")
        // "A" plus one emoji code point: deleting 1 must remove only the emoji.
        assertTrue(editor.deleteSurroundingCodePoints(1))
        assertEquals("A", editor.text)
    }

    @Test
    fun `deleteSurroundingCodePoints fails when asking for too much`() {
        val editor = StudioEditorAdapter()
        editor.commit("hi")
        assertFalse(editor.deleteSurroundingCodePoints(3))
        assertEquals("hi", editor.text)
    }

    @Test
    fun `commitReplacement appends replacement text`() {
        val editor = StudioEditorAdapter()
        editor.commit("raw ")
        editor.deleteSurroundingCodePoints(4)
        editor.commitReplacement("refined")
        assertEquals("refined", editor.text)
    }

    @Test
    fun `reset clears text and invalidates the editor token`() {
        val editor = StudioEditorAdapter()
        val before = editor.editorToken()
        editor.commit("hello")
        editor.reset()
        assertEquals("", editor.text)
        assertTrue(editor.editorToken() > before)
    }

    @Test
    fun `invalidateEditor changes the token without clearing text`() {
        val editor = StudioEditorAdapter()
        val before = editor.editorToken()
        editor.commit("hello")
        editor.invalidateEditor()
        assertTrue(editor.editorToken() > before)
        assertEquals("hello", editor.text)
    }

    @Test
    fun `password field can be simulated`() {
        val editor = StudioEditorAdapter()
        editor.setPasswordField(true)
        assertTrue(editor.isPasswordField())
        editor.setPasswordField(false)
        assertFalse(editor.isPasswordField())
    }
}
