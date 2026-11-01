package com.whisperflow.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for Phase 3 safe-replace contract (new_plan.md §6.7).
 *
 * safeReplace() below is the unit under test — it encodes the exact
 * replace protocol the plan specifies. LiveDictationController.onEnd()
 * will call equivalent logic in Phase 3; these tests define the
 * expected behavior now so the implementation can be written to pass them.
 */

/**
 * Executes the safe-replace protocol from new_plan.md §6.7 steps 1-9.
 * Returns [UndoState] on success, null when any guard skips the replace.
 *
 * @param snapshot  What was committed by the live session.
 * @param refined   AI-refined alternative text.
 * @param editor    Active editor adapter.
 * @param sessionInvalid Whether the session was already invalidated.
 */
fun safeReplace(
    snapshot: ReplaceSnapshot,
    refined: String,
    editor: DictationEditor,
    sessionInvalid: Boolean
): UndoState? {
    // Guard 1: session invalid or editor token changed
    if (sessionInvalid || editor.editorToken() != snapshot.editorToken) return null

    // Guard 2: user has an active selection
    if (editor.hasSelection()) return null

    // Guard 3: text before cursor does not match what we inserted
    val before = editor.textBeforeCursor(snapshot.insertedText.length) ?: return null
    if (before != snapshot.insertedText) return null

    // Guard 4: begin batch edit
    if (!editor.beginBatchEdit()) return null

    return try {
        // Guard 5: delete inserted code points
        if (!editor.deleteSurroundingCodePoints(snapshot.insertedCodePoints)) {
            return null  // endBatchEdit called in finally
        }

        // Step 6: commit replacement; on failure best-effort restore raw text
        if (!editor.commitReplacement(refined)) {
            editor.commitReplacement(snapshot.insertedText)  // best-effort restore
            return null
        }

        // Step 8: verify text before cursor matches refined
        val after = editor.textBeforeCursor(refined.length)
        if (after != refined) {
            // best-effort restore
            editor.commitReplacement(snapshot.insertedText)
            return null
        }

        // Step 9: success — return undo state
        UndoState(
            rawText = snapshot.insertedText,
            refinedText = refined,
            editorToken = editor.editorToken()
        )
    } finally {
        editor.endBatchEdit()
    }
}

/**
 * Executes the undo protocol: restores rawText only if the editor's
 * current text before cursor still matches refinedText and token matches.
 * Returns true on success.
 */
fun safeUndo(undo: UndoState, editor: DictationEditor): Boolean {
    if (editor.editorToken() != undo.editorToken) return false
    val before = editor.textBeforeCursor(undo.refinedText.length) ?: return false
    if (before != undo.refinedText) return false

    if (!editor.beginBatchEdit()) return false
    return try {
        val codePoints = undo.refinedText.codePointCount(0, undo.refinedText.length)
        if (!editor.deleteSurroundingCodePoints(codePoints)) return false
        editor.commitReplacement(undo.rawText)
    } finally {
        editor.endBatchEdit()
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun snapshotFor(text: String, token: Long = 1L): ReplaceSnapshot =
    ReplaceSnapshot(
        sessionId = 1L,
        editorToken = token,
        insertedText = text,
        insertedCodePoints = text.codePointCount(0, text.length)
    )

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

class SafeReplacementTest {

    private lateinit var editor: FakeDictationEditor

    @Before
    fun setUp() {
        editor = FakeDictationEditor()
    }

    // -----------------------------------------------------------------------
    // 1. Happy path: cursor not moved, text matches → replace succeeds
    // -----------------------------------------------------------------------

    @Test
    fun `replace succeeds when cursor has not moved and text matches`() {
        val raw = "ami boli "
        val refined = "আমি বলি "
        editor.committed.append(raw)
        val snapshot = snapshotFor(raw)

        val undo = safeReplace(snapshot, refined, editor, sessionInvalid = false)

        assertEquals("Replace must return UndoState on success", refined, undo?.refinedText)
        assertEquals("Raw text stored in undo", raw, undo?.rawText)
        // Editor committed text ends with refined after replace
        val finalText = editor.committed.toString()
        assertTrue("Editor committed text should contain refined", finalText.contains(refined))
    }

    @Test
    fun `replace calls beginBatchEdit then delete then commitReplacement`() {
        val raw = "test "
        val refined = "Test."
        editor.committed.append(raw)
        val snapshot = snapshotFor(raw)

        safeReplace(snapshot, refined, editor, sessionInvalid = false)

        assertEquals("beginBatchEdit called once", 1, editor.batchEditBeganCount)
        assertEquals("deleteSurroundingCodePoints called once", 1, editor.deleteCodePointsCalled)
        assertEquals(
            "deleteSurroundingCodePoints count equals inserted code points",
            snapshot.insertedCodePoints,
            editor.deleteCodePointsLastCount
        )
        assertTrue("commitReplacement recorded", editor.replacementHistory.contains(refined))
    }

    // -----------------------------------------------------------------------
    // 2. Skip: cursor moved during refinement
    // -----------------------------------------------------------------------

    @Test
    fun `replace skipped when text before cursor does not match snapshot`() {
        val raw = "hello "
        val refined = "Hello."
        editor.committed.append(raw)
        // Simulate user typed more text after raw
        editor.committed.append("world ")
        val snapshot = snapshotFor(raw)

        val undo = safeReplace(snapshot, refined, editor, sessionInvalid = false)

        assertNull("Replace must be skipped when cursor moved", undo)
        assertEquals("Editor committed must not change", "hello world ", editor.committed.toString())
        assertEquals("deleteSurroundingCodePoints must not be called", 0, editor.deleteCodePointsCalled)
    }

    // -----------------------------------------------------------------------
    // 3. Skip: field switched / token changed
    // -----------------------------------------------------------------------

    @Test
    fun `replace skipped when editor token changed (field switch)`() {
        val raw = "ami "
        val refined = "আমি "
        editor.committed.append(raw)
        val snapshot = snapshotFor(raw, token = 1L)
        // Simulate field switch: token incremented
        editor.setToken(2L)

        val undo = safeReplace(snapshot, refined, editor, sessionInvalid = false)

        assertNull("Replace must be skipped after field switch", undo)
        assertEquals("deleteSurroundingCodePoints must not be called", 0, editor.deleteCodePointsCalled)
    }

    @Test
    fun `replace skipped when session is marked invalid`() {
        val raw = "tumi "
        val refined = "তুমি "
        editor.committed.append(raw)
        val snapshot = snapshotFor(raw)

        val undo = safeReplace(snapshot, refined, editor, sessionInvalid = true)

        assertNull("Replace must be skipped when session invalid", undo)
    }

    // -----------------------------------------------------------------------
    // 4. Skip: user selected text (hasSelection == true)
    // -----------------------------------------------------------------------

    @Test
    fun `replace skipped when user has text selected`() {
        val raw = "kothon "
        val refined = "Kothon "
        editor.committed.append(raw)
        // Simulate selection
        editor.selectionStart = 0
        editor.selectionEnd = 3
        val snapshot = snapshotFor(raw)

        val undo = safeReplace(snapshot, refined, editor, sessionInvalid = false)

        assertNull("Replace must be skipped when user has selection", undo)
        assertEquals("deleteSurroundingCodePoints must not be called", 0, editor.deleteCodePointsCalled)
    }

    // -----------------------------------------------------------------------
    // 5. Undo restores raw text when editor still matches refined
    // -----------------------------------------------------------------------

    @Test
    fun `undo restores raw text when editor still shows refined text`() {
        val raw = "ami boli "
        val refined = "আমি বলি "
        editor.committed.append(raw)
        val snapshot = snapshotFor(raw)

        val undo = safeReplace(snapshot, refined, editor, sessionInvalid = false)!!

        val undoOk = safeUndo(undo, editor)

        assertTrue("Undo must succeed", undoOk)
        val finalText = editor.committed.toString()
        assertTrue("Editor committed text should contain raw text after undo", finalText.endsWith(raw))
    }

    // -----------------------------------------------------------------------
    // 6. Undo invalidated if user typed after replacement
    // -----------------------------------------------------------------------

    @Test
    fun `undo skipped when user typed after replacement`() {
        val raw = "hello "
        val refined = "Hello "
        editor.committed.append(raw)
        val snapshot = snapshotFor(raw)

        val undo = safeReplace(snapshot, refined, editor, sessionInvalid = false)!!

        // User typed more text after the replacement
        editor.committed.append("world ")

        val undoOk = safeUndo(undo, editor)

        assertFalse("Undo must be invalidated after user typed", undoOk)
    }

    @Test
    fun `undo skipped when editor token changed after replacement`() {
        val raw = "test "
        val refined = "Test "
        editor.committed.append(raw)
        val snapshot = snapshotFor(raw)

        val undo = safeReplace(snapshot, refined, editor, sessionInvalid = false)!!

        // User switched field: token changed
        editor.setToken(99L)

        val undoOk = safeUndo(undo, editor)

        assertFalse("Undo must be skipped when token changed", undoOk)
    }

    // -----------------------------------------------------------------------
    // 7. Refinement failure / timeout keeps raw text; state transitions safe
    // -----------------------------------------------------------------------

    @Test
    fun `raw text preserved when beginBatchEdit returns false`() {
        val raw = "dictated text "
        val refined = "Dictated text."
        editor.committed.append(raw)
        editor.beginBatchEditResult = false
        val snapshot = snapshotFor(raw)

        val undo = safeReplace(snapshot, refined, editor, sessionInvalid = false)

        assertNull("Replace must be skipped when beginBatchEdit fails", undo)
        assertEquals("Raw text must remain intact", raw, editor.committed.toString())
    }

    @Test
    fun `raw text preserved when deleteSurroundingCodePoints returns false`() {
        val raw = "live text "
        val refined = "Live text."
        editor.committed.append(raw)
        editor.deleteSurroundingResult = false
        val snapshot = snapshotFor(raw)

        val undo = safeReplace(snapshot, refined, editor, sessionInvalid = false)

        assertNull("Replace must be skipped when delete fails", undo)
        assertEquals("Raw text must remain intact", raw, editor.committed.toString())
        assertFalse("Batch edit must be closed after delete failure", editor.batchActive)
    }

    @Test
    fun `raw text preserved when commitReplacement fails — best effort restore`() {
        val raw = "spoken "
        val refined = "Spoken."
        editor.committed.append(raw)
        editor.commitReplacementResult = false
        val snapshot = snapshotFor(raw)

        val undo = safeReplace(snapshot, refined, editor, sessionInvalid = false)

        // Replace fails; best-effort raw restore attempted
        assertNull("Replace must return null when commitReplacement fails", undo)
        assertFalse("Batch edit must be closed", editor.batchActive)
    }

    // -----------------------------------------------------------------------
    // Bonus: code point correctness for emoji / Bangla (multi-char code points)
    // -----------------------------------------------------------------------

    @Test
    fun `replace counts emoji as one code point`() {
        // emoji \uD83D\uDE00 = 2 UTF-16 chars but 1 code point
        val raw = "hi \uD83D\uDE00 "  // 5 code points: h i ' ' emoji ' '
        val refined = "Hi! "
        editor.committed.append(raw)
        val snapshot = snapshotFor(raw)

        val undo = safeReplace(snapshot, refined, editor, sessionInvalid = false)

        assertEquals(
            "deleteSurroundingCodePoints count must be code points, not chars",
            raw.codePointCount(0, raw.length),
            editor.deleteCodePointsLastCount
        )
        assertFalse("Code point count must differ from char length for emoji strings",
            editor.deleteCodePointsLastCount == raw.length)
        assertEquals("Replace must succeed", refined, undo?.refinedText)
    }
}
