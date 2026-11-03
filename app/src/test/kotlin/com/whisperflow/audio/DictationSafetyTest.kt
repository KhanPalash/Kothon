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
class DictationSafetyTest {

    private lateinit var fake: FakeSpeechSession
    private lateinit var scope: TestScope

    private val testConfig = SpeechConfig(languageTag = "bn-BD", preferOnDevice = true)

    @Before
    fun setUp() {
        fake = FakeSpeechSession()
        scope = TestScope(UnconfinedTestDispatcher())
    }

    @Test
    fun `password field no speech session started`() = runTest {
        val editor = FakeDictationEditor(isPassword = true)
        val ctrl = LiveDictationController(fake, editor, scope)
        ctrl.start(testConfig)
        assertFalse("SpeechSession must not start for password field", fake.started)
    }

    @Test
    fun `password field status message set`() = runTest {
        val editor = FakeDictationEditor(isPassword = true)
        val ctrl = LiveDictationController(fake, editor, scope)
        ctrl.start(testConfig)
        assertTrue(ctrl.uiState.value.statusMessage.contains("password", ignoreCase = true))
    }

    @Test
    fun `commit failure invalidates session and stops`() = runTest {
        val editor = FakeDictationEditor(commitSupported = false)
        val ctrl = LiveDictationController(fake, editor, scope)
        ctrl.start(testConfig)
        fake.simulateFinal("test")
        assertEquals(DictationState.ERROR, ctrl.uiState.value.state)
    }

    @Test
    fun `composing not supported finals still commit`() = runTest {
        val editor = FakeDictationEditor(composingSupported = false)
        val ctrl = LiveDictationController(fake, editor, scope)
        ctrl.start(testConfig)
        fake.simulatePartial("test")
        assertEquals("", editor.composingText) // composing not applied
        fake.simulateFinal("test")
        assertEquals("test ", editor.committed.toString()) // final still committed
    }

    @Test
    fun `blank partial is ignored`() = runTest {
        val editor = FakeDictationEditor()
        val ctrl = LiveDictationController(fake, editor, scope)
        ctrl.start(testConfig)
        fake.simulatePartial("   ")
        assertEquals("", editor.composingText)
    }

    @Test
    fun `blank final is ignored`() = runTest {
        val editor = FakeDictationEditor()
        val ctrl = LiveDictationController(fake, editor, scope)
        ctrl.start(testConfig)
        fake.simulateFinal("   ")
        assertEquals("", editor.committed.toString())
    }

    @Test
    fun `FakeDictationEditor deleteSurroundingCodePoints works`() {
        val editor = FakeDictationEditor()
        editor.committed.append("hello world ")
        // "hello world " is 12 chars, 12 code points
        val ok = editor.deleteSurroundingCodePoints(12)
        assertTrue(ok)
        assertEquals("", editor.committed.toString())
    }

    @Test
    fun `FakeDictationEditor deleteSurroundingCodePoints emoji`() {
        val editor = FakeDictationEditor()
        // emoji is 2 chars but 1 code point
        editor.committed.append("hi \uD83D\uDE00")
        val ok = editor.deleteSurroundingCodePoints(4) // h,i,' ',emoji = 4 code points
        assertTrue(ok)
        assertEquals("", editor.committed.toString())
    }

    @Test
    fun `cancel during listening destroys session`() = runTest {
        val editor = FakeDictationEditor()
        val ctrl = LiveDictationController(fake, editor, scope)
        ctrl.start(testConfig)
        ctrl.cancelSession()
        assertTrue(fake.destroyed)
        assertEquals(DictationState.IDLE, ctrl.uiState.value.state)
    }
}
