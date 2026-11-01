package com.whisperflow.audio

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class RefineReplaceTest {

    private lateinit var fake: FakeSpeechSession
    private lateinit var editor: FakeDictationEditor
    private lateinit var scope: TestScope
    private val testConfig = SpeechConfig(languageTag = "en-US", preferOnDevice = false)

    private fun makeController(
        autoRefine: Boolean = true,
        refiner: (suspend (String) -> String?)? = null
    ): LiveDictationController {
        return LiveDictationController(
            speechSession = fake,
            editor = editor,
            scope = scope,
            autoRefine = autoRefine,
            textRefiner = refiner
        )
    }

    @Before
    fun setUp() {
        fake = FakeSpeechSession()
        editor = FakeDictationEditor()
        scope = TestScope(UnconfinedTestDispatcher())
    }

    // ── Phase 2: REFINING state ───────────────────────────────────────────────

    @Test
    fun `autoRefine true transitions to REFINING on stop then onEnd`() = runTest {
        var refineCalled = false
        val ctrl = makeController(autoRefine = true, refiner = { raw ->
            refineCalled = true
            "refined: $raw"
        })
        ctrl.start(testConfig)
        fake.simulateFinal("hello world")
        ctrl.stop()
        fake.simulateEnd()
        // With UnconfinedTestDispatcher the coroutine runs inline
        assertTrue("refiner must be called", refineCalled)
    }

    @Test
    fun `autoRefine false does not call refiner`() = runTest {
        var refineCalled = false
        val ctrl = makeController(autoRefine = false, refiner = { _ ->
            refineCalled = true
            "should not matter"
        })
        ctrl.start(testConfig)
        fake.simulateFinal("hello world")
        ctrl.stop()
        fake.simulateEnd()
        assertFalse("refiner must NOT be called when autoRefine=false", refineCalled)
    }

    @Test
    fun `blank committed text skips refiner`() = runTest {
        var refineCalled = false
        val ctrl = makeController(autoRefine = true, refiner = { _ ->
            refineCalled = true
            null
        })
        ctrl.start(testConfig)
        // no finals committed
        ctrl.stop()
        fake.simulateEnd()
        assertFalse("refiner must NOT be called for blank text", refineCalled)
    }

    // ── Phase 3: safe replace ─────────────────────────────────────────────────

    @Test
    fun `safe replace succeeds when cursor matches`() = runTest {
        val ctrl = makeController(autoRefine = true, refiner = { _ -> "refined text" })
        ctrl.start(testConfig)
        fake.simulateFinal("hello world")
        ctrl.stop()
        fake.simulateEnd()
        // "hello world " should have been replaced by "refined text"
        assertTrue(
            "editor must contain refined text, got: '${editor.committed}'",
            editor.committed.toString().endsWith("refined text")
        )
    }

    @Test
    fun `safe replace skipped when editor token changed`() = runTest {
        val ctrl = makeController(autoRefine = true, refiner = { _ -> "refined" })
        ctrl.start(testConfig)
        fake.simulateFinal("hello")
        ctrl.stop()
        // change token before onEnd fires
        editor.setToken(99L)
        fake.simulateEnd()
        // raw text stays; refined never inserted
        assertFalse(
            "refined must NOT appear when token changed",
            editor.committed.toString().contains("refined")
        )
    }

    @Test
    fun `safe replace skipped when selection active`() = runTest {
        val ctrl = makeController(autoRefine = true, refiner = { _ -> "refined" })
        ctrl.start(testConfig)
        fake.simulateFinal("hello")
        ctrl.stop()
        editor.selectionStart = 0
        editor.selectionEnd = 3
        fake.simulateEnd()
        assertFalse(
            "refined must NOT appear when selection active",
            editor.committed.toString().contains("refined")
        )
    }

    @Test
    fun `safe replace skipped when textBeforeCursor does not match`() = runTest {
        val ctrl = makeController(autoRefine = true, refiner = { _ -> "refined" })
        ctrl.start(testConfig)
        fake.simulateFinal("hello")
        ctrl.stop()
        // Simulate user typed extra text after dictation
        editor.committed.append("extra")
        fake.simulateEnd()
        assertFalse(
            "refined must NOT appear when cursor mismatch",
            editor.committed.toString().contains("refined")
        )
    }

    @Test
    fun `refiner returning null keeps raw text`() = runTest {
        val ctrl = makeController(autoRefine = true, refiner = { _ -> null })
        ctrl.start(testConfig)
        fake.simulateFinal("hello world")
        ctrl.stop()
        fake.simulateEnd()
        // raw text must still be present
        assertTrue(
            "raw text must be kept when refiner returns null",
            editor.committed.toString().contains("hello world")
        )
    }

    @Test
    fun `refiner returning blank keeps raw text`() = runTest {
        val ctrl = makeController(autoRefine = true, refiner = { _ -> "  " })
        ctrl.start(testConfig)
        fake.simulateFinal("hello world")
        ctrl.stop()
        fake.simulateEnd()
        assertTrue(
            "raw text must be kept when refiner returns blank",
            editor.committed.toString().contains("hello world")
        )
    }

    // ── Undo ─────────────────────────────────────────────────────────────────

    @Test
    fun `undo after replace restores raw text`() = runTest {
        val ctrl = makeController(autoRefine = true, refiner = { _ -> "refined text" })
        ctrl.start(testConfig)
        fake.simulateFinal("hello world")
        ctrl.stop()
        fake.simulateEnd()
        // After replace canUndo should be true
        assertTrue("canUndo must be true after replace", ctrl.uiState.value.canUndo)
        ctrl.undo()
        assertTrue(
            "raw text must be restored after undo, got: '${editor.committed}'",
            editor.committed.toString().contains("hello world")
        )
        assertFalse("canUndo must be false after undo consumed", ctrl.uiState.value.canUndo)
    }

    @Test
    fun `undo is no-op when nothing was replaced`() = runTest {
        val ctrl = makeController(autoRefine = false)
        ctrl.start(testConfig)
        fake.simulateFinal("hello")
        ctrl.stop()
        fake.simulateEnd()
        assertFalse("canUndo must be false when no replace happened", ctrl.uiState.value.canUndo)
        ctrl.undo() // must not throw
    }

    // ── onFieldChanged ────────────────────────────────────────────────────────

    @Test
    fun `onFieldChanged invalidates session immediately`() = runTest {
        val ctrl = makeController(autoRefine = false)
        ctrl.start(testConfig)
        fake.simulateFinal("hello")
        ctrl.onFieldChanged(newToken = 42L)
        assertEquals(DictationState.IDLE, ctrl.uiState.value.state)
        assertTrue("session must be destroyed on field change", fake.destroyed)
    }

    @Test
    fun `onFieldChanged cancels pending refine`() = runTest {
        var refineCalledCount = 0
        // refiner that is slow - but UnconfinedTestDispatcher makes it synchronous;
        // test ensures onFieldChanged before onEnd prevents replace
        val ctrl = makeController(autoRefine = true, refiner = { _ ->
            refineCalledCount++
            "refined"
        })
        ctrl.start(testConfig)
        fake.simulateFinal("hello")
        ctrl.stop()
        // Change field before session ends
        ctrl.onFieldChanged(newToken = 99L)
        fake.simulateEnd()
        // Even if refiner ran, it must not replace in new field
        assertFalse(
            "refined text must NOT appear after field change",
            editor.committed.toString().contains("refined")
        )
    }
}
