package com.whisperflow.audio

/**
 * Hand-written fake DictationEditor for unit tests.
 */
class FakeDictationEditor(
    private var isPassword: Boolean = false,
    private var token: Long = 1L,
    var composingSupported: Boolean = true,
    var commitSupported: Boolean = true
) : DictationEditor {

    val committed = StringBuilder()
    var composingText: String = ""
    var batchActive = false
    var selectionStart = -1
    var selectionEnd = -1

    // Tracking for safe-replace tests
    val replacementHistory = mutableListOf<String>()
    var deleteCodePointsCalled = 0
    var deleteCodePointsLastCount = 0
    var batchEditBeganCount = 0
    var beginBatchEditResult = true
    var deleteSurroundingResult = true
    var commitReplacementResult = true

    override fun isPasswordField(): Boolean = isPassword

    override fun editorToken(): Long = token

    fun setToken(t: Long) { token = t }

    override fun setComposing(text: String): Boolean {
        if (!composingSupported) return false
        composingText = text
        return true
    }

    override fun commit(text: String): Boolean {
        if (!commitSupported) return false
        committed.append(text)
        composingText = ""
        return true
    }

    override fun finishComposing(): Boolean {
        if (composingText.isNotEmpty()) {
            committed.append(composingText)
            composingText = ""
        }
        return true
    }

    override fun textBeforeCursor(length: Int): String? {
        val s = committed.toString()
        return if (s.length >= length) s.substring(s.length - length) else s
    }

    override fun hasSelection(): Boolean = selectionStart != selectionEnd && selectionStart >= 0

    override fun beginBatchEdit(): Boolean {
        if (!beginBatchEditResult) return false
        batchActive = true
        batchEditBeganCount++
        return true
    }

    override fun deleteSurroundingCodePoints(codePoints: Int): Boolean {
        deleteCodePointsCalled++
        deleteCodePointsLastCount = codePoints
        if (!deleteSurroundingResult) return false
        val s = committed.toString()
        val cpCount = s.codePointCount(0, s.length)
        if (codePoints > cpCount) return false
        // Convert code point count to char index
        var idx = s.length
        var remaining = codePoints
        while (remaining > 0 && idx > 0) {
            idx = s.offsetByCodePoints(idx, -1)
            remaining--
        }
        committed.delete(idx, committed.length)
        return true
    }

    override fun endBatchEdit() {
        batchActive = false
    }

    override fun commitReplacement(text: String): Boolean {
        if (!commitReplacementResult) return false
        replacementHistory.add(text)
        committed.append(text)
        return true
    }

    // Additional helpers for Phase 5 tests
    fun insertText(text: String) {
        committed.append(text)
    }

    fun getText(): String = committed.toString()

    fun setCursor(pos: Int) {
        // In this fake, cursor position is tracked as the end of committed text
        // For tests, we just note it but the editor logic uses textBeforeCursor
    }

    fun getCursor(): Int = committed.length

    fun setSelection(start: Int, end: Int) {
        selectionStart = start
        selectionEnd = end
    }

    fun setPasswordField(isPassword: Boolean) {
        this.isPassword = isPassword
    }

    val committedText: String
        get() = committed.toString()
}
