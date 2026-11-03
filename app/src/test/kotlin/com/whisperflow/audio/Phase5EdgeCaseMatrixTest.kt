package com.whisperflow.audio

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Phase5EdgeCaseMatrixTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun `Bangla compound grapheme replacement preserves codepoint integrity`() = testScope.runTest {
        val editor = FakeDictationEditor()
        // Start with empty editor to test pure Bangla grapheme handling
        val session = FakeSpeechSession()
        val controller = LiveDictationController(
            speechSession = session,
            editor = editor,
            scope = this,
            dispatcher = testDispatcher,
            autoRefine = false
        )

        controller.start(SpeechConfig("bn-BD", preferOnDevice = true))
        advanceUntilIdle()

        session.sendPartial("আমি খুব ভালো")
        advanceUntilIdle()
        session.sendFinal("আমি খুব ভালো")
        session.simulateEnd()
        advanceUntilIdle()

        val result = editor.getText()
        assertTrue("Should contain Bangla text", result.contains("আমি খুব ভালো"))
        assertEquals(13, result.codePointCount(0, result.length)) // "আমি খুব ভালো " = 13 code points
    }

    @Test
    fun `Emoji and ZWJ sequences preserved during deletion`() = testScope.runTest {
        val editor = FakeDictationEditor()
        editor.insertText("Hello 👨‍👩‍👧‍👦 world")
        editor.setCursor(14) // After emoji family

        val session = FakeSpeechSession()
        val controller = LiveDictationController(
            speechSession = session,
            editor = editor,
            scope = this,
            dispatcher = testDispatcher,
            autoRefine = false
        )

        controller.start(SpeechConfig("en-US", preferOnDevice = true))
        advanceUntilIdle()

        session.sendFinal("there")
        session.simulateEnd()
        advanceUntilIdle()

        val result = editor.getText()
        assertTrue(result.contains("👨‍👩‍👧‍👦"))
        assertTrue(result.contains("there"))
    }

    @Test
    fun `Rapid start-stop cycles debounced correctly`() = testScope.runTest {
        val editor = FakeDictationEditor()
        val session = FakeSpeechSession()
        val controller = LiveDictationController(
            speechSession = session,
            editor = editor,
            scope = this,
            dispatcher = testDispatcher,
            autoRefine = false
        )

        controller.start(SpeechConfig("en-US", preferOnDevice = true))
        advanceUntilIdle()
        assertEquals(DictationState.LISTENING, controller.uiState.value.state)

        controller.start(SpeechConfig("en-US", preferOnDevice = true))
        advanceUntilIdle()
        assertEquals(DictationState.LISTENING, controller.uiState.value.state)

        controller.stop()
        session.simulateEnd()
        advanceUntilIdle()
        assertEquals(DictationState.DONE, controller.uiState.value.state)

        controller.start(SpeechConfig("en-US", preferOnDevice = true))
        advanceUntilIdle()
        assertEquals(DictationState.LISTENING, controller.uiState.value.state)
    }

    @Test
    fun `Field switch during refinement invalidates session`() = testScope.runTest {
        val editor = FakeDictationEditor()
        editor.setToken(100L)

        val session = FakeSpeechSession()
        val refineDelay: suspend (String) -> String? = { kotlinx.coroutines.delay(5000); "refined" }
        val controller = LiveDictationController(
            speechSession = session,
            editor = editor,
            scope = this,
            dispatcher = testDispatcher,
            autoRefine = true,
            textRefiner = refineDelay
        )

        controller.start(SpeechConfig("en-US", preferOnDevice = true))
        advanceUntilIdle()
        session.sendFinal("test text")
        session.simulateEnd()

        assertEquals(DictationState.REFINING, controller.uiState.value.state)

        editor.setToken(200L)
        controller.onFieldChanged(200L)
        advanceUntilIdle()

        assertEquals(DictationState.IDLE, controller.uiState.value.state)
        assertFalse(controller.uiState.value.canUndo)
    }

    @Test
    fun `Undo invalidated after user manual edit`() = testScope.runTest {
        val editor = FakeDictationEditor()
        val session = FakeSpeechSession()
        val refiner: suspend (String) -> String? = { input -> "$input refined" }
        val controller = LiveDictationController(
            speechSession = session,
            editor = editor,
            scope = this,
            dispatcher = testDispatcher,
            autoRefine = true,
            textRefiner = refiner
        )

        controller.start(SpeechConfig("en-US", preferOnDevice = true))
        advanceUntilIdle()
        session.sendFinal("original")
        session.simulateEnd()
        advanceUntilIdle()

        assertEquals(DictationState.DONE, controller.uiState.value.state)
        assertTrue(controller.uiState.value.canUndo)

        editor.insertText(" modified")
        val undoSucceeded = controller.undo()
        assertFalse(undoSucceeded)
        assertFalse(controller.uiState.value.canUndo)
    }

    @Test
    fun `Refinement timeout falls back to raw text`() = testScope.runTest {
        val editor = FakeDictationEditor()
        val session = FakeSpeechSession()
        val slowRefiner: suspend (String) -> String? = {
            kotlinx.coroutines.delay(20_000)
            "never arrives"
        }
        val controller = LiveDictationController(
            speechSession = session,
            editor = editor,
            scope = this,
            dispatcher = testDispatcher,
            autoRefine = true,
            textRefiner = slowRefiner
        )

        controller.start(SpeechConfig("en-US", preferOnDevice = true))
        advanceUntilIdle()
        session.sendFinal("raw text")
        session.simulateEnd()
        advanceUntilIdle()

        assertEquals(DictationState.DONE, controller.uiState.value.state)
        assertEquals("raw text ", editor.getText())
        assertFalse(controller.uiState.value.canUndo)
    }

    @Test
    fun `Duplicate final suppression prevents double commit`() = testScope.runTest {
        val editor = FakeDictationEditor()
        val session = FakeSpeechSession()
        val controller = LiveDictationController(
            speechSession = session,
            editor = editor,
            scope = this,
            dispatcher = testDispatcher,
            autoRefine = false
        )

        controller.start(SpeechConfig("en-US", preferOnDevice = true))
        advanceUntilIdle()

        session.sendPartial("hello world")
        advanceUntilIdle()
        session.sendFinal("hello world")
        session.simulateEnd()
        advanceUntilIdle()
        session.sendFinal("hello world")
        session.simulateEnd()
        advanceUntilIdle()

        val text = editor.getText()
        assertEquals("hello world ", text)
    }

    @Test
    fun `Password field blocks voice input`() = testScope.runTest {
        val editor = FakeDictationEditor()
        editor.setPasswordField(true)

        val session = FakeSpeechSession()
        val controller = LiveDictationController(
            speechSession = session,
            editor = editor,
            scope = this,
            dispatcher = testDispatcher,
            autoRefine = false
        )

        controller.start(SpeechConfig("en-US", preferOnDevice = true))
        advanceUntilIdle()

        assertEquals(DictationState.IDLE, controller.uiState.value.state)
        assertTrue(controller.uiState.value.statusMessage.contains("password"))
        assertFalse(session.isStarted())
    }

    @Test
    fun `Editor token mismatch rejects replacement`() = testScope.runTest {
        val editor = FakeDictationEditor()
        editor.setToken(100L)

        val session = FakeSpeechSession()
        val refiner: suspend (String) -> String? = { "refined" }
        val controller = LiveDictationController(
            speechSession = session,
            editor = editor,
            scope = this,
            dispatcher = testDispatcher,
            autoRefine = true,
            textRefiner = refiner
        )

        controller.start(SpeechConfig("en-US", preferOnDevice = true))
        advanceUntilIdle()
        session.sendFinal("test")
        advanceUntilIdle()

        editor.setToken(200L)
        val undoSucceeded = controller.undo()
        assertFalse(undoSucceeded)
    }

    @Test
    fun `Selection exists blocks safe replacement`() = testScope.runTest {
        val editor = FakeDictationEditor()
        editor.insertText("existing text")
        editor.setSelection(0, 8)

        val session = FakeSpeechSession()
        val refiner: suspend (String) -> String? = { "refined" }
        val controller = LiveDictationController(
            speechSession = session,
            editor = editor,
            scope = this,
            dispatcher = testDispatcher,
            autoRefine = true,
            textRefiner = refiner
        )

        controller.start(SpeechConfig("en-US", preferOnDevice = true))
        advanceUntilIdle()
        session.sendFinal("new")
        advanceUntilIdle()

        editor.setSelection(0, 3)
        val undoSucceeded = controller.undo()
        assertFalse(undoSucceeded)
    }

    @Test
    fun `Cursor moved after commit invalidates undo`() = testScope.runTest {
        val editor = FakeDictationEditor()
        val session = FakeSpeechSession()
        val refiner: suspend (String) -> String? = { input -> "$input refined" }
        val controller = LiveDictationController(
            speechSession = session,
            editor = editor,
            scope = this,
            dispatcher = testDispatcher,
            autoRefine = true,
            textRefiner = refiner
        )

        controller.start(SpeechConfig("en-US", preferOnDevice = true))
        advanceUntilIdle()
        session.sendFinal("test")
        advanceUntilIdle()

        val beforeCursor = editor.getCursor()
        editor.setCursor(0)

        val undoSucceeded = controller.undo()
        assertFalse(undoSucceeded)
        assertFalse(controller.uiState.value.canUndo)
    }

    @Test
    fun `Multiple partials coalesce correctly`() = testScope.runTest {
        val editor = FakeDictationEditor()
        val session = FakeSpeechSession()
        val controller = LiveDictationController(
            speechSession = session,
            editor = editor,
            scope = this,
            dispatcher = testDispatcher,
            autoRefine = false
        )

        controller.start(SpeechConfig("en-US", preferOnDevice = true))
        advanceUntilIdle()

        session.sendPartial("hello")
        advanceUntilIdle()
        session.sendPartial("hello world")
        advanceUntilIdle()
        session.sendPartial("hello world test")
        advanceUntilIdle()
        session.sendFinal("hello world test")
        session.simulateEnd()
        advanceUntilIdle()

        assertEquals("hello world test ", editor.getText())
    }

    @Test
    fun `Cancel during refinement cleans up state`() = testScope.runTest {
        val editor = FakeDictationEditor()
        val session = FakeSpeechSession()
        val slowRefiner: suspend (String) -> String? = {
            kotlinx.coroutines.delay(10_000)
            "never"
        }
        val controller = LiveDictationController(
            speechSession = session,
            editor = editor,
            scope = this,
            dispatcher = testDispatcher,
            autoRefine = true,
            textRefiner = slowRefiner
        )

        controller.start(SpeechConfig("en-US", preferOnDevice = true))
        advanceUntilIdle()
        session.sendFinal("test")
        session.simulateEnd()

        assertEquals(DictationState.REFINING, controller.uiState.value.state)

        controller.cancelSession()
        advanceUntilIdle()

        assertEquals(DictationState.IDLE, controller.uiState.value.state)
        assertFalse(controller.uiState.value.canUndo)
        assertNull(controller.pendingRefinedText)
    }
}
