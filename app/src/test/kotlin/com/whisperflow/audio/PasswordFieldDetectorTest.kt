package com.whisperflow.audio

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 0 Password Field Detection Tests
 *
 * Gate Criteria: Zero failures - all password types must be detected
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PasswordFieldDetectorTest {

    private fun createEditorInfo(inputType: Int): EditorInfo {
        return EditorInfo().apply {
            this.inputType = inputType
        }
    }

    @Test
    fun `text password field is detected`() {
        val editorInfo = createEditorInfo(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        assertTrue("Text password must be detected", PasswordFieldDetector.isPasswordField(editorInfo))
        assertTrue("Voice must be disabled", PasswordFieldDetector.shouldDisableVoice(editorInfo))
    }

    @Test
    fun `visible password field is detected`() {
        val editorInfo = createEditorInfo(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        )
        assertTrue("Visible password must be detected", PasswordFieldDetector.isPasswordField(editorInfo))
        assertTrue("Voice must be disabled", PasswordFieldDetector.shouldDisableVoice(editorInfo))
    }

    @Test
    fun `web password field is detected`() {
        val editorInfo = createEditorInfo(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )
        assertTrue("Web password must be detected", PasswordFieldDetector.isPasswordField(editorInfo))
        assertTrue("Voice must be disabled", PasswordFieldDetector.shouldDisableVoice(editorInfo))
    }

    @Test
    fun `numeric password field is detected`() {
        val editorInfo = createEditorInfo(
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        )
        assertTrue("Numeric password must be detected", PasswordFieldDetector.isPasswordField(editorInfo))
        assertTrue("Voice must be disabled", PasswordFieldDetector.shouldDisableVoice(editorInfo))
    }

    @Test
    fun `normal text field is not password`() {
        val editorInfo = createEditorInfo(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        )
        assertFalse("Normal text must not be detected as password", PasswordFieldDetector.isPasswordField(editorInfo))
        assertFalse("Voice must be enabled", PasswordFieldDetector.shouldDisableVoice(editorInfo))
    }

    @Test
    fun `email field is not password`() {
        val editorInfo = createEditorInfo(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )
        assertFalse("Email field must not be detected as password", PasswordFieldDetector.isPasswordField(editorInfo))
        assertFalse("Voice must be enabled", PasswordFieldDetector.shouldDisableVoice(editorInfo))
    }

    @Test
    fun `normal number field is not password`() {
        val editorInfo = createEditorInfo(
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_NORMAL
        )
        assertFalse("Normal number must not be detected as password", PasswordFieldDetector.isPasswordField(editorInfo))
        assertFalse("Voice must be enabled", PasswordFieldDetector.shouldDisableVoice(editorInfo))
    }

    @Test
    fun `null editorInfo is not password`() {
        assertFalse("Null EditorInfo must not be detected as password", PasswordFieldDetector.isPasswordField(null))
        assertFalse("Voice must be enabled for null", PasswordFieldDetector.shouldDisableVoice(null))
    }

    @Test
    fun `input type description for text password`() {
        val editorInfo = createEditorInfo(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        val description = PasswordFieldDetector.getInputTypeDescription(editorInfo)
        assertEquals("TEXT/PASSWORD", description)
    }

    @Test
    fun `input type description for web password`() {
        val editorInfo = createEditorInfo(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )
        val description = PasswordFieldDetector.getInputTypeDescription(editorInfo)
        assertEquals("TEXT/WEB_PASSWORD", description)
    }

    @Test
    fun `input type description for numeric password`() {
        val editorInfo = createEditorInfo(
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        )
        val description = PasswordFieldDetector.getInputTypeDescription(editorInfo)
        assertEquals("NUMBER/PASSWORD", description)
    }

    @Test
    fun `input type description for normal text`() {
        val editorInfo = createEditorInfo(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        )
        val description = PasswordFieldDetector.getInputTypeDescription(editorInfo)
        assertEquals("TEXT/NORMAL", description)
    }

    @Test
    fun `input type description for null`() {
        val description = PasswordFieldDetector.getInputTypeDescription(null)
        assertEquals("null", description)
    }
}
