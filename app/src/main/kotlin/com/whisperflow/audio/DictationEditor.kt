package com.whisperflow.audio

interface DictationEditor {
    fun isPasswordField(): Boolean
    fun editorToken(): Long
    fun setComposing(text: String): Boolean
    fun commit(text: String): Boolean
    fun finishComposing(): Boolean
    fun textBeforeCursor(length: Int): String?
    fun hasSelection(): Boolean
    fun beginBatchEdit(): Boolean
    fun deleteSurroundingCodePoints(codePoints: Int): Boolean
    fun endBatchEdit()
    fun commitReplacement(text: String): Boolean
}
