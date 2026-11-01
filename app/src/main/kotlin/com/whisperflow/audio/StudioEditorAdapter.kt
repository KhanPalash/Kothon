package com.whisperflow.audio

/**
 * In-memory [DictationEditor] for the Compose studio screen.
 *
 * The studio does not own an InputConnection; the controller can still use the
 * exact same live typing, safe replace and undo logic as the IME by writing to
 * this adapter. [text] is the single source of truth rendered by the screen.
 */
class StudioEditorAdapter : DictationEditor {

    private val committed = StringBuilder()
    private var composing: String = ""
    private var token: Long = 1L
    private var passwordField: Boolean = false

    /** Full editor content, including an active composing region. */
    val text: String
        get() = committed.toString() + composing

    fun reset() {
        committed.clear()
        composing = ""
        token++
    }

    /** Simulates a field switch so a pending safe-replace is rejected. */
    fun invalidateEditor() {
        token++
    }

    fun setPasswordField(value: Boolean) {
        passwordField = value
    }

    override fun isPasswordField(): Boolean = passwordField

    override fun editorToken(): Long = token

    override fun setComposing(text: String): Boolean {
        composing = text
        return true
    }

    override fun commit(text: String): Boolean {
        // commitText replaces the active composing region.
        composing = ""
        committed.append(text)
        return true
    }

    override fun finishComposing(): Boolean {
        // finishComposingText keeps the composing text in the editor.
        if (composing.isNotEmpty()) {
            committed.append(composing)
            composing = ""
        }
        return true
    }

    override fun textBeforeCursor(length: Int): String? {
        val full = text
        return when {
            length <= 0 -> ""
            length >= full.length -> full
            else -> full.takeLast(length)
        }
    }

    override fun hasSelection(): Boolean = false

    override fun beginBatchEdit(): Boolean = true

    override fun deleteSurroundingCodePoints(codePoints: Int): Boolean {
        if (codePoints <= 0) return true
        val full = text
        val totalCodePoints = full.codePointCount(0, full.length)
        if (codePoints > totalCodePoints) return false

        var cutIndex = full.length
        repeat(codePoints) {
            cutIndex = full.offsetByCodePoints(cutIndex, -1)
        }
        val remaining = full.substring(0, cutIndex)
        committed.clear()
        committed.append(remaining)
        composing = ""
        return true
    }

    override fun endBatchEdit() = Unit

    override fun commitReplacement(text: String): Boolean {
        composing = ""
        committed.append(text)
        return true
    }
}
