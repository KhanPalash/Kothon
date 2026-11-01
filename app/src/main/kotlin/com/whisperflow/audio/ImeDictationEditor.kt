package com.whisperflow.audio

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

/**
 * [DictationEditor] backed by the IME's live [InputConnection].
 *
 * All calls fail closed: a missing/recreated connection returns false so the
 * controller keeps the already inserted raw text instead of deleting blindly.
 */
class ImeDictationEditor(
    private val connection: () -> InputConnection?,
    private val editorInfo: () -> EditorInfo?,
    private val token: () -> Long
) : DictationEditor {

    override fun isPasswordField(): Boolean =
        PasswordFieldDetector.shouldDisableVoice(editorInfo())

    override fun editorToken(): Long = token()

    override fun setComposing(text: String): Boolean =
        connection()?.setComposingText(text, 1) ?: false

    override fun commit(text: String): Boolean =
        connection()?.commitText(text, 1) ?: false

    override fun finishComposing(): Boolean {
        val conn = connection() ?: return false
        conn.finishComposingText()
        return true
    }

    override fun textBeforeCursor(length: Int): String? =
        connection()?.getTextBeforeCursor(length, 0)?.toString()

    override fun hasSelection(): Boolean {
        val conn = connection() ?: return false
        return try {
            !conn.getSelectedText(0).isNullOrEmpty()
        } catch (e: Exception) {
            // If selection state cannot be read, skip replacement (fail closed).
            true
        }
    }

    override fun beginBatchEdit(): Boolean =
        connection()?.beginBatchEdit() ?: false

    override fun deleteSurroundingCodePoints(codePoints: Int): Boolean =
        connection()?.deleteSurroundingTextInCodePoints(codePoints, 0) ?: false

    override fun endBatchEdit() {
        connection()?.endBatchEdit()
    }

    override fun commitReplacement(text: String): Boolean =
        connection()?.commitText(text, 1) ?: false
}
