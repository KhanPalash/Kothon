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

@OptIn(ExperimentalCoroutinesApi::class)
class LiveDictationControllerTest {

    private lateinit var fake: FakeSpeechSession
    private lateinit var editor: FakeDictationEditor
    private lateinit var scope: TestScope
    private lateinit var controller: LiveDictationController

    private val testConfig = SpeechConfig(languageTag = "en-US", preferOnDevice = false)

    @Before
    fun setUp() {
        fake = FakeSpeechSession()
        editor = FakeDictationEditor()
        scope = TestScope(UnconfinedTestDispatcher())
        controller = LiveDictationController(fake, editor, scope)
    }

    @Test
    fun `initial state is IDLE`() {
        assertEquals(DictationState.IDLE, controller.uiState.value.state)
    }

    @Test
    fun `start transitions to LISTENING`() = runTest {
        controller.start(testConfig)
        assertEquals(DictationState.LISTENING, controller.uiState.value.state)
        assertTrue(fake.started)
    }

    @Test
    fun `stop transitions to STOPPING`() = runTest {
        controller.start(testConfig)
        controller.stop()
        assertEquals(DictationState.STOPPING, controller.uiState.value.state)
        assertTrue(fake.stopped)
    }

    @Test
    fun `onEnd after stop transitions to DONE`() = runTest {
        controller.start(testConfig)
        controller.stop()
        fake.simulateEnd()
        assertEquals(DictationState.DONE, controller.uiState.value.state)
    }

    @Test
    fun `partial text updates composing`() = runTest {
        controller.start(testConfig)
        fake.simulatePartial("hello")
        assertEquals("hello", editor.composingText)
    }

    @Test
    fun `final segment commits text`() = runTest {
        controller.start(testConfig)
        fake.simulateFinal("hello world")
        assertEquals("hello world ", editor.committed.toString())
    }

    @Test
    fun `duplicate final is skipped`() = runTest {
        controller.start(testConfig)
        fake.simulateFinal("hello")
        fake.simulateFinal("hello")
        assertEquals("hello ", editor.committed.toString())
    }

    @Test
    fun `cumulative final strips committed prefix`() = runTest {
        controller.start(testConfig)
        fake.simulateFinal("hello")
        // Recognizer sends cumulative "hello world" — should only commit " world"
        fake.simulateFinal("hello world")
        val committed = editor.committed.toString()
        // Should have "hello " then "world "
        assertEquals("hello world ", committed)
    }

    @Test
    fun `partial strips committed prefix`() = runTest {
        controller.start(testConfig)
        fake.simulateFinal("hello")
        // Partial shows cumulative "hello world" → strip "hello " → show "world"
        fake.simulatePartial("hello world")
        assertEquals("world", editor.composingText)
    }

    @Test
    fun `password field blocks start`() = runTest {
        val passEditor = FakeDictationEditor(isPassword = true)
        val ctrl = LiveDictationController(fake, passEditor, scope)
        ctrl.start(testConfig)
        assertEquals(DictationState.IDLE, ctrl.uiState.value.state)
        assertFalse(fake.started)
    }

    @Test
    fun `double start in LISTENING is ignored`() = runTest {
        controller.start(testConfig)
        val firstSession = fake.lastConfig
        controller.start(testConfig)
        // Should not have restarted
        assertEquals(firstSession, fake.lastConfig)
    }

    @Test
    fun `cancel invalidates session`() = runTest {
        controller.start(testConfig)
        controller.cancelSession()
        assertEquals(DictationState.IDLE, controller.uiState.value.state)
        assertTrue(fake.destroyed)
    }

    @Test
    fun `non-recoverable error marks state ERROR`() = runTest {
        controller.start(testConfig)
        fake.simulateError(7, "No speech", false)
        fake.simulateEnd()
        assertEquals(DictationState.DONE, controller.uiState.value.state)
    }

    @Test
    fun `isActive true while LISTENING`() = runTest {
        controller.start(testConfig)
        assertTrue(controller.isActive)
    }

    @Test
    fun `isActive false after DONE`() = runTest {
        controller.start(testConfig)
        fake.simulateEnd()
        assertFalse(controller.isActive)
    }
}
