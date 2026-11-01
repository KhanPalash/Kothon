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
class LiveDictationControllerPhase4Test {

    private lateinit var fake: FakeSpeechSession
    private lateinit var editor: FakeDictationEditor
    private lateinit var scope: TestScope

    private val testConfig = SpeechConfig(languageTag = "en-US", preferOnDevice = false)

    private fun makeController(
        autoRefine: Boolean = false,
        refiner: (suspend (String) -> String?)? = null
    ): LiveDictationController = LiveDictationController(
        speechSession = fake,
        editor = editor,
        scope = scope,
        autoRefine = autoRefine,
        textRefiner = refiner
    )

    @Before
    fun setUp() {
        fake = FakeSpeechSession()
        editor = FakeDictationEditor()
        scope = TestScope(UnconfinedTestDispatcher())
    }

    @Test
    fun `liveText tracks partial and committed text`() = runTest {
        val ctrl = makeController()
        ctrl.start(testConfig)

        fake.simulatePartial("hel")
        assertEquals("hel", ctrl.uiState.value.liveText)

        fake.simulateFinal("hello")
        assertEquals("hello ", ctrl.uiState.value.liveText)

        fake.simulatePartial("hello world")
        assertEquals("hello world", ctrl.uiState.value.liveText)
    }

    @Test
    fun `custom separator is used when committing finals`() = runTest {
        val ctrl = makeController()
        ctrl.start(testConfig, separator = "")

        fake.simulateFinal("こんにちは")
        fake.simulateFinal("世界")

        assertEquals("こんにちは世界", editor.committed.toString())
        assertEquals("こんにちは世界", ctrl.uiState.value.liveText)
    }

    @Test
    fun `sessionId is stable for a session and increments on restart`() = runTest {
        val ctrl = makeController()
        ctrl.start(testConfig)
        val firstId = ctrl.uiState.value.sessionId
        assertTrue(firstId > 0L)

        fake.simulateFinal("hello")
        ctrl.stop()
        fake.simulateEnd()
        assertEquals(firstId, ctrl.uiState.value.sessionId)

        ctrl.start(testConfig)
        assertTrue(ctrl.uiState.value.sessionId > firstId)
    }

    @Test
    fun `autoRefine off exposes raw text and canRefine`() = runTest {
        val ctrl = makeController(autoRefine = false)
        ctrl.start(testConfig)
        fake.simulateFinal("hello world")
        ctrl.stop()
        fake.simulateEnd()

        assertEquals(DictationState.DONE, ctrl.uiState.value.state)
        assertTrue(ctrl.uiState.value.canRefine)
        assertFalse(ctrl.uiState.value.canUndo)
        assertEquals("hello world ", ctrl.uiState.value.liveText)
        assertEquals("hello world ", editor.committed.toString())
    }

    @Test
    fun `refineNow manually refines and records undo state`() = runTest {
        var refineCalls = 0
        val ctrl = makeController(autoRefine = false) { _ ->
            refineCalls++
            "REFINED"
        }
        ctrl.start(testConfig)
        fake.simulateFinal("hello world")
        ctrl.stop()
        fake.simulateEnd()

        ctrl.refineNow()

        assertEquals(1, refineCalls)
        assertEquals(DictationState.DONE, ctrl.uiState.value.state)
        assertEquals("REFINED", editor.committed.toString())
        assertEquals("REFINED", ctrl.uiState.value.liveText)
        assertEquals("hello world ", ctrl.lastRawText)
        assertTrue(ctrl.uiState.value.canUndo)
        assertFalse(ctrl.uiState.value.canRefine)
    }

    @Test
    fun `undo after manual refine restores raw live text`() = runTest {
        val ctrl = makeController(autoRefine = false) { "REFINED" }
        ctrl.start(testConfig)
        fake.simulateFinal("hello world")
        ctrl.stop()
        fake.simulateEnd()
        ctrl.refineNow()

        val undone = ctrl.undo()

        assertTrue(undone)
        assertEquals("hello world ", editor.committed.toString())
        assertEquals("hello world ", ctrl.uiState.value.liveText)
        assertFalse(ctrl.uiState.value.canUndo)
    }

    @Test
    fun `refineNow is a no-op when nothing was spoken`() = runTest {
        var refineCalls = 0
        val ctrl = makeController(autoRefine = false) {
            refineCalls++
            "REFINED"
        }
        ctrl.start(testConfig)
        ctrl.stop()
        fake.simulateEnd()

        ctrl.refineNow()

        assertEquals(0, refineCalls)
        assertEquals(DictationState.DONE, ctrl.uiState.value.state)
    }

    @Test
    fun `refineNow is a no-op while listening`() = runTest {
        var refineCalls = 0
        val ctrl = makeController(autoRefine = false) {
            refineCalls++
            "REFINED"
        }
        ctrl.start(testConfig)
        fake.simulateFinal("hello")

        ctrl.refineNow()

        assertEquals(0, refineCalls)
        assertEquals(DictationState.LISTENING, ctrl.uiState.value.state)
    }

    @Test
    fun `autoRefine on keeps refined text and no manual refine`() = runTest {
        val ctrl = makeController(autoRefine = true) { "REFINED" }
        ctrl.start(testConfig)
        fake.simulateFinal("hello")
        ctrl.stop()
        fake.simulateEnd()

        assertEquals("REFINED", editor.committed.toString())
        assertEquals("REFINED", ctrl.uiState.value.liveText)
        assertTrue(ctrl.uiState.value.canUndo)
        assertFalse(ctrl.uiState.value.canRefine)
    }

    @Test
    fun `sessionText reflects what the controller inserted`() = runTest {
        val ctrl = makeController(autoRefine = false)
        ctrl.start(testConfig)
        fake.simulateFinal("hello")
        ctrl.stop()
        fake.simulateEnd()

        assertEquals("hello ", ctrl.sessionText)
    }
}
