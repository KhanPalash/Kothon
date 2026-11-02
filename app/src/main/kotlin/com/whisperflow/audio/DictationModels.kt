package com.whisperflow.audio

data class DictationSession(
    val id: Long,
    val editorToken: Long,
    val startCursor: Int,
    val separator: String,
    var committed: String = "",
    var currentPartial: String = "",
    var lastFinal: String = "",
    var composingVisible: Boolean = false,
    var invalid: Boolean = false
)

data class ReplaceSnapshot(
    val sessionId: Long,
    val editorToken: Long,
    val insertedText: String,
    val insertedCodePoints: Int
)

data class UndoState(
    val rawText: String,
    val refinedText: String,
    val editorToken: Long = 0L,
    val codePoints: Int = 0
) {
    val textToRestore: String get() = rawText
}

enum class DictationState {
    IDLE, LISTENING, STOPPING, REFINING, DONE, ERROR
}

data class DictationUiState(
    val state: DictationState = DictationState.IDLE,
    val statusMessage: String = "",
    val amplitude: Float = 0f,
    val canUndo: Boolean = false,
    /** True when auto-refine is off and the user can request refinement manually. */
    val canRefine: Boolean = false,
    /** Raw + current partial text for the in-app studio preview. */
    val liveText: String = "",
    /** Session identity so the studio can de-duplicate completion handling. */
    val sessionId: Long = 0L
)
