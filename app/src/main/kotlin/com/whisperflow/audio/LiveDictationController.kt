package com.whisperflow.audio

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class LiveDictationController(
    private val speechSession: SpeechSession,
    private val editor: DictationEditor,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    private val dispatcher: CoroutineDispatcher? = null,
    var autoRefine: Boolean = true,
    var textRefiner: (suspend (String) -> String?)? = null
) : SpeechSession.Listener {

    constructor(
        speechSession: SpeechSession,
        editor: DictationEditor,
        scope: CoroutineScope,
        textRefiner: suspend (String) -> String?
    ) : this(
        speechSession = speechSession,
        editor = editor,
        scope = scope,
        dispatcher = null,
        autoRefine = true,
        textRefiner = textRefiner
    )

    private val _uiState = MutableStateFlow(DictationUiState())
    val uiState: StateFlow<DictationUiState> = _uiState.asStateFlow()

    private var session: DictationSession? = null
    private var sessionIdCounter = 0L
    private var refineJob: Job? = null
    private var lastUndoState: UndoState? = null
    var pendingRefinedText: String? = null
        private set

    /** Raw text produced by live speech for the last completed session. */
    var lastRawText: String = ""
        private set

    /** Text currently inserted by the controller (raw or refined). */
    val sessionText: String
        get() = session?.committed.orEmpty()

    val isActive: Boolean
        get() = _uiState.value.state == DictationState.LISTENING ||
                _uiState.value.state == DictationState.STOPPING ||
                _uiState.value.state == DictationState.REFINING

    fun start(config: SpeechConfig, separator: String = " ") {
        val current = _uiState.value.state
        if (current == DictationState.LISTENING ||
            current == DictationState.STOPPING ||
            current == DictationState.REFINING
        ) {
            return
        }
        if (editor.isPasswordField()) {
            _uiState.value = DictationUiState(
                state = DictationState.IDLE,
                statusMessage = "Voice disabled for password fields."
            )
            return
        }

        refineJob?.cancel()
        refineJob = null
        lastUndoState = null
        pendingRefinedText = null
        lastRawText = ""

        val newSession = DictationSession(
            id = ++sessionIdCounter,
            editorToken = editor.editorToken(),
            startCursor = 0,
            separator = separator
        )
        session = newSession
        _uiState.value = DictationUiState(
            state = DictationState.LISTENING,
            statusMessage = "Listening…",
            sessionId = newSession.id
        )
        speechSession.start(config, this)
    }

    fun stop() {
        val current = _uiState.value.state
        if (current != DictationState.LISTENING) {
            return
        }
        _uiState.value = _uiState.value.copy(state = DictationState.STOPPING, statusMessage = "Stopping…")
        speechSession.stop()
    }

    fun cancelSession() {
        refineJob?.cancel()
        refineJob = null
        session?.invalid = true
        lastUndoState = null
        pendingRefinedText = null
        speechSession.destroy()
        _uiState.value = DictationUiState(state = DictationState.IDLE)
    }

    fun onFieldChanged(newToken: Long = 0L) {
        refineJob?.cancel()
        refineJob = null
        session?.invalid = true
        lastUndoState = null
        pendingRefinedText = null
        speechSession.destroy()
        _uiState.value = DictationUiState(state = DictationState.IDLE)
    }

    fun undo(): Boolean {
        val undo = lastUndoState ?: return false
        lastUndoState = null

        if (editor.editorToken() != undo.editorToken && undo.editorToken != 0L) {
            _uiState.value = _uiState.value.copy(canUndo = false)
            return false
        }
        if (editor.hasSelection()) {
            _uiState.value = _uiState.value.copy(canUndo = false)
            return false
        }
        if (undo.refinedText.isNotEmpty()) {
            val before = editor.textBeforeCursor(undo.codePoints)
            val matches = before == undo.refinedText ||
                (undo.refinedText.length != undo.codePoints &&
                 editor.textBeforeCursor(undo.refinedText.length) == undo.refinedText)
            if (!matches) {
                _uiState.value = _uiState.value.copy(canUndo = false)
                return false
            }
        }

        editor.beginBatchEdit()
        try {
            val deleted = editor.deleteSurroundingCodePoints(undo.codePoints)
            if (!deleted) {
                _uiState.value = _uiState.value.copy(canUndo = false)
                return false
            }
            val restored = editor.commitReplacement(undo.textToRestore)
            if (!restored) {
                _uiState.value = _uiState.value.copy(canUndo = false)
                return false
            }
            _uiState.value = _uiState.value.copy(
                statusMessage = "Reverted to raw text.",
                canUndo = false,
                canRefine = false,
                liveText = undo.textToRestore
            )
            return true
        } finally {
            editor.endBatchEdit()
        }
    }

    // SpeechSession.Listener callbacks

    override fun onReady() {
        _uiState.value = _uiState.value.copy(statusMessage = "Listening…")
    }

    override fun onPartial(text: String) {
        val sess = session ?: return
        if (sess.invalid) return

        val normalized = text.trim()
        if (normalized.isBlank()) return

        // Strip committed prefix to avoid duplicate display
        val stripped = if (normalized.startsWith(sess.committed)) {
            normalized.removePrefix(sess.committed).trimStart()
        } else {
            normalized
        }
        if (stripped.isBlank()) return

        val ok = editor.setComposing(stripped)
        if (ok) {
            sess.composingVisible = true
            sess.currentPartial = stripped
            _uiState.value = _uiState.value.copy(
                statusMessage = "Listening…",
                liveText = sess.committed + stripped
            )
        } else {
            sess.composingVisible = false
            sess.currentPartial = ""
            _uiState.value = _uiState.value.copy(
                statusMessage = "Live preview unavailable in this app.",
                liveText = sess.committed
            )
        }
    }

    override fun onSegmentFinal(text: String) {
        val sess = session ?: return
        if (sess.invalid) return

        val normalized = text.trim()
        if (normalized.isBlank()) return

        // Dedupe: exact match with last final
        if (normalized == sess.lastFinal) return

        // Strip committed prefix if final is cumulative
        val stripped = if (normalized.startsWith(sess.committed)) {
            normalized.removePrefix(sess.committed).trimStart()
        } else {
            normalized
        }
        if (stripped.isBlank()) return

        // Word-level overlap: last 2 words
        val strippedWords = stripped.split(" ").filter { it.isNotBlank() }
        val committedWords = sess.committed.trim().split(" ").filter { it.isNotBlank() }
        if (strippedWords.isNotEmpty() && committedWords.size >= 2) {
            val lastTwo = committedWords.takeLast(2)
            val firstTwo = strippedWords.take(2)
            if (lastTwo == firstTwo) return
        }

        val toCommit = stripped + sess.separator
        val ok = editor.commit(toCommit)
        if (!ok) {
            sess.invalid = true
            _uiState.value = _uiState.value.copy(
                state = DictationState.ERROR,
                statusMessage = "Text insertion failed."
            )
            speechSession.destroy()
            return
        }

        sess.committed += toCommit
        sess.lastFinal = normalized
        sess.currentPartial = ""
        sess.composingVisible = false
        _uiState.value = _uiState.value.copy(liveText = sess.committed)
    }

    override fun onError(code: Int, message: String, recoverable: Boolean) {
        _uiState.value = _uiState.value.copy(statusMessage = message)
        if (!recoverable) {
            session?.invalid = true
        }
    }

    override fun onEnd() {
        val sess = session
        if (sess != null && !sess.invalid) {
            // Finish composing: composingVisible → commit partial
            if (sess.composingVisible && sess.currentPartial.isNotBlank()) {
                editor.finishComposing()
                sess.committed += sess.currentPartial
                sess.composingVisible = false
                sess.currentPartial = ""
            } else {
                editor.finishComposing()
            }
        }

        val currentState = _uiState.value.state
        if (currentState == DictationState.LISTENING || currentState == DictationState.STOPPING) {
            val committedText = sess?.committed.orEmpty()
            if (autoRefine && committedText.isNotBlank() && sess != null && !sess.invalid) {
                transitionToRefining(sess, committedText)
            } else {
                _uiState.value = _uiState.value.copy(
                    state = DictationState.DONE,
                    statusMessage = if (committedText.isNotBlank()) "Done." else "No speech detected.",
                    canUndo = false,
                    canRefine = committedText.isNotBlank() &&
                        !autoRefine &&
                        sess != null &&
                        !sess.invalid,
                    liveText = committedText,
                    sessionId = sess?.id ?: _uiState.value.sessionId
                )
            }
        }
    }

    /**
     * Manual refinement for when `live_auto_refine` is disabled.
     * Only valid after a session has finished with non-blank text.
     */
    fun refineNow() {
        val sess = session ?: return
        if (_uiState.value.state != DictationState.DONE || sess.invalid) return
        if (textRefiner == null) return
        if (sess.committed.isBlank()) return
        transitionToRefining(sess, sess.committed)
    }

    private fun transitionToRefining(sess: DictationSession, committedText: String) {
        val snapshot = ReplaceSnapshot(
            sessionId = sess.id,
            editorToken = sess.editorToken,
            insertedText = committedText,
            insertedCodePoints = committedText.codePointCount(0, committedText.length)
        )
        lastRawText = committedText
        pendingRefinedText = null
        _uiState.value = _uiState.value.copy(
            state = DictationState.REFINING,
            statusMessage = "Refining…",
            canUndo = false,
            canRefine = false,
            liveText = committedText,
            sessionId = sess.id
        )
        speechSession.destroy()
        refineJob?.cancel()

        val block: suspend CoroutineScope.() -> Unit = {
            val refiner = textRefiner
            if (refiner == null) {
                _uiState.value = _uiState.value.copy(
                    state = DictationState.DONE,
                    statusMessage = "Kept raw text",
                    canUndo = false,
                    canRefine = true
                )
            } else {
                val refined = try {
                    withTimeoutOrNull(REFINE_TIMEOUT_MS) {
                        refiner(snapshot.insertedText)
                    }
                } catch (e: Exception) {
                    null
                }

                if (refined.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(
                        state = DictationState.DONE,
                        statusMessage = "Kept raw text",
                        canUndo = false,
                        canRefine = true
                    )
                } else {
                    applySafeReplace(sess, snapshot, refined)
                }
            }
        }

        refineJob = if (dispatcher != null) {
            scope.launch(dispatcher, block = block)
        } else {
            scope.launch(block = block)
        }
    }

    private fun applySafeReplace(
        sess: DictationSession,
        snapshot: ReplaceSnapshot,
        refinedText: String
    ) {
        // Safe replace checks:
        // 1. editorToken must match session token
        if (sess.invalid || editor.editorToken() != snapshot.editorToken) {
            pendingRefinedText = refinedText
            _uiState.value = _uiState.value.copy(
                state = DictationState.DONE,
                statusMessage = "Kept raw text",
                canUndo = false,
                canRefine = false,
                liveText = snapshot.insertedText
            )
            return
        }

        // 2. hasSelection must be false
        if (editor.hasSelection()) {
            pendingRefinedText = refinedText
            _uiState.value = _uiState.value.copy(
                state = DictationState.DONE,
                statusMessage = "Refinement available: tap to apply",
                canUndo = false,
                canRefine = false,
                liveText = snapshot.insertedText
            )
            return
        }

        // 3. textBeforeCursor must match insertedText
        val before = editor.textBeforeCursor(snapshot.insertedCodePoints)
        val matches = before == snapshot.insertedText ||
            (snapshot.insertedText.length != snapshot.insertedCodePoints &&
             editor.textBeforeCursor(snapshot.insertedText.length) == snapshot.insertedText)
        if (!matches) {
            pendingRefinedText = refinedText
            _uiState.value = _uiState.value.copy(
                state = DictationState.DONE,
                statusMessage = "Refinement available: tap to apply",
                canUndo = false,
                canRefine = false,
                liveText = snapshot.insertedText
            )
            return
        }

        // Replace
        editor.beginBatchEdit()
        try {
            val deleted = editor.deleteSurroundingCodePoints(snapshot.insertedCodePoints)
            if (!deleted) {
                _uiState.value = _uiState.value.copy(
                    state = DictationState.DONE,
                    statusMessage = "Kept raw text",
                    canUndo = false,
                    canRefine = true,
                    liveText = snapshot.insertedText
                )
                return
            }
            val committed = editor.commitReplacement(refinedText)
            if (!committed) {
                editor.commitReplacement(snapshot.insertedText)
                _uiState.value = _uiState.value.copy(
                    state = DictationState.DONE,
                    statusMessage = "Kept raw text",
                    canUndo = false,
                    canRefine = true,
                    liveText = snapshot.insertedText
                )
                return
            }

            val refinedCodePoints = refinedText.codePointCount(0, refinedText.length)
            lastUndoState = UndoState(
                rawText = snapshot.insertedText,
                refinedText = refinedText,
                editorToken = snapshot.editorToken,
                codePoints = refinedCodePoints
            )
            _uiState.value = _uiState.value.copy(
                state = DictationState.DONE,
                statusMessage = "Refined.",
                canUndo = true,
                canRefine = false,
                liveText = refinedText,
                sessionId = snapshot.sessionId
            )
        } finally {
            editor.endBatchEdit()
        }
    }

    companion object {
        private const val REFINE_TIMEOUT_MS = 15_000L
    }
}
